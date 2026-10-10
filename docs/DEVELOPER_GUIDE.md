# e-Plattform Developer Guide

How the code is organised and the conventions to follow. For getting started,
tests and deployment, see the [README](../README.md).

## Contents

1. [Architecture](#architecture)
2. [Backend](#backend)
3. [Frontend](#frontend)
4. [Form system](#form-system)
5. [Access control](#access-control)
6. [Audit logging](#audit-logging)
7. [Accessibility](#accessibility)
8. [Database changes](#database-changes)
9. [Working on the code](#working-on-the-code)

## Architecture

```
Next.js 15 (React 19, TanStack Query, Tailwind)
   │  REST/JSON, /api/v1/*, JWT in the Authorization header
   ▼
Spring Boot 3.2 (Java 21)
   ├── PostgreSQL (Flyway)       Neon in the demo, Docker locally
   └── File storage              Vercel Blob in the demo, MinIO locally
```

On Vercel both run as services in one project (`vercel.json`): the frontend as
Next.js, the backend as a container from `backend/Dockerfile.vercel`. The
browser calls the API on the same origin. Locally the frontend calls
`NEXT_PUBLIC_API_URL` (default `http://localhost:8080`).

Spring profiles: `dev` (local, Docker Compose), `vercel` (demo) and `prod`
(starting point for a production setup). Tests run on the default profile
against Postgres in Testcontainers.

## Backend

```
se.eplatform/
├── auth/          Login, demo personas, sessions, cron endpoints
├── cases/         Cases, answers, messages, notes, assignment, status changes
│   └── evaluator/ Conditions on fields
├── flow/          E-services: flows, steps, fields, statuses, categories (admin API)
├── user/          Users and roles
├── storage/       Attachments (MinIO or Vercel Blob behind one interface)
├── pdf/           Case PDF (tagged PDF/UA)
├── notification/  E-mail (Thymeleaf templates)
├── statistics/    Admin statistics
├── audit/         Audit log: @Audited, interceptor, hash chain, verification
├── privacy/       Register extract, erasure, retention; API for the security role
├── ops/           System events and status for the IT role
├── common/        Security (JWT, CurrentUser, rate limit), config, errors
└── integration/   BankID and payment (stubs)
```

`search/` and `file/` are empty placeholders.

Conventions:

- Controllers in `api/`, logic in `service/`, entities in `domain/`, Spring Data
  repositories in `repository/`.
- DTOs are records.
- Constructor injection.
- Null fields are left out of JSON responses.
- Swedish texts in API descriptions (OpenAPI) and messages shown to users;
  English in code and comments.
- Every controller has an OpenAPI `@Tag`. Internal endpoints are `@Hidden`.

## Frontend

```
src/
├── app/
│   ├── citizen/          Services, form, my cases, profile
│   ├── manager/          Dashboard and case handling
│   ├── admin/            E-services, categories, statistics, users
│   ├── security/         Audit log, people (extract/erasure), retention
│   ├── ops/              Status and system log
│   ├── auth/login/       Persona login
│   ├── tillganglighet/   Accessibility statement
│   └── api/blob/         Next.js routes for Blob uploads/downloads
├── components/
│   ├── form/             FormRenderer, FormContext, QueryRenderer, fields/
│   ├── layout/           Header, Footer, StaffShell, PageAccessibility, BackendKeepWarm
│   ├── ui/               Modal, Toast, MobileCard
│   ├── auth/             RequireRole, SessionTimeout
│   ├── cases/            MessageThread, CaseTimeline
│   └── admin/            ConditionEditor
├── context/AuthContext.tsx
└── lib/
    ├── api/              One module per API area (cases, manager, admin, security, ops, files)
    ├── auth/             Login API and token storage
    └── statusColor.ts    Readable badge colours for admin-chosen status colours
```

Use these instead of building your own:

| Need | Use |
|------|-----|
| A staff area (header + sidebar) | `StaffShell` with `theme="admin" \| "security" \| "ops"` |
| A dialog | `Modal`, a native `<dialog>` with focus trap, Escape and focus return; named by its first heading |
| A role check on a page | `RequireRole` (the API checks too) |
| Buttons, cards, inputs | `btn-primary`, `btn-secondary`, `btn-danger`, `card`, `input`, `label`, `th`, `page-title` in `globals.css` |
| The area's accent colour | `brand-50…900` (blue by default, purple admin, slate security, teal ops), not `blue-*` or `purple-*` |
| A status badge with a colour from the database | `statusBadgeStyle(color)` |
| Notifications | `toast` from `@/hooks/useToast` |

## Form system

An e-service is `FlowFamily → Flow (version) → Step → QueryDefinition`. A case
stores one `QueryInstance` per field.

**Field types** (`QueryType`): `TEXT`, `TEXTAREA`, `NUMBER`, `EMAIL`, `PHONE`,
`URL`, `DATE`, `DATETIME`, `TIME`, `SELECT`, `MULTISELECT`, `RADIO`,
`CHECKBOX`, `FILE`, `IMAGE`, `MAP`, `LOCATION`, `SIGNATURE`, `ORGANIZATION`,
`PERSON`, and the layout elements `HEADING`, `PARAGRAPH`, `DIVIDER`. Each has
a component in `components/form/fields/`, chosen in `QueryRenderer`.

**Conditions** (`EvaluatorDefinition`) set other fields to `VISIBLE`,
`VISIBLE_REQUIRED` or `HIDDEN` based on an answer. The evaluator types are:
`VALUE_EQUALS`, `VALUE_NOT_EQUALS`, `VALUE_IN`, `VALUE_NOT_IN`,
`VALUE_CONTAINS`, `VALUE_NOT_CONTAINS`, `VALUE_GREATER_THAN`,
`VALUE_LESS_THAN`, `VALUE_BETWEEN`, `REGEX_MATCH`, `IS_EMPTY`,
`IS_NOT_EMPTY`. They are evaluated in the browser (`useEvaluator`). Required
fields are currently only enforced there; see the known gaps.

**A new field type** needs:

1. The enum value, plus a migration that adds it to the `chk_query_type`
   constraint on `query_definitions`.
2. A component wrapped in `FieldWrapper`. It provides the label (or
   `fieldset`/`legend` when the field has several inputs), the description,
   the error message and the ARIA attributes.
3. An `id`/`htmlFor` on every inner input.
4. `autocomplete` for personal data.

## Access control

- **The acting user comes from the token.** Use `CurrentUser`, never a user id
  from the request.
- **Case access goes through `CaseAccessService`.** A citizen asking for
  someone else's case gets 404, not 403.
- **URL roles are set in `SecurityConfig`:**
  - `/api/v1/admin/**`: `ADMIN`, `FLOW_EDITOR`
  - `/api/v1/security/**`: `SECURITY_OFFICER`
  - `/api/v1/ops/**`: `OPERATIONS`
- **Tests:** add a case to `CaseAuthorizationTest` when you add an endpoint
  that touches personal data.

## Audit logging

Annotate controller methods that read or change personal data:

```java
@GetMapping("/{id}")
@Audited(value = AuditAction.CASE_VIEW, entity = "CASE")
public ResponseEntity<CaseDTO> getCase(@PathVariable UUID id) { … }
```

`AuditInterceptor` writes the entry after the request:
- **Outcome** comes from the HTTP status (401/403/404 are recorded as denied).
- **Data subject** is resolved from the entity (`AuditSubjectResolver`).
- **Extra context:** set it with `AuditContext`, for example the id of a new
  case or the new status.

For events outside a request, use `AuditService.recordSystem`.

The log is append-only: a database trigger rejects updates and deletes, and
every entry carries the hash of the previous one. Never write to
`audit_events` directly. System events for IT (`SystemEventService`) must not
contain personal data.

## Accessibility

The target is WCAG 2.1 AA. `frontend/e2e/` holds Playwright tests with
axe-core, and CI runs them on every pull request.

- **A new page** goes into the list in `e2e/pages.a11y.spec.ts`.
- **Page titles** come from the page's `<h1>` (`DocumentTitle`), so every page
  needs exactly one `h1`.
- **Icon-only buttons** need an `aria-label`.
- **Text colours:** use at least `gray-500` on white and `gray-600` on
  `gray-100`. Coloured text uses the 700 shade.
- **Dialogs** use `Modal`.

Run the tests locally with the stack started:
`cd frontend && npm run test:a11y`.

## Database changes

Add a new Flyway migration, `V<n>__description.sql`, and never edit one that
has been merged; Neon runs them when an instance starts. Hibernate validates
the schema in the tests (`ddl-auto: validate`).

## Working on the code

- **Branches:** branch from `main` and open a pull request. CI must pass:
  backend tests, frontend lint/types/build and accessibility.
- **Previews:** every pull request gets a Vercel preview. It shares the demo
  database, so be careful with destructive tests.
- **Commits:** an imperative subject line in English that describes the
  change, for example "Add retention period to the flow settings". Explain
  why in the body when it isn't obvious.
- **Backend tests:** they extend `IntegrationTest`, which uses Testcontainers
  with Postgres and has helpers to log in as personas.
