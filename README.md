# Pareidolia Leave Tracker

A Spring Boot application in which MySQL 8 is the source of truth and a Google Apps Script web app supplies the manager approval page and Google Sheet synchronization.

## Workflow

1. An employee submits the leave form at `/` (or `POST /api/leave-requests`).
2. Spring Boot stores a `PENDING` request and emails the manager two links: **Approve** and **Reject**.
3. Each link opens the Apps Script web app, which displays a confirmation page. This extra click prevents mail-security link scanners from approving leave accidentally.
4. Apps Script signs and sends the final decision to Spring Boot.
5. Spring Boot locks the request and balance row, records exactly one decision, decrements the approved balance, emails the employee, and asks Apps Script to upsert the request in the Leave Requests sheet.

The approval token is one-time, expires, and is stored only as a SHA-256 hash in the database. The Spring Boot ↔ Apps Script calls additionally use an HMAC secret.

## MySQL database setup

Create an empty MySQL 8.0+ database with UTF-8 support, then create a least-privilege application user. Flyway creates the tables when the application starts.

```sql
CREATE DATABASE leave_tracker_dev
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'leave_tracker'@'localhost' IDENTIFIED BY '<password-from-your-secret-store>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
 ON leave_tracker_dev.* TO 'leave_tracker'@'localhost';
FLUSH PRIVILEGES;
```

Use the same password only in your uncommitted environment configuration; it is never stored in this repository. If MySQL runs in Docker, replace `localhost` in the MySQL user host with `%` or the Docker network host as appropriate.

## Run locally

Prerequisites: Java 17+ and Maven 3.9+.

```powershell
# Create an uncommitted .env file from application-example.env and set MYSQL_PASSWORD,
# MYSQL_ROOT_PASSWORD, and SPRING_DATASOURCE_PASSWORD to strong local values.
docker compose up -d
$env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/leave_tracker_dev?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC"
$env:SPRING_DATASOURCE_USERNAME="leave_tracker"
$env:SPRING_DATASOURCE_PASSWORD="<your-local-mysql-password>"
$env:APP_BASE_URL="http://localhost:8080"
$env:APP_ADMIN_API_KEY="local-admin-key-change-me"
mvn spring-boot:run
```

The application always uses MySQL at runtime. H2 exists only in the automated-test scope, using H2's MySQL compatibility mode; it is not packaged as a runtime database. Before production, provide every value in `application-example.env`, use HTTPS, and replace the development admin key.

## Complete local manager workflow with Mailpit

The `dev` Spring profile provides a localhost-only replacement for the Google Apps Script confirmation page. It is intended only for fictional local data. The existing signed decision endpoint, approval-token hashing, expiry validation, and duplicate-decision protection remain in use.

1. Start Mailpit only (this does not start MySQL):

```powershell
docker compose -f docker-compose.dev.yml up -d
```

2. Open the local Mailpit inbox at `http://127.0.0.1:8025`. It accepts SMTP on `127.0.0.1:1025` and never sends mail to an external provider.
3. Start MySQL separately using your existing local workflow, then start the application with the `dev` profile. In IntelliJ, use the application configuration for `com.acme.hr.leavetracker.PareidoliaLeaveTrackerApplication` and set these environment variables:

```text
SPRING_PROFILES_ACTIVE=dev
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/leave_tracker_dev?useSSL=false&allowPublicKeyRetrieval=true&connectionTimeZone=UTC
SPRING_DATASOURCE_USERNAME=leave_tracker
SPRING_DATASOURCE_PASSWORD=<local MySQL password>
APP_ADMIN_API_KEY=<fictional local admin key>
APP_GOOGLE_SCRIPT_SHARED_SECRET=<fictional local HMAC secret>
APP_BASE_URL=http://127.0.0.1:8081
```

Do not set `APP_GOOGLE_SCRIPT_WEB_APP_URL` for this workflow. The dev profile binds Spring Boot to `127.0.0.1:8081`, routes SMTP to Mailpit, uses the fictional sender `no-reply@leave-tracker.local.test`, and disables Google Sheet sync. The sync service also refuses all Google calls whenever the `dev` profile is active, even if an inherited environment variable contains a web-app URL.

4. Create fictional employee and manager addresses ending in `@example.test`, then submit leave from the normal employee form.
5. In Mailpit, open the manager email and select **Approve** or **Reject**. The local link opens `GET /dev/approval`, which displays a confirmation page. Confirming the form makes a server-side, HMAC-signed request to the unchanged `POST /api/integrations/google/decisions` endpoint.
6. Confirm that the request status and balance changed as expected, then open Mailpit again to view the employee decision email. Use a separate, non-overlapping fictional request to exercise rejection; rejection does not alter the balance.

The local approval page and its link builder are both restricted with `@Profile("dev")`; they are not registered in production. The HMAC secret stays server-side and the raw one-time token appears only in the manager's local approval link. Neither value is written to normal API responses or application logs.

## Run from IntelliJ IDEA

1. Open this folder as a Maven project and select a Java 17+ SDK.
2. Create an IntelliJ **Application** run configuration for `com.acme.hr.leavetracker.PareidoliaLeaveTrackerApplication`.
3. In the run configuration's environment variables, set at least `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `APP_ADMIN_API_KEY`. Set the mail and Apps Script variables from `application-example.env` when enabling those integrations.
4. Start MySQL first, then run the configuration. Flyway validates or applies the schema automatically.

Run the automated test suite with the Maven `test` goal in IntelliJ's Maven tool window. The test profile uses an isolated in-memory H2 database in MySQL compatibility mode.

## Initial HR setup

Create an employee. `X-HR-Admin-Key` must match `APP_ADMIN_API_KEY`.

```http
POST /api/admin/employees
X-HR-Admin-Key: local-admin-key-change-me
Content-Type: application/json

{
  "email": "sana@example.com",
  "fullName": "Sana Shah",
  "managerEmail": "manager@example.com",
  "plLeaveDays": 20,
  "clLeaveDays": 10,
  "slLeaveDays": 10
}
```

Use `GET /api/admin/leave-requests` to obtain the HR dashboard data. To change a PL allocation later, call `PUT /api/admin/employees/{employeeId}/balances/PL` with `{ "entitlementDays": 25 }`. Supported leave types are `PL`, `CL`, and `SL`.

## Google Apps Script setup

1. Create a Google Sheet, then open **Extensions → Apps Script**.
2. Paste [`google-apps-script/Code.gs`](google-apps-script/Code.gs) into `Code.gs`.
3. In **Project Settings → Script Properties**, set:
   - `SPRING_API_BASE_URL` — public HTTPS URL for this application, without a trailing slash.
   - `INTEGRATION_SECRET` — exactly the same value as `APP_GOOGLE_SCRIPT_SHARED_SECRET`.
   - `LEAVE_SHEET_ID` — the Sheet ID from its URL.
4. Deploy it as a **Web app**. Execute as **Me** and grant access to the managers who will decide leave (or your organization, as appropriate).
5. Put its `/exec` URL in `APP_GOOGLE_SCRIPT_WEB_APP_URL`, restart Spring Boot, and submit a test request.

The script creates a `Leave Requests` tab automatically. Do not put the shared secret in the email links or in the sheet.

The included browser form is intended for a trusted internal network. Before exposing it publicly, protect employee-facing routes with your company SSO (for example, Google Workspace SAML/OIDC) and take the employee email from the authenticated identity rather than from the form field.

## API summary

| Endpoint | Purpose |
| --- | --- |
| `POST /api/leave-requests` | Employee leave submission |
| `GET /api/employees/{email}/balance` | Employee balances |
| `POST /api/admin/employees` | HR employee and opening balances |
| `PUT /api/admin/employees/{id}/balances/{leaveType}` | HR allocation change |
| `GET /api/admin/leave-requests` | HR request view |
| `POST /api/integrations/google/decisions` | Apps Script only; signed decision callback |

See the inline API error messages for validation details.
