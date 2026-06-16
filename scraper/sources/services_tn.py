"""
Proof-of-concept scraper for services.tn.

Fetches a page, saves raw HTML to data/raw/, and prints extracted text lines.
Site-specific parsers will be added per procedure type in later iterations.

Usage:
  cd scraper
  pip install -r requirements.txt
  python -m sources.services_tn --url https://www.services.tn --slug sample-page
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
from pathlib import Path

import requests

from normalize import extract_text_lines

ROOT = Path(__file__).resolve().parents[1]
RAW_DIR = ROOT / "data" / "raw"


def fetch_and_save(url: str, slug: str) -> Path:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = RAW_DIR / f"{slug}_{timestamp}.html"

    response = requests.get(
        url,
        timeout=30,
        headers={"User-Agent": "DosyaScraper/0.1 (+https://github.com/dosya-tn)"},
    )
    response.raise_for_status()
    output.write_text(response.text, encoding="utf-8")
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description="Scrape services.tn page to raw HTML")
    parser.add_argument("--url", required=True)
    parser.add_argument("--slug", required=True, help="Filename prefix for saved HTML")
    args = parser.parse_args()

    saved = fetch_and_save(args.url, args.slug)
    lines = extract_text_lines(saved.read_text(encoding="utf-8"))
    print(f"Saved: {saved}")
    print(f"Extracted {len(lines)} text lines. First 10:")
    for line in lines[:10]:
        print(f"  - {line}")


if __name__ == "__main__":
    main()
