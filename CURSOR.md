## Dosya (دوسيا) — Cursor Project Guide

This file explains the **project intent, structure, and key constraints** so Cursor (and humans) consistently work in line with Dosya’s goals.

- **App purpose**: Civic tech helper for Tunisian citizens to navigate government bureaucracy.
- **Core principle**: **Structured, verified data is the source of truth; AI is only the interface.** The AI layer must never invent facts about procedures.
- **Stack**:
  - **Backend**: Spring Boot (Java 21), REST API
  - **Database**: PostgreSQL + **pgvector** (for embeddings)
  - **Frontend**: Angular (web, mobile-ready backend)
  - **AI / RAG**: LLM (e.g. Gemini) + pgvector similarity search over `Procedure` embeddings

---

## Domain model (v1 mental model)

These are the core concepts Cursor should expect and preserve as the codebase grows.

- **Procedure**
  - `id`
  - `title_*` variants: FR / AR / TN dialect (e.g. `titleFr`, `titleAr`, `titleTn`)
  - `ministry` / `administration`
  - `category` (identity, business, civil status, housing, taxes, etc.)
  - `requiredDocuments` (list)
  - `steps` (ordered list)
  - `fees`
  - `processingTime`
  - `officeLocations`
  - `sourceUrl` / `sourceReference`
  - `lastVerifiedAt`
  - `embedding` (pgvector column derived from the above, used for RAG)

- **User**
  - `id`, `name`, `email` / auth provider
  - `savedProcedures` (bookmarks)

- **ChatSession / ChatMessage**
  - `sessionId`, `userId`
  - messages: `role`, `content`, `retrievedProcedureIds`, `createdAt`

Expect this model to evolve as we ingest real data, but **do not break the idea that `Procedure` is the canonical source for civic information.**

---

## RAG & AI behavior rules (critical)

Cursor should **enforce these constraints** whenever adding or modifying AI-related code:

- **Grounded answers only**
  - Every AI/chat answer **must be based on retrieved `Procedure` records** (and related metadata).
  - No freeform facts about rules, fees, or locations that are not explicitly present in stored procedures.

- **Retrieval-first pattern**
  1. Receive user question.
  2. Retrieve top `Procedure` rows via pgvector similarity search over embeddings.
  3. Pass only those procedures (plus metadata) into the LLM.
  4. Generate an answer that **summarizes / explains the retrieved data**, not external knowledge.

- **Source and recency always shown**
  - API responses for chat must include **source procedure IDs, titles, and `lastVerifiedAt` (and ideally `sourceUrl`)**.
  - Frontend chat UI must always display **which procedures were used and when they were last verified**.

- **Bilingual / local language**
  - Treat **French + Arabic (and Tunisian dialect)** as **first-class fields**, not add-ons.
  - Text fields that users see should either:
    - Have language-specific columns, or
    - Be stored with language tags or i18n structures.
  - When building APIs, prefer explicit language parameters (e.g. `lang=fr|ar|tn`) over guessing.

---

## Intended project structure (to aim for)

Backend (`Spring Boot`), under `src/main/java/com/example/dossia`:

- `DossiaApplication.java` — Spring Boot entrypoint.
- `procedure/`
  - `Procedure.java` — JPA entity for procedures (with multilingual fields + pgvector column).
  - `ProcedureRepository.java` — Spring Data repository, including vector-similarity queries.
  - `ProcedureController.java` — REST CRUD endpoints and RAG retrieval endpoints.
  - `ProcedureService.java` — business logic, embedding updates, versioning, verification metadata.
- `chat/`
  - `ChatSession.java`, `ChatMessage.java` — entities or documents.
  - `ChatController.java` — REST chat endpoint using RAG pipeline.
  - `ChatService.java` — orchestration: retrieve procedures → call LLM → persist messages.
- `user/`
  - `User.java`, `UserRepository.java`, `UserController.java` — user accounts + bookmarks.

Frontend (`Angular`), under `frontend/` (or similar):

- `app/`
  - `core/` — services (API clients for `Procedure`, `Chat`, `User`, auth).
  - `features/`
    - `procedures/` — search, browse, detail pages, checklists.
    - `chat/` — RAG chat UI, showing sources and last-verified info.
  - `shared/` — UI components, typography, layout.

---

## Branding & UI guidance

- **Name**: Dosya (دوسيا) — “file / dossier” in Tunisian Arabic.
- **Palette — “Modern Tunisia”**:
  - Carthage Teal
  - Deep Night
  - Flag Red
  - Sea Foam
  - Mint
- **Design principles**:
  - Prioritize **clarity and legibility** over cleverness.
  - Target audience includes non-technical users; avoid dense terminology.
  - Always surface **checklists, steps, and document reminders** where possible (leveraging structured data).

---

## Implementation priorities (v1 roadmap)

Cursor should assume these as the next main milestones when generating scaffolding or changes:

1. **Backend foundation**
   - Set up `Procedure` entity, repository, and CRUD REST controller.
   - Configure PostgreSQL + pgvector in Spring Boot.
2. **Angular shell**
   - Create Angular app with theme using the “Modern Tunisia” palette.
   - Stub pages for: home, procedure search/list, procedure detail, chat.
3. **Data ingestion**
   - Scripts/services to import real Tunisian government procedures into the `Procedure` table.
4. **Embedding pipeline**
   - Service that turns procedure text into embeddings and stores them in pgvector.
5. **RAG chat endpoint**
   - Controller + service to implement retrieval → LLM → grounded answer (with sources).
6. **Chat UI**
   - Conversational UI that always shows:
     - Answer
     - Underlying procedures used
     - `lastVerifiedAt` and `sourceUrl` for each.

When in doubt, **protect correctness over cleverness**: never let the AI say something the data model cannot justify.

