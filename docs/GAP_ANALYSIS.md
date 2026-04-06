# Gap Analysis: Open-ePlatform vs Modern e-Plattform

This document analyzes the features in the original Open-ePlatform and identifies what has been implemented, what is missing, and what improvements have been made in our modern version.

---

## Executive Summary

| Category | Open-ePlatform | Modern e-Plattform | Gap | Status |
|----------|----------------|-------------------|-----|--------|
| Query Types | 15+ types | 13 types | 2 missing (MAP, SIGNATURE) | ✅ Core done |
| Signing | BankID, Multi-party | Stubbed | Full implementation needed | ⏳ Requires contract |
| PDF Generation | iText-based | ✅ OpenHTMLToPDF | Basic implementation done | ✅ Done |
| Notifications | Email templates | Stubbed | Full implementation needed | 🔴 Next priority |
| Statistics | Built-in module | Not implemented | Needed | ⏳ Planned |
| Integration API | Callback system | Stubbed | Needed | ⏳ Planned |
| Authentication | SAML, BankID | Mock only | BankID/OAuth2 needed | ⏳ Requires contract |

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

### Implemented in Modern Version

| Query Type | Open-ePlatform | Modern | Notes |
|------------|----------------|--------|-------|
| TextField | `TextFieldQuery` | `TEXT` | Implemented |
| TextArea | `TextAreaQuery` | `TEXTAREA` | Implemented |
| DropDown | `DropDownQuery` | `SELECT` | Implemented |
| Checkbox | `CheckboxQuery` | `CHECKBOX` | Implemented |
| RadioButton | `RadioButtonQuery` | `RADIO` | Implemented |
| FileUpload | `FileUploadQuery` | `FILE` | Implemented |
| ContactDetail | `ContactDetailQuery` | Separate fields | Different approach |
| OrganizationDetail | `OrganizationDetailQuery` | Not specific | Could use TEXT |
| Hidden | Implicit | `HIDDEN` | Implemented |
| Number | - | `NUMBER` | Added |
| Date | - | `DATE` | Added |
| DateTime | - | `DATETIME` | Added |
| Email | - | `EMAIL` | Added |
| Phone | - | `PHONE` | Added |
| PersonNumber | - | `PERSONNUMMER` | Added |

### Missing Query Types

| Query Type | Open-ePlatform | Priority | Notes |
|------------|----------------|----------|-------|
| TreeQuery | Hierarchical selection | Medium | Complex component |
| MultiTreeQuery | Multiple trees | Low | Complex component |
| BaseMapQuery | Map selection | High | Requires map library |
| SinglePolygonMapQuery | Polygon drawing | Medium | Requires map library |
| MultiGeometryMapQuery | Multiple geometries | Low | Complex |
| GeneralMapQuery | General map | Medium | Requires map library |
| PUDMapQuery | PUD-specific | Low | Sweden-specific |
| ManualMultiSignQuery | Multi-party signing | High | For signing flow |
| StopQuery | Flow stopper | Low | Can use evaluators |
| ChildQuery | Child data | Low | Sweden-specific |
| FileInfoQuery | File metadata | Low | Can extend FILE |

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

## 5. Notification System

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
└── (empty package)
```

### Required Implementation

1. Email service (Spring Mail)
2. Template engine (Thymeleaf or similar)
3. SMS integration
4. Push notifications (optional)
5. Notification preferences per user
6. Notification triggers on status change

---

## 6. Statistics & Reporting

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
(Not implemented)
```

### Required Implementation

1. Case statistics (count, processing time, etc.)
2. Flow usage statistics
3. User activity statistics
4. Export to Excel/CSV
5. Dashboard visualizations

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

## 8. Authentication & Authorization

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
├── MockAuthService
├── SecurityConfig (basic)
└── AuthController
```

### Required Implementation

1. OAuth2/OIDC integration
2. BankID authentication
3. Freja eID authentication
4. Role-based access control (partially done)
5. Organization-based access
6. API key authentication for integrations

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

### Phase 1: Critical (Required for Basic Usage)

1. ~~**PDF generation** - Required for case documents~~ ✅ DONE
2. **Email notifications** - Required for user communication 🔴 NEXT
3. **Security hardening** - CORS, authorization, input validation 🔴 NEXT
4. **BankID authentication** - Required for production ⏳ Requires contract

### Phase 2: High Priority

5. **Statistics dashboard** - Required for administrators
6. **API authorization** - Role-based access control on endpoints
7. **Rate limiting** - Prevent API abuse
8. **Audit logging** - Track user actions

### Phase 3: Medium Priority

9. **MAP query type** - Common requirement for permits
10. **Multi-party signing** - Required for complex flows (requires BankID)
11. **Flow import/export** - Required for flow management
12. **Integration webhooks** - For external systems

### Phase 4: Lower Priority

13. **Tree query types** - For hierarchical selections
14. **Read receipts** - For message tracking
15. **Feedback surveys** - Nice to have
16. **Operating messages** - Nice to have

---

## 12. Security Review Findings (2026-04-06)

A security review identified the following areas requiring attention:

### Critical (Before Production)

| Issue | Status | Notes |
|-------|--------|-------|
| Mock authentication | ⚠️ Known | Requires BankID contract |
| Missing API authorization | 🔴 TODO | Add @PreAuthorize |
| Open CORS policy | 🔴 TODO | Restrict to specific origins |
| File upload validation | 🔴 TODO | Validate MIME types |
| PII encryption | ⏳ Planned | Encrypt personnummer etc. |

### High Priority

| Issue | Status | Notes |
|-------|--------|-------|
| Rate limiting | 🔴 TODO | Prevent brute force |
| Audit logging | 🔴 TODO | Track user actions |
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
- 13 query types
- File upload with MinIO
- PDF generation with download
- Admin portal for flow management
- Manager portal for case handling
- Citizen portal for applications

### In Progress 🔄
- Security hardening
- Email notifications

### Blocked ⏳
- BankID authentication (requires contract)
- Digital signing (requires BankID)

### Remaining Gaps
1. **Notifications** - Email/SMS system
2. **Statistics** - Reporting and analytics
3. **Special query types** - MAP, TREE
4. **Security** - Authorization, rate limiting, audit logging

The modern architecture makes it easier to implement these features incrementally, and the technology choices (Spring Boot, React, PostgreSQL) provide a solid foundation for future development.
