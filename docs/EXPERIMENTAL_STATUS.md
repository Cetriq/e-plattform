# e-Plattform – Status

> The platform runs as a public **demo**. It is not ready for production; the
> blockers are listed under [What production needs](#what-production-needs).

**Last updated:** 2026-10-10

## Summary

The core of Open-ePlatform works end to end: administrators build e-services,
citizens apply, case workers handle the cases, and the security and IT roles
can follow up. Compliance with GDPR, the archives act and WCAG 2.1 AA is built
in. What is missing for production is mainly real e-legitimation (BankID or
Freja), and a few things around it.

## Features

### Citizens

| Feature | Status | Notes |
|---------|--------|-------|
| Browse e-services | ✅ | Grouped by category (no free-text search in the UI) |
| Multi-step forms | ✅ | 20 field types plus headings, paragraphs and dividers |
| Conditional fields | ✅ | Show, hide or require fields based on other answers |
| Draft saving | ✅ | Saved as you go |
| Attachments | ✅ | Uploaded straight to private storage; type and size checked |
| My cases, status and history | ✅ | |
| Messages to and from the case worker | ✅ | With unread markers |
| Case PDF | ✅ | Tagged PDF/UA in Swedish |
| Profile | ✅ | Name and phone; e-mail comes from the login |
| Map field | ⚠️ Partial | Position is given as address or coordinates; no map yet |
| Signature field | ⚠️ Partial | Drawn or typed name, not a legal e-signature |
| BankID / Freja eID | ❌ | Shown as "coming soon"; demo login with personas |

### Case workers

| Feature | Status | Notes |
|---------|--------|-------|
| Case list with filters (all, mine, unassigned) and search | ✅ | |
| Assign cases | ✅ | To one person; not to groups |
| Status changes | ✅ | Only the transitions the e-service allows; some require a comment |
| Messages and internal notes | ✅ | Internal notes are never shown to the citizen |
| PDF export | ✅ | |
| E-mail notifications | ⚠️ | Implemented with templates; switched off in the demo |

### Administrators

| Feature | Status | Notes |
|---------|--------|-------|
| E-services: steps, fields, conditions, statuses | ✅ | Condition editor in the UI |
| Publish, versions | ✅ | Basic versioning per flow family |
| Retention period per e-service | ✅ | Months after the case is closed |
| Categories and service types | ✅ | |
| Users | ⚠️ | List only; roles cannot be changed in the UI yet |
| Statistics | ✅ | Overview and cases per status |
| Import, export or copy e-services | ❌ | |

### Information security and data protection

| Feature | Status | Notes |
|---------|--------|-------|
| Audit log (spårbarhetslogg) | ✅ | Who read or changed what, about whom, outcome, IP. Hash-chained and append-only |
| Integrity check of the log | ✅ | Detects any changed or removed entry |
| Export | ✅ | CSV (Excel-friendly) and JSON |
| Register extract (GDPR art. 15/20) | ✅ | JSON with cases, messages, internal notes, attachments and the access log |
| Erasure (art. 17) | ✅ | Drafts deleted, account anonymised; submitted cases kept as allmänna handlingar until retention |
| Retention (gallring) | ✅ | Nightly; overview of what is due in the next 30 days; manual run |

### IT and operations

| Feature | Status | Notes |
|---------|--------|-------|
| Component status | ✅ | Database (latency, schema version), file storage, e-mail |
| Requests, errors, memory, cold starts, scheduled jobs | ✅ | Per instance |
| System log | ✅ | Technical events only, no personal data |

### Accessibility

WCAG 2.1 AA: all page types, form steps, dialogs and error states pass axe-core,
and keyboard use, page titles and reflow at 320 px are tested on every change.
A screen reader test and an external review remain; until then the
[accessibility statement](https://e-plattform.vercel.app/tillganglighet) says
"partially compliant".

## Security

| Area | State |
|------|-------|
| Authentication | Demo personas + shared access code; signed JWT (HS256, 8 h) with a warning and option to extend before it expires |
| Authorisation | Enforced by the API: citizens only reach their own cases and files (others give 404); admin, security and operations APIs require their role |
| Rate limiting | Per IP: 100 requests, 10 logins and 20 uploads per minute |
| File uploads | Content-type detection, allow list, size limits, private storage |
| Audit log | Tamper-evident (hash chain + database trigger) |
| Secrets | Vercel environment variables; none in the repository |

## Known gaps

| Gap | Impact |
|-----|--------|
| No e-legitimation | Blocks production |
| Required fields and conditions are only checked in the browser | A crafted API call could submit an incomplete case |
| JWT in `localStorage`, no Content-Security-Policy | Token theft if an XSS bug is ever introduced |
| Files of erased or retention-removed cases stay in Blob storage | Orphan files until cleaned up |
| Preview deployments share the production database | A preview can change demo data |
| Cold start of about 13 s after 5 minutes without traffic | Slow first click |
| Roles cannot be changed in the UI | Done in the database today |
| No PII encryption at rest beyond Neon's disk encryption | |
| Redis, Meilisearch and RabbitMQ from the original design are not used | Search is plain SQL |

## What production needs

**Must have**

1. BankID or Freja eID (OIDC), replacing the demo login
2. Server-side validation of required fields and conditions
3. A separate production database and environment, with backups and restore tested
4. Content-Security-Policy, and tokens in httpOnly cookies
5. Deletion of attachment files when cases are removed
6. Screen reader test and external accessibility review
7. Security review / penetration test

**Should have**

1. Role administration in the UI
2. E-mail notifications switched on with a real sender domain
3. An always-on backend instance (or native image) to remove cold starts
4. Import/export of e-services

## Version history

| Version | Date | Notes |
|---------|------|-------|
| 0.1.0 | 2024 | Initial experimental version |
| 0.1.1 | 2026-04 | Rate limiting, file validation, Swagger, statistics |
| 0.2.0 | 2026-10 | Demo on Vercel (Neon, Blob); access control and isolated demo citizens; case handling (assignment, status rules, messages, notes, conditions); audit log, register extract, erasure and retention; security and IT workspaces; shared design system; WCAG 2.1 AA with tests in CI; faster cold starts; live API documentation |
