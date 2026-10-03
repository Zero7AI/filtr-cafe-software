# FILTR POS — Demo Package

> **⚠️ This is a self-contained demo. It does NOT connect to the production database, Supabase, Firebase, or Google Sheets.**

---

## What's Inside

| File | Purpose |
|------|---------|
| `index.html` | **Web Demo** — Open in any browser, works immediately. All data stays in browser localStorage. |
| `filtr-coffee-demo.apk` | **Android APK** — Install on any Android phone/tablet to demo the native app. |
| `video-walkthrough.md` | Video script & talking points for recording a demo video. |

---

## 🌐 Web Demo (Recommended for sharing)

### Option 1: Just open the file
Double-click `index.html` — it works offline in any browser. No server needed.

### Option 2: Local server (for phone/tablet testing on same WiFi)
```bash
cd demo/
python3 -m http.server 8080
```
Then open `http://<your-ip>:8080` on any device on the same WiFi.

### Option 3: Host it anywhere
Upload `index.html` to any static hosting:
- **GitHub Pages** — Push to a repo, enable Pages
- **Netlify** — Drag & drop the file
- **Vercel** — Deploy in 1 click
- **Firebase Hosting** — `firebase deploy`

The client gets a link they can open instantly on phone, tablet, or desktop.

---

## 📱 Android APK

1. Transfer `filtr-coffee-demo.apk` to the Android device
2. Tap to install (enable "Install from unknown sources" if prompted)
3. Open **FILTR** app

> The APK is the same production app. The demo data isolation depends on it being installed on a fresh device (no existing data).

---

## 🎬 Recording a Demo Video

See `video-walkthrough.md` for a step-by-step script. Key flow:

1. **Create Order** — Show the POS menu grid, add items, enter customer name, place order
2. **Kitchen Management** — Mark items as made one by one, show per-unit tracking
3. **Payment** — Mark as Cash/UPI, show the UPI QR code receipt
4. **Analytics** — Show the dashboard with revenue, top SKUs, and kitchen prep times
5. **Menu Management** — Add/edit items, toggle stock availability
6. **Customers** — Show the CRM ledger with order history and tab settlement
7. **Inventory** — Show stock levels with health indicators

Use any screen recorder (OBS, phone built-in recorder, or Loom).

---

## 📋 Features Covered in Demo

- ✅ Fast POS checkout with menu grid & category filters
- ✅ Customer auto-suggest from order history
- ✅ Per-unit kitchen preparation tracking (KDS)
- ✅ Cash & UPI payment modes with QR code generation
- ✅ Real-time analytics dashboard (revenue, top SKUs, prep times)
- ✅ Menu master management (add/edit/stock toggle)
- ✅ Customer CRM with credit ledger & 1-tap settlement
- ✅ Inventory management with stock health indicators
- ✅ Thermal receipt preview with branded footer
- ✅ Google Sheets cloud sync (in production app)
- ✅ AI Voice ordering via Gemini (in production app)
