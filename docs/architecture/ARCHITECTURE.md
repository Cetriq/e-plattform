# Modern e-Plattform - Arkitekturförslag

## 1. Översikt

En modern ersättning för Open-ePlatform, byggd med Java 21+ och containeriserad med Docker.

### Kärnprinciper
- **Cloud-native** - Containeriserad, skalbar, observerbar
- **API-first** - REST + GraphQL för flexibilitet
- **Event-driven** - Asynkron kommunikation mellan tjänster
- **Domain-driven** - Tydlig domänmodell och bounded contexts

---

## 2. Teknologistack

### Backend
| Komponent | Teknologi | Motivering |
|-----------|-----------|------------|
| **Runtime** | Java 21+ (GraalVM) | Virtual threads, pattern matching, records |
| **Framework** | Spring Boot 3.2+ | Mogen, vältestat, stort ekosystem |
| **API** | Spring WebFlux + GraphQL | Reaktivt, flexibelt |
| **ORM** | Spring Data JPA + Hibernate 6 | Standard, kraftfullt |
| **Validering** | Jakarta Validation + custom | Deklarativ validering |
| **Säkerhet** | Spring Security 6 + OAuth2/OIDC | Modern autentisering |

### Databas & Lagring
| Komponent | Teknologi | Motivering |
|-----------|-----------|------------|
| **Primär DB** | PostgreSQL 16 | JSON-stöd, robusthet, partitionering |
| **Cache** | Redis 7 | Sessions, cache, pub/sub |
| **Sökning** | Meilisearch / Elasticsearch | Snabb fulltextsökning |
| **Fillagring** | MinIO (S3-kompatibel) | Skalbar objektlagring |
| **Meddelandekö** | RabbitMQ / Apache Kafka | Event-driven arkitektur |

### Frontend
| Komponent | Teknologi | Motivering |
|-----------|-----------|------------|
| **Framework** | React 18 + TypeScript | Komponentbaserat, typesafe |
| **Meta-framework** | Next.js 14 / Vite | SSR, optimering |
| **State** | TanStack Query + Zustand | Server state + client state |
| **Formulär** | React Hook Form + Zod | Dynamiska formulär, validering |
| **UI** | Tailwind CSS + Radix UI | Tillgängligt, modernt |

### DevOps & Infrastruktur
| Komponent | Teknologi | Motivering |
|-----------|-----------|------------|
| **Container** | Docker + Docker Compose | Lokal utveckling, deployment |
| **Orkestrering** | Kubernetes (produktion) | Skalning, self-healing |
| **CI/CD** | GitHub Actions | Automatiserad pipeline |
| **Observabilitet** | OpenTelemetry + Grafana | Traces, metrics, logs |
| **Reverse Proxy** | Traefik / nginx | Load balancing, SSL |

---

## 3. Systemarkitektur

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND                                    │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  Next.js / React SPA                                             │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐ │    │
│  │  │ Medborgare│  │ Handlägg.│  │  Admin   │  │ Formulärbyggare │ │    │
│  │  │   Portal  │  │  Portal  │  │  Portal  │  │                  │ │    │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────────────┘ │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           API GATEWAY                                    │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  Traefik / Spring Cloud Gateway                                  │    │
│  │  • Rate limiting  • SSL termination  • Routing  • Auth          │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         BACKEND SERVICES                                 │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │ Flow Service │  │ Case Service │  │ User Service │  │Notification │  │
│  │              │  │              │  │              │  │   Service   │  │
│  │ • Formulär   │  │ • Ärenden    │  │ • Authn/Authz│  │ • Email     │  │
│  │ • Steg       │  │ • Status     │  │ • Profiler   │  │ • SMS       │  │
│  │ • Frågor     │  │ • Meddelanden│  │ • Grupper    │  │ • Push      │  │
│  │ • Evaluators │  │ • Historik   │  │ • Roller     │  │             │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘  │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │ File Service │  │Search Service│  │ PDF Service  │  │ Integration │  │
│  │              │  │              │  │              │  │   Service   │  │
│  │ • Upload     │  │ • Indexering │  │ • Generering │  │ • BankID    │  │
│  │ • Download   │  │ • Sökning    │  │ • Mallar     │  │ • Betalning │  │
│  │ • Preview    │  │ • Filter     │  │ • Export     │  │ • Externa   │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘  │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                                       │
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │  PostgreSQL  │  │    Redis     │  │  Meilisearch │  │    MinIO    │  │
│  │              │  │              │  │              │  │             │  │
│  │ • Flows      │  │ • Sessions   │  │ • Ärenden    │  │ • Bilagor   │  │
│  │ • Cases      │  │ • Cache      │  │ • Formulär   │  │ • Dokument  │  │
│  │ • Users      │  │ • Pub/Sub    │  │ • Fullttext  │  │ • PDF       │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘  │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                        RabbitMQ / Kafka                           │   │
│  │  • case.created  • case.updated  • notification.send  • ...      │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Domänmodell

### 4.1 Bounded Contexts

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FLOW CONTEXT                                     │
│  Ansvarar för: Formulärdefinitioner, steg, frågor, villkorslogik        │
│                                                                          │
│  Aggregat:                                                               │
│  • Flow (root) → Step → QueryDefinition → EvaluatorDefinition           │
│  • FlowFamily, FlowType, Category                                        │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                         CASE CONTEXT                                     │
│  Ansvarar för: Ärendeinstanser, status, värden, meddelanden             │
│                                                                          │
│  Aggregat:                                                               │
│  • Case (root) → QueryInstance → QueryValue                              │
│  • CaseStatus, CaseEvent, Message                                        │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                         IDENTITY CONTEXT                                 │
│  Ansvarar för: Användare, grupper, roller, behörigheter                 │
│                                                                          │
│  Aggregat:                                                               │
│  • User (root) → UserProfile                                             │
│  • Organization → Group → Role                                           │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Entitetsdiagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              FLOW                                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐         ┌──────────────┐         ┌──────────────┐     │
│  │  FlowFamily  │◄────────│     Flow     │────────►│   FlowType   │     │
│  │              │   1:N   │              │   N:1   │              │     │
│  │ • id         │         │ • id         │         │ • id         │     │
│  │ • name       │         │ • name       │         │ • name       │     │
│  │ • icon       │         │ • version    │         │ • description│     │
│  └──────────────┘         │ • enabled    │         └──────────────┘     │
│                           │ • published  │                               │
│                           └──────┬───────┘                               │
│                                  │ 1:N                                   │
│                                  ▼                                       │
│                           ┌──────────────┐                               │
│                           │     Step     │                               │
│                           │              │                               │
│                           │ • id         │                               │
│                           │ • name       │                               │
│                           │ • sortOrder  │                               │
│                           └──────┬───────┘                               │
│                                  │ 1:N                                   │
│                                  ▼                                       │
│  ┌──────────────┐         ┌──────────────┐         ┌──────────────┐     │
│  │  Evaluator   │◄────────│QueryDefinition────────►│  QueryType   │     │
│  │  Definition  │   1:N   │              │   N:1   │              │     │
│  │              │         │ • id         │         │ • TEXT       │     │
│  │ • id         │         │ • name       │         │ • SELECT     │     │
│  │ • condition  │         │ • required   │         │ • CHECKBOX   │     │
│  │ • targetIds  │         │ • config     │         │ • FILE       │     │
│  └──────────────┘         └──────────────┘         │ • DATE       │     │
│                                                     │ • MAP        │     │
│                                                     │ • ...        │     │
│                                                     └──────────────┘     │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                              CASE                                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐         ┌──────────────┐         ┌──────────────┐     │
│  │     Flow     │◄────────│     Case     │────────►│    Status    │     │
│  │              │   N:1   │              │   N:1   │              │     │
│  │              │         │ • id         │         │ • id         │     │
│  │              │         │ • flowId     │         │ • name       │     │
│  │              │         │ • statusId   │         │ • type       │     │
│  │              │         │ • created    │         │ • config     │     │
│  │              │         │ • submitted  │         └──────────────┘     │
│  │              │         │ • priority   │                               │
│  │              │         └──────┬───────┘                               │
│  │              │                │                                       │
│  └──────────────┘    ┌───────────┼───────────┬───────────┐              │
│                      │           │           │           │              │
│                      ▼           ▼           ▼           ▼              │
│               ┌────────────┐ ┌────────┐ ┌────────┐ ┌──────────┐         │
│               │QueryInstance│ │ Event  │ │Message │ │Attachment│         │
│               │            │ │        │ │        │ │          │         │
│               │ • queryId  │ │ • type │ │ • from │ │ • fileId │         │
│               │ • value    │ │ • data │ │ • body │ │ • name   │         │
│               │ • state    │ │ • time │ │ • read │ │ • size   │         │
│               └────────────┘ └────────┘ └────────┘ └──────────┘         │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Databasschema (PostgreSQL)

### 5.1 Flow-tabeller

```sql
-- Tjänstfamiljer (gruppering av versioner)
CREATE TABLE flow_families (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    icon            VARCHAR(100),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Tjänsttyper (kategorisering)
CREATE TABLE flow_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    color           VARCHAR(7),
    sort_order      INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Kategorier
CREATE TABLE categories (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_type_id    UUID REFERENCES flow_types(id),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    sort_order      INTEGER DEFAULT 0
);

-- Flöden/Formulär (huvudentitet)
CREATE TABLE flows (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id       UUID REFERENCES flow_families(id),
    type_id         UUID REFERENCES flow_types(id),
    category_id     UUID REFERENCES categories(id),

    name            VARCHAR(255) NOT NULL,
    version         INTEGER NOT NULL DEFAULT 1,

    -- Beskrivningar
    short_description   TEXT,
    long_description    TEXT,
    submitted_message   TEXT,

    -- Konfiguration
    enabled             BOOLEAN DEFAULT false,
    require_auth        BOOLEAN DEFAULT true,
    require_signing     BOOLEAN DEFAULT false,
    sequential_signing  BOOLEAN DEFAULT false,
    allow_save_draft    BOOLEAN DEFAULT true,

    -- Publicering
    status              VARCHAR(20) DEFAULT 'DRAFT',  -- DRAFT, PUBLISHED, ARCHIVED
    publish_date        TIMESTAMPTZ,
    unpublish_date      TIMESTAMPTZ,

    -- Metadata
    external_link       VARCHAR(1024),
    tags                TEXT[],

    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      UUID,

    UNIQUE(family_id, version)
);

-- Index för sökning
CREATE INDEX idx_flows_status ON flows(status);
CREATE INDEX idx_flows_type ON flows(type_id);
CREATE INDEX idx_flows_tags ON flows USING GIN(tags);

-- Steg i formulär
CREATE TABLE steps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id         UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,

    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    sort_order      INTEGER NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_steps_flow ON steps(flow_id);

-- Frågedefinitioner (formulärfält)
CREATE TABLE query_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_id         UUID NOT NULL REFERENCES steps(id) ON DELETE CASCADE,

    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    help_text       TEXT,

    query_type      VARCHAR(50) NOT NULL,  -- TEXT, TEXTAREA, SELECT, CHECKBOX, etc.

    -- Konfiguration (typ-specifik)
    config          JSONB DEFAULT '{}',

    -- Tillstånd
    required        BOOLEAN DEFAULT false,
    default_state   VARCHAR(20) DEFAULT 'VISIBLE',  -- VISIBLE, HIDDEN

    -- Export
    export_name     VARCHAR(255),
    exportable      BOOLEAN DEFAULT true,

    sort_order      INTEGER NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_query_defs_step ON query_definitions(step_id);
CREATE INDEX idx_query_defs_type ON query_definitions(query_type);

-- Evaluatorer (villkorslogik)
CREATE TABLE evaluator_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_id        UUID NOT NULL REFERENCES query_definitions(id) ON DELETE CASCADE,

    name            VARCHAR(255),
    description     TEXT,

    evaluator_type  VARCHAR(50) NOT NULL,  -- VALUE_EQUALS, REGEX, CUSTOM

    -- Villkor (JSON för flexibilitet)
    condition       JSONB NOT NULL,

    -- Målstyrning
    target_query_ids UUID[] NOT NULL,
    target_state    VARCHAR(20) NOT NULL,  -- VISIBLE, VISIBLE_REQUIRED, HIDDEN

    enabled         BOOLEAN DEFAULT true,
    sort_order      INTEGER DEFAULT 0,

    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_evaluators_query ON evaluator_definitions(query_id);

-- Status-definitioner per flöde
CREATE TABLE status_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id         UUID NOT NULL REFERENCES flows(id) ON DELETE CASCADE,

    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    internal_description TEXT,

    status_type     VARCHAR(30) NOT NULL,  -- INITIAL, IN_PROGRESS, WAITING, COMPLETED, CANCELLED

    -- Konfiguration
    config          JSONB DEFAULT '{}',

    -- Behörigheter
    user_can_edit       BOOLEAN DEFAULT false,
    user_can_delete     BOOLEAN DEFAULT false,
    user_can_message    BOOLEAN DEFAULT true,

    -- SLA
    handling_days   INTEGER,

    sort_order      INTEGER DEFAULT 0,

    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Tillåtna statusövergångar
CREATE TABLE status_transitions (
    from_status_id  UUID REFERENCES status_definitions(id) ON DELETE CASCADE,
    to_status_id    UUID REFERENCES status_definitions(id) ON DELETE CASCADE,

    requires_comment BOOLEAN DEFAULT false,

    PRIMARY KEY (from_status_id, to_status_id)
);
```

### 5.2 Case-tabeller

```sql
-- Ärenden (instanser av formulär)
CREATE TABLE cases (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id         UUID NOT NULL REFERENCES flows(id),
    status_id       UUID REFERENCES status_definitions(id),

    -- Referensnummer (läsbart)
    reference_number VARCHAR(50) UNIQUE,

    -- Aktuellt steg
    current_step_id UUID REFERENCES steps(id),

    -- Tidsstämplar
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    submitted_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,

    -- Prioritet
    priority        VARCHAR(10) DEFAULT 'NORMAL',  -- LOW, NORMAL, HIGH, URGENT

    -- Beskrivningar
    user_description    VARCHAR(500),
    manager_description VARCHAR(500),

    -- Skapare
    created_by      UUID NOT NULL,

    -- Metadata
    metadata        JSONB DEFAULT '{}'
);

-- Index
CREATE INDEX idx_cases_flow ON cases(flow_id);
CREATE INDEX idx_cases_status ON cases(status_id);
CREATE INDEX idx_cases_created_by ON cases(created_by);
CREATE INDEX idx_cases_created_at ON cases(created_at DESC);
CREATE INDEX idx_cases_reference ON cases(reference_number);

-- Ägarrelationer (medborgare)
CREATE TABLE case_owners (
    case_id         UUID REFERENCES cases(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL,

    added_at        TIMESTAMPTZ DEFAULT NOW(),

    PRIMARY KEY (case_id, user_id)
);

-- Handläggarrelationer
CREATE TABLE case_managers (
    case_id         UUID REFERENCES cases(id) ON DELETE CASCADE,
    user_id         UUID,
    group_id        UUID,

    assigned_at     TIMESTAMPTZ DEFAULT NOW(),
    assigned_by     UUID,

    CHECK (user_id IS NOT NULL OR group_id IS NOT NULL)
);

CREATE INDEX idx_case_managers_case ON case_managers(case_id);
CREATE INDEX idx_case_managers_user ON case_managers(user_id);

-- Frågeinstanser (formulärvärden)
CREATE TABLE query_instances (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    query_def_id    UUID NOT NULL REFERENCES query_definitions(id),

    -- Aktuellt tillstånd
    state           VARCHAR(20) DEFAULT 'VISIBLE',

    -- Värde (JSONB för flexibilitet)
    value           JSONB,

    -- Metadata
    populated       BOOLEAN DEFAULT false,
    validated       BOOLEAN DEFAULT false,

    updated_at      TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE(case_id, query_def_id)
);

CREATE INDEX idx_query_instances_case ON query_instances(case_id);

-- Sökindex för formulärvärden
CREATE TABLE case_search_values (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    query_def_id    UUID NOT NULL REFERENCES query_definitions(id),

    search_value    VARCHAR(500),

    UNIQUE(case_id, query_def_id)
);

CREATE INDEX idx_case_search_case ON case_search_values(case_id);
CREATE INDEX idx_case_search_value ON case_search_values(search_value);

-- Händelselogg
CREATE TABLE case_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,

    event_type      VARCHAR(50) NOT NULL,

    -- Detaljer
    data            JSONB DEFAULT '{}',

    -- Relaterade objekt
    status_id       UUID REFERENCES status_definitions(id),

    -- Metadata
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      UUID,

    -- För sökning
    description     TEXT
);

CREATE INDEX idx_case_events_case ON case_events(case_id);
CREATE INDEX idx_case_events_type ON case_events(event_type);
CREATE INDEX idx_case_events_created ON case_events(created_at DESC);

-- Meddelanden (intern)
CREATE TABLE internal_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,

    message         TEXT NOT NULL,

    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      UUID NOT NULL,

    deleted_at      TIMESTAMPTZ,
    deleted_by      UUID
);

-- Meddelanden (extern - till medborgare)
CREATE TABLE external_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID NOT NULL REFERENCES cases(id) ON DELETE CASCADE,

    message         TEXT NOT NULL,

    -- Avsändare
    from_manager    BOOLEAN DEFAULT false,
    system_message  BOOLEAN DEFAULT false,

    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      UUID NOT NULL,

    -- Läskvitto
    read_at         TIMESTAMPTZ,
    read_by         UUID
);

CREATE INDEX idx_ext_messages_case ON external_messages(case_id);

-- Bilagor
CREATE TABLE attachments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Koppling (polymorf)
    entity_type     VARCHAR(50) NOT NULL,  -- CASE, MESSAGE, QUERY_INSTANCE
    entity_id       UUID NOT NULL,

    -- Fil
    file_id         UUID NOT NULL,  -- Referens till MinIO
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100),
    size_bytes      BIGINT,

    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      UUID
);

CREATE INDEX idx_attachments_entity ON attachments(entity_type, entity_id);

-- Dynamiska attribut
CREATE TABLE case_attributes (
    case_id         UUID REFERENCES cases(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    value           TEXT,

    PRIMARY KEY (case_id, name)
);
```

### 5.3 Identitetstabeller

```sql
-- Användare
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identitet
    external_id     VARCHAR(255) UNIQUE,  -- BankID personnummer, etc.
    email           VARCHAR(255) UNIQUE,

    -- Profil
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    phone           VARCHAR(50),

    -- Status
    active          BOOLEAN DEFAULT true,
    email_verified  BOOLEAN DEFAULT false,

    -- Metadata
    settings        JSONB DEFAULT '{}',

    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_external_id ON users(external_id);

-- Organisationer
CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name            VARCHAR(255) NOT NULL,
    org_number      VARCHAR(20),

    parent_id       UUID REFERENCES organizations(id),

    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Grupper
CREATE TABLE groups (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id),

    name            VARCHAR(255) NOT NULL,
    description     TEXT,

    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Roller
CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name            VARCHAR(100) NOT NULL UNIQUE,
    description     TEXT,

    permissions     TEXT[] DEFAULT '{}'
);

-- Användar-grupp-koppling
CREATE TABLE user_groups (
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    group_id        UUID REFERENCES groups(id) ON DELETE CASCADE,

    PRIMARY KEY (user_id, group_id)
);

-- Användar-roll-koppling
CREATE TABLE user_roles (
    user_id         UUID REFERENCES users(id) ON DELETE CASCADE,
    role_id         UUID REFERENCES roles(id) ON DELETE CASCADE,

    PRIMARY KEY (user_id, role_id)
);
```

---

## 6. API-design

### 6.1 REST API

```yaml
# OpenAPI 3.0 (förenklad)

/api/v1/flows:
  GET:    Lista alla publicerade flöden
  POST:   Skapa nytt flöde (admin)

/api/v1/flows/{flowId}:
  GET:    Hämta flödesdefinition
  PUT:    Uppdatera flöde
  DELETE: Ta bort flöde

/api/v1/flows/{flowId}/steps:
  GET:    Lista steg
  POST:   Skapa steg

/api/v1/flows/{flowId}/publish:
  POST:   Publicera flöde

/api/v1/cases:
  GET:    Lista ärenden (med filter)
  POST:   Skapa nytt ärende

/api/v1/cases/{caseId}:
  GET:    Hämta ärende med alla värden
  PUT:    Uppdatera ärende
  DELETE: Ta bort ärende

/api/v1/cases/{caseId}/submit:
  POST:   Skicka in ärende

/api/v1/cases/{caseId}/status:
  PUT:    Ändra status

/api/v1/cases/{caseId}/messages:
  GET:    Lista meddelanden
  POST:   Skicka meddelande

/api/v1/cases/{caseId}/events:
  GET:    Hämta historik

/api/v1/cases/{caseId}/attachments:
  GET:    Lista bilagor
  POST:   Ladda upp bilaga

/api/v1/search/cases:
  POST:   Sök ärenden (med facetter)

/api/v1/users/me:
  GET:    Hämta inloggad användare
  PUT:    Uppdatera profil

/api/v1/users/me/cases:
  GET:    Mina ärenden
```

### 6.2 GraphQL Schema

```graphql
type Query {
  # Flows
  flows(filter: FlowFilter, pagination: Pagination): FlowConnection!
  flow(id: ID!): Flow

  # Cases
  cases(filter: CaseFilter, pagination: Pagination): CaseConnection!
  case(id: ID!): Case
  myCases(pagination: Pagination): CaseConnection!

  # Search
  searchCases(query: String!, filters: CaseFilter): SearchResult!

  # User
  me: User!
}

type Mutation {
  # Cases
  createCase(input: CreateCaseInput!): Case!
  updateCase(id: ID!, input: UpdateCaseInput!): Case!
  submitCase(id: ID!): Case!

  updateCaseStatus(id: ID!, statusId: ID!, comment: String): Case!

  # Messages
  sendMessage(caseId: ID!, input: MessageInput!): Message!

  # Files
  uploadAttachment(caseId: ID!, file: Upload!): Attachment!
}

type Subscription {
  caseUpdated(caseId: ID!): Case!
  newMessage(caseId: ID!): Message!
}

type Flow {
  id: ID!
  name: String!
  version: Int!
  description: String

  steps: [Step!]!
  statuses: [StatusDefinition!]!

  enabled: Boolean!
  requireAuth: Boolean!
  requireSigning: Boolean!

  createdAt: DateTime!
}

type Step {
  id: ID!
  name: String!
  sortOrder: Int!

  queries: [QueryDefinition!]!
}

type QueryDefinition {
  id: ID!
  name: String!
  type: QueryType!
  required: Boolean!
  config: JSON!

  evaluators: [EvaluatorDefinition!]!
}

type Case {
  id: ID!
  referenceNumber: String!

  flow: Flow!
  status: StatusDefinition!
  currentStep: Step

  priority: Priority!

  values: [QueryInstance!]!
  events: [CaseEvent!]!
  messages: [Message!]!
  attachments: [Attachment!]!

  owners: [User!]!
  managers: [User!]!

  createdAt: DateTime!
  submittedAt: DateTime
}

type QueryInstance {
  id: ID!
  definition: QueryDefinition!
  state: QueryState!
  value: JSON
}

enum QueryType {
  TEXT
  TEXTAREA
  NUMBER
  DATE
  DATETIME
  SELECT
  MULTISELECT
  CHECKBOX
  RADIO
  FILE
  MAP
  SIGNATURE
}

enum QueryState {
  VISIBLE
  VISIBLE_REQUIRED
  HIDDEN
}

enum Priority {
  LOW
  NORMAL
  HIGH
  URGENT
}
```

---

## 7. Docker-arkitektur

### 7.1 Utvecklingsmiljö

```yaml
# docker-compose.yml
version: '3.9'

services:
  # ===================
  # DATABASER
  # ===================

  postgres:
    image: postgres:16-alpine
    container_name: eplatform-db
    environment:
      POSTGRES_DB: eplatform
      POSTGRES_USER: eplatform
      POSTGRES_PASSWORD: ${DB_PASSWORD:-devpassword}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./docker/postgres/init:/docker-entrypoint-initdb.d
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U eplatform"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: eplatform-redis
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ===================
  # SÖKNING
  # ===================

  meilisearch:
    image: getmeili/meilisearch:v1.6
    container_name: eplatform-search
    environment:
      MEILI_MASTER_KEY: ${MEILI_KEY:-devmasterkey}
      MEILI_ENV: development
    volumes:
      - meilisearch_data:/meili_data
    ports:
      - "7700:7700"

  # ===================
  # FILLAGRING
  # ===================

  minio:
    image: minio/minio:latest
    container_name: eplatform-files
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_USER:-minioadmin}
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD:-minioadmin}
    volumes:
      - minio_data:/data
    ports:
      - "9000:9000"
      - "9001:9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  # ===================
  # MEDDELANDEKÖ
  # ===================

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    container_name: eplatform-mq
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-guest}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASS:-guest}
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    ports:
      - "5672:5672"
      - "15672:15672"  # Management UI

  # ===================
  # BACKEND
  # ===================

  api:
    build:
      context: ./backend
      dockerfile: Dockerfile
      target: development
    container_name: eplatform-api
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/eplatform
      SPRING_DATASOURCE_USERNAME: eplatform
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-devpassword}
      SPRING_REDIS_HOST: redis
      MEILISEARCH_HOST: http://meilisearch:7700
      MEILISEARCH_KEY: ${MEILI_KEY:-devmasterkey}
      MINIO_ENDPOINT: http://minio:9000
      RABBITMQ_HOST: rabbitmq
    volumes:
      - ./backend/src:/app/src
      - ./backend/build:/app/build
    ports:
      - "8080:8080"
      - "5005:5005"  # Debug port
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      meilisearch:
        condition: service_started
      minio:
        condition: service_healthy
      rabbitmq:
        condition: service_started

  # ===================
  # FRONTEND
  # ===================

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
      target: development
    container_name: eplatform-web
    environment:
      NEXT_PUBLIC_API_URL: http://localhost:8080
    volumes:
      - ./frontend/src:/app/src
      - ./frontend/public:/app/public
    ports:
      - "3000:3000"
    depends_on:
      - api

  # ===================
  # REVERSE PROXY
  # ===================

  traefik:
    image: traefik:v3.0
    container_name: eplatform-proxy
    command:
      - "--api.insecure=true"
      - "--providers.docker=true"
      - "--providers.docker.exposedbydefault=false"
      - "--entrypoints.web.address=:80"
    ports:
      - "80:80"
      - "8081:8080"  # Traefik dashboard
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro

  # ===================
  # OBSERVABILITET
  # ===================

  grafana:
    image: grafana/grafana:latest
    container_name: eplatform-grafana
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
    volumes:
      - grafana_data:/var/lib/grafana
      - ./docker/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./docker/grafana/datasources:/etc/grafana/provisioning/datasources
    ports:
      - "3001:3000"

  prometheus:
    image: prom/prometheus:latest
    container_name: eplatform-prometheus
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    ports:
      - "9090:9090"

volumes:
  postgres_data:
  redis_data:
  meilisearch_data:
  minio_data:
  rabbitmq_data:
  grafana_data:
  prometheus_data:
```

### 7.2 Backend Dockerfile (Java 21)

```dockerfile
# backend/Dockerfile

# ===================
# BUILD STAGE
# ===================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Kopiera Gradle-filer först (cache)
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

# Ladda ner beroenden
RUN ./gradlew dependencies --no-daemon

# Kopiera källkod
COPY src src

# Bygg
RUN ./gradlew bootJar --no-daemon -x test

# ===================
# DEVELOPMENT STAGE
# ===================
FROM eclipse-temurin:21-jdk-alpine AS development

WORKDIR /app

# Installera verktyg för utveckling
RUN apk add --no-cache curl

# Kopiera Gradle
COPY --from=builder /app/gradle gradle
COPY --from=builder /app/gradlew .
COPY --from=builder /app/build.gradle.kts .
COPY --from=builder /app/settings.gradle.kts .

# Hot reload med Spring DevTools
ENV SPRING_DEVTOOLS_RESTART_ENABLED=true

EXPOSE 8080 5005

CMD ["./gradlew", "bootRun", "--args='--spring.profiles.active=dev'", "-Dorg.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"]

# ===================
# PRODUCTION STAGE
# ===================
FROM eclipse-temurin:21-jre-alpine AS production

WORKDIR /app

# Säkerhet: icke-root användare
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Kopiera JAR
COPY --from=builder /app/build/libs/*.jar app.jar

# Ägarskap
RUN chown -R appuser:appgroup /app
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM-optimeringar för containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 7.3 Frontend Dockerfile (Next.js)

```dockerfile
# frontend/Dockerfile

# ===================
# BASE
# ===================
FROM node:20-alpine AS base
WORKDIR /app

# ===================
# DEPENDENCIES
# ===================
FROM base AS deps
COPY package.json package-lock.json ./
RUN npm ci

# ===================
# DEVELOPMENT
# ===================
FROM base AS development
COPY --from=deps /app/node_modules ./node_modules
COPY package.json ./

EXPOSE 3000

ENV NODE_ENV=development

CMD ["npm", "run", "dev"]

# ===================
# BUILD
# ===================
FROM base AS builder
COPY --from=deps /app/node_modules ./node_modules
COPY . .

ENV NEXT_TELEMETRY_DISABLED=1

RUN npm run build

# ===================
# PRODUCTION
# ===================
FROM base AS production

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
```

### 7.4 Produktions Docker Compose

```yaml
# docker-compose.prod.yml
version: '3.9'

services:
  postgres:
    image: postgres:16-alpine
    restart: always
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    deploy:
      resources:
        limits:
          memory: 2G

  redis:
    image: redis:7-alpine
    restart: always
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data

  meilisearch:
    image: getmeili/meilisearch:v1.6
    restart: always
    environment:
      MEILI_MASTER_KEY: ${MEILI_KEY}
      MEILI_ENV: production
    volumes:
      - meilisearch_data:/meili_data

  minio:
    image: minio/minio:latest
    restart: always
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD}
    volumes:
      - minio_data:/data

  rabbitmq:
    image: rabbitmq:3.13-alpine
    restart: always
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASS}
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq

  api:
    image: ${REGISTRY}/eplatform-api:${VERSION}
    restart: always
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${DB_NAME}
      SPRING_DATASOURCE_USERNAME: ${DB_USER}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      SPRING_REDIS_HOST: redis
      SPRING_REDIS_PASSWORD: ${REDIS_PASSWORD}
      MEILISEARCH_HOST: http://meilisearch:7700
      MEILISEARCH_KEY: ${MEILI_KEY}
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_USER}
      MINIO_SECRET_KEY: ${MINIO_PASSWORD}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_USER: ${RABBITMQ_USER}
      RABBITMQ_PASS: ${RABBITMQ_PASS}
    deploy:
      replicas: 2
      resources:
        limits:
          memory: 2G
    depends_on:
      - postgres
      - redis
      - meilisearch
      - minio
      - rabbitmq
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.api.rule=Host(`api.${DOMAIN}`)"
      - "traefik.http.services.api.loadbalancer.server.port=8080"

  frontend:
    image: ${REGISTRY}/eplatform-web:${VERSION}
    restart: always
    environment:
      NEXT_PUBLIC_API_URL: https://api.${DOMAIN}
    deploy:
      replicas: 2
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.web.rule=Host(`${DOMAIN}`)"
      - "traefik.http.services.web.loadbalancer.server.port=3000"

  traefik:
    image: traefik:v3.0
    restart: always
    command:
      - "--providers.docker=true"
      - "--providers.docker.exposedbydefault=false"
      - "--entrypoints.web.address=:80"
      - "--entrypoints.websecure.address=:443"
      - "--certificatesresolvers.letsencrypt.acme.tlschallenge=true"
      - "--certificatesresolvers.letsencrypt.acme.email=${ACME_EMAIL}"
      - "--certificatesresolvers.letsencrypt.acme.storage=/letsencrypt/acme.json"
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
      - letsencrypt_data:/letsencrypt

volumes:
  postgres_data:
  redis_data:
  meilisearch_data:
  minio_data:
  rabbitmq_data:
  letsencrypt_data:
```

---

## 8. Projektstruktur

```
eplatform/
├── docker/
│   ├── postgres/
│   │   └── init/
│   │       └── 01-init.sql
│   ├── grafana/
│   │   ├── dashboards/
│   │   └── datasources/
│   └── prometheus/
│       └── prometheus.yml
│
├── backend/
│   ├── Dockerfile
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── se/
│       │   │       └── eplatform/
│       │   │           ├── EplatformApplication.java
│       │   │           │
│       │   │           ├── common/
│       │   │           │   ├── config/
│       │   │           │   ├── exception/
│       │   │           │   ├── security/
│       │   │           │   └── util/
│       │   │           │
│       │   │           ├── flow/
│       │   │           │   ├── api/
│       │   │           │   │   ├── FlowController.java
│       │   │           │   │   ├── FlowGraphQLController.java
│       │   │           │   │   └── dto/
│       │   │           │   ├── domain/
│       │   │           │   │   ├── Flow.java
│       │   │           │   │   ├── Step.java
│       │   │           │   │   ├── QueryDefinition.java
│       │   │           │   │   └── EvaluatorDefinition.java
│       │   │           │   ├── repository/
│       │   │           │   └── service/
│       │   │           │
│       │   │           ├── cases/
│       │   │           │   ├── api/
│       │   │           │   ├── domain/
│       │   │           │   │   ├── Case.java
│       │   │           │   │   ├── QueryInstance.java
│       │   │           │   │   ├── CaseEvent.java
│       │   │           │   │   └── Message.java
│       │   │           │   ├── repository/
│       │   │           │   ├── service/
│       │   │           │   └── evaluator/
│       │   │           │
│       │   │           ├── user/
│       │   │           │   ├── api/
│       │   │           │   ├── domain/
│       │   │           │   ├── repository/
│       │   │           │   └── service/
│       │   │           │
│       │   │           ├── notification/
│       │   │           │   ├── service/
│       │   │           │   └── event/
│       │   │           │
│       │   │           ├── file/
│       │   │           │   ├── api/
│       │   │           │   └── service/
│       │   │           │
│       │   │           ├── search/
│       │   │           │   ├── service/
│       │   │           │   └── indexer/
│       │   │           │
│       │   │           └── integration/
│       │   │               ├── bankid/
│       │   │               └── payment/
│       │   │
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       ├── db/
│       │       │   └── migration/
│       │       │       ├── V1__initial_schema.sql
│       │       │       └── V2__seed_data.sql
│       │       └── graphql/
│       │           └── schema.graphqls
│       │
│       └── test/
│           └── java/
│
├── frontend/
│   ├── Dockerfile
│   ├── package.json
│   ├── next.config.js
│   ├── tailwind.config.js
│   └── src/
│       ├── app/
│       │   ├── layout.tsx
│       │   ├── page.tsx
│       │   ├── (citizen)/
│       │   │   ├── services/
│       │   │   ├── cases/
│       │   │   └── profile/
│       │   ├── (manager)/
│       │   │   ├── dashboard/
│       │   │   ├── cases/
│       │   │   └── flows/
│       │   └── (admin)/
│       │       ├── flows/
│       │       ├── users/
│       │       └── settings/
│       │
│       ├── components/
│       │   ├── ui/
│       │   ├── forms/
│       │   │   ├── FormBuilder.tsx
│       │   │   ├── FormRenderer.tsx
│       │   │   └── fields/
│       │   ├── cases/
│       │   └── layout/
│       │
│       ├── lib/
│       │   ├── api/
│       │   ├── hooks/
│       │   └── utils/
│       │
│       └── types/
│
├── docker-compose.yml
├── docker-compose.prod.yml
├── .env.example
├── Makefile
└── README.md
```

---

## 9. Java 21+ Features att utnyttja

### 9.1 Records för DTOs

```java
// Immutable DTOs med records
public record FlowDTO(
    UUID id,
    String name,
    int version,
    String description,
    boolean enabled,
    List<StepDTO> steps
) {
    public static FlowDTO from(Flow flow) {
        return new FlowDTO(
            flow.getId(),
            flow.getName(),
            flow.getVersion(),
            flow.getDescription(),
            flow.isEnabled(),
            flow.getSteps().stream()
                .map(StepDTO::from)
                .toList()
        );
    }
}

public record CreateCaseRequest(
    UUID flowId,
    Map<UUID, Object> values
) {}
```

### 9.2 Pattern Matching

```java
// Pattern matching för instanceof
public String formatQueryValue(QueryInstance instance) {
    return switch (instance.getValue()) {
        case String s -> s;
        case Number n -> n.toString();
        case List<?> list -> String.join(", ",
            list.stream().map(Object::toString).toList());
        case Map<?, ?> map -> formatMap(map);
        case null -> "";
        default -> instance.getValue().toString();
    };
}

// Sealed classes för evaluator-typer
public sealed interface EvaluatorCondition
    permits ValueEquals, ValueIn, Regex, Custom {

    boolean evaluate(Object value);
}

public record ValueEquals(Object expected) implements EvaluatorCondition {
    @Override
    public boolean evaluate(Object value) {
        return Objects.equals(expected, value);
    }
}

public record Regex(String pattern) implements EvaluatorCondition {
    @Override
    public boolean evaluate(Object value) {
        return value != null &&
            Pattern.matches(pattern, value.toString());
    }
}
```

### 9.3 Virtual Threads

```java
@Configuration
public class VirtualThreadConfig {

    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }

    @Bean
    public AsyncTaskExecutor taskExecutor() {
        return new TaskExecutorAdapter(
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }
}

// Service med blockerande operationer - nu effektivt med virtual threads
@Service
public class CaseService {

    public Case createCase(CreateCaseRequest request) {
        // Blockerande DB-operationer körs nu på virtual threads
        // utan att blockera plattformstrådar
        var flow = flowRepository.findById(request.flowId())
            .orElseThrow();

        var case_ = Case.create(flow, request.values());

        // Asynkron indexering
        searchIndexer.indexAsync(case_);

        return caseRepository.save(case_);
    }
}
```

### 9.4 Structured Concurrency (Preview)

```java
// Java 21 structured concurrency för parallella operationer
public CaseDetails getCaseWithDetails(UUID caseId) throws Exception {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

        Supplier<Case> caseTask = scope.fork(() ->
            caseRepository.findById(caseId).orElseThrow()
        );

        Supplier<List<CaseEvent>> eventsTask = scope.fork(() ->
            eventRepository.findByCaseId(caseId)
        );

        Supplier<List<Message>> messagesTask = scope.fork(() ->
            messageRepository.findByCaseId(caseId)
        );

        Supplier<List<Attachment>> attachmentsTask = scope.fork(() ->
            attachmentRepository.findByEntityId(caseId)
        );

        scope.join();
        scope.throwIfFailed();

        return new CaseDetails(
            caseTask.get(),
            eventsTask.get(),
            messagesTask.get(),
            attachmentsTask.get()
        );
    }
}
```

---

## 10. Säkerhet

### 10.1 Autentisering

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.csrfTokenRepository(
                CookieCsrfTokenRepository.withHttpOnlyFalse()
            ))
            .cors(Customizer.withDefaults())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(Customizer.withDefaults())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/flows/**").hasRole("USER")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .build();
    }
}
```

### 10.2 BankID-integration

```java
@Service
public class BankIdService {

    private final BankIdClient client;

    public AuthResponse initAuth(String personalNumber) {
        var response = client.auth(AuthRequest.builder()
            .personalNumber(personalNumber)
            .endUserIp(getCurrentIp())
            .build());

        return new AuthResponse(
            response.getOrderRef(),
            response.getAutoStartToken()
        );
    }

    public CollectResponse collect(String orderRef) {
        return client.collect(orderRef);
    }

    public SignResponse initSign(String personalNumber, String message) {
        return client.sign(SignRequest.builder()
            .personalNumber(personalNumber)
            .userVisibleData(Base64.encode(message))
            .build());
    }
}
```

---

## 11. Nästa steg

### Fas 1: Foundation (MVP)
1. Sätt upp Docker-miljö med alla tjänster
2. Implementera databasschema med Flyway-migrationer
3. Skapa grundläggande CRUD för Flow och Case
4. Enkel frontend med formulärrendering

### Fas 2: Kärnfunktionalitet
1. Evaluator-system för villkorslogik
2. Statushantering och övergångar
3. Meddelanden (intern/extern)
4. Filuppladdning med MinIO

### Fas 3: Integrationer
1. BankID-autentisering
2. E-signering
3. Betalningsintegration
4. Notifieringar (email/SMS)

### Fas 4: Administration
1. Formulärbyggare (drag-and-drop)
2. Statistik och rapporter
3. Användarhantering
4. Systemkonfiguration

---

## 12. Fördelar jämfört med Open-ePlatform

| Aspekt | Open-ePlatform | Modern version |
|--------|----------------|----------------|
| **Java-version** | Java 8 | Java 21+ |
| **Framework** | Custom Hierarchy | Spring Boot 3.2 |
| **Rendering** | XSLT | React/Next.js |
| **API** | Ingen standard | REST + GraphQL |
| **Databas** | MySQL | PostgreSQL |
| **Container** | Nej | Docker-native |
| **Skalning** | Svårt | Kubernetes-ready |
| **Observabilitet** | Begränsad | OpenTelemetry |
| **Tester** | Begränsade | Fullt testat |
| **Dokumentation** | Begränsad | OpenAPI/GraphQL |
