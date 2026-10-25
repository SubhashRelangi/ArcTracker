<p align="center">
  <img src="assets/logo.png" alt="ArcTracker Logo" width="128" height="128" />
</p>

<h1 align="center">ArcTracker</h1>

<p align="center">
  <strong>Modern, Privacy-First, 100% Offline & Air-Gapped Personal Finance Engine for Android</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin%202.0+-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20(Material%203)-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Database-Room%20SQLite%20(KSP)-4285F4?style=flat&logo=sqlite&logoColor=white" alt="Room SQLite" />
  <img src="https://img.shields.io/badge/Min%20SDK-24-brightgreen?style=flat" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-35-blue?style=flat" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20%2F%20Zero--Cloud-success?style=flat" alt="Privacy" />
  <img src="https://img.shields.io/badge/Encryption-AES--256--GCM-darkgreen?style=flat" alt="Encryption" />
</p>

---

## 📌 Executive Summary

**ArcTracker** is an air-gapped, privacy-respecting financial operating system for Android, engineered completely with **Kotlin** and **Jetpack Compose (Material 3)**.

Unlike traditional expense trackers and fintech applications that harvest sensitive SMS messages, personal transactions, and banking notifications onto remote cloud servers, ArcTracker enforces a strict **Zero-Trace, Air-Gapped Local Vault architecture**:
> **100% of your financial ledger, account balances, merchant histories, notification logs, and rules remain strictly inside your device hardware.** No external servers, no tracking analytics, and no third-party telemetries exist.

ArcTracker harmonizes the ease of automated SMS parsing and background payment detection with uncompromising cryptographic security, multi-account reconciliation, spending velocity envelopes, and granular rule engines.

---

## 🏛️ System Architecture & Privacy Core

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ANDROID HARDWARE SANDBOX                          │
│                                                                             │
│  ┌───────────────────────┐             ┌─────────────────────────────────┐  │
│  │ SMS & Notifications   │             │   UI: Jetpack Compose (M3)      │  │
│  │ Telephony & Listeners │             │   • Universal Sub-Page Headers  │  │
│  └──────────┬────────────┘             │   • Stadium Pill Controls       │  │
│             │                          │   • Tactile Envelopes & Pickers │  │
│             ▼                          └────────────────┬────────────────┘  │
│  ┌───────────────────────┐                              │                   │
│  │ Pattern / Regex Engine│                              │                   │
│  │ • Noise Suppression   │                              ▼                   │
│  │ • Merchant Extraction │             ┌─────────────────────────────────┐  │
│  │ • Account Associator  │────────────▶│    Local Vault Repository       │  │
│  └───────────────────────┘             │    • StateFlow / Coroutines     │  │
│                                        └────────────────┬────────────────┘  │
│                                                         │                   │
│                                                         ▼                   │
│                                        ┌─────────────────────────────────┐  │
│                                        │       Jetpack Room SQLite       │  │
│                                        │  • Encrypted Tables             │  │
│                                        │  • Index Deduplication          │  │
│                                        └────────────────┬────────────────┘  │
│                                                         │                   │
│                                                         ▼                   │
│                                        ┌─────────────────────────────────┐  │
│                                        │ Android Keystore & AES-256-GCM  │  │
│                                        │ • PBKDF2 Key Derivation         │  │
│                                        │ • Screen Hardening (FLAG_SECURE)│  │
│                                        └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## ✨ Core Features & Capabilities

### 🛡️ 1. Air-Gapped Vault & Hardened Security
- **Zero Cloud Footprint**: Built with zero network permissions (`android.permission.INTERNET` is completely absent from the manifest).
- **Android Keystore Hardware Encryption**: Full application snapshots (`.arcvault`) are encrypted with **AES-256-GCM** with **PBKDF2** using 100,000 salt iterations.
- **Screen Capture Protection**: Automated `FLAG_SECURE` window hardening defends sensitive financial ledgers, card numbers, and passphrases from screen recorders and background task snapshots.
- **Zero-Trace Data Purge**: Clear all data permanently with a single two-stage hardware purge that cryptographically erases all local database records and Keystore secrets.

---

### 📥 2. Automated Financial Intake & On-Device Parsing
- **Intelligent Bank SMS Parser**: Accurately recognizes and extracts transaction debits, credits, account numbers, merchants, and balances from all major Indian institutions:
  - Banks: HDFC, ICICI, SBI, Axis, Kotak, PNB, Bank of Baroda, IndusInd, etc.
  - Payment Gateways & UPI Apps: Google Pay, PhonePe, Paytm, CRED, Amazon Pay, Navi, etc.
- **SMS Historical Import Wizard**: Scan your historical SMS inbox with granular time ranges (Last 30 Days, 60 Days, 90 Days, 6 Months, or All Time).
- **Cryptographic Deduplication**: Generates deterministic message hashes (`notificationKey`) ensuring that identical transactions imported via SMS or notifications are never double-counted.

---

### ⚡ 3. Real-Time Notification Monitoring Engine
- **Background Notification Interceptor**: Uses Android’s `NotificationListenerService` to immediately capture transaction events from approved payment apps.
- **Monitored Apps Control Hub**: Dynamically detects installed banking and UPI apps on your hardware. Toggle individual apps on or off without scanning lag, or add custom package listeners.
- **Master Noise Suppression Engine**: Precision ignore filters silence OTPs, balance inquiries, promotional blasts, and carrier messages based on Target Type (`Keyword`, `Sender ID`, `Regex`) and Match Logic (`Contains`, `Exact`, `Starts With`).

---

### 📖 4. Chronological Ledger & Review Workflow
- **Date-Grouped Timelines**: Beautiful timeline organization separated by date headers with daily net income and expense tallies.
- **Pinned Metric Bar**: Real-time totals for Monthly Debits, Monthly Credits, and Net Balance pinned at the top of the ledger.
- **Pending / Unverified Queue**: Instant intake notifications land in a review queue where transactions can be checked, verified with confidence ratings, categorized, or dismissed.
- **Full Transaction Audit & Evidence**: View raw SMS/notification evidence, exact millisecond timestamps, timezones, merchant aliases, and associated financial accounts.

---

### 💳 5. Financial Accounts & Reconciliation Engine
- **Multi-Account Tracking**: Maintain distinct Savings Accounts, Current Accounts, Credit Cards, and Digital Wallets.
- **Account Suffix Matching**: Automatically resolves account identifiers (e.g., `•• 4012` or `•• 8921`) to their corresponding bank accounts.
- **Interactive Card Deck Experience**: Tactile financial card stack mimicking a physical wallet with smooth elevation transitions and quick balances.

---

### 🎯 6. Budgets, Envelopes & Velocity Limits
- **Monthly Cap Target**: Set an overall monthly ceiling with live billing cycle countdowns (days remaining) and allocation percentages.
- **Category Spending Envelopes**: Configure envelope limits for critical categories (Food & Dining, Housing & Utilities, Shopping, Transport, etc.).
- **Threshold Alert Engine**: Local telemetry triggers notifications when spending reaches **80%**, **90%**, or **100%** of defined envelope ceilings.
- **Quick Creator**: Fast tactile category envelope builder with interactive sliders and category modals.

---

### 📦 7. Backup, Portability & Conflict Resolution
- **Universal Spreadsheet Export (CSV)**: Export unencrypted, sanitized transaction records (ISO timestamps, raw amounts, category slugs, and masked account identifiers) with an ArcTracker tactile range modal (**All Time**, **Last 30 Days**, **Last 90 Days**, **Year to Date**).
- **Encrypted Full Snapshot (`.arcvault`)**: Portable encrypted backup container including transactions, custom categories, merchant rules, and budget limits.
- **Live Password Strength Meter**: Dynamic password strength evaluation (Weak, Fair, Good, Strong) evaluating casing, digits, and special characters in real time.
- **3-Way Conflict Resolution Engine**:
  1. **Merge & Deduplicate (Recommended)**: Imports novel records safely by hashing transaction IDs.
  2. **Replace Existing**: Overwrites conflicting transaction IDs while keeping unmatched rows intact.
  3. **Full Wipe & Restore**: Cleans local SQLite tables before rebuilding the ledger from archive.

---

## 🎨 ArcTracker Design System

ArcTracker’s user interface is crafted exclusively in Jetpack Compose following our signature design philosophy:

| Component | Design Standard |
| :--- | :--- |
| **Universal Header (`ArcTrackerHeader`)** | 52.dp baseline height, 38.dp circular navigation buttons, bold 20.sp typography, and contextual security icons. |
| **Action Buttons** | ArcTracker stadium pills (`CircleShape`) across all primary and secondary actions. |
| **Tactile Switches (`ArcTrackerMockSwitch`)** | Forest-green track (`#055B38`) with smooth animated white thumb containing a dark green checkmark. |
| **Containers & Cards** | Soft rounded corners (`RoundedCornerShape(16.dp)` to `24.dp`) with subtle slate borders (`#E2E8F0`). |
| **Keyboard Awareness** | Fully reactive edge-to-edge system insets using `WindowInsets.ime` and `statusBarsPadding`. |

---

## ⚙️ Tech Stack & Dependencies

- **Language**: Kotlin 2.0+ (Coroutines, StateFlow, Flow)
- **UI Framework**: Jetpack Compose (Material 3, Foundation, Animation)
- **Database / ORM**: Jetpack Room SQLite with KSP (Kotlin Symbol Processing)
- **Cryptography**: Java Cryptography Architecture (JCA), Android Keystore, AES-256-GCM, PBKDF2
- **Document Export / Import**: Android Storage Access Framework (SAF)
- **System Services**: `NotificationListenerService`, `TelephonyManager`
- **Build System**: Gradle Kotlin DSL, Android Gradle Plugin (AGP) 8.5+
- **Compatibility**: Android 7.0 (API Level 24) through Android 15 (API Level 35)

---

## 📂 Source Code Structure

```
app/src/main/java/com/subhashrelangi/arctracker/
├── backup/                           # Backup serialization, validation & CSV export
│   ├── BackupManager.kt
│   ├── BackupModels.kt
│   ├── BackupSerializer.kt
│   ├── BackupValidator.kt
│   └── CsvExporter.kt
├── data/                             # Room database, DAOs, and entities
│   ├── AppDatabase.kt
│   ├── Expense.kt
│   ├── ExpenseDao.kt
│   ├── FinancialAccount.kt
│   ├── KnownFinancialAccountDao.kt
│   ├── TransactionCategory.kt
│   ├── TransactionCategoryDao.kt
│   └── UserCategoryRule.kt
├── security/                         # Keystore integration & AES encryption
│   ├── BackupEncryptionManager.kt
│   ├── PrivacySettingsManager.kt
│   └── SensitiveDataSanitizer.kt
├── service/                          # Background readers & reconcilers
│   ├── AccountReconciliationManager.kt
│   ├── NotificationReaderService.kt
│   ├── SmsImportManager.kt
│   └── TransactionManager.kt
├── settings/                         # App monitoring & settings repositories
│   └── MonitoringSettingsRepository.kt
├── ui/                               # Jetpack Compose screens and design system
│   ├── core/                         # Universal headers & navigation
│   │   ├── ArcTrackerHeader.kt
│   │   └── ArcTrackerBottomNavigation.kt
│   ├── home/                         # Dashboard, wallet deck & charts
│   ├── ledger/                       # Chronological transaction timeline
│   ├── review/                       # Notification pending verification queue
│   ├── BackupScreen.kt               # Air-gapped vault archival & CSV export
│   ├── BudgetsScreen.kt              # Monthly caps & envelope velocity engine
│   ├── ClearAllDataScreen.kt         # Two-stage permanent hardware purge
│   ├── IgnoreRulesScreen.kt          # Precision noise suppression engine
│   ├── SettingsScreen.kt             # Settings hub & configuration
│   └── SupportedAppsScreen.kt        # Monitored apps dynamic selector
└── utils/                            # Regex engines & transaction utilities
    ├── CategoryInferenceEngine.kt
    ├── CurrencyUtils.kt
    ├── IgnoreRulesManager.kt
    └── SmsParsingEngine.kt
```

---

## 🔒 Permission Model & Zero-Telemetry Guarantee

ArcTracker requests only the essential Android permissions required to parse transactions on-device:

| Permission | Scope | Privacy Guarantee |
| :--- | :--- | :--- |
| `READ_SMS` | Scans bank SMS messages during manual onboarding or import wizard. | Messages are evaluated on-device through regex patterns. Personal texts, chat threads, and non-financial messages are completely skipped. |
| `POST_NOTIFICATIONS` | Local budget alerts and pending review summaries. | Used solely for local system notifications. Zero external push servers exist. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Intercepts payment notifications from banking and UPI apps. | Operates in an isolated background service on-device. Raw notifications are sanitized, parsed, and never broadcasted. |

> [!IMPORTANT]
> ArcTracker does **NOT** declare `android.permission.INTERNET`. It is physically impossible for the app to transmit your financial records over the internet.

---

## 🚀 Building & Running from Source

### Prerequisites
1. **Android Studio**: Ladybug (2024.2.1+) or newer.
2. **Java Development Kit**: JDK 17 or JDK 21.
3. **Android SDK**: API Level 35 (compileSdk & targetSdk).

### Commands
```bash
# 1. Clone repository
git clone https://github.com/subhashrelangi/ArcTracker.git
cd ArcTracker

# 2. Compile Kotlin source code
./gradlew compileDebugKotlin

# 3. Execute unit test suite (1,390+ automated tests)
./gradlew testDebugUnitTest

# 4. Assemble Debug APK
./gradlew assembleDebug
```

The generated APK will be output to:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License & Attribution

ArcTracker is developed by **Subhash Relangi** as an open-source, privacy-preserving financial operating system. 
Distributed under the **Apache License 2.0**.
