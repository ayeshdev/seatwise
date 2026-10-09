# syntax=docker/dockerfile:1
# Build context: frontend/

FROM node:22-bookworm-slim AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.12.0 --activate

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:1.27-alpine AS runtime

# Rendered by the official image's envsubst step at start (only defined env vars are replaced).
ENV API_UPSTREAM=http://api:8080

COPY nginx/desk.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY nginx/40-write-runtime-config.sh /docker-entrypoint.d/40-write-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/40-write-runtime-config.sh

COPY --from=build /app/dist/seatwise-desk/browser /usr/share/nginx/html

EXPOSE 80
