# Getting Started

A clone-to-running walkthrough for a developer who has just cloned this
repository. Every command is copy-pasteable.

- **0. TL;DR (Windows)** — double-click `start-all.bat`; it starts PostgreSQL,
  the backend and the frontend and opens <http://localhost:5180>.
- **Manual setup** — steps 1–7 below, if you prefer to run each piece yourself.

---

## 1. Prerequisites

| Tool | Version | Check |
| --- | --- | --- |
| JDK | **21** (a JDK, not a JRE) | `javac -version` → `javac 21.x` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 20+ (22 LTS tested) | `node --version` |
| PostgreSQL | 16 | `psql --version` |

> **JDK, not JRE.** `java -version` reporting 1.8 is the usual trap: a JRE has no
> `javac`, so Maven fails with *"No compiler is provided in this environment"*.
> Set `JAVA_HOME` to a JDK 21 install.

No Docker is required — PostgreSQL runs as a local instance.

## 2. Clone the repository

```bash
git clone https://github.com/bhaktaji/clinic-queue-system.git
cd clinic-queue-system
```

## 3. Environment configuration

Everything is configured through environment variables with safe local
defaults, so **no `.env` file is required for local development**. Copy the
templates and edit them only if your setup differs.

```bash
cp frontend/.env.example frontend/.env      # optional
```

| Variable | Default | Set it when |
| --- | --- | --- |
| `APP_DB_URL` | `jdbc:postgresql://localhost:5433/clinicqueue` | your DB lives elsewhere |
| `APP_DB_USER` / `APP_DB_PASSWORD` | `postgres` / `postgres` | your DB has different credentials |
| `APP_CORS_ORIGINS` | `http://localhost:5180,...` | you serve the SPA from another origin |
| `APP_COOKIE_SECURE` | `false` | behind HTTPS (set `true`) |
| `VITE_API_BASE_URL` | empty (same origin) | the API is on a different host |
| `VITE_API_PROXY_TARGET` | `http://localhost:8090` | the dev proxy should point elsewhere |

Integration tests read `APP_IT_DB_URL`
(`jdbc:postgresql://localhost:5433/clinicqueue_test`).

## 4. Database

PostgreSQL must listen on **port 5433** — the backend points at that port.

```bash
# One-time database creation
psql -p 5433 -U postgres -c "CREATE DATABASE clinicqueue;"
psql -p 5433 -U postgres -c "CREATE DATABASE clinicqueue_test;"
```

Flyway creates the schema automatically on first start, and a demo clinic is
seeded into an empty database. No manual DDL.

## 5. Install dependencies

```bash
cd frontend && npm install && cd ..
cd e2e && npm install && cd ..      # only if you plan to run E2E tests
```

## 6. Build

```bash
# Backend
cd backend
mvn -DskipTests package

# Frontend
cd ../frontend
npm run build
```

## 7. Run

Two terminals (plus the database):

```bash
# Terminal 1 — backend on :8090
cd backend
java -jar target/clinic-queue-backend-0.1.0.jar --server.port=8090

# Terminal 2 — frontend on :5180
cd frontend
npm run dev
```

Then open <http://localhost:5180>.

## 8. Verify

```bash
# Backend health
curl http://localhost:8090/actuator/health
# → {"status":"UP"}

# API docs
#   http://localhost:8090/swagger-ui.html

# Frontend serving the SPA
curl -o /dev/null -w "%{http_code}\n" http://localhost:5180/
# → 200

# End-to-end smoke test (39 assertions, needs the backend running)
bash scripts/api-smoke.sh http://localhost:8090
```

Sign in at <http://localhost:5180/login>:

| Username | Password | Role |
| --- | --- | --- |
| `owner` | `owner123` | OWNER |
| `reception` | `recep123` | RECEPTIONIST |
| `doctor` | `doc123` | DOCTOR |

> These are demo seed credentials. Change them before any real deployment.

## 9. Run the tests

```bash
bash scripts/backend-test.sh unit     # no database needed
bash scripts/backend-test.sh all      # unit + PostgreSQL integration tests
bash scripts/frontend.sh lint
bash scripts/frontend.sh test
bash scripts/e2e-test.sh              # Playwright (starts everything itself)
```

`scripts/e2e-test.sh` starts PostgreSQL, the backend and the Vite dev server on
its own and resets the test dates (`2027-06-15` / `2027-06-16`) before running,
so it is safe to re-run.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Connection to localhost:5433 refused` | PostgreSQL is on 5432 | Start it with `-o "-p 5433"`, or run `scripts/ensure-postgres.sh` |
| `No compiler is provided in this environment` | `JAVA_HOME` points at a JRE | Point `JAVA_HOME` at a JDK 21 |
| `Address already in use: 8090 / 5180` | stale servers | Run `stop-all.bat`, or free the port |
| Frontend loads but every request fails | API not reachable | Check <http://localhost:8090/actuator/health>; the Vite proxy targets `VITE_API_PROXY_TARGET` |
| Login returns 403 | missing CSRF cookie | Reload the page; the client fetches `/auth/csrf` on start |
| E2E: `create session failed: 409` | a previous run left the test date | The helpers reset those dates; if you ran them manually, delete those sessions |

See **[README.md](README.md)** for the architecture, API reference, security
model and requirement traceability.
