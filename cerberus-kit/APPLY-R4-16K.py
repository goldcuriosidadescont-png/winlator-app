#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'source/Cerberus').resolve()

def patch(path, old, new, count=1):
    p = root / path
    s = p.read_text(encoding='utf-8')
    if old not in s:
        raise SystemExit(f'[FAIL] marker missing in {path}: {old!r}')
    p.write_text(s.replace(old, new, count), encoding='utf-8')
    print('[PATCH]', path)

patch('app/build.gradle', "compileSdk 35\n    buildToolsVersion '35.0.0'", "compileSdk 36\n    buildToolsVersion '36.0.0'")
patch('app/build.gradle', 'versionCode 822', 'versionCode 823')
patch('app/build.gradle', 'versionName "8.2.0-Cerberus-Bionic-R3-ANR-Container-Fix"', 'versionName "8.2.0-Cerberus-Bionic-R4-16K-Compat"')

patch('app/src/main/AndroidManifest.xml',
      '        android:extractNativeLibs="true"\n',
      '        android:extractNativeLibs="true"\n        android:pageSizeCompat="enabled"\n')

patch('app/src/main/cpp/CMakeLists.txt',
      'cmake_minimum_required(VERSION 3.22.1)\n\n',
      'cmake_minimum_required(VERSION 3.22.1)\n\n'
      'if(ANDROID)\n'
      '    add_link_options(\n'
      '        "-Wl,-z,max-page-size=16384"\n'
      '        "-Wl,-z,common-page-size=16384"\n'
      '    )\n'
      'endif()\n\n')

patch('app/src/main/java/com/winlator/core/PreloaderDialog.java',
      '''    public void showOnUiThread(final int textResId) {\n        activity.runOnUiThread(() -> show(textResId));\n    }\n\n''',
      '''    public void showOnUiThread(final int textResId) {\n        activity.runOnUiThread(() -> show(textResId));\n    }\n\n    public synchronized void setText(int textResId) {\n        if (dialog == null) create();\n        TextView textView = dialog.findViewById(R.id.TextView);\n        if (textView != null) textView.setText(textResId);\n    }\n\n    public void setTextOnUiThread(final int textResId) {\n        activity.runOnUiThread(() -> setText(textResId));\n    }\n\n''')

patch('app/src/main/java/com/winlator/XServerDisplayActivity.java',
      '''                    CerberusCrashLogger.log("BIONIC", "async install begin");\n                    if (!CerberusBionicInstaller.ensureInstalled(this)) {\n''',
      '''                    CerberusCrashLogger.log("BIONIC", "async install begin");\n                    preloaderDialog.setTextOnUiThread(R.string.installing_system_files);\n                    if (!CerberusBionicInstaller.ensureInstalled(this)) {\n''')

patch('app/src/main/java/com/winlator/XServerDisplayActivity.java',
      '''                    CerberusCrashLogger.log("BIONIC", "imagefs ready version=" + rootFS.getVersion());\n\n                    if (containerManager == null || !containerManager.activateContainerInRoot(container, rootFS)) {\n''',
      '''                    CerberusCrashLogger.log("BIONIC", "imagefs ready version=" + rootFS.getVersion());\n                    preloaderDialog.setTextOnUiThread(R.string.starting_up);\n\n                    if (containerManager == null || !containerManager.activateContainerInRoot(container, rootFS)) {\n''')

print('[PASS] R4 16K compatibility/source alignment patch applied')
