# Windows Migration & Antigravity Setup Guide

Follow these quick steps on your new Windows laptop:

---

### Step 1: Extract the Project
1. Copy `filtr-billing-windows.zip` to your Windows laptop.
2. Right-click the `.zip` file and select **Extract All...**
3. Choose a convenient location, for example:
   `C:\Users\<YourUsername>\Projects\filtr-billing` or `C:\Users\<YourUsername>\Desktop\filtr-billing`

---

### Step 2: Configure Android SDK (`local.properties`)
1. Open the extracted folder in Windows File Explorer.
2. If `local.properties` exists, open it in Notepad and verify `sdk.dir`.
3. Set it to your Windows Android SDK path:
   ```properties
   sdk.dir=C\:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
   ```
   *(Note: Double backslashes `\\` or forward slashes `/` are standard in properties files).*

---

### Step 3: Open in Antigravity on Windows
1. Launch **Antigravity** on your Windows laptop.
2. Click **File -> Open Folder...** (or **Projects -> Add Workspace**).
3. Select your extracted `filtr-billing` folder.

---

### Step 4: Start the Conversation
In the Antigravity chat panel, paste this prompt:

> *"Hi! I've migrated this project from my Linux laptop to Windows. Please read `PROJECT_HANDOFF.md` and `PROJECT_CONTEXT.md` to get full context on the architecture, database, and features, and let's continue."*

Antigravity will instantly load `AGENTS.md`, read `PROJECT_HANDOFF.md`, and have complete 100% awareness of your project without any cross-platform path issues!

---

### Optional: Restoring Raw Chat History
If you also want the old raw conversation databases loaded into your Antigravity history panel on Windows:
1. In the zip, you will find an `antigravity_history_backup/` folder.
2. Copy its contents into your Windows Antigravity data folder:
   `C:\Users\<YourUsername>\.gemini\antigravity\`
3. Restart Antigravity.
