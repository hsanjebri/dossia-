#!/usr/bin/env bash
# Load repo-root .env into the process, then start Spring Boot (profile: local).
# Usage (from repo root):  ./scripts/run-backend.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
  echo "Loaded .env"
else
  echo "WARNING: .env not found — Gemini may run offline" >&2
fi

if [[ -z "${GEMINI_API_KEY:-}" ]]; then
  echo "WARNING: GEMINI_API_KEY is empty" >&2
else
  echo "GEMINI_API_KEY: set (${#GEMINI_API_KEY} chars)"
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
exec ./mvnw spring-boot:run
