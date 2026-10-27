# PayMatch

Bank statement import and payment reconciliation for small Polish companies. It imports bank statements
(MT940 and bank CSV exports), matches incoming transfers to invoices and gives a clear review workflow for
payments that can't be matched automatically.

> Work in progress. A full description, architecture and design notes will follow.

## Running locally (development)

Requirements: Java 21, Node 20+, Docker.

```bash
docker compose up -d db                  # PostgreSQL on localhost:5433
cd backend && ./mvnw spring-boot:run     # API on http://localhost:8080
cd frontend && npm install && npm run dev  # UI on http://localhost:5173
```

Demo accounts: `anna` / `demo1234` and `piotr` / `demo1234`.
API docs: http://localhost:8080/swagger-ui.html

## Tests

```bash
cd backend && ./mvnw test   # needs Docker for Testcontainers
```

`samples/invoices.csv` is an example invoice list (Windows-1250, `;`-separated) you can import from the Invoices page.
