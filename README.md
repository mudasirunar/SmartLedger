# SmartLedger

SmartLedger is an Android application for managing personal expense ledgers, daily log tracking (such as milk and utility records), and financial analytics with AI-assisted insights.

---

## Features

- **Ledger Tracking**: Track milk consumption, electricity billing, general expenses, and custom daily ledgers.
- **Custom Daily Records**: Support for up to 3 customizable fields per day with flexible data grids.
- **AI Analytics**: Integration with Groq API to provide spending summaries, forecasts, and usage suggestions based on ledger history.
- **Data Protection**: Local database storage with Room, soft-delete trash bin auto-purged after 15 days, and backup/restore capabilities.
- **Notifications**: Automated daily entry reminders with fallback midnight checks.

---

## Technical Overview

- **Language**: Kotlin
- **UI Framework**: Android XML Layouts & Material Design 3
- **Database**: Room Database
- **Networking**: Retrofit 2 & Gson
- **Background Operations**: Android WorkManager & Coroutines
- **Charting**: MPAndroidChart
- **Splash Integration**: AndroidX `core-splashscreen`

---

## Setup & Local Configuration

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/mudasirunar/SmartLedger.git
   ```

2. **Open in Android Studio**:
   Open the cloned project directory in Android Studio.

3. **Configure API Key**:
   Create a `local.properties` file in the project root directory and add your Groq API key:
   ```properties
   GROQ_API_KEY="YOUR_GROQ_API_KEY_HERE"
   ```

4. **Build & Run**:
   Sync Gradle files and run the application on an emulator or physical device.

---

## Project Structure

- `app/src/main/java/com/mudasir/smartledger/`: Contains source code divided into `activity`, `adapter`, `data`, `db`, `network`, and `util`.
- `docs/`: Technical documentation covering system architecture, feature specifications, and development standards.
- `.agents/`: Development guidelines and operational rules for AI contributors.
