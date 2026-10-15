# Environment Variables Reference

## Required for All Environments

| Variable | Description | Example |
|----------|-------------|---------|
| `DB_PASSWORD` | MySQL root password | `<strong-random-password>` |
| `ADMIN_USERNAME` | Backend admin login username | `admin` |
| `ADMIN_PASSWORD` | Backend admin login password (min 12 chars in prod) | `<strong-password>` |
| `JWT_SECRET` | Base64-encoded JWT signing key | `<base64-string>` |
| `GRAFANA_ADMIN_PASSWORD` | Grafana dashboard admin password | `<strong-password>` |

## Optional / Env-Specific

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile (`local`, `prod`) | `local` |
| `REDIS_PASSWORD` | Redis auth password | _(empty)_ |
| `CORS_ORIGINS` | Comma-separated allowed origins | `http://localhost` |
| `REACT_APP_API_URL` | Frontend API base URL | `http://localhost/admin` |
| `REACT_APP_WS_URL` | Frontend WebSocket URL | `ws://localhost/ws` |
| `SPRING_MAIL_USERNAME` | SMTP username for alerts | _(empty)_ |
| `SPRING_MAIL_PASSWORD` | SMTP password | _(empty)_ |
| `ALERT_EMAIL_RECIPIENT` | Alert destination email | _(empty)_ |
| `GRAFANA_ROOT_URL` | Grafana external URL | `http://localhost:3001` |

## Build-Time Variables (Set by CI)

| Variable | Description | Default |
|----------|-------------|---------|
| `GIT_SHA` | Git commit SHA | `dev` |
| `BUILD_TIME` | ISO 8601 build timestamp | `unknown` |
| `VERSION` | Semantic version or date-SHA tag | `0.0.0-dev` |

## Ports (Internal)

| Service | Port | Protocol |
|---------|------|----------|
| Backend (Spring Boot) | 8080 | HTTP |
| Inventory Service | 8081 | HTTP |
| Frontend (Nginx) | 80 | HTTP |
| MySQL | 3306 | TCP |
| Redis | 6379 | TCP |
| Prometheus | 9090 | HTTP |
| Grafana | 3000 | HTTP |
| Alertmanager | 9093 | HTTP |
| Tempo | 3200 | HTTP |
| OTel Collector (gRPC) | 4317 | gRPC |
| OTel Collector (HTTP) | 4318 | HTTP |

## Exposed Ports (Production)

| Port | Service | Notes |
|------|---------|-------|
| 80 | Nginx (→ HTTPS redirect) | Redirects to 443 |
| 443 | Nginx (TLS termination) | Main entry point |

> [!WARNING]
> In production, **only** ports 80 and 443 should be publicly accessible. All other ports are internal-only via Docker networks or VPC security groups.
