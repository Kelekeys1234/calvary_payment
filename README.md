<<<<<<< HEAD
# Payment Management Web Application

A full-stack app that replaces a Google Form for recording payments. Google Sheets is the data
store (two worksheets: `Users` and `Payments`). No relational database is used.

```
payment-app-project/
├── backend/    Spring Boot 3 / Java 17 REST API
└── frontend/   React + TypeScript (Vite) web app
```

Each folder has its own detailed README (`backend/README.md`, `frontend/README.md`). This file
covers the fastest path to running both together.

---

## 1. Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 18+ and npm
- A Google Cloud project + a Google Spreadsheet (see `backend/README.md` sections 3–4 for the
  full step-by-step: creating a service account, enabling the Sheets API, and sharing the sheet)

---

## 2. Run the backend

```bash
cd backend

export GOOGLE_SHEETS_SPREADSHEET_ID="your-spreadsheet-id"
export GOOGLE_APPLICATION_CREDENTIALS="credentials/service-account.json"
# (place your downloaded service-account key at that path first)

mvn spring-boot:run
```

Confirm it's up:
```bash
curl http://localhost:8080/api/health
# {"status":"UP"}
```

The backend listens on **http://localhost:8080**.

---

## 3. Run the frontend

In a separate terminal:

```bash
cd frontend
npm install
npm run dev
```

The app opens on **http://localhost:5173** and talks to the backend at
`http://localhost:8080/api` (configurable in `frontend/.env`).

---

## 4. Use it

1. Open http://localhost:5173 — you'll land on **New Payment**.
2. Enter a phone number. If it matches an existing user, their name appears automatically.
   If not, you'll be asked for a name so a user can be created inline.
3. Fill in the rest of the form and submit — you'll see a confirmation receipt with the
   generated Payment ID.
4. **Payment History** shows every payment recorded, with search and filters (purpose, type,
   currency, visitor status).
5. **Users** lists everyone recorded; clicking a user shows their totals (loans, donations,
   overall) and their individual payment history.

---

## 5. Notes

- `createdBy` is currently hardcoded to `Diana` on the backend (see `backend/README.md` §9) —
  swap in real authentication later without changing the frontend contract.
- The frontend never sends `id`, `created`, or `createdBy` — those are always generated
  server-side, per the spec.
- CORS on the backend is pre-configured to allow `localhost`/`127.0.0.1` on any port for local
  development. Tighten `backend/.../config/WebConfig.java` before deploying anywhere public.
=======
# calvary_payment
calvary_payment
>>>>>>> 0731601a5a0427ff659c03071f5fad06d655dc84
