# Payment Management API (Spring Boot + Google Sheets)

A Spring Boot 3 / Java 17 REST API that records payment transactions, using a Google Spreadsheet
(two worksheets: `Users` and `Payments`) as the data store instead of a relational database.

This package contains the **backend only**. It's a complete, runnable Spring Boot app you can
start locally and hit with curl/Postman right away.

---

## 1. Requirements

- Java 17+ (`java -version`)
- Maven 3.9+ (or use the included setup below — no Maven wrapper is bundled, so install Maven
  locally: `brew install maven` / `apt install maven` / [maven.apache.org](https://maven.apache.org/download.cgi))
- A Google Cloud project with the Sheets API enabled
- A Google Spreadsheet you control

---

## 2. Project layout

```
payment-app/
├── pom.xml
├── src/main/java/com/example/payment/
│   ├── PaymentApplication.java
│   ├── config/          # GoogleSheetsConfig, GoogleSheetsProperties, WebConfig (CORS)
│   ├── controller/       # UserController, PaymentController, HealthController
│   ├── service/          # UserService, PaymentService, GoogleSheetsService
│   ├── dto/              # Request/response payloads
│   ├── model/            # User, Payment, and enums
│   └── exception/        # GlobalExceptionHandler + custom exceptions
├── src/main/resources/application.yml
└── credentials/          # put your service-account.json here (gitignored)
```

---

## 3. Set up Google Cloud credentials

1. Go to the [Google Cloud Console](https://console.cloud.google.com/) and create (or select) a project.
2. Enable the **Google Sheets API**: APIs & Services → Library → search "Google Sheets API" → Enable.
3. Create a service account: IAM & Admin → Service Accounts → **Create Service Account**.
   - Any name is fine, e.g. `payment-app-sheets`.
   - No project-level IAM role is required (access is granted at the spreadsheet level instead — see below).
4. Open the new service account → **Keys** tab → **Add Key** → **Create new key** → JSON.
   A `.json` file downloads — this is your credential file.
5. Copy that file into this project as:
   ```
   payment-app/credentials/service-account.json
   ```
   (This path is gitignored so you won't accidentally commit it.)
6. Open the JSON file and note the `client_email` value, e.g.
   `payment-app-sheets@your-project.iam.gserviceaccount.com`.

---

## 4. Set up the Google Spreadsheet

1. Create a new Google Sheet.
2. Rename the two default tabs (or add tabs) so you have exactly:
   - `Users`
   - `Payments`
3. Add header rows (row 1) exactly as follows — the app reads data starting at row 2, so headers
   are for your own reference only, but keep the column order:

   **Users** (A1:E1):
   ```
   ID | NAME | PHONE_NUMBER | CREATED_BY | CREATED
   ```

   **Payments** (A1:I1):
   ```
   ID | USER_ID | IS_VISITOR | PAYMENT_TYPE | PAYMENT_PURPOSE | AMOUNT | CURRENCY | CREATED_BY | CREATED
   ```
4. **Share the spreadsheet** with the service account's `client_email` (from step 3.6 above) with
   **Editor** access — click **Share** in the top-right of Google Sheets and paste the email.
5. Copy the **spreadsheet ID** from the URL:
   ```
   https://docs.google.com/spreadsheets/d/<THIS_PART_IS_THE_ID>/edit
   ```

---

## 5. Configure the app

Set the following environment variables before running (recommended), or edit
`src/main/resources/application.yml` directly:

```bash
export GOOGLE_SHEETS_SPREADSHEET_ID="your-spreadsheet-id"
export GOOGLE_APPLICATION_CREDENTIALS="credentials/service-account.json"
# Optional overrides (defaults shown):
export GOOGLE_SHEETS_USERS_SHEET="Users"
export GOOGLE_SHEETS_PAYMENTS_SHEET="Payments"
```

`GOOGLE_SHEETS_SPREADSHEET_ID` has no default and **must** be set, or every request that touches
Sheets will fail.

---

## 6. Run it

```bash
cd payment-app
mvn spring-boot:run
```

Or build a jar and run it:

```bash
mvn clean package
java -jar target/payment-app-1.0.0.jar
```

The app starts on **http://localhost:8080**.

Check it's alive (no Google Sheets call needed):

```bash
curl http://localhost:8080/api/health
# {"status":"UP"}
```

> If the app fails to start with a "credentials file not found" error, double-check
> `GOOGLE_APPLICATION_CREDENTIALS` points at a real file (absolute or relative-to-working-dir path).

---

## 7. Try the API

**Create a user**
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe", "phoneNumber": "08012345678"}'
```

**Look up a user by phone**
```bash
curl "http://localhost:8080/api/users/search?phone=08012345678"
```

**Record a payment for an existing user**
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{
        "phoneNumber": "08012345678",
        "isVisitor": false,
        "paymentType": "BANK_TRANSFER",
        "paymentPurpose": "LOAN",
        "amount": 50000.00,
        "currency": "NGN"
      }'
```

**Record a payment for a brand-new phone number** (pass `name` too, so the user gets created
automatically — this happens atomically as part of the payment request):
```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -d '{
        "phoneNumber": "08099998888",
        "name": "New Visitor",
        "isVisitor": true,
        "paymentType": "CASH",
        "paymentPurpose": "DONATION",
        "amount": 10000.00,
        "currency": "NGN"
      }'
```

**List all payments**
```bash
curl http://localhost:8080/api/payments
```

**Filter/search payments** (matches payment ID, user name, or phone number)
```bash
curl "http://localhost:8080/api/payments/search?query=john"
```

**Get a user's payment history + totals**
```bash
curl http://localhost:8080/api/users/1/summary
```

---

## 8. Full endpoint reference

### Users
| Method | Path | Description |
|---|---|---|
| GET | `/api/users` | List all users |
| GET | `/api/users/{id}` | Get a user by ID |
| GET | `/api/users/search?phone=...` | Find a user by phone number |
| GET | `/api/users/{id}/summary` | User details + payment totals + history |
| POST | `/api/users` | Create a user |

### Payments
| Method | Path | Description |
|---|---|---|
| GET | `/api/payments` | List all payments (with user name/phone joined in) |
| GET | `/api/payments/{id}` | Get a payment by ID (e.g. `PAY-000001`) |
| GET | `/api/payments/user/{userId}` | All payments for a user |
| GET | `/api/payments/search?query=...` | Search by payment ID, user name, or phone |
| POST | `/api/payments` | Record a payment (auto-creates the user if new) |

### Misc
| Method | Path | Description |
|---|---|---|
| GET | `/api/health` | Liveness check (no Sheets call) |

---

## 9. Design notes

- **Server-controlled fields**: `id`, `created`, and `createdBy` are never accepted from the
  client on either `POST /api/users` or `POST /api/payments` — the DTOs simply don't expose them,
  and the service layer sets them.
- **`createdBy`** is currently hardcoded to `Diana` via `app.default-created-by` in
  `application.yml`. Swap this for a `SecurityContext` lookup once authentication is added — the
  hook is already marked with `// TODO` comments in `UserService` and `PaymentService`.
- **Duplicate users**: `GoogleSheetsService.createUser` re-checks for an existing phone number
  under a lock immediately before appending a row, so two near-simultaneous requests for the same
  new phone number won't create two user rows.
- **ID generation**: Payment IDs (`PAY-000001`, `PAY-000002`, ...) and User IDs are computed by
  scanning the sheet for the current max and incrementing, guarded by an in-process lock. This is
  safe for a single running instance. **It is not safe across multiple horizontally-scaled
  instances** — Google Sheets has no atomic increment primitive. If you need to scale out, move ID
  allocation to something that supports atomic increments (a small database sequence, Redis
  `INCR`, etc.) before going multi-instance.
- **Money**: all amounts use `BigDecimal`, never `double`/`float`.
- **Errors**: a global `@RestControllerAdvice` returns clean JSON errors
  (`{"message": "...", "status": ..., "timestamp": ...}`) and never leaks stack traces or
  credentials, even when the underlying cause is a Google Sheets API failure.
- **CORS**: `WebConfig` allows `localhost`/`127.0.0.1` on any port, for a local React/Vite
  frontend. Tighten this before deploying anywhere public.

---

## 10. Next steps (not included in this package)

This deliverable is the backend. A matching React + TypeScript frontend (payment form, payment
history table with filters, user details page) can be generated separately to consume these
endpoints — just ask, and it'll be wired to call `http://localhost:8080/api/...` via Axios.
