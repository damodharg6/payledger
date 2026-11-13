# syntax=docker/dockerfile:1
# PayLedger dashboard image. Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
# Stage 1 type-checks, tests and builds with Node; stage 2 serves the static files with nginx.

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY . .
RUN npm test && npm run build

FROM nginx:1.29-alpine
# The official image fills /etc/nginx/templates/*.template from environment variables at start.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV API_URL=http://app:8080 \
    PORT=80
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s CMD wget -qO- http://127.0.0.1:${PORT}/ >/dev/null || exit 1
