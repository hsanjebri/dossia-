"""
Scraper for demarches.tn — primary automated source (COMMUNITY, not official).

Usage:
  cd scraper
  pip install -r requirements.txt
  python -m sources.demarches_tn --url https://demarches.tn/some-article/
  python -m sources.demarches_tn --discover --limit 5

Every output is DRAFT with sourceReference noting demarches.tn.
Human must verify against services.gov.tn or ministry site before --verify.
"""

from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlparse

import requests
from bs4 import BeautifulSoup

from normalize import draft_procedure, slugify

ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "data" / "raw"
DRAFT_DIR = ROOT / "data" / "draft"

HEADERS = {
    "User-Agent": "DosyaScraper/0.1 (+https://github.com/hsanjebri/dossia-; civic research)",
    "Accept-Language": "fr,ar;q=0.9",
}

# Map demarches.tn category slugs → Dosya ProcedureCategory
CATEGORY_MAP = {
    "etat-civil": "CIVIL_STATUS",
    "identite": "CIVIL_STATUS",
    "finances": "TAX",
    "travail": "SOCIAL",
    "travail-retraite": "SOCIAL",
    "travail-et-retraite": "SOCIAL",
    "mobilite": "VEHICLES",
    "voyages": "VEHICLES",
    "education": "EDUCATION",
    "entreprise": "BUSINESS",
    "aide-sociale": "SOCIAL",
    "autres-formalites": "SOCIAL",
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


def guess_category(url: str, soup: BeautifulSoup) -> str:
    path = urlparse(url).path.lower()
    for key, value in CATEGORY_MAP.items():
        if key in path:
            return value
    for key, value in CATEGORY_MAP.items():
        for link in soup.select("a[href]"):
            href = (link.get("href") or "").lower()
            if key in href:
                return value
    return "SOCIAL"


def extract_list_items(
    soup: BeautifulSoup,
    heading_keywords: tuple[str, ...],
    *,
    step_mode: bool = False,
) -> list[dict]:
    items: list[dict] = []
    for heading in soup.find_all(["h2", "h3", "h4", "strong"]):
        text = heading.get_text(" ", strip=True).lower()
        if not any(k in text for k in heading_keywords):
            continue
        sibling = heading.find_next(["ul", "ol"])
        if not sibling:
            continue
        for li in sibling.find_all("li", recursive=False):
            line = li.get_text(" ", strip=True)
            if not line:
                continue
            index = len(items) + 1
            if step_mode:
                items.append({"stepNumber": index, "titleFr": line, "titleAr": line})
            else:
                items.append({"sortOrder": index, "titleFr": line, "titleAr": line})
        if items:
            break
    return items


def parse_article(url: str, html: str | None = None) -> dict:
    html = html or fetch(url)
    soup = BeautifulSoup(html, "lxml")

    title_el = soup.find("h1")
    title_fr = title_el.get_text(strip=True) if title_el else "Sans titre"

    content = soup.find("article") or soup.find("main") or soup.body
    paragraphs = []
    if content:
        for p in content.find_all("p"):
            text = p.get_text(" ", strip=True)
            if text and len(text) > 40:
                paragraphs.append(text)
    description_fr = paragraphs[0] if paragraphs else None

    documents = extract_list_items(
        soup,
        ("document", "pièce", "pièces", "justificatif", "fournir", "dossier"),
    )
    steps = extract_list_items(
        soup,
        ("étape", "etape", "procédure", "demarche", "comment", "marche"),
        step_mode=True,
    )

    category = guess_category(url, soup)
    slug = slugify(title_fr)

    return draft_procedure(
        slug=slug,
        title_fr=title_fr,
        title_ar=title_fr,  # flag for manual Arabic translation
        description_fr=description_fr,
        description_ar=None,
        ministry="À vérifier — source demarches.tn",
        category=category,
        difficulty="MODERATE",
        delivery_mode="À vérifier",
        processing_time="À vérifier",
        fees="À vérifier",
        source_url=url,
        source_reference="demarches.tn (COMMUNITY — verify against official source before publish)",
        documents=documents or None,
        steps=steps or None,
    )


def discover_article_urls(home_html: str, limit: int = 20) -> list[str]:
    soup = BeautifulSoup(home_html, "lxml")
    base = "https://www.demarches.tn"
    base_host = urlparse(base).netloc.removeprefix("www.")
    seen: set[str] = set()
    urls: list[str] = []

    for anchor in soup.select("a[href]"):
        href = anchor.get("href", "")
        if not href or href.startswith("#"):
            continue
        full = urljoin(base, href)
        host = urlparse(full).netloc.removeprefix("www.")
        if host != base_host:
            continue
        if re.search(r"/(category|categorie|tag|author|page|contact|wp-|wp-json|feed)", full, re.I):
            continue
        if full.rstrip("/") in {base, "https://demarches.tn", "https://www.demarches.tn"}:
            continue
        if full not in seen:
            seen.add(full)
            urls.append(full)
        if len(urls) >= limit:
            break
    return urls


def save_draft(procedure: dict) -> Path:
    DRAFT_DIR.mkdir(parents=True, exist_ok=True)
    slug = procedure["slug"]
    path = DRAFT_DIR / f"{slug}.json"
    path.write_text(json.dumps(procedure, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def main() -> None:
    parser = argparse.ArgumentParser(description="Scrape demarches.tn articles")
    parser.add_argument("--url", help="Single article URL to scrape")
    parser.add_argument("--discover", action="store_true", help="Discover URLs from homepage")
    parser.add_argument("--limit", type=int, default=5, help="Max URLs when discovering")
    args = parser.parse_args()

    if args.discover:
        home = fetch("https://www.demarches.tn/")
        save_raw(home, "homepage")
        urls = discover_article_urls(home, limit=args.limit)
        print(f"Discovered {len(urls)} URLs:")
        for url in urls:
            print(f"  {url}")
            try:
                html = fetch(url)
                save_raw(html, slugify(url))
                procedure = parse_article(url, html)
                out = save_draft(procedure)
                print(f"    -> draft: {out.name}")
            except requests.RequestException as exc:
                print(f"    -> skip: {exc}")
        return

    if not args.url:
        parser.error("Provide --url or --discover")

    html = fetch(args.url)
    save_raw(html, slugify(args.url))
    procedure = parse_article(args.url, html)
    out = save_draft(procedure)
    print(f"Saved draft: {out}")
    print(json.dumps(procedure, ensure_ascii=False, indent=2)[:500] + "...")


if __name__ == "__main__":
    main()
