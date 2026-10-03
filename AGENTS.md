# FILTR Billing - Antigravity Agent Guidelines

## 1. Project Overview & Architecture
**FILTR Billing** is a Jetpack Compose Android POS, Kitchen Display System (KDS), Menu Management, and Gemini-powered Voice Order system for **FILTR Coffee**.

- **Language & Platform**: Kotlin / Android (Target SDK 34 / Min SDK 26)
- **UI Framework**: Jetpack Compose with Material Design 3 (Dark/Light mode support)
- **Local Persistence**: Room Database (`orders`, `order_items`, `menu_items`, `upi_accounts`)
- **Remote Sync**: Offline-first synchronization with Google Sheets via Google Apps Script Webhooks
- **AI Voice Ordering**: Audio recording + Gemini 2.5 Flash API order parsing
- **Thermal Printing**: ESC/POS thermal receipt builder with 58mm/80mm support

## 2. Core Documentation
> **IMPORTANT**: Always refer to [`PROJECT_CONTEXT.md`](file:///PROJECT_CONTEXT.md) in the project root for:
> - Complete Room Database schemas and data models
> - Webhook API actions (`syncOrder`, `deleteOrder`, `getTodayMaxOrderId`, `syncItemMaster`, etc.)
> - Order ID generation formats (`FLT-YYYYMMDD-XXXX`)
> - ESC/POS thermal printing formatting rules
> - UI Navigation & Screen hierarchy

## 3. Platform & Environment Notes
- **Operating System**: Windows (or cross-platform developer environment).
- **Gradle Execution**: Use `gradlew.bat` (on Windows CMD/PowerShell) or `./gradlew` (in Git Bash / WSL).
- **Android SDK**: Ensure `sdk.dir` in `local.properties` points to the local Android SDK path (e.g. `C:\\Users\\<Username>\\AppData\\Local\\Android\\Sdk`).

## 4. Key Engineering Guardrails
1. **Offline-First Integrity**: Never block UI operations on network calls. Always persist locally to Room first, then trigger background sync.
2. **Order ID Sequence Preservation**: Respect the dynamic sequence generator syncing with Google Sheets to prevent ID collision.
3. **Historical Data Protection**: Never hard-delete menu items that have associated order records—mark them as inactive/unavailable instead.
4. **Receipt Formatting**: Ensure thermal receipt lines stay strictly within 32 columns (58mm) or 48 columns (80mm).
