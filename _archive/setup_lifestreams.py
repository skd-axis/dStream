#!/usr/bin/env python3
"""
Life Streams — Android Widget Project Setup Script
Run this from: /Users/admin/Desktop/dStream

Usage:
    python3 setup_lifestreams.py

What it does:
    1. Validates all source .kt files are present
    2. Creates the full Android res/ directory tree
    3. Writes all required XML layout / config files
    4. Copies .kt files into the correct package path
    5. Writes WidgetUpdater.kt (helper referenced by MainActivity)
    6. Patches capacitor.config.json if it exists
    7. Prints next steps
"""

import os
import sys
import shutil
import json
from pathlib import Path

# ── Config ────────────────────────────────────────────────────────────────────
BASE_DIR        = Path(__file__).parent.resolve()
ANDROID_DIR     = BASE_DIR / "android"
APP_SRC         = ANDROID_DIR / "app" / "src" / "main"
JAVA_PKG        = APP_SRC / "java" / "com" / "lifestreams" / "app"
RES_DIR         = APP_SRC / "res"
PACKAGE_NAME    = "com.lifestreams.app"

# Source .kt files expected next to this script
REQUIRED_KT = [
    "GridWidget.kt",
    "TopStreamsWidget.kt",
    "StreamData.kt",
    "MainActivity.kt",
]

# ── Colours for terminal output ───────────────────────────────────────────────
GREEN  = "\033[92m"
YELLOW = "\033[93m"
RED    = "\033[91m"
CYAN   = "\033[96m"
RESET  = "\033[0m"
BOLD   = "\033[1m"

def ok(msg):   print(f"{GREEN}  ✓  {msg}{RESET}")
def warn(msg): print(f"{YELLOW}  ⚠  {msg}{RESET}")
def err(msg):  print(f"{RED}  ✗  {msg}{RESET}")
def info(msg): print(f"{CYAN}  →  {msg}{RESET}")
def step(msg): print(f"\n{BOLD}{msg}{RESET}")

# ══════════════════════════════════════════════════════════════════════════════
# 1. Validate source files
# ══════════════════════════════════════════════════════════════════════════════
def check_sources():
    step("Step 1 — Checking source .kt files …")
    missing = []
    for fname in REQUIRED_KT:
        p = BASE_DIR / fname
        if p.exists():
            ok(f"Found {fname}")
        else:
            err(f"Missing {fname}  (expected at {p})")
            missing.append(fname)
    if missing:
        print(f"\n{RED}Please place the missing files next to this script and re-run.{RESET}")
        sys.exit(1)

# ══════════════════════════════════════════════════════════════════════════════
# 2. Check android/ directory exists (Capacitor must have been run already)
# ══════════════════════════════════════════════════════════════════════════════
def check_android_dir():
    step("Step 2 — Checking android/ project directory …")
    if not ANDROID_DIR.exists():
        warn("android/ directory not found.")
        print(f"""
{YELLOW}  You need to initialise the Capacitor project first:{RESET}

    cd {BASE_DIR}
    npm init -y
    npm install @capacitor/core @capacitor/cli @capacitor/android @capacitor/preferences
    npx cap init          # App name: Life Streams  |  ID: com.lifestreams.app
    npx cap add android

{YELLOW}  Then re-run this script.{RESET}
""")
        sys.exit(1)
    ok(f"android/ found at {ANDROID_DIR}")

# ══════════════════════════════════════════════════════════════════════════════
# 3. Create directory tree
# ══════════════════════════════════════════════════════════════════════════════
def make_dirs():
    step("Step 3 — Creating directory tree …")
    dirs = [
        JAVA_PKG,
        RES_DIR / "layout",
        RES_DIR / "xml",
        RES_DIR / "values",
        RES_DIR / "drawable",
    ]
    for d in dirs:
        d.mkdir(parents=True, exist_ok=True)
        ok(f"mkdir -p {d.relative_to(BASE_DIR)}")

# ══════════════════════════════════════════════════════════════════════════════
# 4. Copy .kt files
# ══════════════════════════════════════════════════════════════════════════════
def copy_kt_files():
    step("Step 4 — Copying Kotlin source files …")
    for fname in REQUIRED_KT:
        src = BASE_DIR / fname
        dst = JAVA_PKG / fname
        shutil.copy2(src, dst)
        ok(f"{fname}  →  {dst.relative_to(BASE_DIR)}")

# ══════════════════════════════════════════════════════════════════════════════
# 5. Write WidgetUpdater.kt  (helper referenced by MainActivity)
# ══════════════════════════════════════════════════════════════════════════════
WIDGET_UPDATER_KT = '''\
package com.lifestreams.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdater {
    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)

        // Update GridWidget instances
        val gridIds = mgr.getAppWidgetIds(
            ComponentName(context, GridWidget::class.java)
        )
        gridIds.forEach { id ->
            GridWidget.updateGridWidget(context, mgr, id)
        }

        // Update TopStreamsWidget instances
        val topIds = mgr.getAppWidgetIds(
            ComponentName(context, TopStreamsWidget::class.java)
        )
        topIds.forEach { id ->
            TopStreamsWidget.updateTopWidget(context, mgr, id)
        }
    }
}
'''

def write_widget_updater():
    step("Step 5 — Writing WidgetUpdater.kt …")
    dst = JAVA_PKG / "WidgetUpdater.kt"
    dst.write_text(WIDGET_UPDATER_KT)
    ok(f"WidgetUpdater.kt  →  {dst.relative_to(BASE_DIR)}")

# ══════════════════════════════════════════════════════════════════════════════
# 6. Write XML resource files
# ══════════════════════════════════════════════════════════════════════════════

WIDGET_IMAGE_LAYOUT = '''\
<?xml version="1.0" encoding="utf-8"?>
<ImageView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_image"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:scaleType="fitXY" />
'''

WIDGET_GRID_INFO = '''\
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_image"
    android:previewImage="@drawable/widget_preview_grid"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_grid_desc" />
'''

WIDGET_TOP_INFO = '''\
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_image"
    android:previewImage="@drawable/widget_preview_top"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:description="@string/widget_top_desc" />
'''

STRINGS_ADDITION = '''\
    <!-- Life Streams widget labels (merge into your existing strings.xml) -->
    <string name="widget_grid_desc">Life Streams — perspective grid of all your streams</string>
    <string name="widget_top_desc">Life Streams — top 3 streams by hours logged</string>
    <string name="app_name">Life Streams</string>
'''

MANIFEST_RECEIVERS = '''\
        <!-- ══ Life Streams Widgets ══ -->

        <receiver
            android:name=".GridWidget"
            android:exported="true"
            android:label="Life Streams — Grid">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_grid_info" />
        </receiver>

        <receiver
            android:name=".TopStreamsWidget"
            android:exported="true"
            android:label="Life Streams — Top 3">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/widget_top_info" />
        </receiver>
'''

def write_xml_files():
    step("Step 6 — Writing XML resource files …")

    files = {
        RES_DIR / "layout"   / "widget_image.xml"     : WIDGET_IMAGE_LAYOUT,
        RES_DIR / "xml"      / "widget_grid_info.xml"  : WIDGET_GRID_INFO,
        RES_DIR / "xml"      / "widget_top_info.xml"   : WIDGET_TOP_INFO,
    }
    for path, content in files.items():
        path.write_text(content)
        ok(f"{path.name}  →  {path.relative_to(BASE_DIR)}")

    # strings.xml — merge or create
    strings_path = RES_DIR / "values" / "strings.xml"
    if strings_path.exists():
        existing = strings_path.read_text()
        if "widget_grid_desc" not in existing:
            # Insert before closing </resources>
            patched = existing.replace("</resources>", STRINGS_ADDITION + "</resources>")
            strings_path.write_text(patched)
            ok("strings.xml — merged widget strings")
        else:
            warn("strings.xml — widget strings already present, skipped")
    else:
        strings_path.write_text(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n{STRINGS_ADDITION}</resources>\n')
        ok("strings.xml — created")

    # Placeholder PNG stubs for widget previews (1×1 transparent PNG)
    _TINY_PNG = (
        b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01"
        b"\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89"
        b"\x00\x00\x00\nIDATx\x9cc\x00\x01\x00\x00\x05\x00\x01"
        b"\r\n-\xb4\x00\x00\x00\x00IEND\xaeB`\x82"
    )
    for name in ("widget_preview_grid.png", "widget_preview_top.png"):
        p = RES_DIR / "drawable" / name
        if not p.exists():
            p.write_bytes(_TINY_PNG)
            ok(f"{name} — placeholder written (replace with real screenshot later)")
        else:
            warn(f"{name} — already exists, skipped")

# ══════════════════════════════════════════════════════════════════════════════
# 7. Patch AndroidManifest.xml
# ══════════════════════════════════════════════════════════════════════════════
def patch_manifest():
    step("Step 7 — Patching AndroidManifest.xml …")
    manifest_path = APP_SRC / "AndroidManifest.xml"

    if not manifest_path.exists():
        warn(f"AndroidManifest.xml not found at {manifest_path}")
        warn("It will be created by Capacitor during 'npx cap sync'. Re-run this script after that.")
        return

    content = manifest_path.read_text()

    if "GridWidget" in content:
        warn("AndroidManifest.xml — widget receivers already present, skipped")
        return

    # Insert receivers just before </application>
    if "</application>" not in content:
        warn("AndroidManifest.xml — couldn't find </application> tag, manual edit needed")
        _write_manifest_snippet()
        return

    patched = content.replace("</application>", MANIFEST_RECEIVERS + "\n    </application>")
    manifest_path.write_text(patched)
    ok("AndroidManifest.xml — widget receivers inserted")

def _write_manifest_snippet():
    """Fallback: write the receivers to a separate file for manual copy-paste."""
    out = BASE_DIR / "manifest_receivers_to_add.xml"
    out.write_text(MANIFEST_RECEIVERS)
    warn(f"Snippet written to {out.name} — paste it inside <application> in your manifest manually.")

# ══════════════════════════════════════════════════════════════════════════════
# 8. Patch capacitor.config.json
# ══════════════════════════════════════════════════════════════════════════════
def patch_capacitor_config():
    step("Step 8 — Checking capacitor.config.json …")
    cfg_path = BASE_DIR / "capacitor.config.json"

    if not cfg_path.exists():
        warn("capacitor.config.json not found — will be created by 'npx cap init'.")
        info("After running 'npx cap init', re-run this script OR add manually:")
        print("""
    {
      "appId": "com.lifestreams.app",
      "appName": "Life Streams",
      "webDir": "www",
      "plugins": {
        "Preferences": { "group": "CAPPreferences" }
      }
    }
""")
        return

    try:
        cfg = json.loads(cfg_path.read_text())
    except json.JSONDecodeError:
        warn("capacitor.config.json — could not parse JSON, skipping patch.")
        return

    changed = False

    if cfg.get("appId") != PACKAGE_NAME:
        warn(f"appId is '{cfg.get('appId')}' — expected '{PACKAGE_NAME}'")
        warn("Update appId manually if needed.")

    plugins = cfg.setdefault("plugins", {})
    prefs   = plugins.setdefault("Preferences", {})
    if prefs.get("group") != "CAPPreferences":
        prefs["group"] = "CAPPreferences"
        changed = True

    if cfg.get("webDir") != "www":
        cfg["webDir"] = "www"
        changed = True

    if changed:
        cfg_path.write_text(json.dumps(cfg, indent=2))
        ok("capacitor.config.json — updated (webDir + Preferences.group)")
    else:
        ok("capacitor.config.json — already correct, no changes needed")

# ══════════════════════════════════════════════════════════════════════════════
# 9. Print summary
# ══════════════════════════════════════════════════════════════════════════════
def print_summary():
    print(f"""
{BOLD}{'═'*60}
  Setup complete!
{'═'*60}{RESET}

{BOLD}Files written:{RESET}
  android/app/src/main/java/com/lifestreams/app/
    ├── GridWidget.kt
    ├── TopStreamsWidget.kt
    ├── StreamData.kt
    ├── MainActivity.kt
    └── WidgetUpdater.kt

  android/app/src/main/res/
    ├── layout/widget_image.xml
    ├── xml/widget_grid_info.xml
    ├── xml/widget_top_info.xml
    ├── values/strings.xml
    └── drawable/widget_preview_*.png

{BOLD}Next steps:{RESET}

  1. Put your index.html in www/index.html
     (rename life-streams.html → index.html)

  2. Update save() / load() in index.html to use Capacitor Preferences
     (see SETUP_GUIDE.md Step 3 for the exact code swap)

  3. Sync and open in Android Studio:
{CYAN}       cd {BASE_DIR}
       npx cap sync android
       npx cap open android{RESET}

  4. In Android Studio:
       Build → Generate Signed Bundle/APK → APK
       (or press the ▶ Run button to test on a connected phone)

  5. To install via USB:
{CYAN}       adb install android/app/release/app-release.apk{RESET}

{YELLOW}  Tip: replace the placeholder widget_preview_*.png files with
  real screenshots after your first build.{RESET}
""")

# ══════════════════════════════════════════════════════════════════════════════
# Entry point
# ══════════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    print(f"\n{BOLD}Life Streams — Android Widget Setup{RESET}")
    print(f"Working directory: {BASE_DIR}\n")

    check_sources()
    check_android_dir()
    make_dirs()
    copy_kt_files()
    write_widget_updater()
    write_xml_files()
    patch_manifest()
    patch_capacitor_config()
    print_summary()
