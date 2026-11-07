# syntax=docker/dockerfile:1
#
# Multi-stage build:
#   1. "build" stage: Node compiles the Angular app into static files.
#   2. final stage:   a small nginx image serves those files. Node, the source code and
#                     node_modules are NOT in the final image, so it is small and has less to attack.
#
# Build:  docker build -t airline-web ./frontend
# Run:    docker run -p 8080:8080 -e API_GATEWAY_URL=http://host.docker.internal:5000 airline-web

# ---------- 1. Build ----------
FROM node:24.22-alpine AS build
WORKDIR /app

# Copy only the dependency manifests first. Docker caches this layer, so `npm ci` re-runs only
# when dependencies change, not on every code change.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build \
    # Source maps are generated as "hidden" (not linked from the JS). Upload them to your error
    # tracker here if you use one; they are deleted so they never ship in the image.
    && find dist -name '*.map' -delete

# ---------- 2. Serve ----------
# "unprivileged" variant: nginx runs as a normal user (not root) and listens on port 8080.
FROM nginxinc/nginx-unprivileged:1.30-alpine

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf nginx/proxy.conf /etc/nginx/snippets/
COPY --from=build /app/dist/airline-web/browser /usr/share/nginx/html

# Where /api and /auth are forwarded. Override per environment with -e API_GATEWAY_URL=...
ENV API_GATEWAY_URL=http://api-gateway:5000

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
