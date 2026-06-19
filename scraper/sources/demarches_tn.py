"""
Scraper for demarches.tn — primary automated source (COMMUNITY, not official).

Real data = individual article pages (e.g. /carte-identite-tunisienne/), NOT category
landing pages (e.g. /identite/).

Usage:
  cd scraper
  pip install -r requirements.txt

  # Scrape one real article
  python -m sources.demarches_tn --url https://www.demarches.tn/carte-identite-tunisienne/

  # Discover + scrape real articles from category pages (recommended)
  python -m sources.demarches_tn --articles --limit 10

  # Then import (Spring Boot must be running)
  python import_to_api.py

Every output is DRAFT. Human must verify against services.gov.tn before --verify.
"""

from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup, Tag

from normalize import draft_procedure, pick_short_value, slugify, truncate_field

ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "data" / "raw"
DRAFT_DIR = ROOT / "data" / "draft"
BASE = "https://www.demarches.tn"

HEADERS = {
    "User-Agent": "DosyaScraper/0.1 (+https://github.com/hsanjebri/dossia-; civic research)",
    "Accept-Language": "fr,ar;q=0.9",
}

# Category / nav slugs — NOT procedure articles
CATEGORY_SLUGS = {
    "etat-civil",
    "identite",
    "travail-et-retraite",
    "mobilite",
    "etrangers",
    "tunisiens-france",
    "aide-sociale",
    "formalites",
    "a-propos-de-nous",
    "mentions-legales",
    "politique-de-confidentialite",
    "contactez-nous",
    "formulaire-de-contact",
    "contact",
    "notaires",
}

SEED_CATEGORY_PAGES = [
    f"{BASE}/",
    f"{BASE}/identite/",
    f"{BASE}/etat-civil/",
    f"{BASE}/travail-et-retraite/",
    f"{BASE}/mobilite/",
    f"{BASE}/formalites/documents-administratifs/",
    f"{BASE}/formalites/finances/",
    f"{BASE}/aide-sociale/",
]

CATEGORY_MAP = {
    "etat-civil": "CIVIL_STATUS",
    "identite": "CIVIL_STATUS",
    "nationalite": "CIVIL_STATUS",
    "carte-identite": "CIVIL_STATUS",
    "passeport": "CIVIL_STATUS",
    "certificat": "CIVIL_STATUS",
    "livret": "CIVIL_STATUS",
    "casier": "CIVIL_STATUS",
    "finances": "TAX",
    "travail": "SOCIAL",
    "retraite": "SOCIAL",
    "mobilite": "VEHICLES",
    "voyage": "VEHICLES",
    "education": "EDUCATION",
    "etudes": "EDUCATION",
    "entreprise": "BUSINESS",
    "societe": "BUSINESS",
    "logement": "SOCIAL",
    "aide-sociale": "SOCIAL",
}


def fetch(url: str) -> str:
    response = requests.get(url, timeout=30, headers=HEADERS)
    response.raise_for_status()
    return response.text


def save_raw(html: str, slug: str) -> Path:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    path = RAW_DIR / f"demarches_{slug}_{ts}.html"
    path.write_text(html, encoding="utf-8")
    return path


def article_slug_from_url(url: str) -> str | None:
    path = urlparse(url).path.strip("/")
    if not path or "/" in path:
        return None
    slug = path.lower()
    if slug in CATEGORY_SLUGS:
        return None
    if slug.startswith("wp-"):
        return None
    return slug


def is_article_url(url: str) -> bool:
    return article_slug_from_url(url) is not None


def guess_category(url: str, soup: BeautifulSoup, title: str) -> str:
    haystack = f"{url} {title}".lower()
    for key, value in CATEGORY_MAP.items():
        if key in haystack:
            return value
    for link in soup.select("a[href]"):
        href = (link.get("href") or "").lower()
        for key, value in CATEGORY_MAP.items():
            if key in href:
                return value
    return "SOCIAL"


def content_root(soup: BeautifulSoup):
    return (
        soup.select_one(".elementor-widget-theme-post-content")
        or soup.select_one(".entry-content")
        or soup.find("article")
        or soup.find("main")
    )


def clean_line(text: str) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    text = re.sub(r"^(lire la suite|en savoir plus)\s*→?$", "", text, flags=re.I).strip()
    return text


FOOTER_KEYWORDS = {
    "à propos de nous",
    "a propos de nous",
    "mentions légales",
    "mentions legales",
    "confidentialité",
    "confidentialite",
    "contact",
    "contactez-nous",
}


def is_footer_line(line: str) -> bool:
    lower = line.lower()
    return any(k in lower for k in FOOTER_KEYWORDS)


def lists_after_heading(heading) -> list[str]:
    lines: list[str] = []
    for sibling in heading.find_next_siblings():
        if not isinstance(sibling, Tag):
            continue
        if sibling.name in {"h2", "h3", "h4"}:
            break
        candidate = sibling if sibling.name in {"ul", "ol"} else sibling.find(["ul", "ol"])
        if not isinstance(candidate, Tag):
            continue
        for li in candidate.find_all("li", recursive=False):
            line = clean_line(li.get_text(" ", strip=True))
            if line and len(line) > 8 and not is_footer_line(line):
                lines.append(line)
        if lines:
            break
    return lines


def paragraphs_after_heading(heading) -> list[str]:
    lines: list[str] = []
    for sibling in heading.find_next_siblings():
        if not isinstance(sibling, Tag):
            continue
        if sibling.name in {"h2", "h3", "h4"}:
            break
        if sibling.name == "p":
            text = clean_line(sibling.get_text(" ", strip=True))
            if text and len(text) > 25 and not is_footer_line(text):
                lines.append(text)
        else:
            for p in sibling.find_all("p", recursive=False):
                text = clean_line(p.get_text(" ", strip=True))
                if text and len(text) > 25 and not is_footer_line(text):
                    lines.append(text)
    return lines


def extract_documents(root) -> list[str]:
    if not root:
        return []
    lines: list[str] = []
    seen: set[str] = set()
    doc_keywords = (
        "document", "pièce", "pièces", "justificatif", "papiers", "fournir",
        "dossier", "requis", "nécessaire", "comment renouveler",
    )

    for heading in root.find_all(["h2", "h3", "h4"]):
        title = heading.get_text(" ", strip=True).lower()
        if not any(k in title for k in doc_keywords):
            continue
        for line in lists_after_heading(heading):
            if line.lower() not in seen:
                seen.add(line.lower())
                lines.append(line)
    return lines


def extract_steps(root) -> list[str]:
    if not root:
        return []
    lines: list[str] = []
    seen: set[str] = set()
    step_headings = (
        "comment obtenir", "comment renouveler", "comment faire",
        "où", "ou ", "marche à suivre", "marche a suivre",
        "étapes", "etapes", "combien de temps", "délai", "delai",
    )
    skip_keywords = ("coût", "cout", "validité", "validite", "en quoi consiste", "que se passe", "consiste")
    procedural_keywords = ("où", "ou ", "comment", "combien de temps", "marche à suivre", "étapes")

    for heading in root.find_all(["h2", "h3"]):
        title = heading.get_text(" ", strip=True).lower()
        if any(k in title for k in skip_keywords):
            continue
        if ("biométrique" in title or "biometrique" in title) and not any(
            k in title for k in procedural_keywords
        ):
            continue
        if not any(k in title for k in step_headings):
            continue
        for line in paragraphs_after_heading(heading):
            if line.rstrip().endswith(":"):
                continue
            if line.lower() not in seen:
                seen.add(line.lower())
                lines.append(line)
    return lines


def extract_section_text(root, keywords: tuple[str, ...]) -> str | None:
    if not root:
        return None
    for heading in root.find_all(["h2", "h3", "h4"]):
        title = heading.get_text(" ", strip=True).lower()
        if not any(k in title for k in keywords):
            continue
        parts: list[str] = []
        for sibling in heading.find_next_siblings():
            if sibling.name in {"h2", "h3", "h4"}:
                break
            if sibling.name == "p":
                text = clean_line(sibling.get_text(" ", strip=True))
                if text:
                    parts.append(text)
        if parts:
            return " ".join(parts[:3])
    return None


def extract_fees_and_time(text: str) -> tuple[str | None, str | None]:
    fees = None
    processing = None

    fee_patterns = [
        r"(\d+[,.]?\d*\s*(?:dinars?|TND|DT))",
        r"(gratuit(?:e)?(?:ment)?)",
        r"(sans frais)",
    ]
    for pattern in fee_patterns:
        match = re.search(pattern, text, re.I)
        if match:
            fees = match.group(1)
            break

    time_patterns = [
        r"(\d+\s*(?:à|-)?\s*\d*\s*(?:jours?|semaines?|mois)(?:\s+ouvrables)?)",
        r"(\d+\s*(?:heures?|minutes?))",
        r"(imm[ée]diat(?:ement|)|instantan[ée])",
        r"(m[êe]me jour)",
    ]
    for pattern in time_patterns:
        match = re.search(pattern, text, re.I)
        if match:
            processing = match.group(1)
            break

    return fees, processing


def discover_article_urls(limit: int = 30) -> list[str]:
    seen: set[str] = set()
    urls: list[str] = []

    for page in SEED_CATEGORY_PAGES:
        try:
            html = fetch(page)
        except requests.RequestException:
            continue
        soup = BeautifulSoup(html, "lxml")
        for anchor in soup.select("a[href]"):
            href = anchor.get("href", "")
            if not href:
                continue
            full = urljoin(BASE, href)
            if urlparse(full).netloc.removeprefix("www.") != "demarches.tn":
                continue
            if not is_article_url(full):
                continue
            normalized = full if full.endswith("/") else full + "/"
            if normalized not in seen:
                seen.add(normalized)
                urls.append(normalized)
            if len(urls) >= limit:
                return urls
    return urls


def parse_article(url: str, html: str | None = None) -> dict:
    html = html or fetch(url)
    soup = BeautifulSoup(html, "lxml")
    root = content_root(soup)

    title_el = soup.find("h1")
    title_fr = title_el.get_text(strip=True) if title_el else "Sans titre"

    paragraphs: list[str] = []
    if root:
        for p in root.find_all("p"):
            text = clean_line(p.get_text(" ", strip=True))
            if text and len(text) > 40:
                paragraphs.append(text)
    description_fr = paragraphs[0] if paragraphs else None

    doc_lines = extract_documents(root)
    step_lines = extract_steps(root)

    full_text = root.get_text(" ", strip=True) if root else soup.get_text(" ", strip=True)
    fees_hint, time_hint = extract_fees_and_time(full_text)
    fees_section = extract_section_text(root, ("coût", "cout", "tarif", "frais", "prix", "validité"))
    time_section = extract_section_text(root, ("délai", "delai", "durée", "duree", "traitement", "combien de temps"))
    fees_from_section, time_from_section = extract_fees_and_time(fees_section or "")
    _, time_from_time_section = extract_fees_and_time(time_section or "")

    documents = [
        {"sortOrder": i, "titleFr": line, "titleAr": line}
        for i, line in enumerate(doc_lines, start=1)
    ]
    steps = [
        {
            "stepNumber": i,
            "titleFr": truncate_field(line, 500) or line[:500],
            "titleAr": truncate_field(line, 500) or line[:500],
        }
        for i, line in enumerate(step_lines, start=1)
    ]

    category = guess_category(url, soup, title_fr)

    return draft_procedure(
        slug=slugify(title_fr),
        title_fr=title_fr,
        title_ar=title_fr,
        description_fr=description_fr,
        description_ar=None,
        ministry="À vérifier — source demarches.tn",
        category=category,
        difficulty="MODERATE",
        delivery_mode="À vérifier",
        processing_time=pick_short_value(time_from_time_section, time_hint, time_section, max_length=100),
        fees=pick_short_value(fees_from_section, fees_hint, fees_section, max_length=100),
        source_url=url,
        source_reference="demarches.tn (COMMUNITY — verify against official source before publish)",
        documents=documents or None,
        steps=steps or None,
    )


def save_draft(procedure: dict) -> Path:
    DRAFT_DIR.mkdir(parents=True, exist_ok=True)
    slug = procedure["slug"]
    path = DRAFT_DIR / f"{slug}.json"
    path.write_text(json.dumps(procedure, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def scrape_url(url: str) -> Path:
    if not is_article_url(url):
        print(f"Warning: {url} looks like a category page, not an article.")
    html = fetch(url)
    save_raw(html, slugify(url))
    procedure = parse_article(url, html)
    out = save_draft(procedure)
    print(f"Saved draft: {out.name}")
    print(f"  documents: {len(procedure.get('documents') or [])}")
    print(f"  steps: {len(procedure.get('steps') or [])}")
    print(f"  fees: {procedure.get('fees')}")
    return out


def scrape_articles(limit: int) -> None:
    urls = discover_article_urls(limit=limit)
    print(f"Found {len(urls)} article URLs")
    for url in urls:
        print(f"\n→ {url}")
        try:
            scrape_url(url)
        except requests.RequestException as exc:
            print(f"  skip: {exc}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Scrape real demarches.tn articles")
    parser.add_argument("--url", help="Single article URL")
    parser.add_argument("--articles", action="store_true", help="Discover and scrape real articles")
    parser.add_argument("--discover", action="store_true", help="Alias for --articles")
    parser.add_argument("--limit", type=int, default=10, help="Max articles to scrape")
    args = parser.parse_args()

    if args.articles or args.discover:
        scrape_articles(args.limit)
        return

    if args.url:
        scrape_url(args.url)
        return

    parser.print_help()


if __name__ == "__main__":
    main()
