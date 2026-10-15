# ── Build Stage ──
FROM node:18-alpine AS build

ARG REACT_APP_API_URL
ARG REACT_APP_WS_URL
ARG GIT_SHA=dev
ARG VERSION=0.0.0-dev

WORKDIR /app
COPY package*.json ./
RUN npm ci --production=false
COPY . .
RUN npm run build

# ── Serve Stage ──
FROM nginx:1.27-alpine

# Substitute only the backend URL in the nginx template; preserve nginx variables
# such as $uri, $host, and $http_upgrade.
ENV NGINX_ENVSUBST_FILTER='^BACKEND_URL$'

ARG GIT_SHA=dev
ARG VERSION=0.0.0-dev

LABEL org.opencontainers.image.title="SHBS Frontend" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.source="https://github.com/ritik-luck/SHBS"

# Run nginx as non-root
RUN addgroup -S frontend && adduser -S frontend -G frontend && \
    chown -R frontend:frontend /var/cache/nginx /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && chown frontend:frontend /var/run/nginx.pid

COPY --from=build /app/build /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget --spider -q http://localhost:8080/ || exit 1

USER frontend:frontend

CMD ["nginx", "-g", "daemon off;"]
