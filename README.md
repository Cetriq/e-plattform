# e-Plattform

> **EXPERIMENTAL PROJECT**
>
> This project is under active development and is **not ready for production**.
> APIs, database schema, and functionality may change without notice.
> Use only for evaluation and development purposes.
>
> See [EXPERIMENTAL_STATUS.md](docs/EXPERIMENTAL_STATUS.md) for details.

Modern e-service platform for public administration, inspired by Open-ePlatform but built with contemporary technology.

## Technology Stack

### Backend
- **Java 21** with Spring Boot 3.2
- **PostgreSQL 16** for database
- **Redis 7** for cache and sessions
- **Meilisearch** for full-text search
- **MinIO** for file storage (S3-compatible)
- **RabbitMQ** for message queue

### Frontend
- **Next.js 14** with React 18
- **TypeScript** for type safety
- **Tailwind CSS** for styling
- **TanStack Query** for server state
- **React Hook Form** + **Zod** for forms

### DevOps
- **Docker** & **Docker Compose**
- **Traefik** as reverse proxy
- **Prometheus** + **Grafana** for observability

## Getting Started

### Prerequisites

- Docker Desktop
- Java 21 (for local development without Docker)
- Node.js 20 (for local development without Docker)

### Quick Start with Docker

```bash
# Clone and navigate to project folder
cd Open-E_Plattform

# Create .env file
cp .env.example .env

# Start all services
make dev
```

Then open:
- **Frontend**: http://localhost:3000
- **API**: http://localhost:8080
- **API Documentation (Swagger)**: http://localhost:8080/swagger-ui.html
- **Meilisearch**: http://localhost:7700
- **MinIO Console**: http://localhost:9001
- **RabbitMQ Management**: http://localhost:15672
- **Mailpit (Email)**: http://localhost:8025
- **Grafana**: http://localhost:3001
- **Prometheus**: http://localhost:9090

### Local Development

```bash
# Start infrastructure (database, cache, etc.)
make infra

# In one terminal - start backend
make backend

# In another terminal - start frontend
make frontend-install
make frontend
```

## Project Structure

```
Open-E_Plattform/
├── backend/                    # Java Spring Boot backend
│   ├── src/main/java/se/eplatform/
│   │   ├── common/            # Shared code (config, exceptions)
│   │   ├── flow/              # Forms/flows
│   │   ├── cases/             # Cases
│   │   ├── user/              # Users
│   │   └── ...
│   └── src/main/resources/
│       ├── db/migration/      # Flyway SQL migrations
│       └── application*.yml   # Configuration
│
├── frontend/                   # Next.js frontend
│   └── src/
│       ├── app/               # App Router pages
│       ├── components/        # React components
│       ├── lib/               # Utilities
│       └── types/             # TypeScript types
│
├── docker/                     # Docker configuration
│   ├── postgres/
│   ├── grafana/
│   └── prometheus/
│
├── docs/                       # Documentation
│   ├── architecture/          # Architecture documentation
│   ├── EXPERIMENTAL_STATUS.md # Current status report
│   └── DEVELOPER_GUIDE.md     # Developer guide
│
├── docker-compose.yml         # Development environment
├── docker-compose.prod.yml    # Production environment
└── Makefile                   # Shortcuts
```

## API

### API Documentation

Full API documentation is available via Swagger UI:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

The documentation includes:
- All available endpoints with Swedish descriptions
- Request/response schemas with examples
- Authentication information (JWT Bearer tokens)
- Rate limiting details
- Test user credentials

### REST API Examples

```
GET  /api/v1/flows              # List published flows
GET  /api/v1/flows/{id}         # Get a flow with all details
GET  /api/v1/flows/search?q=    # Search flows

POST /api/v1/cases              # Create new case
GET  /api/v1/cases/{id}         # Get case
PUT  /api/v1/cases/{id}/values  # Update case values
POST /api/v1/cases/{id}/submit  # Submit case
PUT  /api/v1/cases/{id}/status  # Change status
```

## Database

Schema is managed with Flyway. Migrations are in `backend/src/main/resources/db/migration/`.

```bash
# Run migrations
make migrate

# Open psql
make psql
```

## Test Data

Test data is created automatically on startup:

**Users:**
- admin@example.com (Admin)
- handlaggare@example.com (Manager)
- medborgare@example.com (Citizen)

**Flows:**
- Building Permit Application (with steps and questions)

## Commands

```bash
make dev           # Start development environment
make up            # Start in background
make down          # Stop services
make logs          # Show logs
make clean         # Clean everything
make test          # Run tests
make health        # Health check
make psql          # Open database CLI
make redis-cli     # Open Redis CLI
```

## Documentation

- [Architecture](docs/architecture/ARCHITECTURE.md) - Detailed architecture documentation
- [Experimental Status](docs/EXPERIMENTAL_STATUS.md) - Current implementation status
- [Developer Guide](docs/DEVELOPER_GUIDE.md) - Development guide and code structure

## License

AGPL-3.0 (same as Open-ePlatform)

**DISCLAIMER:** This software is experimental and provided "AS IS" without warranty of any kind. It is not suitable for production use.
