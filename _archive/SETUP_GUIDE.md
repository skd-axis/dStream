# Life Streams — Android APK + Widget Setup Guide

## What's included
- `GridWidget.kt`      — 2×2 home screen widget showing all checkers in perspective grid
- `TopStreamsWidget.kt`— 2×2 home screen widget showing top 3 streams with progress bars
- `StreamData.kt`      — shared data layer reading from Capacitor's SharedPreferences
- `MainActivity.kt`    — Capacitor activity that triggers widget refresh on app open/close
- `AndroidManifest.xml`— declares both widgets and the main activity
- `build.gradle`       — Capacitor + Kotlin dependencies

---

## Step 1 — Install prerequisites (one time)
1. Install **Android Studio** (free): https://developer.android.com/studio
2. Install **Node.js** (LTS): https://nodejs.org

---

## Step 2 — Create a Capacitor project

```bash
# Create a new folder for your project
mkdir lifestreams && cd lifestreams

# Init npm project
npm init -y

# Install Capacitor
npm install @capacitor/core @capacitor/cli @capacitor/android @capacitor/preferences

# Init Capacitor (answer the prompts: app name = "Life Streams", id = com.lifestreams.app)
npx cap init

# Add Android platform
npx cap add android
```

---

## Step 3 — Add your HTML app

1. Create a `www/` folder in your project root
2. Copy `life-streams.html` into `www/` and **rename it to `index.html`**
3. Update `capacitor.config.json`:
```json
{
  "appId": "com.lifestreams.app",
  "appName": "Life Streams",
  "webDir": "www",
  "plugins": {
    "Preferences": {
      "group": "CAPPreferences"
    }
  }
}
```

### Important: update the HTML to use Capacitor Preferences
In your `index.html`, replace the `save()` and `load()` functions:

```javascript
// At the top of your <script>, import Capacitor Preferences
// (Capacitor auto-injects this when running natively)
const { Preferences } = window.Capacitor?.Plugins || {};

async function save() {
  const data = JSON.stringify(streams);
  if (Preferences) {
    await Preferences.set({ key: 'ls_v13', value: data });
  } else {
    localStorage.setItem('ls_v13', data); // fallback for browser testing
  }
}

async function load() {
  let data = null;
  if (Preferences) {
    const result = await Preferences.get({ key: 'ls_v13' });
    data = result.value;
  } else {
    data = localStorage.getItem('ls_v13');
  }
  if (data) {
    streams = JSON.parse(data);
    streams.forEach(s => { s._animZ = s.hours * UNIT; });
  }
}
```

---

## Step 4 — Copy the native widget files

Copy all files from this folder into your Android project:

```
android/app/src/main/java/com/lifestreams/app/
  ├── MainActivity.kt
  ├── StreamData.kt
  ├── GridWidget.kt
  ├── TopStreamsWidget.kt
  └── WidgetUpdater.kt

android/app/src/main/res/
  ├── layout/widget_image.xml
  ├── xml/widget_grid_info.xml
  ├── xml/widget_top_info.xml
  └── values/strings.xml   ← merge with existing strings.xml

android/app/src/main/AndroidManifest.xml  ← merge widget receivers into existing manifest
android/app/build.gradle                  ← check dependencies match
```

---

## Step 5 — Sync and open in Android Studio

```bash
npx cap sync android
npx cap open android
```

---

## Step 6 — Build the APK

In Android Studio:
1. Wait for Gradle sync to finish
2. **Build → Generate Signed Bundle/APK → APK**
3. Create a new keystore (save the password!)
4. Choose `release`, click Finish
5. APK is in `android/app/release/app-release.apk`

To install directly on your phone via USB:
```bash
adb install android/app/release/app-release.apk
```

---

## Step 7 — Add widgets to home screen

1. Long-press your Android home screen
2. Tap **Widgets**
3. Find **Life Streams** — you'll see two options:
   - **Life Streams — Grid** (all checkers, perspective view)
   - **Life Streams — Top 3** (top 3 streams with progress bars)
4. Drag either to your home screen

Widgets auto-refresh every 30 minutes, and also update instantly when you open/close the app.

---

## Notes
- The widgets read data from `CAPPreferences` SharedPreferences, which Capacitor writes to automatically
- Widgets tap to open the app
- Both widgets are 2×2 cells but can be resized
- Widget previews need placeholder images (`widget_preview_grid.png`, `widget_preview_top.png`) in `res/drawable/` — add any placeholder PNG for now
