"""Load source registry from sources.yaml."""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

REGISTRY_PATH = Path(__file__).resolve().parent / "sources.yaml"


def load_sources() -> list[dict[str, Any]]:
    with REGISTRY_PATH.open(encoding="utf-8") as handle:
        data = yaml.safe_load(handle)
    return data.get("sources", [])


def get_source(source_id: str) -> dict[str, Any] | None:
    for source in load_sources():
        if source["id"] == source_id:
            return source
    return None


def auto_sources() -> list[dict[str, Any]]:
    return [s for s in load_sources() if s.get("mode") == "auto"]
