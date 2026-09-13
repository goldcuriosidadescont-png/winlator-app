#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

VERSION="0.5.0"
VERSION_CODE="500"
BOX64_URL="https://github.com/The412Banner/Nightlies/releases/download/Box64-Bionic/Box64-0.4.4-Bionic.wcp"
BOX64_SHA="b092973f60555d6d5605a260b52acb1ccb4767aee631732f20b7626bd8fb05eb"
PROTON_URL="https://github.com/GameNative/proton-wine/releases/download/proton-11.0-2-20260903/proton-wine-11.0-2-x86_64.wcp.xz"
PROTON_SHA="d5bc98a15e1a73876048ae19adc53550bbaea9c835493cf67cc028e3fe88a57e"
TERMUXFS_URL="https://github.com/GameNative/termux-on-gha/releases/download/build-20260218/termuxfs-x86_64.tar"
TERMUXFS_SHA="24f9223c2d268e0c8660de2ff9b6c11ef10f60e03a3f20a6a14a96dd703222ee"

BUILD="$ROOT/build"
DL="$BUILD/downloads"
STAGE="$BUILD/stage"
ASSETS="$BUILD/assets"
OUT="$BUILD/out"

rm -rf "$BUILD"
mkdir -p "$DL" "$STAGE/box64" "$STAGE/proton" "$ASSETS/runtime/box64" "$ASSETS/runtime/proton" "$ASSETS/runtime/compatlib" "$OUT"

say() { printf '\n[CerberusBuilder] %s\n' "$*"; }
verify() {
    local expected="$1" file="$2"
    local got
    got="$(sha256sum "$file" | awk '{print $1}')"
    [[ "$got" == "$expected" ]] || { echo "SHA256 mismatch for $file"; echo "expected=$expected"; echo "got=$got"; exit 1; }
}

extract_any() {
    local src="$1" dst="$2"
    mkdir -p "$dst"
    echo "archive file(1): $(file -b "$src")"
    echo -n "archive magic: "
    od -An -tx1 -N16 "$src" | tr -d '\n'; echo

    if unzip -tqq "$src" >/dev/null 2>&1; then
        echo "archive format: ZIP"
        unzip -q "$src" -d "$dst"
        return 0
    fi
    if tar -tf "$src" >/dev/null 2>&1; then
        echo "archive format: TAR"
        tar -xf "$src" -C "$dst"
        return 0
    fi
    if xz -t "$src" >/dev/null 2>&1; then
        echo "archive format: XZ stream"
        if xz -dc "$src" | tar -tf - >/dev/null 2>&1; then
            xz -dc "$src" | tar -xf - -C "$dst"
        else
            xz -dc "$src" > "$dst/payload.bin"
        fi
        return 0
    fi
    if zstd -t "$src" >/dev/null 2>&1; then
        echo "archive format: ZSTD stream"
        if zstd -q -dc "$src" | tar -tf - >/dev/null 2>&1; then
            zstd -q -dc "$src" | tar -xf - -C "$dst"
        else
            zstd -q -dc "$src" > "$dst/payload.bin"
        fi
        return 0
    fi
    if 7z t "$src" >/dev/null 2>&1; then
        echo "archive format: 7z-supported container"
        7z x -y -o"$dst" "$src" >/dev/null
        return 0
    fi

    echo "Unsupported payload format: $src"
    return 1
}

say "Downloading pinned Box64 Bionic 0.4.4"
curl -L --fail --retry 4 --retry-delay 3 -o "$DL/box64.wcp" "$BOX64_URL"
verify "$BOX64_SHA" "$DL/box64.wcp"

say "Downloading pinned Proton-Wine 11.0-2 x86_64 Bionic"
curl -L --fail --retry 4 --retry-delay 3 -o "$DL/proton.wcp.xz" "$PROTON_URL"
verify "$PROTON_SHA" "$DL/proton.wcp.xz"

say "Detecting and extracting Box64 WCP"
extract_any "$DL/box64.wcp" "$STAGE/box64"
BOX64_BIN="$(find "$STAGE/box64" -type f -name box64 -print -quit)"
[[ -n "$BOX64_BIN" && -f "$BOX64_BIN" ]] || { echo "Box64 binary not found in WCP"; find "$STAGE/box64" -maxdepth 4 -type f -print; exit 1; }
cp -L "$BOX64_BIN" "$ASSETS/runtime/box64/box64"

say "Detecting and extracting Proton WCP"
extract_any "$DL/proton.wcp.xz" "$STAGE/proton"
[[ -d "$STAGE/proton/bin" && -d "$STAGE/proton/lib" ]] || { echo "Unexpected Proton layout"; find "$STAGE/proton" -maxdepth 3 -type d -print | head -n100; exit 1; }
cp -aL "$STAGE/proton/bin" "$ASSETS/runtime/proton/"
cp -aL "$STAGE/proton/lib" "$ASSETS/runtime/proton/"
if [[ -d "$STAGE/proton/share" ]]; then cp -aL "$STAGE/proton/share" "$ASSETS/runtime/proton/"; fi
if [[ -f "$STAGE/proton/profile.json" ]]; then cp "$STAGE/proton/profile.json" "$ASSETS/runtime/proton/"; fi

say "Downloading x86_64 Bionic userspace used to build Proton (dependency source)"
curl -L --fail --retry 4 --retry-delay 5 -o "$DL/termuxfs-x86_64.tar" "$TERMUXFS_URL"
verify "$TERMUXFS_SHA" "$DL/termuxfs-x86_64.tar"
mkdir -p "$STAGE/termuxfs"
tar -xf "$DL/termuxfs-x86_64.tar" -C "$STAGE/termuxfs"
TERMUX_LIB="$(find "$STAGE/termuxfs" -type d -path '*/data/data/com.termux/files/usr/lib' -print -quit)"
if [[ -z "$TERMUX_LIB" ]]; then
    TERMUX_LIB="$(find "$STAGE/termuxfs" -type d -path '*/usr/lib' -print -quit)"
fi
[[ -n "$TERMUX_LIB" && -d "$TERMUX_LIB" ]] || { echo "Termux usr/lib not found"; exit 1; }
echo "TERMUX_LIB=$TERMUX_LIB"

say "Resolving external DT_NEEDED closure for the embedded Proton tree"
python3 - "$ASSETS/runtime/proton" "$TERMUX_LIB" "$ASSETS/runtime/compatlib" <<'PY'
import os, re, shutil, subprocess, sys
proton, termux, out = sys.argv[1:]
os.makedirs(out, exist_ok=True)

SYSTEM = {
    'libc.so','libm.so','libdl.so','liblog.so','libandroid.so','libpthread.so',
    'librt.so','libstdc++.so','libz.so','libEGL.so','libGLESv2.so','libvulkan.so'
}

provided = set()
for root, _, files in os.walk(proton):
    for n in files:
        if '.so' in n or n.startswith('lib'):
            provided.add(n)

candidates = {}
for root, _, files in os.walk(termux):
    for n in files:
        if '.so' in n:
            candidates.setdefault(n, os.path.join(root, n))

needed_re = re.compile(r'Shared library: \[(.+?)\]')
def needed(path):
    try:
        p = subprocess.run(['readelf','-d',path], text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=5)
        return needed_re.findall(p.stdout)
    except Exception:
        return []

def is_elf(path):
    try:
        with open(path,'rb') as f: return f.read(4) == b'\x7fELF'
    except Exception: return False

queue = []
for root, _, files in os.walk(proton):
    for n in files:
        p = os.path.join(root,n)
        if is_elf(p): queue.append(p)

seen_files = set()
missing = set()
while queue:
    p = queue.pop()
    rp = os.path.realpath(p)
    if rp in seen_files: continue
    seen_files.add(rp)
    for dep in needed(p):
        if dep in SYSTEM or dep in provided or os.path.exists(os.path.join(out,dep)):
            continue
        src = candidates.get(dep)
        if not src:
            missing.add(dep)
            continue
        real = os.path.realpath(src)
        dst = os.path.join(out, dep)
        shutil.copy2(real, dst)
        provided.add(dep)
        if is_elf(dst): queue.append(dst)

print('compat libraries copied:', len(os.listdir(out)))
print('unresolved non-system names:', ', '.join(sorted(missing)) if missing else 'none')
PY

say "Generating immutable runtime manifest"
BOX64_ASSET_SHA="$(sha256sum "$ASSETS/runtime/box64/box64" | awk '{print $1}')"
cat > "$ASSETS/runtime/BUILDINFO.txt" <<EOF
Cerberus Bionic PC Runtime ${VERSION}
Box64 source: ${BOX64_URL}
Box64 package SHA256: ${BOX64_SHA}
Box64 embedded binary SHA256: ${BOX64_ASSET_SHA}
Proton source: ${PROTON_URL}
Proton package SHA256: ${PROTON_SHA}
Termux dependency source: ${TERMUXFS_URL}
Termux package SHA256: ${TERMUXFS_SHA}
Architecture: Android ARM64 host / x86_64 Windows guest
Runtime ABI: Bionic
EOF

say "Runtime staged"
du -sh "$ASSETS/runtime" || true
find "$ASSETS/runtime" -maxdepth 3 -type f | head -n 80

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n1)"
PLATFORM=""
for api in 36 35 34 33; do
    if [[ -f "$ANDROID_HOME/platforms/android-$api/android.jar" ]]; then PLATFORM="$ANDROID_HOME/platforms/android-$api/android.jar"; break; fi
done
[[ -n "$BT" && -x "$BT/aapt2" ]] || { echo "Android build-tools unavailable under $ANDROID_HOME"; exit 1; }
[[ -n "$PLATFORM" ]] || { echo "Android platform android.jar unavailable"; exit 1; }

echo "BUILD_TOOLS=$BT"
echo "PLATFORM=$PLATFORM"

say "Compiling Android resources"
mkdir -p "$BUILD/compiled" "$BUILD/classes" "$BUILD/dex"
"$BT/aapt2" compile --dir "$ROOT/res" -o "$BUILD/compiled/res.zip"

say "Linking resources + embedded runtime assets"
"$BT/aapt2" link \
    -o "$BUILD/unsigned.apk" \
    -I "$PLATFORM" \
    --manifest "$ROOT/AndroidManifest.xml" \
    --min-sdk-version 26 \
    --target-sdk-version 28 \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION" \
    -A "$ASSETS" \
    "$BUILD/compiled/res.zip"

say "Compiling Java host"
javac -encoding UTF-8 -source 8 -target 8 \
    -bootclasspath "$PLATFORM" \
    -d "$BUILD/classes" \
    $(find "$ROOT/src" -name '*.java' -print)

say "Generating classes.dex"
mapfile -t CLASSFILES < <(find "$BUILD/classes" -name '*.class' -print)
"$BT/d8" --lib "$PLATFORM" --min-api 26 --output "$BUILD/dex" "${CLASSFILES[@]}"
( cd "$BUILD/dex" && zip -q "$BUILD/unsigned.apk" classes.dex )

say "zipalign"
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

say "Creating signing identity for this build line"
KEYSTORE="$OUT/cerberus-v05.keystore"
keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" \
    -storepass cerberus05 \
    -keypass cerberus05 \
    -alias cerberus-v05 \
    -keyalg RSA -keysize 3072 -validity 3650 \
    -dname "CN=Cerberus Bionic PC v0.5,O=Cerberus Tweaks,C=BR"

APK="$OUT/CerberusBionicPC-v0.5.0-AllInOne-arm64.apk"
"$BT/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:cerberus05 \
    --key-pass pass:cerberus05 \
    --out "$APK" \
    "$BUILD/aligned.apk"

say "Verifying APK signature"
"$BT/apksigner" verify --verbose --print-certs "$APK"

say "Final checksums"
sha256sum "$APK" "$KEYSTORE" | tee "$OUT/SHA256SUMS.txt"
cat > "$OUT/INSTALL.txt" <<'EOF'
Cerberus Bionic PC v0.5.0 All-In-One

This APK uses the same package id com.cerberus.bionicpc but a new signing key.
If an older v0.4 build is installed, uninstall it once before installing v0.5:
  pm uninstall com.cerberus.bionicpc

Then copy the APK to /data/local/tmp and install with pm install.
The first app start copies the embedded Box64/Proton runtime to the private app sandbox,
creates the Default container, runs wine --version, then attempts wineboot -u.

The v0.5 scope is CPU/Win32/container bootstrap. X11/Android Surface and D3D/Vulkan are the next stage.
EOF

say "DONE: $APK"
ls -lh "$OUT"
