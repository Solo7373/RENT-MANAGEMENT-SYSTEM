# Rental Management System

A desktop rental-property management application for small landlords, hostels and
SACCO-style property managers. It runs entirely on one machine with no server or
database installation. Day-to-day property management works offline; optional SMS
and WhatsApp reminders need an internet connection, and cloud backup uses a folder
synced by OneDrive, Google Drive or Dropbox.

Built with **Java 21**, **JavaFX 21**, **SQLite**, **BCrypt** and **OpenPDF**.

---

## Features

### Portfolio
- Properties: add, edit, delete, double-click to edit
- Rooms: add, edit, delete, mark occupied/vacant, condition notes
- Tenants: full profile with phone, WhatsApp, email, National ID, School ID,
  Refugee ID, room number and rent/pricing, plus live search
- Tenancies: assign a tenant to a vacant room, capture monthly rent and deposit,
  terminate a tenancy (frees the room automatically)

### Money
- Payments: record payments (MTN Mobile Money, Airtel Money, Cash, Bank Transfer),
  verify them, delete them and print a **PDF receipt**
- Rent charges: one charge per active tenancy per month, generated automatically
  when the app is opened (also available as a manual button)
- Payment allocation: a verified payment is spread across the tenant's oldest
  unpaid charges; partial payments are supported (`PENDING -> PARTIAL -> PAID`)
- Deleting a verified payment reverses its allocations, so arrears stay correct
- Late fees: overdue charges receive the configured percentage once, through an
  explicit **Apply Late Fees** action with an audit entry
- Expenses: record property expenses by category and date
- Reports: live revenue, occupancy, arrears, expenses, net income, expected
  monthly rent, property summary, outstanding charges and monthly revenue

### Exports and receipts
- Printable PDF payment receipts with company header, receipt number, amount,
  payment method and the rent charges the payment was applied to
- Reports can be exported to **CSV** (UTF-8, Excel-friendly) or **PDF**:
  - Financial summary
  - Outstanding rent charges
  - Property occupancy
  - Monthly revenue
  - Expenses
- Files are saved in the application data folder and opened with the default
  viewer after export

### Documents
- Attach National ID, School ID, Refugee ID, lease agreements and other files to
  a tenant
- Files are validated (PDF, images, Word, text; 10 MB maximum), copied into a
  managed folder with safe generated names, and can be opened or deleted
- Deleting a tenant also removes their document records and files

### Administration
- BCrypt-hashed admin login (`admin` / `admin123` on a fresh database)
- Change password (persists across restarts)
- Payment methods: enable/disable the methods offered to tenants
- **Billing & Company settings**: company name/address/phone/email, currency,
  rent due day, grace period, late-fee percentage, receipt prefix and backup
  retention - all used by receipts, exports and charge generation
- System settings: raw key/value configuration for advanced changes
- Activity log: audit trail of logins, payments, tenancies, settings and CRUD
  actions
- **Safer backups**: each backup is a zip containing a consistent `VACUUM INTO`
  database snapshot, all tenant documents, a readable `backup-info.txt` and a
  SHA-256 checksum. Old backups are pruned by the retention setting, and any
  backup can be verified from the Settings screen.
- Dark mode: full light/dark theme, remembered between sessions

### Communication and cloud backup
- **Guided setup wizard** opens after the first login and can be reopened from
  Settings > Communication or Reminders > Communication Setup. It asks the admin
  for:
  - the administrator's own name, phone, email and WhatsApp number
  - SMS gateway account details
  - WhatsApp Cloud API account details
  - the cloud provider, cloud account and local sync folder for backups
- **SMS gateways**: Africa's Talking, Twilio or a generic HTTP gateway. The
  admin's phone is used for test messages and system alerts; tenant phone numbers
  are used for rent reminders.
- **WhatsApp**: Meta WhatsApp Cloud API with a phone number ID and access token.
  An approved template can be configured for messages outside Meta's 24-hour
  customer-service window.
- **Reminders**: rules and notification history now store the recipient, status
  and provider error. Select a queued reminder and press **Send Now** to deliver
  it through the configured channel. Email has no delivery provider, so those
  entries can only be marked sent manually.
- **Cloud backup**: after each backup the zip and `.sha256` checksum are copied
  into a folder synchronised by the cloud provider. The copy is verified, old
  cloud backups are pruned by the same retention setting, and uploads can be
  automatic or run manually from Settings > Backup.
- Phone numbers are normalised with the configured default country code, so local
  numbers such as `0700...` become `+256700...`.
- Credentials are stored locally in `system_settings`, are masked in the setup
  screens, and are **not encrypted**. Use app-specific API keys/tokens and keep
  the PC and its backups protected.

### Packaging
- `jpackage` build script produces a portable Windows app image with a bundled
  Java runtime, so the target PC does not need Java or Maven
- Optional `.exe` / `.msi` installer build (requires WiX Toolset 3.x)

### UI / UX
- Single-window shell with collapsible hamburger sidebar
- Dashboard is the first screen after login
- Keyboard shortcuts: `P` Properties, `R` Rooms, `T` Tenants, `A` Tenancies,
  `M` Payments, `X` Expenses, `B` Reports, `E` Reminders, `S` Settings,
  `Esc` Dashboard. Shortcuts are ignored while typing in an input field.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| UI | JavaFX 21 (controls + FXML dependency, code-built views) |
| Database | SQLite (file: `rentaldb.sqlite`) via `sqlite-jdbc` 3.44.1.0 |
| Password hashing | BCrypt (`spring-security-crypto`) |
| PDF | OpenPDF 1.3.43 |
| Messaging | `java.net.http.HttpClient` (SMS gateways + WhatsApp Cloud API) |
| Build | Maven |
| Packaging | `jpackage` (bundled runtime) |

No Spring Boot, no web server, no PostgreSQL.

---

## Data and Backups

All writable data lives outside the installation folder so the app works when
installed under `Program Files`:

```
%APPDATA%\RentalManagementSystem\
├── rentaldb.sqlite          # database
├── documents\tenant-<id>\   # attached tenant files
├── backups\                 # consistent zip backups + .sha256 checksums
├── receipts\                # generated payment receipt PDFs
└── reports\                 # CSV and PDF report exports
```

On first launch, a `rentaldb.sqlite` and `documents/`/`backups/` folder in the
old working directory are migrated automatically. Tests and advanced users can
override the folder with `-Drentals.dataDir=C:\path\to\folder`.

A backup zip contains:

- `rentaldb.sqlite` - a consistent snapshot made with SQLite `VACUUM INTO`
  (safe while the app is running)
- `documents/...` - every attached tenant file
- `backup-info.txt` - creation time, company name, integrity result and count
- a sibling `.sha256` file for verification

Backups are **not encrypted**. Store them on a protected drive or encrypted
volume if they contain real tenant data.

### Cloud backup destination

RentalSys does not ask for a cloud password or use OAuth. Instead:

1. Install and sign in to OneDrive, Google Drive for desktop or Dropbox on the
   same PC.
2. Open the setup wizard and choose the cloud provider and account label.
3. Select a folder inside that provider's local sync directory (for example
   `C:\Users\me\OneDrive\RentalBackups`). The wizard can try to detect it, and
   always tests that the folder is writable before saving.
4. Keep **Upload each new backup automatically** ticked, or use
   Settings > Backup > **Upload Latest** later.

Each upload copies the zip and `.sha256` file, verifies the checksum of the copy,
then prunes older cloud copies using the same `backup_retention` value. The actual
upload to the cloud is performed by the provider's sync client, so the folder
must stay signed in and let files finish syncing. Cloud backup is a convenience,
not a replacement for an offline backup.

---

## How to Run

### Prerequisites
1. JDK 21
2. Maven 3.9+

### Run
```powershell
cd rental-management-system
mvn clean compile exec:java "-Dexec.mainClass=com.rentals.Launcher"
```

Or with the JavaFX Maven plugin:
```powershell
mvn javafx:run
```

The database is created automatically in `%APPDATA%\RentalManagementSystem` on
first launch, together with the default admin, payment methods, settings and
reminder rules.

### Default login
- Username: `admin`
- Password: `admin123`

Change it from **Settings -> Account** after the first login.

---

## Communication and Cloud Setup

The setup wizard opens automatically after the first login until it is completed.
Later, open **Settings > Communication > Open Setup Wizard** (or the
**Communication Setup** button in Reminders).

### Admin contact
Enter the administrator's name, phone, email and WhatsApp number. The phone is
required and is used for test messages and system alerts. Choose the default
country code so local numbers are normalised correctly.

### SMS
Pick the gateway you already have an account with:

| Provider | Details required |
|---|---|
| Africa's Talking | Username, API key, sender ID (optional) |
| Twilio | Account SID, auth token, From number |
| Generic HTTP | Endpoint URL; optional Bearer API key |

Use **Send Test SMS** to send a message to the admin phone. Generic gateways
receive JSON `{to, message, sender}` unless the URL contains `{to}` and
`{message}` placeholders, in which case a GET request is made.

### WhatsApp
You need a Meta business account with the WhatsApp Cloud API enabled:

1. Create or connect a WhatsApp business phone number in Meta's developer console.
2. Copy the **phone number ID** and a permanent **system-user access token**.
3. Optionally enter an approved template name and language. A template with one
   body variable (`{{1}}`) lets RentalSys send reminders outside the 24-hour
   service window; without a template, plain text works only inside that window.
4. Use **Send Test WhatsApp** to send a message to the admin WhatsApp number.

### Cloud backup
Install and sign in to the provider's desktop client, choose the provider and
account label, then select (or detect) a folder inside the sync directory and
press **Test Folder**. Nothing is saved until the folder test succeeds.

### Sending reminders
Open **Reminders**, queue an SMS or WhatsApp message, select it in the history and
press **Send Now**. The status, recipient and any provider error are written back
to the notification row. Sending requires internet access. Email is not delivered
by the app and can only be marked sent manually.

---

## Build a Windows App Image

```powershell
powershell -ExecutionPolicy Bypass -File .\package.ps1
```

Output:

```
dist\Rental Management System\Rental Management System.exe
```

The `dist\Rental Management System` folder is portable: copy it to another
Windows PC and run the `.exe`. It bundles its own Java runtime and writes data
to `%APPDATA%\RentalManagementSystem`.

For an installer:

```powershell
powershell -ExecutionPolicy Bypass -File .\package.ps1 -Type exe
```

This requires **WiX Toolset 3.x** on PATH (or in its default install folder).
Without WiX, use the portable app image; it needs no installer.

---

## Project Structure

```
rental-management-system/
├── src/main/java/com/rentals/
│   ├── Launcher.java                  # main entry point
│   ├── RentalApplication.java         # JavaFX Application
│   ├── config/
│   │   ├── AppPaths.java              # AppData folders + legacy migration
│   │   └── DatabaseInitializer.java   # schema + seed data
│   ├── settings/AppSettings.java      # cached typed settings
│   ├── util/FormatUtils.java          # money formatting + open file
│   ├── dao/                           # JDBC data access objects
│   │   ├── DatabaseConnection.java
│   │   ├── PropertyDAO, RoomDAO, TenantDAO, TenancyDAO
│   │   ├── PaymentDAO, PaymentMethodDAO, RentChargeDAO, ExpenseDAO
│   │   ├── TenantDocumentDAO, ReminderDAO, AuditDAO, ReportDAO, SettingsDAO
│   ├── service/
│   │   ├── BackupService.java         # VACUUM INTO zip backups + checksums
│   │   ├── CloudBackupService.java    # cloud-folder upload, verify, retention
│   │   ├── MessagingService.java      # SMS gateways + WhatsApp Cloud API
│   │   ├── NotificationService.java   # sends queued reminders, stores result
│   │   ├── DocumentService.java       # tenant file validation/copy/open/delete
│   │   ├── ReceiptService.java        # OpenPDF payment receipts
│   │   ├── ReportExportService.java   # CSV + PDF report exports
│   │   ├── authentication, session, layout, theme, audit
│   ├── ui/                            # one package per screen
│   │   ├── LoginView.java
│   │   ├── dashboard/, properties/, rooms/, tenants/, tenancies/
│   │   ├── payments/, expenses/, reports/, reminders/
│   │   ├── settings/ (SettingsView, IntegrationsSetupDialog)
│   ├── properties/, rooms/, tenants/, tenancies/
│   ├── payments/, rent/, expenses/, reminders/, notifications/
│   ├── documents/, audit/, settings/, users/
├── src/main/resources/
│   ├── css/style.css                  # light theme
│   ├── css/dark.css                   # dark theme overrides
│   └── icon.png
├── packaging/icon.ico                 # Windows icon for jpackage
├── package.ps1                        # app-image / installer build
├── dist/                              # generated app image (after packaging)
└── pom.xml
```

---

## Database Overview

15 tables:

`users`, `properties`, `rooms`, `tenants`, `tenancies`, `rent_charges`,
`payments`, `payment_allocations`, `payment_methods`, `expenses`,
`tenant_documents`, `reminder_rules`, `notification_logs`, `audit_logs`,
`system_settings`.

Key business rules:
- Creating a tenancy marks its room `OCCUPIED`; terminating it marks the room
  `VACANT` and sets `end_date`.
- A rent charge is unique per tenancy per month (generation is idempotent).
- The due date uses the configured `due_day` setting (1-28).
- A charge can receive a late fee once; `late_fee_applied` prevents duplicates
  and the fee is added to the outstanding amount.
- A payment starts `PENDING`; verifying it allocates the money oldest-due first.
- A charge becomes `PARTIAL` or `PAID` based on the allocated amount.
- The schema is upgraded in place for older database files (missing columns are
  added with `ALTER TABLE`).

---

## Verification

A headless smoke test of the Now-phase backend passes **83/83 checks** against a
throw-away database, covering:

- configurable company/billing settings and currency formatting
- due dates driven by the due-day setting
- late-fee application (and prevention of double application)
- payment recording, verification and allocation
- PDF receipt generation with the configured receipt prefix
- CSV and PDF report exports (summary, outstanding, properties, revenue,
  expenses)
- tenant document attach/list/delete and folder cleanup
- admin contact, SMS/WhatsApp and cloud settings
- phone normalisation with the default country code
- SMS and WhatsApp delivery through a local mock gateway (no live credentials)
- reminder delivery status, recipient and provider-error recording
- failure handling for HTTP gateway errors and unconfigured email
- consistent zip backups, checksum verification, retention pruning and document
  inclusion
- cloud upload, checksum verification and cloud retention
- update of the existing database and schema

The UI was rendered off-screen and inspected for the Settings tabs (including
Communication, Billing & Company and Backup) and the Reminders screen with its
Send Now action and recipient/error columns. The packaged app image was launched
and confirmed to create its database in `%APPDATA%\RentalManagementSystem` and to
open the communication setup wizard on first login.

---

## Known Limitations

- The Windows `.exe`/`.msi` installer build needs WiX Toolset 3.x. Without it,
  the portable app image is the supported output.
- Backups and the database are not encrypted, and provider credentials are stored
  locally in `system_settings` without encryption.
- SMS and WhatsApp need a live provider account, valid credentials and an
  internet connection. The automated test uses a local mock gateway, so a real
  delivery test must be done by the admin with their own accounts.
- WhatsApp plain-text messages work only inside Meta's 24-hour customer-service
  window; outside it, an approved template is required.
- Cloud backup copies files to a folder maintained by OneDrive/Google Drive/
  Dropbox. Actual cloud upload and conflict handling are performed by that
  provider's client; RentalSys does not integrate with its API or store its
  password.
- Email reminders are not delivered by the app; they can be marked sent manually.
- Documents are stored locally on one PC; there is no built-in multi-device sync.
- Receipts are plain PDFs; local tax/legal receipt requirements are the
  operator's responsibility.

See [PROJECT_ANALYSIS.md](PROJECT_ANALYSIS.md) for an honest breakdown of what is
hard, what is missing, and the remaining roadmap.
