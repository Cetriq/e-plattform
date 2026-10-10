# Gap Analysis: Open-ePlatform vs e-Plattform

How the rewrite compares with the original Open-ePlatform (2016 codebase):
what is implemented, what is missing, and what is new.

**Last updated:** 2026-10-10. Earlier versions of this document listed field
types and features that were not in the code (`PERSONNUMMER`, `CURRENCY`,
`HIDDEN`, message attachments, group assignment, Redis/Meilisearch use); these
have been corrected.

## Summary

| Area | Open-ePlatform | e-Plattform | Status |
|------|----------------|-------------|--------|
| E-services (flows, steps, fields, conditions) | Yes | Yes, simplified model | ✅ |
| Field types | 15+ query modules | 20 input types + 3 layout elements | ✅ core, ⚠️ map and tree types |
| Case handling | Yes | Assignment, status rules, messages, internal notes | ✅ |
| PDF of cases | iText + XSL | OpenHTMLToPDF, tagged PDF/UA | ✅ |
| Notifications | E-mail (XSL) | E-mail (Thymeleaf), off in demo | ⚠️ |
| Statistics | Statistics module | Overview and cases per status | ⚠️ basic |
| Authentication | SAML, BankID, Freja | Demo personas + JWT | ❌ e-legitimation missing |
| Signing | BankID, multi-party | Drawn/typed signature only | ❌ |
| Integrations | Callback API | REST API with OpenAPI docs | ⚠️ no webhooks |
| Traceability, GDPR, retention | Limited | Audit log, register extract, erasure, retention | ✅ new |
| Accessibility | XSLT pages, not assessed | WCAG 2.1 AA, tested in CI | ✅ new |
| Operations | Server logs | Status page and technical system log | ✅ new |

## 1. Domain model

### E-services

| Feature | Open-ePlatform | e-Plattform | Status |
|---------|----------------|-------------|--------|
| Flow, flow family, versions | Yes | Yes | ✅ (basic versioning) |
| Flow type and category | Yes | Yes | ✅ |
| Steps and field definitions | Yes | Yes | ✅ |
| Conditions (evaluators) | Complex | Show/hide/require based on answers, editor in the UI | ✅ |
| Status definitions and transitions | Yes | Per e-service, with "requires comment" | ✅ |
| Retention period | Per flow family | Per e-service (months after closing) | ✅ |
| Publishing | Date-based | Publish/unpublish | Simplified |
| Preview of a draft e-service | Yes | No | ❌ |
| PDF templates per e-service | Yes | One shared layout | ❌ |
| Tags, checks, external flows | Yes | No | ❌ |
| Import, export, copy | XML | No | ❌ |

### Cases

| Feature | Open-ePlatform | e-Plattform | Status |
|---------|----------------|-------------|--------|
| Case with answers | FlowInstance + QueryInstance | Case + QueryInstance | ✅ |
| Case events (history) | Yes | Yes | ✅ |
| Messages to the applicant | Yes | Yes, with unread markers | ✅ |
| Internal notes | Yes | Yes, never shown to the citizen | ✅ |
| Attachments on messages | Yes | No (attachments belong to answers) | ❌ |
| Case manager | Users and groups | One assigned user | ⚠️ |
| Several owners per case | Yes | No | ❌ |
| Bookmarks, abandoned-flow statistics | Yes | No | ❌ |

## 2. Field types

Implemented (backend `QueryType` and a renderer in the frontend):

| Type | Open-ePlatform counterpart | Notes |
|------|----------------------------|-------|
| `TEXT`, `TEXTAREA` | TextField, TextArea | |
| `NUMBER`, `EMAIL`, `PHONE`, `URL` | TextField with formats | Own types |
| `DATE`, `DATETIME`, `TIME` | – | |
| `SELECT`, `MULTISELECT`, `RADIO`, `CHECKBOX` | DropDown, Checkbox, RadioButton | |
| `FILE`, `IMAGE` | FileUpload | |
| `PERSON` | ContactDetail | Autocomplete tokens for name, e-mail, phone |
| `ORGANIZATION` | OrganizationDetail | |
| `LOCATION` | – | Address |
| `MAP` | BaseMapQuery | ⚠️ Address or coordinates; no map component yet |
| `SIGNATURE` | – | Drawn or typed; not a legal e-signature |
| `HEADING`, `PARAGRAPH`, `DIVIDER` | Text blocks | Layout only |

Missing compared with Open-ePlatform:

| Type | Priority | Notes |
|------|----------|-------|
| ManualMultiSign | High | Needs BankID |
| Map with drawing (polygon, multi-geometry, PUD) | Medium | Needs a map service |
| Tree and multi-tree | Medium | |
| Child data | Low | Needs population register lookup |

## 3. Authentication and signing

| Feature | Open-ePlatform | e-Plattform | Status |
|---------|----------------|-------------|--------|
| BankID, Freja eID | Yes | "Coming soon" in the UI | ❌ |
| SAML for staff | Yes | No | ❌ |
| Demo login | – | Personas, shared access code, isolated citizen accounts | ✅ (demo only) |
| Session | Server session | Signed JWT, warning before expiry with option to extend | ✅ |
| Role-based access | Yes | `USER`, `MANAGER`, `ADMIN`, `FLOW_EDITOR`, `SECURITY_OFFICER`, `OPERATIONS` | ✅ |
| Access to cases | Yes | Enforced in the API; other people's cases return 404 | ✅ |
| BankID signing, multi-party signing | Yes | No | ❌ |

## 4. PDF

OpenHTMLToPDF renders the case with all steps and answers. The PDF is tagged
(PDF/UA) with Swedish as its language, a title and an embedded font, so it can
be read with a screen reader. Citizens can download their own cases;
case workers any case.

Missing: attachments embedded in the PDF, per-e-service templates, signatures.

## 5. Notifications

E-mail with Thymeleaf templates on submission, status change, completion and
new messages. Switched off in the demo (`EMAIL_ENABLED`). SMS, notification
preferences and attaching the PDF are missing.

## 6. Statistics

Overview totals and cases per status for administrators. Missing compared with
Open-ePlatform: usage per e-service over time, abandoned flows, export.

## 7. Integrations and API

All functions are available as a REST API under `/api/v1`, documented with
OpenAPI (Swagger UI live in the demo). Missing: webhooks or events for
external systems, callback API, archive (e-arkiv) and payment integration.
The BankID and payment packages in `integration/` are stubs.

## 8. Administration

| Feature | Open-ePlatform | e-Plattform | Status |
|---------|----------------|-------------|--------|
| E-service editor | Yes | Steps, fields, conditions, statuses, retention | ✅ |
| Categories and service types | Yes | Yes | ✅ |
| User list | Yes | Yes | ✅ |
| Change roles and groups | Yes | No UI | ❌ |
| Operating messages, feedback surveys, approval workflow | Yes | No | ❌ |

## 9. Compliance (new)

Open-ePlatform predates GDPR in its current form. e-Plattform adds:

| Requirement | Implementation |
|-------------|----------------|
| Traceability (GDPR art. 30/32, NIS2) | Audit log of every read and change of personal data: who, what, whose data, outcome (including denied access), IP. Hash-chained; a database trigger blocks changes; integrity check in the UI |
| Right of access and portability (art. 15/20) | Register extract per person, including who has accessed their data |
| Right to erasure (art. 17) | Drafts deleted and account anonymised; submitted cases kept as allmänna handlingar (art. 17.3 b) |
| Retention (archives act) | Retention period per e-service; nightly job; overview of what is due |
| Separation of duties | Administrators no longer see the audit log; it belongs to the security role. IT sees technical logs without personal data |

## 10. Accessibility (new)

WCAG 2.1 AA, as the DOS act requires: shared components for dialogs, form
fields and colours; 31 automated tests (axe-core) on every change; accessibility
statement at `/tillganglighet`. A screen reader test and an external review
remain.

## 11. Technology

| Aspect | Open-ePlatform | e-Plattform |
|--------|----------------|-------------|
| Language | Java 8 | Java 21 |
| Framework | OpenHierarchy | Spring Boot 3.2 |
| Database | MySQL | PostgreSQL (Neon in the demo) with Flyway |
| Files | File system | MinIO locally, Vercel Blob in the demo |
| Presentation | XSLT | Next.js 15, React 19, Tailwind |
| API | XML | REST + JSON, OpenAPI |
| Hosting | Application server | Vercel (container + Next.js), Docker locally |
| Tests | – | 49 backend, 31 accessibility, in CI |

## 12. Priorities

1. **BankID/Freja (OIDC)**: blocks production
2. **Server-side validation** of required fields and conditions
3. **Security hardening**: CSP, tokens in httpOnly cookies, penetration test
4. **Production environment**: separate database, backups, no cold starts
5. **Role administration** in the UI
6. **Integrations**: webhooks, e-arkiv
7. **Field types**: map, tree, multi-party signing (with BankID)
8. **E-service import/export and copying**
