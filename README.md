<p align="center">
  <img src="assets/logo.png" alt="ArcTracker Logo" width="112" height="112" />
</p>

<h1 align="center">ArcTracker</h1>

<p align="center">
  <strong>Private, 100% Offline Personal Expense Tracker for Android</strong><br>
  Track spending automatically from Bank SMS & UPI notifications — with zero cloud servers and zero ads.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Privacy-100%25_Offline-059669?style=flat-square" alt="100% Offline" />
  <img src="https://img.shields.io/badge/Internet_Access-None_(Air--Gapped)-red?style=flat-square" alt="Zero Internet" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License: MIT" /></a>
</p>

---

## 💡 What is ArcTracker?

Most expense tracking apps require you to upload your personal bank messages, transaction histories, and sensitive financial data to third-party cloud servers.

**ArcTracker is completely different.** It is an automated, privacy-first personal finance manager that turns your Bank SMS and UPI payment notifications into a clean, categorized ledger — **entirely on your device**.

- **No accounts or sign-ups required**
- **Zero internet permission** (`android.permission.INTERNET` is completely absent from the app)
- **100% of your financial data stays securely on your phone**

---

## ✨ Key Features

### ⚡ 1. Automatic Expense Tracking
- **Bank SMS Parsing**: Automatically detects debits, credits, account numbers, and balances from all major Indian banks (SBI, HDFC, ICICI, Axis, Kotak, PNB, and more).
- **Real-Time Notification Listener**: Instantly captures push notifications from UPI apps (Google Pay, PhonePe, Paytm, CRED, Amazon Pay, Navi, etc.) the second a payment happens.
- **Smart Spam & Promo Filter**: Built-in noise engine automatically ignores OTPs, balance inquiries, recharge promos, and carrier spam so your ledger stays spotless.
- **Historical SMS Import**: Scan past bank SMS messages across customizable date ranges (Last 30 Days, 90 Days, 6 Months, or All Time) with deterministic deduplication so transactions are never counted twice.

### 🧠 2. Intelligent Offline Categorization
- Automatically recognizes merchants (e.g., Swiggy, Zomato, Uber, Amazon, Flipkart, IRCTC, APSRTC, Netflix, Apollo Pharmacy) and assigns the right category (Food, Travel, Shopping, Bills, Healthcare, etc.).
- Built-in compound tokenizer understands local Indian merchants, regional transit corporations (TSRTC, KSRTC, BMTC, etc.), and UPI handles completely offline.
- Easily customize categories and create your own merchant rules that adapt to your personal spending habits.

### 💳 3. Multi-Account Management
- Track multiple bank accounts, credit cards, and digital wallets in one unified dashboard.
- Automatically links transactions to the right bank account by matching masked account numbers (e.g., `••4012`).
- Monitor balances and see which accounts you spend from most frequently.

### 📊 4. Budgets & Spending Insights
- Set monthly spending limits and category-wise budget envelopes.
- Clean visual progress bars alert you when you reach 80%, 90%, or 100% of your budget limit.
- Intuitive daily, weekly, and monthly summaries showing total income, total spending, and net savings.

### 🔒 5. Encrypted Backups & Data Security
- **Spreadsheet Export**: Export your transaction ledger to clean CSV files anytime for Excel or Google Sheets.
- **AES-256-GCM Encrypted Backups**: Save full encrypted backup snapshots (`.arcvault`) protected by your custom password and Android Keystore hardware keys.
- **Biometric App Lock & Screen Protection**: Lock the app with your device's Fingerprint, Face Unlock, or PIN, with automatic screenshot blocking (`FLAG_SECURE`) to protect your balance from prying eyes.

---

## 🔍 How It Works

```
Bank SMS / UPI Push Notification
              │
              ▼
  ┌───────────────────────┐
  │  Noise & Spam Filter  │ ──▶ Discards OTPs, balance alerts & carrier spam
  └───────────┬───────────┘
              │
              ▼
  ┌───────────────────────┐
  │  Smart Parser Engine  │ ──▶ Extracts amount, merchant, type & account
  └───────────┬───────────┘
              │
              ▼
  ┌───────────────────────┐
  │  Offline Categorizer  │ ──▶ Matches merchant to category (Food, Travel, etc.)
  └───────────┬───────────┘
              │
              ▼
  ┌───────────────────────┐
  │  Private On-Device DB │ ──▶ Stored securely in Room SQLite (Zero Cloud)
  └───────────────────────┘
```

---

## 🏦 Supported Banks & Payment Apps

ArcTracker works seamlessly with notifications and SMS messages from all major financial institutions in India:

| Category | Supported Providers |
| :--- | :--- |
| **Major Banks** | State Bank of India (SBI), HDFC Bank, ICICI Bank, Axis Bank, Kotak Mahindra, Punjab National Bank (PNB), Bank of Baroda, IndusInd Bank, Canara Bank, Union Bank, Federal Bank, IDFC FIRST Bank, and more. |
| **UPI & Wallets** | Google Pay, PhonePe, Paytm, CRED, Amazon Pay, BHIM, Navi, Slice, Jupiter, Fi Money, Super.money, and more. |
| **Cards & Credit** | All major Visa, Mastercard, and RuPay credit and debit cards. |

---

## 🛡️ Privacy by Design

We believe your financial records belong exclusively to you. ArcTracker is built around three core principles:

1. **Zero Internet Access**: ArcTracker does not declare the `android.permission.INTERNET` permission. The Android operating system physically prevents the app from connecting to any network.
2. **Zero Analytics / Telemetry**: No tracking SDKs, no advertising libraries, and no crash reporters that send data outside your device.
3. **Hardware-Level Encryption**: Encrypted backups use **AES-256-GCM** with cryptographic keys derived via **PBKDF2** (100,000 salt iterations).

### Permissions Explained

| Permission | Purpose |
| :--- | :--- |
| `READ_SMS` | Used only to detect and import your bank transaction SMS messages locally. Personal texts, chat threads, and non-financial messages are completely ignored. |
| `BIND_NOTIFICATION_LISTENER` | Captures payment notifications from approved banking and UPI apps (Google Pay, PhonePe, etc.) as they arrive. |
| `POST_NOTIFICATIONS` | Displays local budget reminders and pending transaction review alerts directly on your device. |

---

## 🛠️ Tech Stack

- **Language**: Kotlin 2.0+ (Coroutines, StateFlow)
- **UI Framework**: Jetpack Compose with Material 3
- **Local Database**: Jetpack Room SQLite with KSP
- **Security**: Android Keystore, AES-256-GCM, BiometricPrompt
- **Architecture**: Modern Android Clean Architecture with Reactive Flows
- **Compatibility**: Android 7.0 (API Level 24) through Android 15 (API Level 35)

---

## 🚀 Building from Source

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1) or newer
- **JDK**: JDK 17 or JDK 21
- **Android SDK**: API Level 35

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/SubhashRelangi/ArcTracker.git
cd ArcTracker

# 2. Run automated unit test suite (1,500+ tests)
./gradlew testDebugUnitTest

# 3. Assemble Debug APK
./gradlew assembleDebug
```

The compiled APK will be output to:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is open-source and licensed under the [MIT License](LICENSE).

Developed by **Subhash Relangi**.
