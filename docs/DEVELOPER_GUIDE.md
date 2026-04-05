# e-Plattform Developer Guide

This guide provides an overview of the e-Plattform architecture, code structure, and development practices.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Backend Structure](#backend-structure)
3. [Frontend Structure](#frontend-structure)
4. [Domain Models](#domain-models)
5. [API Design](#api-design)
6. [Form System](#form-system)
7. [Local Development](#local-development)
8. [Contributing](#contributing)

---

## Architecture Overview

e-Plattform follows a modern microservices-ready architecture with clear separation between frontend and backend.

### Backend

- **Framework:** Spring Boot 3.2 with Java 21
- **Architecture:** Domain-Driven Design (DDD) with bounded contexts
- **Database:** PostgreSQL 16 with Flyway migrations
- **Cache:** Redis 7
- **File Storage:** MinIO (S3-compatible)
- **Message Queue:** RabbitMQ

### Frontend

- **Framework:** Next.js 14 with React 18
- **Language:** TypeScript
- **State Management:** TanStack Query (server state) + React Context (form state)
- **Styling:** Tailwind CSS
- **Forms:** React Hook Form + Zod validation

### Communication

```
Frontend (Next.js)
      |
      | REST API (JSON)
      v
Backend (Spring Boot)
      |
      +-- PostgreSQL (primary data)
      +-- Redis (cache, sessions)
      +-- MinIO (files)
      +-- RabbitMQ (async events)
```

---

## Backend Structure

### Package Organization

```
se.eplatform/
├── EplatformApplication.java    # Main entry point
├── common/                      # Shared code
│   ├── config/                  # Spring configuration
│   ├── exception/               # Custom exceptions & handlers
│   ├── security/                # Authentication & authorization
│   └── util/                    # Utilities
├── flow/                        # Flow bounded context
│   ├── api/                     # REST controllers & DTOs
│   ├── domain/                  # Entities & value objects
│   ├── repository/              # Data access
│   └── service/                 # Business logic
├── cases/                       # Case bounded context
│   ├── api/
│   ├── domain/
│   ├── repository/
│   ├── service/
│   └── evaluator/               # Conditional logic evaluation
├── user/                        # User bounded context
│   ├── api/
│   ├── domain/
│   ├── repository/
│   └── service/
├── file/                        # File handling
│   ├── api/
│   └── service/
└── integration/                 # External integrations
    ├── bankid/                  # BankID (stubbed)
    └── payment/                 # Payment (stubbed)
```

### Key Patterns

**Repository Pattern**
```java
public interface CaseRepository extends JpaRepository<Case, UUID> {
    List<Case> findByCreatedByOrderByCreatedAtDesc(UUID userId);
    Optional<Case> findByIdAndCreatedBy(UUID id, UUID userId);
}
```

**Service Layer**
```java
@Service
@Transactional
public class CaseService {
    public Case createCase(UUID flowId, UUID userId) {
        // Business logic here
    }
}
```

**DTO Mapping with Records**
```java
public record CaseDTO(
    UUID id,
    String referenceNumber,
    FlowSummaryDTO flow,
    StatusDTO status,
    List<QueryInstanceDTO> values
) {
    public static CaseDTO from(Case case_) {
        return new CaseDTO(
            case_.getId(),
            case_.getReferenceNumber(),
            // ...
        );
    }
}
```

---

## Frontend Structure

### Directory Organization

```
src/
├── app/                         # Next.js App Router
│   ├── layout.tsx               # Root layout
│   ├── page.tsx                 # Home page
│   ├── (public)/                # Public routes (no auth)
│   │   └── services/            # Service catalog
│   ├── (auth)/                  # Authenticated routes
│   │   ├── cases/               # User's cases
│   │   └── case/[id]/           # Case details
│   └── (admin)/                 # Admin routes
│       └── flows/               # Flow management
├── components/
│   ├── ui/                      # Base UI components
│   ├── form/                    # Form system components
│   │   ├── FormRenderer.tsx     # Main form renderer
│   │   ├── FormContext.tsx      # Form state management
│   │   └── fields/              # Field type components
│   ├── cases/                   # Case-related components
│   └── layout/                  # Layout components
├── lib/
│   ├── api/                     # API client functions
│   ├── hooks/                   # Custom React hooks
│   └── utils/                   # Utility functions
└── types/                       # TypeScript type definitions
```

### Key Components

**FormRenderer** - Renders forms based on flow definition
```tsx
<FormRenderer
  flow={flowData}
  initialValues={existingValues}
  onSubmit={handleSubmit}
  onSaveDraft={handleSaveDraft}
/>
```

**FormContext** - Manages form state and field visibility
```tsx
const { values, setFieldValue, getFieldState } = useFormContext();
```

**Field Components** - Type-specific field renderers
```tsx
// Each QueryType has a corresponding component
<TextField definition={queryDef} />
<SelectField definition={queryDef} />
<FileField definition={queryDef} />
```

---

## Domain Models

### Flow Context

The Flow context defines form templates:

```
FlowFamily (groups versions)
    └── Flow (a form version)
            ├── name, version, description
            ├── enabled, requireAuth, requireSigning
            └── Steps (ordered sections)
                    └── QueryDefinitions (fields)
                            ├── name, type, required
                            ├── config (type-specific JSON)
                            └── EvaluatorDefinitions
                                    └── condition, targetQueryIds, targetState
```

### Case Context

The Case context handles form submissions:

```
Case (a submitted form)
    ├── referenceNumber
    ├── status (from StatusDefinition)
    ├── QueryInstances (field values)
    │       ├── value (JSON)
    │       └── state (VISIBLE, HIDDEN, etc.)
    ├── CaseEvents (audit log)
    ├── Messages (internal/external)
    └── Attachments (files)
```

### User Context

```
User
    ├── email, firstName, lastName
    ├── Roles (ADMIN, MANAGER, USER)
    │       └── Permissions
    └── Groups (organizational units)
```

---

## API Design

### REST Endpoints

**Flows**
```
GET  /api/v1/flows              # List published flows
GET  /api/v1/flows/{id}         # Get flow with full definition
GET  /api/v1/flows/search?q=    # Search flows
```

**Cases**
```
POST /api/v1/cases              # Create new case
GET  /api/v1/cases              # List user's cases
GET  /api/v1/cases/{id}         # Get case details
PUT  /api/v1/cases/{id}/values  # Update field values
POST /api/v1/cases/{id}/submit  # Submit case
PUT  /api/v1/cases/{id}/status  # Change status (managers)
```

**Files**
```
POST /api/v1/files/upload       # Upload file
GET  /api/v1/files/{id}         # Download file
```

### Response Format

```json
{
  "id": "uuid",
  "referenceNumber": "2024-00001",
  "flow": { "id": "uuid", "name": "Building Permit" },
  "status": { "id": "uuid", "name": "Submitted", "type": "INITIAL" },
  "values": [
    {
      "queryDefinitionId": "uuid",
      "value": "John Doe",
      "state": "VISIBLE"
    }
  ],
  "createdAt": "2024-01-15T10:30:00Z"
}
```

---

## Form System

### Query Types

| Type | Description | Config Options |
|------|-------------|----------------|
| TEXT | Single-line text | maxLength, placeholder |
| TEXTAREA | Multi-line text | maxLength, rows |
| NUMBER | Numeric input | min, max, step |
| DATE | Date picker | minDate, maxDate |
| SELECT | Dropdown | options[], multiple |
| CHECKBOX | Checkboxes | options[] |
| RADIO | Radio buttons | options[] |
| FILE | File upload | accept, maxSize, maxFiles |
| HIDDEN | Hidden field | - |

### Evaluator System

Evaluators control field visibility based on other field values:

```json
{
  "evaluatorType": "VALUE_EQUALS",
  "condition": {
    "sourceQueryId": "uuid-of-source-field",
    "expectedValue": "yes"
  },
  "targetQueryIds": ["uuid-of-target-field"],
  "targetState": "VISIBLE_REQUIRED"
}
```

**Evaluator Types:**
- `VALUE_EQUALS` - Source value equals expected
- `VALUE_NOT_EQUALS` - Source value does not equal expected
- `VALUE_IN` - Source value is in a list
- `VALUE_NOT_EMPTY` - Source has any value
- `REGEX` - Source matches regex pattern

**Target States:**
- `VISIBLE` - Field is shown (optional)
- `VISIBLE_REQUIRED` - Field is shown and required
- `HIDDEN` - Field is hidden

---

## Local Development

### Prerequisites

- Docker Desktop
- Java 21 (for running backend outside Docker)
- Node.js 20 (for running frontend outside Docker)

### Quick Start with Docker

```bash
# Clone repository
git clone <repo-url>
cd Open-E_Plattform

# Copy environment file
cp .env.example .env

# Start all services
make dev
```

### Running Without Docker

```bash
# Start infrastructure only
make infra

# Terminal 1: Backend
cd backend
./gradlew bootRun

# Terminal 2: Frontend
cd frontend
npm install
npm run dev
```

### Test Users

| Email | Role | Permissions |
|-------|------|-------------|
| admin@example.com | Admin | Full access |
| handlaggare@example.com | Manager | Case management |
| medborgare@example.com | User | Create/view own cases |

### Useful Commands

```bash
make dev        # Start development environment
make logs       # View all logs
make logs-api   # View backend logs only
make psql       # Open database CLI
make redis-cli  # Open Redis CLI
make clean      # Remove all containers and volumes
```

---

## Contributing

### Code Standards

**Backend (Java)**
- Follow Google Java Style Guide
- Use records for DTOs
- Prefer constructor injection
- Write meaningful Javadoc for public APIs

**Frontend (TypeScript)**
- Use functional components with hooks
- Prefer named exports
- Use TypeScript strict mode
- Follow ESLint/Prettier configuration

### Commit Conventions

Use conventional commits:
```
feat: add new field type for signatures
fix: resolve date picker timezone issue
docs: update API documentation
refactor: extract form validation logic
```

### Pull Request Process

1. Create feature branch from `main`
2. Make changes with tests
3. Update documentation if needed
4. Submit PR with clear description
5. Address review comments
6. Squash and merge when approved

---

## Further Reading

- [Architecture Documentation](./architecture/ARCHITECTURE.md)
- [Experimental Status Report](./EXPERIMENTAL_STATUS.md)
