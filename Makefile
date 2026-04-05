.PHONY: help dev up down build logs clean test migrate

# Default target
help:
	@echo "e-Plattform - Tillgängliga kommandon:"
	@echo ""
	@echo "  make dev        - Starta utvecklingsmiljön (docker-compose up)"
	@echo "  make up         - Starta alla tjänster i bakgrunden"
	@echo "  make down       - Stoppa alla tjänster"
	@echo "  make build      - Bygg alla Docker-images"
	@echo "  make logs       - Visa loggar från alla tjänster"
	@echo "  make clean      - Ta bort alla volymer och containers"
	@echo "  make test       - Kör tester"
	@echo "  make migrate    - Kör databasmigrationer"
	@echo ""
	@echo "  make backend    - Starta endast backend (utan Docker)"
	@echo "  make frontend   - Starta endast frontend (utan Docker)"
	@echo "  make infra      - Starta endast infrastruktur (db, redis, etc.)"
	@echo ""

# Starta utvecklingsmiljön
dev:
	docker compose up

# Starta i bakgrunden
up:
	docker compose up -d

# Stoppa tjänster
down:
	docker compose down

# Bygg images
build:
	docker compose build

# Visa loggar
logs:
	docker compose logs -f

# Visa specifika loggar
logs-api:
	docker compose logs -f api

logs-frontend:
	docker compose logs -f frontend

logs-db:
	docker compose logs -f postgres

# Rensa allt
clean:
	docker compose down -v --remove-orphans
	rm -rf backend/build
	rm -rf frontend/.next
	rm -rf frontend/node_modules

# Kör tester
test:
	cd backend && ./gradlew test

# Kör migrationer manuellt
migrate:
	cd backend && ./gradlew flywayMigrate

# Starta endast infrastruktur
infra:
	docker compose up -d postgres redis meilisearch minio rabbitmq

# Starta backend lokalt (kräver att infra kör)
backend:
	cd backend && ./gradlew bootRun

# Starta frontend lokalt
frontend:
	cd frontend && npm run dev

# Installera frontend-beroenden
frontend-install:
	cd frontend && npm install

# Skapa .env från example
env:
	cp .env.example .env

# Bygg för produktion
build-prod:
	docker compose -f docker-compose.prod.yml build

# Starta produktion
up-prod:
	docker compose -f docker-compose.prod.yml up -d

# Öppna psql
psql:
	docker compose exec postgres psql -U eplatform -d eplatform

# Öppna Redis CLI
redis-cli:
	docker compose exec redis redis-cli

# Se status
status:
	docker compose ps

# Hälsokontroll
health:
	@echo "Checking services..."
	@curl -s http://localhost:8080/actuator/health | jq . || echo "API: Not responding"
	@curl -s http://localhost:3000 > /dev/null && echo "Frontend: OK" || echo "Frontend: Not responding"
	@curl -s http://localhost:7700/health | jq . || echo "Meilisearch: Not responding"
	@curl -s http://localhost:9000/minio/health/live && echo "MinIO: OK" || echo "MinIO: Not responding"
