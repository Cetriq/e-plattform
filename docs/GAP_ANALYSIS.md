# Gap Analysis: Open-ePlatform vs Modern e-Plattform

This document analyzes the features in the original Open-ePlatform and identifies what has been implemented, what is missing, and what improvements have been made in our modern version.

---

## Executive Summary

| Category | Open-ePlatform | Modern e-Plattform | Gap |
|----------|----------------|-------------------|-----|
| Query Types | 15+ types | 13 types | 2 missing (MAP, SIGNATURE) |
| Signing | BankID, Multi-party | Stubbed | Full implementation needed |
| PDF Generation | iText-based | Not implemented | Full implementation needed |
| Notifications | Email templates | Stubbed | Full implementation needed |
| Statistics | Built-in module | Not implemented | Needed |
| Integration API | Callback system | Stubbed | Needed |
| Authentication | SAML, BankID | Mock only | BankID/OAuth2 needed |

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

## 4. PDF Generation

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
(Not implemented)
```

### Required Implementation

1. PDF generation service using modern library (OpenPDF, iText 7, or Apache PDFBox)
2. Template system for PDF layout
3. Support for Swedish characters
4. Attachment embedding
5. Digital signature embedding

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

1. **BankID authentication** - Required for production
2. **PDF generation** - Required for case documents
3. **Email notifications** - Required for user communication

### Phase 2: High Priority

4. **MAP query type** - Common requirement for permits
5. **Multi-party signing** - Required for complex flows
6. **Statistics dashboard** - Required for administrators
7. **Flow import/export** - Required for flow management

### Phase 3: Medium Priority

8. **Tree query types** - For hierarchical selections
9. **Integration webhooks** - For external systems
10. **Read receipts** - For message tracking
11. **Flow versioning improvements** - For flow management

### Phase 4: Lower Priority

12. **Feedback surveys** - Nice to have
13. **Operating messages** - Nice to have
14. **PUD-specific queries** - Sweden-specific
15. **Advanced flow approval** - Complex workflow

---

## 12. Conclusion

The modern e-Plattform has successfully implemented the core functionality of Open-ePlatform with a cleaner, more maintainable architecture. The main gaps are in:

1. **Authentication** - BankID/SAML integration
2. **Signing** - Multi-party digital signing
3. **PDF Generation** - Document creation
4. **Notifications** - Email/SMS system
5. **Statistics** - Reporting and analytics
6. **Special query types** - MAP, TREE, SIGNATURE

The modern architecture makes it easier to implement these features incrementally, and the technology choices (Spring Boot, React, PostgreSQL) provide a solid foundation for future development.
