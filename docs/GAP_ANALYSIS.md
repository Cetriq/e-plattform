# Gap Analysis: Open-ePlatform vs Modern e-Plattform

This document analyzes the features in the original Open-ePlatform and identifies what has been implemented, what is missing, and what improvements have been made in our modern version.

---

## Executive Summary

| Category | Open-ePlatform | Modern e-Plattform | Gap | Status |
|----------|----------------|-------------------|-----|--------|
| Query Types | 15+ types | 23 types | None for core types | ✅ Done |
| Signing | BankID, Multi-party | Stubbed | Full implementation needed | ⏳ Requires contract |
| PDF Generation | iText-based | ✅ OpenHTMLToPDF | Basic implementation done | ✅ Done |
| Notifications | Email templates | ✅ Thymeleaf + Spring Mail | SMS not implemented | ✅ Done |
| Statistics | Built-in module | ✅ Statistics dashboard | Basic implementation | ✅ Done |
| Integration API | Callback system | Stubbed | Needed | ⏳ Planned |
| Authentication | SAML, BankID | Mock + JWT | BankID/OAuth2 needed | ⏳ Requires contract |
| Security | Basic | ✅ JWT + RBAC + Rate Limiting + Audit | PII encryption needed | ✅ Done |
| API Documentation | None | ✅ Swagger/OpenAPI | Full documentation | ✅ Done |

**Last updated:** 2026-04-06

---

## 1. Core Domain Model

### Flow Management

| Feature | Open-ePlatform | Modern | Status |
|---------|----------------|--------|--------|
| Flow entity | `Flow.java` (100+ fields) | `Flow.java` (simplified) | Implemented (core) |
| FlowFamily (versioning) | Yes | Yes | Implemented |
| FlowType (categorization) | Yes | Yes | Implemented |
| Category | Yes | Yes | Implemented |
| Steps | Yes | Yes | Implemented |
| QueryDescriptor | Yes | `QueryDefinition` | Implemented |
| Evaluators | Complex system | Simplified | Implemented |
| Status definitions | Extensive | Basic | Implemented |
| Status transitions | Defined in DB | Defined in DB | Implemented |
| Flow versioning | Advanced | Basic | Partial |
| Flow publishing | Date-based | Boolean | Simplified |
| Flow preview | Yes | No | Missing |
| Flow forms (PDF templates) | Yes | No | Missing |
| Tags | Yes | No | Missing |
| Checks (checkboxes) | Yes | No | Missing |
| External flows (redirect) | Yes | No | Missing |

### Case Management

| Feature | Open-ePlatform | Modern | Status |
|---------|----------------|--------|--------|
| FlowInstance | Yes | `Case` | Implemented |
| QueryInstance | Yes | Yes | Implemented |
| Case events | Yes | `CaseEvent` | Implemented |
| Internal messages | Yes | Yes | Implemented |
| External messages | Yes | Yes | Implemented |
| Message attachments | Yes | Yes | Implemented |
| Read receipts | Yes | No | Missing |
| Case attributes | Dynamic | JSONB metadata | Implemented (different) |
| Case owners | Multiple | Single creator | Simplified |
| Case managers | User + Group | User + Group | Implemented |
| Flow instance statistics | Yes | No | Missing |
| Aborted flow tracking | Yes | No | Missing |
| User bookmarks | Yes | No | Missing |

---

## 2. Query Types (Form Fields)

### Implemented in Modern Version (23 types)

| Query Type | Open-ePlatform | Modern | Notes |
|------------|----------------|--------|-------|
| TextField | `TextFieldQuery` | `TEXT` | ✅ Implemented |
| TextArea | `TextAreaQuery` | `TEXTAREA` | ✅ Implemented |
| DropDown | `DropDownQuery` | `SELECT` | ✅ Implemented |
| Checkbox | `CheckboxQuery` | `CHECKBOX` | ✅ Implemented |
| RadioButton | `RadioButtonQuery` | `RADIO` | ✅ Implemented |
| FileUpload | `FileUploadQuery` | `FILE` | ✅ Implemented |
| ContactDetail | `ContactDetailQuery` | `PERSON` | ✅ Implemented (PersonField.tsx) |
| OrganizationDetail | `OrganizationDetailQuery` | `ORGANIZATION` | ✅ Implemented (OrganizationField.tsx) |
| Hidden | Implicit | `HIDDEN` | ✅ Implemented |
| Number | - | `NUMBER` | ✅ Added |
| Date | - | `DATE` | ✅ Added |
| DateTime | - | `DATETIME` | ✅ Added |
| Time | - | `TIME` | ✅ Added |
| Email | - | `EMAIL` | ✅ Added |
| Phone | - | `PHONE` | ✅ Added |
| PersonNumber | - | `PERSONNUMMER` | ✅ Added |
| URL | - | `URL` | ✅ Added |
| Currency | - | `CURRENCY` | ✅ Added |
| MultiSelect | - | `MULTISELECT` | ✅ Added |
| Image | - | `IMAGE` | ✅ Added |
| Location/Address | - | `LOCATION` | ✅ Implemented (LocationField.tsx) |
| Map | `BaseMapQuery` | `MAP` | ✅ Implemented (MapField.tsx) |
| Signature | - | `SIGNATURE` | ✅ Implemented (SignatureField.tsx) |

### Missing/Future Query Types

| Query Type | Open-ePlatform | Priority | Notes |
|------------|----------------|----------|-------|
| TreeQuery | Hierarchical selection | Medium | Complex component |
| MultiTreeQuery | Multiple trees | Low | Complex component |
| SinglePolygonMapQuery | Polygon drawing | Low | Advanced map feature |
| MultiGeometryMapQuery | Multiple geometries | Low | Complex |
| PUDMapQuery | PUD-specific | Low | Sweden-specific |
| ManualMultiSignQuery | Multi-party signing | High | Requires BankID |
| ChildQuery | Child data | Low | Sweden-specific |

---

## 3. Signing System

### Open-ePlatform Signing

```
SigningProvider interface
├── DummySigningProvider (dev)
├── BankID integration
├── Multi-party signing
├── Sequential signing
├── Poster signing skip
└── Signature in PDF
```

### Modern e-Plattform Signing

```
BankIdService (stubbed)
└── No actual implementation
```

### Gap

| Feature | Status | Priority |
|---------|--------|----------|
| BankID authentication | Stubbed | Critical |
| BankID signing | Stubbed | Critical |
| Multi-party signing | Missing | High |
| Sequential signing | Missing | High |
| Signature visualization | Missing | Medium |
| Freja eID | Missing | Medium |

---

## 4. PDF Generation ✅ IMPLEMENTED

### Open-ePlatform PDF

```
FlowInstancePDFGenerator module
├── iText library
├── XSL templates (FlowInstancePDF.sv.xsl)
├── Custom fonts
├── Color profiles
├── PDF attachments
└── PDF scheduling
```

### Modern e-Plattform PDF

```
PdfService.java
├── OpenHTMLToPDF library
├── HTML-to-PDF conversion
├── Styled templates (CSS)
├── Swedish character support
├── Case data rendering
└── Download endpoint (/api/v1/cases/{id}/pdf)
```

### Implementation Status

| Feature | Status |
|---------|--------|
| PDF generation service | ✅ Implemented |
| HTML template system | ✅ Implemented |
| Swedish characters | ✅ Works |
| Case data in PDF | ✅ Implemented |
| Frontend download button | ✅ Implemented |
| Attachment embedding | ⏳ Not yet |
| Digital signature embedding | ⏳ Requires BankID |

---

## 5. Notification System ✅ IMPLEMENTED

### Open-ePlatform Notifications

```
StandardFlowNotificationHandler
├── Email templates (XSL-based)
├── Flow family notification settings
├── Tag replacement in messages
├── PDF attachment to emails
├── Size validation
└── Notification options per status
```

### Modern e-Plattform Notifications

```
notification/
├── config/
│   └── EmailConfig.java          # Spring Mail configuration
├── domain/
│   └── NotificationType.java     # Enum for notification types
└── service/
    ├── EmailService.java         # Thymeleaf email rendering
    └── NotificationService.java  # High-level notification API

templates/email/
├── case-submitted.html           # New case confirmation
├── case-status-changed.html      # Status update notification
├── case-completed.html           # Case completion notification
├── new-message.html              # New message to citizen
└── new-message-manager.html      # New message to manager
```

### Implementation Status

| Feature | Status |
|---------|--------|
| Email service (Spring Mail) | ✅ Implemented |
| Template engine (Thymeleaf) | ✅ Implemented |
| Case submission notification | ✅ Implemented |
| Status change notification | ✅ Implemented |
| Message notification | ✅ Implemented |
| MailHog for development | ✅ Configured in docker-compose |
| SMS integration | ⏳ Not yet |
| Push notifications | ⏳ Not yet |
| Notification preferences | ⏳ Not yet |

---

## 6. Statistics & Reporting ✅ IMPLEMENTED

### Open-ePlatform Statistics

```
StatisticsModule
├── FlowStatistics
├── FlowFamilyStatistics
├── FlowInstanceStatistic bean
├── IntegerEntry, FloatEntry, NumberEntry
└── StatisticsAPIExtensionProvider
```

### Modern e-Plattform Statistics

```
statistics/
├── StatisticsController.java    # REST endpoints for statistics
└── StatisticsService.java       # Aggregation queries

Endpoints:
├── GET /api/v1/admin/statistics/overview     # System overview
├── GET /api/v1/admin/statistics/cases        # Case counts by status
└── GET /api/v1/admin/statistics/audit        # Recent audit events
```

### Implementation Status

| Feature | Status |
|---------|--------|
| Case count by status | ✅ Implemented |
| System overview (totals) | ✅ Implemented |
| Audit event listing | ✅ Implemented |
| Flow usage statistics | ⏳ Not yet |
| Export to Excel/CSV | ⏳ Not yet |
| Dashboard visualizations | ✅ Basic frontend page |

---

## 7. Integration & API

### Open-ePlatform Integration

```
FlowEngineIntegrationCallback module
├── IntegrationMessage
├── AddMessage
├── SetAttributeResponse
├── External system callbacks
└── API source management
```

### Modern e-Plattform Integration

```
integration/
├── bankid/ (stubbed)
└── payment/ (stubbed)
```

### Required Implementation

1. Webhook system for external integrations
2. Event publishing (RabbitMQ already configured)
3. External API for case management
4. Payment provider integration
5. Document archive integration

---

## 8. Authentication & Authorization ✅ CORE IMPLEMENTED

### Open-ePlatform Auth

```
├── SAML adapter (MinimalUserSAMLAdapter)
├── BankID/Freja eID
├── Foreign ID support
├── Manager/Admin access controllers
└── Session-based access
```

### Modern e-Plattform Auth

```
auth/
├── MockAuthService              # Development accounts
├── AuthController               # Login/logout endpoints
└── AuthResponse                 # JWT token response

common/
├── config/
│   └── SecurityConfig.java      # Spring Security configuration
│       ├── Dev mode (relaxed)   # enforce-roles: false
│       └── Prod mode (strict)   # enforce-roles: true
└── security/
    └── JwtAuthenticationFilter.java  # JWT token validation
```

### Implementation Status

| Feature | Status |
|---------|--------|
| JWT authentication | ✅ Implemented |
| Role-based access control | ✅ Implemented (ADMIN, MANAGER, USER) |
| Development accounts | ✅ Implemented (3 test users) |
| CORS configuration | ✅ Configured for specific origins |
| Dev/prod security modes | ✅ Switchable via config |
| Admin endpoint protection | ✅ Requires ADMIN/FLOW_EDITOR role |
| Manager endpoint protection | ✅ Requires MANAGER role |
| OAuth2/OIDC integration | ⏳ Not yet |
| BankID authentication | ⏳ Requires contract |
| Freja eID authentication | ⏳ Not yet |
| Organization-based access | ⏳ Not yet |
| API key authentication | ⏳ Not yet |

---

## 9. Administrative Features

### Missing Admin Features

| Feature | Open-ePlatform | Modern | Priority |
|---------|----------------|--------|----------|
| Flow browser | Yes | Basic list | Medium |
| Flow import/export | XML-based | No | Medium |
| Flow copying | Yes | No | Medium |
| User management UI | Yes | Basic | Medium |
| Group management | Yes | Basic | Medium |
| Flow family manager | Yes | No | Medium |
| Operating messages | Yes | No | Low |
| Feedback surveys | Yes | No | Low |
| Flow approval workflow | Yes | No | Low |

---

## 10. Improvements in Modern Version

### Technical Improvements

| Aspect | Open-ePlatform | Modern | Benefit |
|--------|----------------|--------|---------|
| Java version | Java 8 | Java 21 | Virtual threads, records, pattern matching |
| Framework | Custom Hierarchy | Spring Boot 3.2 | Standard, well-documented |
| Database | MySQL | PostgreSQL | JSONB, better performance |
| API | XSLT + XML | REST + JSON | Modern, standard |
| Frontend | XSLT rendering | React/Next.js | Modern, component-based |
| Container | None | Docker-native | Easy deployment |
| Cache | Custom | Redis | Standard, scalable |
| Search | Custom | Meilisearch | Fast, modern |
| Files | Filesystem | MinIO (S3) | Scalable, standard |

### Code Quality Improvements

1. **Domain-Driven Design** - Clear bounded contexts
2. **Simplified entities** - Records for DTOs
3. **REST API** - Standard HTTP verbs and status codes
4. **TypeScript frontend** - Type safety
5. **Modern form handling** - React Hook Form + Zod
6. **Responsive design** - Tailwind CSS

---

## 11. Recommended Implementation Priorities

### Phase 1: Critical (Required for Basic Usage) ✅ COMPLETE

1. ~~**PDF generation** - Required for case documents~~ ✅ DONE
2. ~~**Email notifications** - Required for user communication~~ ✅ DONE
3. ~~**Security hardening** - CORS, authorization, input validation~~ ✅ DONE
4. **BankID authentication** - Required for production ⏳ Requires contract

### Phase 2: High Priority ✅ COMPLETE

5. ~~**Statistics dashboard** - Required for administrators~~ ✅ DONE
6. ~~**Rate limiting** - Prevent API abuse~~ ✅ DONE (Bucket4j, configurable)
7. ~~**Audit logging** - Track user actions~~ ✅ DONE (async, PII sanitization)
8. ~~**File upload validation** - Validate MIME types, scan for malware~~ ✅ DONE (Apache Tika)

### Phase 3: Medium Priority

9. **Multi-party signing** - Required for complex flows (requires BankID)
10. **Flow import/export** - Required for flow management
11. **Integration webhooks** - For external systems
12. **PII encryption** - Encrypt personnummer etc. in database

### Phase 4: Lower Priority

13. **Tree query types** - For hierarchical selections
14. **Read receipts** - For message tracking
15. **Feedback surveys** - Nice to have
16. **Operating messages** - Nice to have
17. **httpOnly cookies** - Move JWT from localStorage

---

## 12. Security Review Findings (2026-04-06)

A security review identified the following areas requiring attention:

### Critical (Before Production)

| Issue | Status | Notes |
|-------|--------|-------|
| Mock authentication | ⚠️ Known | Requires BankID contract |
| Missing API authorization | ✅ DONE | JWT filter + role-based security config |
| Open CORS policy | ✅ DONE | Restricted to specific origins |
| File upload validation | ✅ DONE | Apache Tika MIME detection, whitelist, blocked extensions |
| PII encryption | ⏳ Planned | Encrypt personnummer etc. |

### High Priority

| Issue | Status | Notes |
|-------|--------|-------|
| Rate limiting | ✅ DONE | Bucket4j, 100/min general, 10/min auth, 20/min uploads |
| Audit logging | ✅ DONE | Async logging with PII sanitization |
| PDF injection | ✅ Mitigated | HTML escaping in place |

### Medium Priority

| Issue | Status | Notes |
|-------|--------|-------|
| Token in localStorage | ⏳ Planned | Move to httpOnly cookies |
| CSP headers | 🔴 TODO | Add Content-Security-Policy |
| Debug logging | ⏳ Review | Check for PII leaks |

---

## 13. Conclusion

The modern e-Plattform has successfully implemented the core functionality of Open-ePlatform with a cleaner, more maintainable architecture.

### Completed ✅
- Core domain model (Flow, Case, QueryInstance)
- **23 query types** including Person, Organization, Location, Map, Signature
- File upload with MinIO
- PDF generation with download
- **Email notification system** with Thymeleaf templates
- **JWT authentication** with role-based access control
- **CORS and security configuration** (dev/prod modes)
- Admin portal for flow management (categories, flow types, flows)
- Manager portal for case handling
- Citizen portal for applications
- **Rate limiting** with Bucket4j (configurable per endpoint type)
- **Audit logging** with async processing and PII sanitization
- **File upload validation** with Apache Tika MIME detection
- **Statistics dashboard** with case counts and audit events
- **API documentation** with Swagger/OpenAPI (Swedish descriptions)

### In Progress 🔄
- PII encryption for sensitive data

### Blocked ⏳
- BankID authentication (requires contract)
- Digital signing (requires BankID)
- Multi-party signing (requires BankID)

### Remaining Gaps
1. **PII encryption** - Encrypt personnummer etc. in database
2. **Tree query types** - For hierarchical selections
3. **SMS notifications** - Mobile notifications
4. **Flow import/export** - XML/JSON format
5. **httpOnly cookies** - Move JWT from localStorage

The modern architecture makes it easier to implement these features incrementally, and the technology choices (Spring Boot, React, PostgreSQL) provide a solid foundation for future development.

---

## Appendix: Recent Changes Log

### 2026-04-07 - Security & Documentation Update
- ✅ Added Swagger/OpenAPI documentation with Swedish descriptions
- ✅ Implemented rate limiting with Bucket4j (configurable per endpoint)
- ✅ Added audit logging with async processing and PII sanitization
- ✅ Added file upload validation with Apache Tika MIME detection
- ✅ Implemented statistics dashboard (overview, case counts, audit events)
- ✅ Removed GraphQL (not needed)
- ✅ Updated SecurityConfig for Swagger endpoints

### 2026-04-06 - Major Update
- ✅ Added 10 new query types (23 total)
- ✅ Implemented email notification system with 5 templates
- ✅ Added JWT authentication filter
- ✅ Configured role-based access control (ADMIN, MANAGER, USER)
- ✅ Added dev/prod security mode switching
- ✅ Configured CORS for production
- ✅ Added Mailpit to docker-compose for email testing
- ✅ Added category management admin page
