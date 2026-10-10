# e-Plattform

A modern e-service platform for Swedish public administration: a rewrite of
[Open-ePlatform](https://github.com/Open-ePlatform) (2016 codebase) on current
technology and current law (GDPR, the archives act, the DOS act/WCAG 2.1 AA).

> **Status: demo.** The platform runs as a public demo on Vercel. It is not
> ready for production: there is no e-legitimation yet and a few gaps remain.
> See [EXPERIMENTAL_STATUS.md](docs/EXPERIMENTAL_STATUS.md).

**Demo:** <https://e-plattform.vercel.app> — log in by picking a persona and
entering the demo access code (the `DEMO_ACCESS_CODE` variable in the Vercel
project). **API documentation:** <https://e-plattform.vercel.app/swagger-ui/index.html>

## What it does

| Role | Persona | Can |
|------|---------|-----|
| Citizen (`USER`) | `medborgare@example.com` | Find e-services, fill in multi-step forms with conditional fields and attachments, follow their cases and message the case worker |
| Case worker (`MANAGER`) | `handlaggare@example.com` | Handle incoming cases: assign, change status according to the e-service's rules, message the applicant, write internal notes, export PDF |
| Administrator (`ADMIN`, `FLOW_EDITOR`) | `admin@example.com` | Build e-services (steps, fields, conditions, statuses, retention period), categories, users, statistics |
| Information security & data protection (`SECURITY_OFFICER`) | `informationssakerhet@example.com` | Search and export the tamper-evident audit log, register extracts, erasure requests, retention (gallring) |
| IT & operations (`OPERATIONS`) | `it-drift@example.com` | Component status, response times, cold starts, scheduled jobs and the technical system log (no personal data) |

In the demo each visitor who logs in as a citizen gets an isolated account,
removed after `DEMO_RETENTION_DAYS` days.

## Architecture

```
Browser ──▶ Vercel (fra1)
             ├── frontend   Next.js 15, React 19, Tailwind     (service "frontend")
             └── /api/v1/*  Spring Boot 3.2, Java 21, container (service "backend")
                               ├── Neon Postgres (fra1)        cases, flows, audit log
                               └── Vercel Blob, private (fra1)  attachments
```

- One Vercel project with two [services](https://vercel.com/docs/services)
  (`vercel.json`). `/api/v1/*`, `/swagger-ui` and `/api-docs` go to the
  backend, everything else to the frontend.
- The backend is a container (`backend/Dockerfile.vercel`) with a Class Data
  Sharing archive for faster cold starts. It scales to zero after 5 minutes
  without traffic; the first request after that takes about 13 seconds. An
  open tab keeps it warm.
- Database migrations (Flyway, `backend/src/main/resources/db/migration`) run
  when an instance starts.
- Files are uploaded from the browser straight to private Blob storage through
  Next.js routes that check the case first.
- Nightly Vercel cron jobs remove expired demo accounts and run retention.

Locally the same code runs against Postgres and MinIO in Docker Compose.

## Getting started locally

Requirements: Docker Desktop, Node.js 22, and Java 21 if you run the backend
outside Docker.

```bash
cp .env.example .env
make dev                 # Postgres, MinIO, Mailpit, API, frontend, Prometheus, Grafana
```

| | |
|---|---|
| Frontend | <http://localhost:3000> |
| API | <http://localhost:8080> |
| API documentation | <http://localhost:8080/swagger-ui/index.html> |
| MinIO console | <http://localhost:9001> |
| Mailpit (e-mail) | <http://localhost:8025> |
| Grafana | <http://localhost:3001> |

Without Docker for the application itself:

```bash
make infra               # Postgres, MinIO and Mailpit only
make backend             # Spring Boot on :8080 (needs JDK 21)
make frontend-install && make frontend   # Next.js on :3000
```

Locally no access code is required and all personas are shared accounts.

## Tests

| What | How | In CI |
|------|-----|-------|
| Backend (49 integration tests with Testcontainers: access control, case handling, audit chain, register extract, erasure, retention, tagged PDF, sessions …) | `cd backend && gradle test` | ✅ |
| Frontend lint, types and build | `cd frontend && npm run lint && npm run type-check && npm run build` | ✅ |
| Accessibility, WCAG 2.1 AA (31 Playwright + axe-core tests: all page types per role, every form step, dialogs, keyboard, page titles, reflow) | start the stack, then `cd frontend && npm run test:a11y` | ✅ |

Gradle 8.6 does not run on JDK 22 or later. With a newer local JDK, run the
backend tests in Docker:

```bash
cd backend
docker run --rm -v "$PWD":/app -w /app -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal gradle:8.6-jdk21 gradle test
```

CI (`.github/workflows/ci.yml`) runs all three jobs on every pull request and
on `main`.

## Deployment

Pushing to `main` deploys production; every pull request gets a preview
deployment. Preview deployments use the same Neon database as production.

Environment variables in the Vercel project:

| Variable | Purpose |
|----------|---------|
| `PG*` (from the Neon integration) | Database connection |
| `BLOB_READ_WRITE_TOKEN` / `BLOB_STORE_ID` (from the Blob integration) | File storage |
| `JWT_SECRET` | Token signing key, at least 32 characters |
| `CRON_SECRET` | Authorises the nightly cron jobs |
| `DEMO_ACCESS_CODE` | Code visitors enter before logging in |
| `DEMO_ISOLATED_CITIZENS` | `true`: every citizen visitor gets their own account |
| `DEMO_RETENTION_DAYS` | Days before demo accounts are removed (default 7) |
| `EMAIL_ENABLED` | E-mail notifications; off in the demo |

## Compliance in brief

- **Traceability:** every read and change of personal data is logged with who,
  what, whose data and the outcome (including denied access). The log is
  hash-chained and append-only, enforced by a database trigger, and can be
  verified and exported by the security role.
- **Data subject rights:** register extract (GDPR art. 15/20) and erasure
  (art. 17). Submitted cases are allmänna handlingar: they are kept and removed
  by retention per e-service, as the archives act requires.
- **Retention (gallring):** per e-service retention period after a case is
  closed; drafts after 90 days, audit log after 24 months.
- **Accessibility:** WCAG 2.1 AA, tested automatically on every change. See
  the [accessibility statement](https://e-plattform.vercel.app/tillganglighet).

## Documentation

- [Experimental status](docs/EXPERIMENTAL_STATUS.md): what works, known gaps,
  what production needs
- [Gap analysis](docs/GAP_ANALYSIS.md): comparison with Open-ePlatform
- [Developer guide](docs/DEVELOPER_GUIDE.md): code structure and conventions
- [Architecture](docs/architecture/ARCHITECTURE.md): original design document

## License

AGPL-3.0, like Open-ePlatform.

**Disclaimer:** provided "as is" without warranty of any kind. Not ready for
production use.
