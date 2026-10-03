<p align="center">
  <img src="assets/logo.png" alt="ArcTracker Logo" width="128" height="128" />
</p>

<h1 align="center">ArcTracker</h1>

<p align="center">
  <strong>Modern, Privacy-First, Local-First Personal Finance & Expense Tracker for Android</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Language" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Database-Room%20SQLite-4285F4?style=flat&logo=sqlite&logoColor=white" alt="Room SQLite" />
  <img src="https://img.shields.io/badge/Min%20SDK-24-brightgreen?style=flat" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target%20SDK-35-blue?style=flat" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20%2F%20Local-success?style=flat" alt="Privacy" />
</p>

---

## 📌 Overview

**ArcTracker** is an offline-first, privacy-respecting financial tracking application built entirely with **Kotlin** and **Jetpack Compose**. 

Unlike conventional financial apps that upload sensitive banking alerts and SMS messages to third-party cloud servers, ArcTracker operates with a strict **Local Vault architecture**: **100% of your financial data, account numbers, merchant details, and transaction history never leaves your device**.

ArcTracker bridges the convenience of automated transaction tracking with zero compromise on privacy through intelligent on-device SMS parsing, a background notification listener, multi-account reconciliation, and customizable categorization engines.

---

## ✨ Key Features

### 🛡️ 100% Offline & Local-First Privacy
- **Zero Cloud Dependence**: No external backend servers, no analytics trackers, no advertising SDKs.
- **Local SQLite Vault**: Powered by Android Jetpack Room with relational indexing for rapid query execution and encrypted data isolation.
- **Data Sovereignty**: You own your data. Backup, export, restore, or wipe everything with one tap.

### ⚡ Automated Bank SMS Parsing & Historical Import
- **Intelligent SMS Parser**: Automatically parses financial transactional SMS messages from major Indian banks (SBI, HDFC, ICICI, Axis, Kotak, PNB, etc.) and UPI applications (PhonePe, Google Pay, Paytm, CRED, etc.).
- **Smart SMS Wizard**: Scan your historical SMS inbox with customizable date filters (Last 30 Days, 60 Days, 90 Days, 6 Months, or All Time).
- **Deduplication Engine**: Unique message signature hashing (`notificationKey`) prevents duplicate expense entries when importing.

### 🔔 Real-Time Payment Notification Listener
- **Background Listener Service**: Captures incoming transaction notifications instantaneously from banking and payment apps via Android's `NotificationListenerService`.
- **Reversible Verification Flow**: Automatically logs incoming payments into an **Unverified / Pending** queue. Users can verify, categorize, edit, or dismiss them with confidence scoring.

### 📒 Chronological Ledger & Search
- **Date-Grouped Timelines**: Beautiful date-separated headers (*TODAY • 20 OCT*, *YESTERDAY*, *18 OCT 2026*) with aggregate daily net income/expense calculations.
- **Live Summary Action Bar**: Pinned metrics displaying real-time monthly Total Debits, Total Credits, and Net Balance.
- **Quick Action Bottom Sheet**: Long-press any transaction card for instant quick actions (Edit or Delete) without losing context.
- **Deep Search & Filtering**: Real-time filtering by merchant, category tag, payment method, or account identifier.

### 💳 Financial Accounts & Reconciliation Engine
- **Multi-Account Support**: Manage multiple Savings Accounts, Current Accounts, Credit Cards, and Digital Wallets.
- **Automatic Matching**: Intelligently correlates parsed account suffixes (e.g., `•• 4821`) to your configured financial accounts.
- **Account Actions**: Assign, reassign, or unlink financial accounts directly from transaction details or the dedicated Financial Accounts manager.

### 🏷️ Intelligent Categorization & Custom Rule Engine
- **Hierarchical Categories**: Standard Debit categories (Food & Dining, Shopping, Transport, Utilities, Entertainment, Healthcare, etc.) and Credit categories (Salary, Refunds, Cashback, Investments, etc.).
- **User Category Rules & Regex Engine**: Define custom keyword or regex pattern rules to auto-categorize recurring expenses.
- **Merchant Alias Management**: Standardize messy merchant strings (e.g., clean `SWIGGY*BANGALORE_IN` to `Swiggy`).

### ✍️ Full-Screen Transaction Editor & Manual Entry
- **Full-Screen Bottom Sheet**: 100% height immersive manual entry sheet with tag-style pill category buttons and custom currency inputs.
- **Exact Detail & Evidence Screen**: Comprehensive transaction audit screen showing raw SMS/notification evidence, settled amount typography, transaction timestamp with seconds and timezone, account cards, and category badges.
- **Inline Editing**: Quickly adjust merchants, categories, notes, amounts, and dates from both the Home dashboard and the Ledger screen.

### 📊 Financial Analytics & Insights
- **Monthly Spending Breakdown**: Visual breakdown of your spending habits across categories.
- **Category Proportions**: Quick visual progress rings indicating high-spend areas.
- **Savings Velocity**: Compare debits vs. credits to ensure budget goals are met.

### 💾 Backup, Restore & CSV Export
- **JSON Local Vault**: Export complete snapshots of your transactions, accounts, rules, and categories into portable JSON backup files.
- **Deduplicated Restore**: Restore existing backups seamlessly without creating duplicate transactions.
- **CSV Spreadsheet Export**: Export transactions to standard CSV format compatible with Microsoft Excel, Google Sheets, or budgeting software.

---

## 🎨 UI & Design Philosophy

ArcTracker features an original design system crafted with Jetpack Compose:
- **Cyber Dark Theme**: Deep black (`#0D0F14`) surfaces with elevated charcoal containers (`#161922` and `#1C222E`).
- **High-Contrast Typography**: Clear hierarchy with customized typography and emerald green accents for credits (`#22C55E`) and coral accents for debits (`#EF4444`).
- **Smooth Animations & Haptics**: Native Compose transitions, animated expansibility, and edge-to-edge system window insets (`WindowInsets.ime` and `statusBarsPadding`).

---

## 🛠️ Tech Stack & Architecture

| Component | Technology | Description |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.0+ | Modern expressive language with Coroutines and Flow |
| **UI Framework** | Jetpack Compose | Declarative UI toolkit with Material 3 |
| **Persistence** | Jetpack Room | SQLite ORM with KSP code generator and indexed queries |
| **Architecture** | Single Activity / MVVM | Unidirectional Data Flow (UDF), Repositories, DAOs |
| **Services** | NotificationListenerService | Native background notification interceptor |
| **Permissions** | Runtime Permissions | Safe permission flow for `READ_SMS` & `POST_NOTIFICATIONS` |
| **Serialization** | Google Gson | JSON parsing for encrypted vault backup/restore |
| **Build System** | Gradle (Kotlin DSL) | Android SDK 35, Min SDK 24, Java 11 target |

---

## 📂 Project Structure

```
ArcTrackerDemo/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/subhashrelangi/arctracker/
│   │   │   │   ├── data/                 # Room Entities, DAOs, Database, Repositories
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── Expense.kt
│   │   │   │   │   ├── FinancialAccount.kt
│   │   │   │   │   ├── TransactionCategory.kt
│   │   │   │   │   └── UserCategoryRule.kt
│   │   │   │   ├── service/              # Notification Listener, Reconciler, Managers
│   │   │   │   │   ├── AccountReconciliationManager.kt
│   │   │   │   │   ├── NotificationReaderService.kt
│   │   │   │   │   ├── SmsImportManager.kt
│   │   │   │   │   └── TransactionManager.kt
│   │   │   │   ├── ui/                   # Jetpack Compose Screens, Components, Dialogs
│   │   │   │   │   ├── home/             # Home Dashboard & Redesign
│   │   │   │   │   ├── ledger/           # Ledger Timeline, Headers, Summary Bars
│   │   │   │   │   ├── sms/              # SMS Scan Wizard, History Importer
│   │   │   │   │   ├── AddExpenseDialog.kt
│   │   │   │   │   ├── EditTransactionPage.kt
│   │   │   │   │   ├── TransactionDetailDialog.kt
│   │   │   │   │   └── TransactionActionSheet.kt
│   │   │   │   ├── utils/                # SMS Regex Parsers, Number Formatters
│   │   │   │   └── MainActivity.kt       # Single-activity orchestrator & navigation
│   │   │   ├── res/                      # Drawables, mipmaps, string resources, colors
│   │   │   └── AndroidManifest.xml       # App manifest, services & permissions
│   │   └── test/                         # Unit tests (Room DAOs, SMS parsers, managers)
│   └── build.gradle.kts                  # App-level dependencies & build configuration
├── assets/                               # Repository visual assets (App Logo)
├── Ui-Designs/                           # Architectural mockups & design references
├── build.gradle.kts                      # Root Gradle build script
└── settings.gradle.kts                   # Gradle project settings
```

---

## 🔒 Permissions & Security Model

ArcTracker strictly requests only the permissions necessary to automate transaction logging locally:

| Permission | Purpose | Privacy Guarantee |
| :--- | :--- | :--- |
| `android.permission.READ_SMS` | Scans bank transaction messages during manual import wizard. | Messages are evaluated on-device through regex patterns. Personal/OTP messages are ignored and never stored or sent anywhere. |
| `android.permission.POST_NOTIFICATIONS` | Displays instant transaction notifications with Quick-Approve actions. | Used solely for local system alerts. |
| `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | Intercepts payment alerts from banking and UPI apps as they occur. | Runs locally in the background. Only matching transaction alerts from recognized payment apps are processed. |

---

## 🚀 Getting Started & Building from Source

### Prerequisites
1. **Android Studio**: Ladybug (2024.2.1+) or newer.
2. **Java Development Kit (JDK)**: JDK 11 or JDK 17.
3. **Android SDK**: API Level 35 (compileSdk & targetSdk).

### Clone & Build
```bash
# 1. Clone the repository
git clone https://github.com/subhashrelangi/ArcTrackerDemo.git
cd ArcTrackerDemo

# 2. Build Debug APK
./gradlew assembleDebug

# 3. Run Unit Tests
./gradlew testDebugUnitTest
```

The compiled APK will be available at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Testing

The codebase includes an extensive suite of unit and instrumentation tests covering:
- Banking SMS regex pattern parsing across various bank formats.
- Account reconciliation and automatic matching algorithms.
- Category inference rules and merchant aliases.
- Database CRUD transactions, indices, and data deduplication.

Run all tests via:
```bash
./gradlew test
```

---

## 📄 License

This project is developed for personal finance management with a privacy-first guarantee. All rights reserved.
