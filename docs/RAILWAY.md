# Deploy Dosya backend + DB on Railway

This guide gets the **Spring Boot API** and a **pgvector Postgres** live on Railway.
Frontend (Vercel) comes next.

> **Why not Railway's built-in Postgres?** Dosya needs the `vector` extension (pgvector).
> Use the `pgvector/pgvector:pg16` Docker image as a database service instead.

---

## 1. Push this repo to GitHub

Make sure `Dockerfile`, `railway.toml`, and the latest backend code are on `main`.

---

## 2. Create a Railway project

1. Go to [railway.app](https://railway.app) → **New Project**
2. **Deploy from GitHub repo** → select `dossia-` (or your fork)

Railway should detect the `Dockerfile` and start building the API.

---

## 3. Add a pgvector database service

1. In the same project → **+ New** → **Docker Image**
2. Image: `pgvector/pgvector:pg16`
3. Variables for that service:

| Variable | Example |
|----------|---------|
| `POSTGRES_USER` | `dossia` |
| `POSTGRES_PASSWORD` | *(strong password)* |
| `POSTGRES_DB` | `dossia` |

4. Add a **Volume** mounted at `/var/lib/postgresql/data` (so data survives restarts)
5. (Optional) Expose TCP if you need local tools — for the API, **private networking** is enough

Rename the service to something clear, e.g. `postgres`.

---

## 4. Wire the API → database

On the **API service** → **Variables**, add:

### Database (private network)

If your DB service is named `postgres`, use Railway **reference variables** or set:

| Variable | Value |
|----------|--------|
| `POSTGRES_HOST` | `${{postgres.RAILWAY_PRIVATE_DOMAIN}}` *(or the private hostname Railway shows)* |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `dossia` |
| `POSTGRES_USER` | `dossia` |
| `POSTGRES_PASSWORD` | *(same as DB service)* |

**Or** set a single JDBC URL:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://${{postgres.RAILWAY_PRIVATE_DOMAIN}}:5432/dossia
SPRING_DATASOURCE_USERNAME=dossia
SPRING_DATASOURCE_PASSWORD=...
```

If Railway gives you `DATABASE_URL=postgresql://...`, Dosya converts it to JDBC automatically.

### App secrets

| Variable | Value |
|----------|--------|
| `JWT_SECRET` | ≥ 32 random characters |
| `ADMIN_EMAILS` | your email |
| `GEMINI_API_KEY` | your key |
| `LLM_PROVIDER` | `gemini` |
| `OLLAMA_ENABLED` | `false` |
| `GEMINI_ENABLED` | `true` |
| `AUTH_COOKIE_SECURE` | `true` |
| `AUTH_COOKIE_SAME_SITE` | `None` |
| `DOSSIA_CORS_ORIGINS` | `http://localhost:4200` *(add Vercel URL later, comma-separated)* |

---

## 5. Public networking

On the **API service** → **Settings** → **Networking** → **Generate Domain**.

Health check path is already set in `railway.toml`: `/api/v1/health`.

Test:

```bash
curl https://YOUR-APP.up.railway.app/api/v1/health
```

---

## 6. After first deploy

1. Flyway runs migrations automatically (including `vector` + tables).
2. Import procedures (admin scrape/import) so chat has data.
3. Embed published procedures from the admin API if you use vector search.

---

## Common failures

| Symptom | Fix |
|---------|-----|
| Crash / healthcheck fail | App must listen on `$PORT` (already in `application.yml`) |
| `extension "vector" is not available` | You used plain Railway Postgres — switch to `pgvector/pgvector:pg16` |
| CORS errors from browser | Set `DOSSIA_CORS_ORIGINS` to your Vercel URL |
| Login cookie missing on Vercel | `AUTH_COOKIE_SECURE=true` + `AUTH_COOKIE_SAME_SITE=None` |
| Build OOM | Increase Railway memory / use Dockerfile multi-stage (already done) |

---

## Next: Vercel frontend

Point Angular `environment.prod.ts` API base URL to `https://YOUR-APP.up.railway.app`, then add that origin to `DOSSIA_CORS_ORIGINS`.
