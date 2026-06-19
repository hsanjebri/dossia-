"""Shared helpers for normalizing scraped content into Dosya import JSON."""

from __future__ import annotations

import re
import unicodedata
from typing import Any


def slugify(text: str) -> str:
    normalized = unicodedata.normalize("NFKD", text)
    ascii_text = normalized.encode("ascii", "ignore").decode("ascii")
    slug = re.sub(r"[^a-z0-9\s-]", "", ascii_text.lower())
    slug = re.sub(r"\s+", "-", slug.strip())
    return re.sub(r"-+", "-", slug) or "procedure"


def draft_procedure(
    *,
    title_fr: str,
    title_ar: str,
    ministry: str,
    category: str,
    difficulty: str,
    source_url: str,
    title_tn: str | None = None,
    description_fr: str | None = None,
    description_ar: str | None = None,
    delivery_mode: str | None = None,
    processing_time: str | None = None,
    fees: str | None = None,
    source_reference: str | None = None,
    slug: str | None = None,
    documents: list[dict[str, Any]] | None = None,
    steps: list[dict[str, Any]] | None = None,
    offices: list[dict[str, Any]] | None = None,
    related_procedure_slugs: list[str] | None = None,
) -> dict[str, Any]:
    return {
        "slug": slug or slugify(title_fr),
        "titleFr": title_fr,
        "titleAr": title_ar,
        "titleTn": title_tn,
        "descriptionFr": description_fr,
        "descriptionAr": description_ar,
        "ministry": ministry,
        "category": category,
        "difficulty": difficulty,
        "deliveryMode": delivery_mode,
        "processingTime": truncate_field(processing_time, 500),
        "fees": truncate_field(fees, 500),
        "sourceUrl": source_url,
        "sourceReference": source_reference,
        "status": "DRAFT",
        "documents": documents or [],
        "steps": steps or [],
        "offices": offices or [],
        "relatedProcedureSlugs": related_procedure_slugs or [],
    }


def truncate_field(value: str | None, max_length: int = 500) -> str | None:
    if value is None:
        return None
    if len(value) <= max_length:
        return value
    return value[: max_length - 1] + "…"


def pick_short_value(
    extracted: str | None,
    hint: str | None,
    section: str | None,
    *,
    max_length: int = 100,
    fallback: str = "À vérifier",
) -> str:
    if extracted:
        return extracted
    if hint:
        return hint
    if section and len(section) <= max_length:
        return section
    return fallback


def extract_text_lines(html: str) -> list[str]:
    from bs4 import BeautifulSoup

    soup = BeautifulSoup(html, "lxml")
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()
    lines: list[str] = []
    for element in soup.stripped_strings:
        line = " ".join(element.split())
        if line:
            lines.append(line)
    return lines
