# ===================
# BASE
# ===================
FROM node:20-alpine AS base
WORKDIR /app

# ===================
# DEPENDENCIES
# ===================
FROM base AS deps

# Kopiera package filer
COPY package.json package-lock.json* ./

# Installera beroenden
RUN npm ci

# ===================
# DEVELOPMENT
# ===================
FROM base AS development

# Kopiera node_modules från deps
COPY --from=deps /app/node_modules ./node_modules
COPY package.json ./
COPY postcss.config.js ./
COPY tailwind.config.js ./
COPY tsconfig.json ./

EXPOSE 3000

ENV NODE_ENV=development
ENV NEXT_TELEMETRY_DISABLED=1

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

RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

COPY --from=builder /app/public ./public

# Kopiera standalone build
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

CMD ["node", "server.js"]
