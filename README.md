# FICC Wash Trade Surveillance

Rule-based Spring Boot + MySQL surveillance demo for detecting potential FICC wash trades, with a small React console for local run requests, alert history search, and calibration result comparison.

FICC means Fixed Income, Currencies, and Commodities.

## Quick Start

```powershell
docker compose up --build
```

Open:

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
MySQL:    localhost:3306
```

The Docker Compose stack starts:

- MySQL 8.4 with local schema, stored procedures, thresholds, trades, and sample run requests.
- Spring Boot backend on port `8080`.
- React/Vite frontend served by Nginx on port `5173`.

If you need to reset the database seed data, remove the Compose volume and start again:

```powershell
docker compose down -v
docker compose up --build
```

## Core Design Choices

1. **Database-driven model registry**

   The backend resolves the surveillance model from MySQL config tables using `appid`, `region`, and `model_class_name`, then restricts execution to registered Java model classes.

2. **Request queue based processing**

   Run requests are stored in MySQL as `PENDING`, claimed by a scheduled Spring worker, processed, then marked `COMPLETED` or `FAILED`.

3. **Calibration vs production comparison**

   Calibration runs store separate alert snapshots and compare them against production history by alert business key instead of generated alert ID.

## Demo Flow

1. Start the stack:

   ```powershell
   docker compose up --build
   ```

2. Open the frontend:

   ```text
   http://localhost:5173
   ```

3. Submit or search a production run request.

   Useful seeded example:

   ```text
   appid:        1
   region:       NAMR
   businessDate: 2026-06-05
   ```

4. The worker claims the request from MySQL, loads trades through stored procedures, evaluates wash-trade rules, stores alert history, and displays results in the frontend.

5. Submit a calibration request with changed thresholds to compare:

   ```text
   NAMRC -> NAMR
   EMEAC -> EMEA
   APACC -> APAC
   ```

Calibration result colors:

| Status | Meaning |
| --- | --- |
| `SAME_AS_PRODUCTION` | Calibration alert has the same business key as a production alert. |
| `PRODUCTION_REMOVED` | Production alert exists, but calibration did not reproduce it. |
| `CALIBRATION_NEW` | Calibration generated a new business key not present in production. |

## Tech Stack

- Java 17
- Spring Boot 3
- MySQL stored procedures
- React + Vite
- Docker Compose
- JUnit 5 + Mockito

## Main API Endpoints

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `/run-request` | `POST` | Register a production run request. |
| `/alert-history` | `GET` | Search production alert history by `appid`, `region`, and `businessDate`. |
| `/calibration-run-request` | `POST` | Register a calibration request and update calibration thresholds. |
| `/calibration-run-requests` | `GET` | Load calibration request rows. |
| `/calibration-results?requestId=...` | `GET` | Load calibration results and compare them to production by business key. |

## Project Structure

```text
src/main/java/com/portfolio/ficc
  app/            request worker and orchestration
  surveillance/   wash-trade detection model and registry
  io/             MySQL repositories and dispatching
  web/            REST controllers
  model/          domain records

frontend/         React request console
sql/              schema, stored procedures, sample data
Dockerfile        backend image
frontend/Dockerfile
docker-compose.yml
```

## Local Development Without Docker

Load the MySQL demo schema manually:

```powershell
mysql -u root -p < sql\mysql_schema_and_sample_data.sql
```

Run the backend:

```powershell
.\mvnw.cmd spring-boot:run
```

Run the frontend in another terminal:

```powershell
cd frontend
npm install
npm run dev
```

The backend database configuration can be overridden with environment variables:

```text
FICC_DATABASE_URL
FICC_DATABASE_USER
FICC_DATABASE_PASSWORD
```

## Tests

Backend:

```powershell
.\mvnw.cmd test
```

Frontend build check:

```powershell
cd frontend
npm run build
```

## Detection Model Summary

The model detects two deterministic wash-trade patterns:

| Pattern | Summary |
| --- | --- |
| One-time transaction | Matched BUY/SELL trades on the business date with the same instrument and counterparty. |
| Cumulative transaction | Aggregated BUY/SELL activity within the configured lookup window. |

Both patterns use database-backed thresholds for:

- quantity tolerance
- total amount tolerance
- minimum matched amount
- cumulative lookup days

## Database Seed

The local SQL script includes:

- model master/config rows for production and calibration appids
- runtime thresholds
- sample FICC trades
- stored procedures
- production and calibration history tables
- drill-out tables for trades behind each alert

Production appids:

| appid | region |
| ---: | --- |
| 1 | NAMR |
| 2 | EMEA |
| 3 | APAC |

Calibration appids:

| appid | region | compares to |
| ---: | --- | --- |
| 4 | NAMRC | NAMR |
| 5 | EMEAC | EMEA |
| 6 | APACC | APAC |

## Notes

- `alert_business_key_hash` is used for production/calibration comparison because generated alert IDs can differ between runs.
- Stored procedures are kept in `sql/mysql_schema_and_sample_data.sql` for local demo simplicity.
- The current project is intentionally scoped as a backend-architecture mimic/demo rather than a full production surveillance platform.
