# FILTR Billing - Project Handoff & Session Continuity

## Summary for the Agent on the New Laptop
This project was migrated from a Linux machine to a Windows machine. The codebase is fully intact, including all Android Kotlin Jetpack Compose code, Room database entities, Google Sheets webhook integrations, ESC/POS receipt generation, and documentation.

## Project At-A-Glance
- **Project Name**: FILTR Billing
- **Platform**: Android (Kotlin, Jetpack Compose, Material 3)
- **Primary Architecture**: MVVM (`MainViewModel.kt` + Room DAOs + OkHttp Google Sheets Sync)
- **AI Integration**: Gemini 2.5 Flash for natural voice order parsing
- **Key Reference**: [`PROJECT_CONTEXT.md`](file:///PROJECT_CONTEXT.md) contains 600+ lines of in-depth technical documentation.
- **Rules Reference**: [`AGENTS.md`](file:///AGENTS.md) contains the workspace-level rules and conventions.

## Codebase Map
- **UI Screens**: `app/src/main/java/com/example/ui/screens/`
  - `BillingScreen.kt`: POS grid, item customizations, UPI payment QR, order placement
  - `KdsScreen.kt`: Kitchen Display System with live status toggling (PREPARING / READY / DELIVERED)
  - `MenuManagementScreen.kt`: Add/edit items, manage categories, sync to Sheets
  - `VoiceOrderScreen.kt`: Audio recording, Gemini 2.5 Flash audio order parsing
  - `AnalyticsScreen.kt`: Sales summary, Cash vs. UPI split, export to Google Sheets
  - `SettingsScreen.kt`: UPI account configs, thermal printer Bluetooth/USB settings
- **ViewModel**: `app/src/main/java/com/example/ui/MainViewModel.kt`
- **Database & Entities**: `app/src/main/java/com/example/data/`
  - `entity/`: `OrderEntity`, `OrderItemEntity`, `MenuItemEntity`, `UpiAccountEntity`
  - `dao/`: Room DAOs for reactive queries
  - `repository/`: Single source of truth combining Room + Remote Webhook
- **Utilities**: `app/src/main/java/com/example/util/`
  - `EscPosBuilder.kt`: Thermal printer ESC/POS command formatting
  - `ThermalImageProcessor.kt`: Bitmap rasterization for logo & QR printing
  - `AudioRecorderManager.kt`: M4A/WAV microphone capture for voice orders

## Windows Environment Quick Setup
1. **Android SDK**: Ensure `local.properties` contains your Windows SDK path, e.g.:
   ```properties
   sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
   ```
2. **Building the App**:
   - In PowerShell / CMD: `.\gradlew.bat assembleDebug`
   - In Git Bash: `./gradlew assembleDebug`
3. **Keystore**: `debug.keystore` is included in the project root to ensure matching APK signing across devices.

## How to Continue in Antigravity on Windows
Start a new conversation in Antigravity on your Windows laptop with:
> *"I have transferred this project to my Windows laptop. Please review `PROJECT_HANDOFF.md` and `PROJECT_CONTEXT.md`, check the build status, and help me continue developing."*
