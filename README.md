# e-Plattform

Modern e-tjänstplattform för offentlig förvaltning, inspirerad av Open-ePlatform men byggd med modern teknologi.

## Teknologistack

### Backend
- **Java 21** med Spring Boot 3.2
- **PostgreSQL 16** för databas
- **Redis 7** för cache och sessions
- **Meilisearch** för fulltextsökning
- **MinIO** för fillagring (S3-kompatibel)
- **RabbitMQ** för meddelandekö

### Frontend
- **Next.js 14** med React 18
- **TypeScript** för typsäkerhet
- **Tailwind CSS** för styling
- **TanStack Query** för server state
- **React Hook Form** + **Zod** för formulär

### DevOps
- **Docker** & **Docker Compose**
- **Traefik** som reverse proxy
- **Prometheus** + **Grafana** för observabilitet

## Kom igång

### Förutsättningar

- Docker Desktop
- Java 21 (för lokal utveckling utan Docker)
- Node.js 20 (för lokal utveckling utan Docker)

### Snabbstart med Docker

```bash
# Klona och gå till projektmappen
cd Open-E_Plattform

# Skapa .env-fil
cp .env.example .env

# Starta alla tjänster
make dev
```

Öppna sedan:
- **Frontend**: http://localhost:3000
- **API**: http://localhost:8080
- **API Docs**: http://localhost:8080/graphiql
- **Meilisearch**: http://localhost:7700
- **MinIO Console**: http://localhost:9001
- **RabbitMQ Management**: http://localhost:15672
- **Grafana**: http://localhost:3001

### Lokal utveckling

```bash
# Starta infrastruktur (databas, cache, etc.)
make infra

# I en terminal - starta backend
make backend

# I en annan terminal - starta frontend
make frontend-install
make frontend
```

## Projektstruktur

```
Open-E_Plattform/
├── backend/                    # Java Spring Boot backend
│   ├── src/main/java/se/eplatform/
│   │   ├── common/            # Gemensam kod (config, exceptions)
│   │   ├── flow/              # Formulär/flöden
│   │   ├── cases/             # Ärenden
│   │   ├── user/              # Användare
│   │   └── ...
│   └── src/main/resources/
│       ├── db/migration/      # Flyway SQL-migrationer
│       └── application*.yml   # Konfiguration
│
├── frontend/                   # Next.js frontend
│   └── src/
│       ├── app/               # App Router pages
│       ├── components/        # React-komponenter
│       ├── lib/               # Utilities
│       └── types/             # TypeScript-typer
│
├── docker/                     # Docker-konfiguration
│   ├── postgres/
│   ├── grafana/
│   └── prometheus/
│
├── docs/                       # Dokumentation
│   └── architecture/          # Arkitekturdokumentation
│
├── docker-compose.yml         # Utvecklingsmiljö
├── docker-compose.prod.yml    # Produktionsmiljö
└── Makefile                   # Genvägar
```

## API

### REST API

```
GET  /api/v1/flows              # Lista publicerade flöden
GET  /api/v1/flows/{id}         # Hämta ett flöde med alla detaljer
GET  /api/v1/flows/search?q=    # Sök flöden

POST /api/v1/cases              # Skapa nytt ärende
GET  /api/v1/cases/{id}         # Hämta ärende
PUT  /api/v1/cases/{id}/values  # Uppdatera ärendevärden
POST /api/v1/cases/{id}/submit  # Skicka in ärende
PUT  /api/v1/cases/{id}/status  # Ändra status
```

### GraphQL

GraphQL-endpoint: `http://localhost:8080/graphql`
GraphiQL: `http://localhost:8080/graphiql`

## Databas

Schemat hanteras med Flyway. Migrationer finns i `backend/src/main/resources/db/migration/`.

```bash
# Kör migrationer
make migrate

# Öppna psql
make psql
```

## Testdata

Vid start skapas testdata automatiskt:

**Användare:**
- admin@example.com (Admin)
- handlaggare@example.com (Handläggare)
- medborgare@example.com (Medborgare)

**Flöden:**
- Ansökan om bygglov (med steg och frågor)

## Kommandon

```bash
make dev           # Starta utvecklingsmiljö
make up            # Starta i bakgrunden
make down          # Stoppa tjänster
make logs          # Visa loggar
make clean         # Rensa allt
make test          # Kör tester
make health        # Hälsokontroll
make psql          # Öppna databas-CLI
make redis-cli     # Öppna Redis-CLI
```

## Arkitektur

Se [docs/architecture/ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md) för detaljerad arkitekturdokumentation.

## Licens

AGPLv3 (samma som Open-ePlatform)
