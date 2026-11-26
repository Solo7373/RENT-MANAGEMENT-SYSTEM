# Rental Management System - Project Analysis

**Date:** 10 November 2026  
**Scope:** honest assessment of what works, what is hard, what may not work,
what is missing, and what should come next.

---

## 1. Executive Summary

The application is a **working single-user desktop rental manager** for a small
landlord, hostel or SACCO. It covers the full local workflow: portfolio setup,
tenant and tenancy records, monthly rent charges, payment recording and
verification, arrears, expenses, reports, reminders, settings, audit trail,
PDF receipts, CSV/PDF exports, tenant document attachments, consistent zip
backups and dark mode.

It is **not** a production-grade, multi-user, cloud or payment-integrated
platform. The gap between "a very solid desktop product" and "a real rental
business platform" is mostly in external integrations, background automation,
security hardening (encryption, roles) and multi-device operations.

**Verdict:** good enough to demo, use on one computer and submit as a complete
desktop project. It can now be packaged as a portable Windows app with a
bundled runtime. It is not ready to run a real business with several staff and
real mobile-money reconciliation without the next phase of work.

---

## 2. What Is Finished and Verified

### Core workflow
- BCrypt admin login (`admin` / `admin123` on a fresh database), show/hide
  password, Enter-key login.
- Properties, rooms, tenants and tenancies: create, read, update, delete.
- Tenancy creation marks the room occupied; termination frees the room.
- Tenants store phone, WhatsApp, email, National ID, School ID, Refugee ID,
  room number and rent/pricing.
- Monthly rent charges are generated automatically on login (idempotent) and
  can also be generated manually.
- Payments are recorded, verified and allocated to the oldest unpaid charges.
- Partial payments move a charge through `PENDING -> PARTIAL -> PAID`.
- Deleting a verified payment reverses its allocations in a transaction.
- Late fees can be applied once to overdue charges, using the configured
  percentage and grace period.
- Reports, receipts and exports use the configured currency and company profile.
- Printable PDF receipts are generated with a receipt number and allocation
  breakdown.
- CSV and PDF exports exist for summary, outstanding charges, property occupancy,
  monthly revenue and expenses.
- Tenant documents can be attached, opened and deleted; deleting a tenant cleans
  up their files.
- Backups are consistent `VACUUM INTO` snapshots zipped with documents and a
  SHA-256 checksum; old backups are pruned by retention.
- Expenses, reporting, audit log, settings, payment-method toggles and database
  backup all read/write live data.
- Reminder rules can be created, enabled/disabled and deleted; notifications can
  be queued, sent through SMS/WhatsApp and tracked with recipient, status, event
  type, provider message ID and provider error.
- An in-app scheduler sends due reminders while the app is open, picks the latest
  applicable enabled rule per charge, deduplicates by rule + charge and retries
  failed sends on the next scan.
- A per-tenant communication history records outbound reminders, receipt SMS and
  test messages with tenant/charge/payment links and provider metadata.
- A guided communication wizard collects the admin contact, SMS gateway
  (sandbox/production), WhatsApp Cloud API and cloud backup folder after first
  login and from Settings.
- SMS API keys and WhatsApp tokens are encrypted with Windows DPAPI and shown
  only as a masked suffix; legacy plain-text keys are migrated at startup.
- Cloud backups copy each verified zip and checksum into a OneDrive/Google
  Drive/Dropbox sync folder and prune old cloud copies by retention.
- Application data lives in `%APPDATA%\RentalManagementSystem`, so the packaged
  app can run from a read-only installation folder.

### UI
- Modern gradient login card, single-window shell, collapsible hamburger
  sidebar, top bar, live dashboard cards and quick actions.
- Full light/dark theme, applied to login and the main shell, persisted in
  `system_settings`.
- Keyboard shortcuts for every module.
- CSS-based theming; views are plain JavaFX panes, no popups for navigation.
- Settings includes dedicated **Billing & Company**, **Communication** and
  **Backup** tabs, plus the raw key/value editor for advanced changes.
- The communication wizard asks for the admin contact, SMS provider and
  environment, WhatsApp Cloud API and cloud sync folder, with Test Connection,
  test-message buttons and validation.
- Payments exposes **Receipt**, **SMS Receipt** and **Apply Late Fees** actions;
  Reports has an export bar for all report types; Tenants has **Documents** and
  **Communication History** dialogs; Reminders has **Send Now**,
  **Run Due Reminders Now**, provider columns and scheduler status.

### Automated backend verification
The original 25-check smoke test still passes, and the expanded test passes
**116/116** checks in an isolated data folder:

1. data folder override and schema creation
2. company/currency/due-day/grace/late-fee/receipt/retention settings reload
3. currency formatting through `FormatUtils`
4. property, room, tenant and tenancy creation
5. charge generation with the configured due date
6. overdue detection and a single late-fee application
7. payment recording, verification and full allocation
8. PDF receipt creation, PDF header and configured receipt prefix
9. expense creation
10. CSV summary, outstanding and property exports
11. PDF monthly-revenue and expense exports
12. tenant document attach/list/delete and tenant-folder cleanup
13. admin contact, SMS/WhatsApp/cloud settings and setup completion
14. DPAPI credential encryption, decryption, masking and plain-text migration
15. phone normalisation with the default country code
16. SMS/WhatsApp test sends and Test Connection through a local mock gateway
17. reminder delivery status, recipient, provider error and failure handling
18. per-tenant communication history and provider metadata
19. receipt SMS for verified payments and unverified-payment rejection
20. scheduler opt-in, rule selection, rendering, dedupe and retry
21. unconfigured email handling
22. zip backup creation, checksum verification, document inclusion and retention
23. cloud folder testing, upload, checksum verification and cloud retention
24. scheduled pruning of older backups

---

## 3. Critical Bugs Found and Fixed in This Pass

These are worth calling out because they would have made the app look "broken"
in normal use.

### 3.1 Adding records silently failed (critical)
Every `create()` used JDBC's
`prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)`.
The bundled SQLite driver (`sqlite-jdbc 3.44.1.0`) throws:

> `not implemented by SQLite JDBC driver`

The exception was caught and only printed to the console, so the UI simply did
nothing when the user clicked **Add**. Properties, rooms, tenants, tenancies,
payments and expenses could not be created on this driver version.

**Fix:** all DAOs now use `DatabaseConnection.lastInsertId(conn)` and SQLite's
`SELECT last_insert_rowid()`. Verified by the 25-check smoke test.

### 3.2 Changed password was reset on every restart
`DatabaseInitializer` used `INSERT OR REPLACE` for the admin user, resetting
the password to `admin123` (and the email/full name) at every startup, which
made the Change Password feature pointless.

**Fix:** switched to `INSERT OR IGNORE`, so the admin is seeded only on first
run and password changes persist.

### 3.3 Deleting a verified payment left phantom allocations
The payment row was deleted but `payment_allocations` and
`rent_charges.paid_amount` were untouched, so arrears stayed artificially low.

**Fix:** `RentChargeDAO.reverseAllocations()` now runs before deletion and
restores each charge's paid amount and status inside a transaction.

### 3.4 Allocation could partially fail
Payment allocation looped over several charges with auto-commit enabled; a
failure halfway through could leave inconsistent data.

**Fix:** allocation and reversal are now transactional (`commit` / rollback on
close), and the charge status is computed from the updated paid amount.

### 3.5 Missing upgrade column
Older databases created before the deposit field did not get the new
`tenancies.deposit` column. An `ALTER TABLE` upgrade step was added.

### 3.6 Dashboard buttons clipped at 1200px
Six fixed-width quick-action buttons were squeezed into one non-wrapping row.
They now use a `FlowPane`, so they wrap instead of truncating labels.

### 3.7 Backup names could collide (Now phase)
Backup files were named with second-level timestamps. Two backups created in the
same second silently overwrote each other. The name now includes milliseconds.

### 3.8 Live database was copied unsafely (Now phase)
The old Backup tab copied `rentaldb.sqlite` while the app was using it, which can
produce an inconsistent file. Backups now use SQLite `VACUUM INTO`, verify the
snapshot with `PRAGMA integrity_check`, and include tenant documents plus a
checksum.

### 3.9 Data was written next to the executable (Now phase)
The database used a relative `rentaldb.sqlite` path, which fails under
`Program Files` and made packaging fragile. Writable state now lives in
`%APPDATA%\RentalManagementSystem`, with a one-time migration of the old
working-directory database and folders.

### 3.10 Hardcoded UGX and due day (Now phase)
Money labels and receipts assumed UGX, and rent was always due on the 5th. These
now come from settings: currency, due day, grace period, late-fee percentage,
company details and receipt prefix.

---

## 4. What Is Genuinely Hard / May Not Work

These are the areas where "just add a button" is not enough. They depend on
external services, money, approvals or a different architecture.

### 4.1 SMS, WhatsApp and reminders
**Current state:** SMS delivery is implemented for Africa's Talking (sandbox and
production), Twilio and a generic HTTP gateway. WhatsApp delivery uses Meta's
WhatsApp Cloud API. A guided wizard collects the administrator's contact details
and each provider's credentials, tests the connection and saves encrypted keys.
Queued reminders can be sent from the Reminders screen; the recipient, status,
event type, provider message ID and provider error are stored.

An opt-in in-app scheduler scans every 15 minutes while an admin is logged in,
selects the latest applicable enabled rule for each outstanding charge, renders
the template and sends once per rule + charge. A unique dedupe key prevents
repeats and is cleared on failure so the next scan can retry. Receipt SMS can be
sent manually after a payment is verified. Every attempt can be reviewed in the
tenant's communication history.

**What still makes it hard:**
- The admin must have a funded provider account, approved sender ID and valid
  API credentials. RentalSys cannot create those accounts. Africa's Talking's
  sandbox only delivers to numbers registered in its sandbox app.
- Automatic sending only runs while the desktop app is open and an admin is
  logged in. There is no Windows service or 24/7 scheduler, so reminders due
  while the app is closed wait until it is opened.
- WhatsApp plain-text messages only work inside Meta's 24-hour customer-service
  window. Outside that window an approved template must be supplied and approved
  by Meta. Template review is outside the app's control.
- There is no webhook server, so the app records the provider's immediate HTTP
  response but not later delivery receipts or read receipts.
- Inbound SMS replies, WhatsApp webhooks and voice calls/voicemail/recordings
  need a public hosted relay. Phase 1 deliberately scopes these out; provider
  message IDs are stored so callbacks can be matched later.
- Email is still not delivered; those reminders can only be marked sent manually.

**Can it work later?** SMS/WhatsApp work today with real accounts. Email would
need an SMTP/transactional provider. Delivery callbacks, inbound messages and
voice need a small hosted webhook relay plus a public HTTPS endpoint. Truly
scheduled sending when the app is closed needs Windows Task Scheduler or an
always-on server.

### 4.2 Mobile money and bank integrations (MTN MoMo, Airtel Money, banks)
**Current state:** the payment method is a label on a manually recorded
payment. Verification and reconciliation are manual.

**Why it is hard:**
- Merchant/collection accounts and API credentials are required.
- Each provider has different APIs, sandbox rules, callbacks and settlement
  timing.
- Real reconciliation needs a public HTTPS endpoint to receive callbacks, plus
  idempotency, matching by reference/phone/amount, retries and exception
  handling.
- Money movement is regulated; going live usually needs a company, KYC,
  contracts and compliance review.
- Testing requires sandbox accounts that may not be available to a student.

**Can it work later?** Only with a backend service (not a pure desktop app) or
an aggregator such as Flutterwave/Pesapal that hides some complexity. Treat it
as a separate product phase.

### 4.3 Multi-user, cloud sync and roles
**Current state:** one SQLite file, one logged-in admin, in-memory session.

**Why it is hard:**
- SQLite is not designed for several computers writing the same file over a
  network share; locking/corruption risks are real.
- Multi-user needs a server database (PostgreSQL/MySQL), a REST API, session
  management, role-based permissions, conflict handling and hosting/backups.
- The schema has a `STAFF` role value but no staff UI, permissions or
  authorization checks; adding those is a security design task, not a screen.

**Can it work later?** Yes, but it is an architectural rewrite of the data
layer and a hosting/operations commitment.

### 4.4 Background automation while the app is closed
**Current state:** charges are generated when someone logs in. Reminders, late
fees and scheduled backups do not run on a timer.

**Why it is hard:**
- A JavaFX process only exists while it is open. "Every 1st of the month at
  08:00" requires Windows Task Scheduler, a Windows service or a server cron
  job.
- Late fees need a clear business rule (grace period, percentage, cap, whether
  they compound), auditability and a way to reverse mistakes.
- Scheduled backups need to handle a live SQLite file safely (`VACUUM INTO` or
  a file lock) and retention/rotation.

**Can it work later?** Yes - Windows Task Scheduler plus a small headless
maintenance mode is the pragmatic path.

### 4.5 Document uploads and scans
**Current state:** local tenant attachments are implemented. Files are validated
(PDF, images, Word, text; 10 MB max), copied into
`%APPDATA%\RentalManagementSystem\documents\tenant-<id>\` with safe generated
names, recorded in `tenant_documents`, and included in backups. The Tenants
screen has a Documents dialog to attach, open and delete files.

**Remaining hard parts:**
- Files are not encrypted at rest; scanned IDs are sensitive personal data.
  A shared PC exposes them to anyone with file access.
- There is no OCR/auto-fill, preview thumbnail, versioning or expiry reminder.
- There is no cloud storage or sharing; everything stays on one machine.
- A formal retention/privacy policy is still the operator's responsibility.

### 4.6 PDF receipts, invoices and report export
**Current state:** implemented with OpenPDF. Payment receipts use the company
profile and configured receipt prefix, show the allocation breakdown, and are
saved under `receipts/`. Reports export to CSV and PDF (summary, outstanding,
property occupancy, monthly revenue and expenses) under `reports/`.

**Remaining hard parts:**
- Receipts are not invoices with tax/VAT fields; legal requirements vary by
  country and are the operator's responsibility.
- There is no logo upload, signature image, emailed receipt or date-range filter.
- Tenant statements (all charges and payments over a period) are not yet built.
- PDFs use built-in Helvetica fonts; non-Latin scripts would need embedded fonts.

### 4.7 Password recovery and account security
**Current state:** one admin, BCrypt hashing, change-password screen.

**Why it is hard / missing:**
- No "forgot password" flow, security questions, recovery codes or lockout
  after failed attempts.
- No password strength policy beyond a 6-character minimum.
- No session timeout; closing the app is the only logout.
- The SQLite file is unencrypted: anyone with file access can read the data or
  replace the admin hash. Audit logs can be edited with a DB tool.
- No encrypted backups; backups now include tenant documents, so they expose
  both the database and the scanned IDs. The SHA-256 checksum detects accidental
  corruption, not deliberate tampering.

**Can it work later?** Yes, but it needs deliberate security design. At minimum:
password reset procedure, login attempt throttling, encrypted backups, and a
clear "this PC must be physically protected" policy.

### 4.8 Data integrity and concurrency edge cases
- SQLite foreign keys are declared but **not enforced** by default. Deleting a
  property/room/tenant with linked records can leave orphans. The UI does not
  currently explain a refusal clearly.
- No database-level `CHECK` constraints for negative amounts.
- No unique constraint on room number per property.
- Two admins (if multi-user is ever added) could verify the same payment
  concurrently; the UI check alone is not enough.
- A tenant with several tenancies has payments allocated oldest-first across
  all of them; there is no explicit "pay this room/tenancy" control.
- Overpayments are not tracked as a tenant credit or refund; the unallocated
  remainder simply sits on the verified payment.

### 4.9 Billing rules are simplified
**Now configurable:** due day, grace period, late-fee percentage, currency,
company details, receipt prefix and backup retention.
**Still simplified:**
- No pro-rating for mid-month move-ins, no weekly/daily billing.
- Late fees are a single percentage applied manually; there are no escalating
  tiers, waivers or automatic scheduling.
- Deposits are recorded but there is no refund/deduction workflow.
- Receipts are linked to payments, not to invoices; there is no formal invoice
  numbering sequence beyond the receipt prefix + payment ID.
- Overpayments are not tracked as a tenant credit.

### 4.10 Distribution and operations
- A portable Windows app image is built with `jpackage` (bundled Java runtime,
  custom icon) by `package.ps1`; the target PC does not need Java or Maven.
- A `.exe` or `.msi` installer requires **WiX Toolset 3.x**, which is not
  installed on this machine, so the portable app image is the shipped artifact.
- There is no automatic update mechanism, crash reporting, structured logs or
  support tooling.
- The app logs warnings for SLF4J NOP and "Unsupported JavaFX configuration"
  when run via `exec:java`; harmless but unpolished.

### 4.11 Cloud backup is folder-sync based, not a cloud API
**Current state:** the admin signs in to OneDrive, Google Drive or Dropbox with
the provider's own desktop client and chooses a folder inside its sync directory.
RentalSys copies the backup zip and checksum there, verifies the copy, and prunes
old files by retention.

**Why it is limited:**
- Actual upload, retries, conflict resolution and account authentication belong to
  the provider's client. RentalSys never sees the cloud password and cannot force
  a sync to finish.
- If the provider client is signed out, paused or offline, files stay local until
  it resumes.
- A shared/synced folder is not an archival backup: deleting a file in the folder
  can delete it in the cloud, subject to the provider's version history.
- The checksum proves the copied file is intact; it does not prove the provider
  uploaded it or protect against ransomware.

**Can it work later?** Direct provider APIs (Microsoft Graph, Google Drive API,
Dropbox API) would need OAuth apps, client secrets, token refresh and review
processes. For this desktop app, folder sync is the pragmatic option.

---

## 5. What Is Missing (Feature Gaps)

### Tenant / lease
- Tenant profile photo (documents are supported, a photo is not)
- Lease agreement generation and e-signature
- Tenant portal or self-service statement
- Notice/move-out workflow, deposit refunds and deductions
- Tenant statement (running balance, charges and payments)

### Money
- Invoices with a formal numbering sequence and tax/VAT fields
- Credit balances, refunds and overpayment handling
- Automatic/scheduled late-fee runs and fee waivers
- Payment plans/installments
- Bank/mobile-money reconciliation and unmatched-payment queue
- Landlord/owner statements, management fees and profit splits
- Security-deposit ledger

### Operations
- Maintenance tickets, work orders and vendor records
- Utility metering (water/electricity) and billing
- Inspections and photo evidence
- Asset/inventory tracking
- Document expiry reminders (IDs, contracts, insurance)
- Advanced reminder automation from overdue charges, with de-duplication

### Reporting
- Date-range filters and comparison periods
- Defaulter lists and aging buckets (30/60/90 days)
- Occupancy trends, move-in/move-out report
- Tax/VAT report
- Dashboard charts (revenue trend, occupancy, expense split)

### Administration
- Staff users, roles and granular permissions
- Password reset/recovery and login throttling
- Scheduled backups and encrypted backups
- Scheduled/background SMS and WhatsApp sending when the app is closed
- Email (SMTP) delivery with retry and delivery status
- Data import/export and migration tools
- Localization (English/Luganda/Swahili) and locale-aware currency/date formats

---

## 6. Roadmap

### Now (completed in this pass)
1. CSV/PDF export for reports and PDF payment receipts. ✅
2. Configurable due day, currency, company details, grace period, late fee,
   receipt prefix and backup retention. ✅
3. Late-fee application with an explicit **Apply Late Fees** action and audit
   entry. ✅
4. Local document attachments (copy file + store path) with size/type checks,
   open/delete and tenant-delete cleanup. ✅
5. Safer backup: `VACUUM INTO` snapshot, documents, checksum and retention. ✅
6. Package with `jpackage` so the app runs without Maven. ✅ (portable app image;
   installer needs WiX)
7. Move writable data to `%APPDATA%` so the packaged app can run from a
   read-only folder. ✅
8. Guided admin contact, SMS, WhatsApp and cloud backup setup wizard. ✅
9. Real SMS delivery (Africa's Talking/Twilio/generic) and WhatsApp Cloud API
   delivery from Reminders, with recipient/status/error tracking. ✅
10. Cloud backup copy into a OneDrive/Google Drive/Dropbox sync folder, with
    checksum verification and retention. ✅
11. Africa's Talking sandbox/production environments, Test Connection and
    provider message ID/status capture. ✅
12. Windows DPAPI encryption for SMS/WhatsApp credentials, masked display and
    automatic migration of legacy plain-text keys. ✅
13. Per-tenant communication history and manual receipt SMS for verified
    payments. ✅
14. In-app scheduler for due reminders with latest-rule selection, rule + charge
    dedupe, retry after failure and a manual Run Now action. ✅

### Next (moderate)
1. Date-range filters on reports and a tenant statement PDF.
2. Email reminders via SMTP, with a retry/status column.
3. Payment-confirmation-triggered receipt SMS once mobile-money/bank APIs exist.
4. Background/scheduled reminder sending when the app is closed (Task Scheduler
   or a small server).
5. Hosted webhook relay for delivery receipts, inbound SMS/WhatsApp replies and
   WhatsApp template management.
6. Staff accounts and role checks (ADMIN vs STAFF).
7. Windows Task Scheduler integration for monthly charges/backups/reminders.
8. Basic tests wired into `mvn test` (the smoke tests are a good starting point).
9. Encrypted backups and a restore wizard with a checksum check.

### Later (hard, external dependencies)
1. Mobile-money and bank integrations through an aggregator, with webhook
   reconciliation and an unmatched-payments queue.
2. Cloud backend (PostgreSQL + API) and multi-device sync.
3. Tenant mobile/web portal.
4. Maintenance and utility-metering modules.
5. Encrypted storage/backups and a formal privacy/retention policy.

### Very hard / not realistic for a solo desktop project today
1. Direct MTN/Airtel/bank production integrations with settlement and
   reconciliation guarantees.
2. Real-time multi-branch, multi-landlord accounting with tax compliance.
3. Automated credit scoring or tenant vetting across landlords.
4. Certified audit/accounting system and regulatory accreditation.

---

## 7. Security and Privacy Notes (Be Honest With Users)

- BCrypt protects passwords, but the database file itself is **not encrypted**.
  Anyone who can copy `rentaldb.sqlite` can read tenant names, phone numbers,
  IDs, document metadata and payment history.
- Backups are zips containing the database, all tenant documents and a checksum;
  they are **not encrypted**, so they expose the same data. The checksum detects
  accidental corruption, not deliberate tampering.
- Generated receipts and report exports also contain tenant and financial data
  and are stored unencrypted in the user profile.
- SMS/WhatsApp API keys, tokens and the cloud folder/account label are stored in
  `system_settings`. API keys and tokens are encrypted with Windows DPAPI (tied to
  the current Windows user and PC) and shown only as a masked suffix. The cloud
  folder/account label remains plain text. DPAPI is not a substitute for full
  database encryption: use app-specific tokens, and keep the PC and backups
  protected. A database restored for another Windows user or PC cannot decrypt the
  saved keys and will ask for them again.
- Cloud backups copy tenant data into a folder synchronised by OneDrive, Google
  Drive or Dropbox. That sends the data through the provider's service; the
  operator must accept the provider's location, retention and privacy terms.
- The audit log is append-only in normal use but can be edited with any SQLite
  tool; it is not tamper-proof.
- Tenant ID numbers and scanned documents are personal data. If the app is used
  for real tenants, the operator needs a lawful basis, access control, retention
  rules and a secure PC/backup location (Uganda's Data Protection and Privacy
  Act applies; other countries have equivalents).
- The default `admin123` password must be changed before any real use.
- There is no login throttling; a weak password can be brute-forced offline if
  the database file is stolen.

---

## 8. How to Reproduce the Verification

Backend smoke test (isolated temporary data folder, no GUI):

```powershell
cd rental-management-system
powershell -ExecutionPolicy Bypass -File .\verify.ps1
```

The test prints each check and finishes with `Result: 116 passed, 0 failed`. It
exercises settings, due dates, late fees, allocation, PDF receipts, CSV/PDF
exports, documents, DPAPI credential encryption/migration, phone normalisation,
SMS/WhatsApp test sends and Test Connection through a local mock gateway, reminder
status/error and provider metadata, tenant communication history, receipt SMS,
scheduler rule selection/dedupe/retry, backup checksums/retention and cloud
upload/checksum/retention. The test source is in `tools/NowPhaseSmokeTest.java`.

GUI checks performed in this pass:
1. Launch the app and log in with `admin` / `admin123`.
2. Dashboard loads with live cards.
3. The communication setup wizard opens on first login and its Admin Contact,
   SMS, WhatsApp and Cloud Backup tabs render, including the SMS environment
   selector, masked-key hints, Test Connection and automation toggles.
4. Settings -> Communication: admin/SMS/WhatsApp/cloud/automation status renders
   and the wizard launcher refreshes the status after saving.
5. Settings -> Billing & Company: fields load, save and the formatting preview
   updates.
6. Settings -> Backup: the improved backup screen renders, lists backups and
   shows the cloud summary/actions.
7. Reminders: the Send Now action, Run Due Reminders Now action, provider/event
   columns, scheduler status and Communication Setup button render.
8. Payments: Receipt, SMS Receipt and Apply Late Fees actions are present, and
   SMS Receipt refuses a payment that is not verified.
9. Reports: the export report selector and CSV/PDF buttons are present.
10. Tenants: the Documents and Communication History actions are present.
11. Launch `dist\Rental Management System\Rental Management System.exe` and
    confirm it creates/uses `%APPDATA%\RentalManagementSystem\rentaldb.sqlite`,
    migrates an existing database (including the new communication_logs table and
    notification columns) and opens the communication setup wizard.

---

## 9. Bottom Line

**What works now:** the complete local rental workflow, with correct rent-charge
and payment-allocation math, reports, expenses, audit, PDF receipts, CSV/PDF
exports, configurable billing/company settings, local tenant documents,
consistent checksummed backups, DPAPI-protected admin/SMS/WhatsApp communication
setup, real SMS/WhatsApp sending with provider metadata, a per-tenant
communication history, manual receipt SMS, an in-app due-reminder scheduler with
dedupe/retry, cloud-folder backup copies, a polished light/dark UI and a
portable `jpackage` app image - verified by **116 automated checks** plus rendered
UI inspection.

**What is hard:** anything that must run while the app is closed (24/7 scheduled
sending), delivery-receipt/inbound/voice webhooks, SMTP email, mobile-money/bank
APIs, multi-user database sync, encrypted storage/privacy, legal tax/VAT receipts
and installer signing. SMS/WhatsApp themselves now work, but only with a real
provider account, approved sender/template and internet access.

**What is missing:** staff roles, password recovery, automatic late-fee
scheduling, deposit refunds, date-range report filters, tenant statements,
backup restore wizard, encrypted backups and email delivery.

**What to add next:** date-range reports and tenant statements, encrypted
backup/restore, SMTP email reminders, a hosted relay for delivery receipts and
inbound replies, scheduled maintenance, then - only if the business justifies it -
a server backend and payment integrations.
