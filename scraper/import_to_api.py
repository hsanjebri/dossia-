"""
Import draft procedure JSON files into the Dosya API.

Usage (from repo root):
  cd scraper
  pip install -r requirements.txt
  python import_to_api.py
  python import_to_api.py --verify passport-request
  python import_to_api.py --file data/draft/passport-request.json

Requires Spring Boot running at DOSSIA_API_URL (default http://localhost:8080).
Imported procedures are saved as DRAFT. Use --verify <slug> after human review.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import requests

from normalize import truncate_field

ROOT = Path(__file__).resolve().parent
DRAFT_DIR = ROOT / "data" / "draft"
DEFAULT_API_URL = os.environ.get("DOSSIA_API_URL", "http://localhost:8080")


def sanitize_procedure(procedure: dict) -> dict:
    """Ensure API field limits before import."""
    procedure = dict(procedure)
    procedure["fees"] = truncate_field(procedure.get("fees"), 500) or "À vérifier"
    procedure["processingTime"] = truncate_field(procedure.get("processingTime"), 500) or "À vérifier"
    steps = []
    for step in procedure.get("steps") or []:
        step = dict(step)
        title = truncate_field(step.get("titleFr"), 500) or "Étape"
        step["titleFr"] = title
        step["titleAr"] = truncate_field(step.get("titleAr"), 500) or title
        steps.append(step)
    procedure["steps"] = steps
    return procedure


def load_json_files(directory: Path) -> list[dict]:
    procedures = []
    for path in sorted(directory.glob("*.json")):
        with path.open(encoding="utf-8") as handle:
            procedures.append(sanitize_procedure(json.load(handle)))
    return procedures


def build_session(api_url: str) -> requests.Session:
    """Create an authenticated session. Admin endpoints require an ADMIN cookie."""
    session = requests.Session()
    email = os.environ.get("DOSSIA_ADMIN_EMAIL")
    password = os.environ.get("DOSSIA_ADMIN_PASSWORD")
    if not email or not password:
        print(
            "Warning: DOSSIA_ADMIN_EMAIL / DOSSIA_ADMIN_PASSWORD not set. "
            "Admin endpoints will reject the request (401/403).",
            file=sys.stderr,
        )
        return session

    response = session.post(
        f"{api_url}/api/v1/auth/login",
        json={"email": email, "password": password},
        timeout=30,
    )
    if response.status_code >= 400:
        raise SystemExit(
            f"Admin login failed ({response.status_code}). "
            "Ensure the user exists and its email is listed in ADMIN_EMAILS."
        )
    print(f"Authenticated as admin: {email}")
    return session


def import_procedures(session: requests.Session, api_url: str, procedures: list[dict]) -> dict:
    response = session.post(
        f"{api_url}/api/v1/admin/procedures/import",
        json={"procedures": procedures},
        timeout=60,
    )
    response.raise_for_status()
    return response.json()


def list_drafts(session: requests.Session, api_url: str) -> dict:
    response = session.get(
        f"{api_url}/api/v1/admin/procedures",
        params={"status": "DRAFT", "size": 100},
        timeout=30,
    )
    response.raise_for_status()
    return response.json()


def verify_by_slug(session: requests.Session, api_url: str, slug: str) -> None:
    drafts = list_drafts(session, api_url)
    match = next((item for item in drafts["content"] if item["slug"] == slug), None)
    if not match:
        raise SystemExit(f"No DRAFT procedure found with slug: {slug}")

    procedure_id = match["id"]
    response = session.patch(
        f"{api_url}/api/v1/admin/procedures/{procedure_id}/verify",
        timeout=30,
    )
    response.raise_for_status()
    print(f"Verified and published: {slug} ({procedure_id})")


def main() -> None:
    parser = argparse.ArgumentParser(description="Import Dosya procedure drafts")
    parser.add_argument("--api-url", default=DEFAULT_API_URL)
    parser.add_argument("--file", type=Path, help="Import a single JSON file")
    parser.add_argument("--verify", metavar="SLUG", help="Verify/publish a draft by slug")
    parser.add_argument("--list-drafts", action="store_true", help="List draft procedures")
    args = parser.parse_args()

    session = build_session(args.api_url)

    if args.list_drafts:
        result = list_drafts(session, args.api_url)
        for item in result["content"]:
            print(f"- {item['slug']} ({item['id']})")
        return

    if args.verify:
        verify_by_slug(session, args.api_url, args.verify)
        return

    if args.file:
        procedures = [sanitize_procedure(json.loads(args.file.read_text(encoding="utf-8")))]
    else:
        procedures = load_json_files(DRAFT_DIR)

    if not procedures:
        raise SystemExit(f"No JSON files found in {DRAFT_DIR}")

    result = import_procedures(session, args.api_url, procedures)
    print(
        f"Import complete: created={result['created']} "
        f"skipped={result['skipped']} errors={len(result['errors'])}"
    )
    for error in result["errors"]:
        print(f"  error: {error}")


if __name__ == "__main__":
    try:
        main()
    except requests.RequestException as exc:
        body = ""
        if exc.response is not None:
            body = exc.response.text[:500]
        print(f"API error: {exc}", file=sys.stderr)
        if body:
            print(f"Response: {body}", file=sys.stderr)
        print(
            "Tip: restart Spring Boot (Ctrl+C then .\\mvnw.cmd spring-boot:run) so admin endpoints load.",
            file=sys.stderr,
        )
        sys.exit(1)
