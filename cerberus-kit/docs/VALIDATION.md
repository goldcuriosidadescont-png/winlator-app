# Validation gates — Cerberus Winlator V8.2.0 repaired source kit

This package separates source/runtime gates from the final Android build/device
gate. A static/source pass is not represented as proof that every Wine/Proton,
FEX/WOWBox64 or graphics package works on-device.

## Distribution/source gates

- compiled application identity: `com.winlator`, versionCode 820;
- app label: `Cerberus Winlator`;
- Cerberus modern UI/resources are present;
- no donor/Bannerlator/cmod application package under `app/src/main`;
- architecture-driven resolver: Classic x86_64/Box64, Bionic x86_64/Box64,
  Bionic ARM64EC/FEXCore + FEXCore/WOWBox64 32-bit;
- correct `FEX_SMCCHECKS`; no obsolete `FEX_SMC_CHECKS` emission;
- HODLL mapping for FEXCore/WOWBox64;
- exact component/runtime selection: explicitly selected missing components
  fail preflight instead of silently falling back;
- Wine/Proton, CPU and guest graphics WCP stores;
- WCP ZIP/TAR-XZ/TAR-ZSTD compatibility, including `./profile.json`;
- archive traversal guards for ZIP/TAR extraction and WCP target mappings;
- FEX DLL/unixlib synchronization including nested ARM64/ARM64EC unixlib paths;
- installed DXVK/VKD3D markers are accepted only when registered payload files
  still exist;
- builtin Bionic cannot masquerade as Proton;
- fresh-prefix staging + rollback when engine/architecture/family/runtime
  changes require a clean prefix;
- Bionic launch preflight and isolated Bionic root;
- hardened Bionic environment (`LD_LIBRARY_PATH`, XDG, fontconfig, ALSA,
  OpenSSL, Vulkan/GStreamer and preload handling);
- ProcessHelper null-environment and process-lifecycle/PID handling guards;
- all Android XML resources parse;
- 23 Cerberus runtime Java classes plus the Bionic launcher and critical
  ProcessHelper/EnvVars/TarCompressorUtils classes pass the included javac gate;
- JNI AArch64 inventory/dependency audit; `libomp.so` is synchronized from the
  exact configured Android NDK before Gradle build;
- Gradle 4 GiB / 2 workers / global noCompress guards;
- Python is the implementation language for prepare/verify/configure/sync/build
  and APK collection. `.bat` files are optional thin Windows launchers only.

## Prepare gate

Primary command:

```text
python PREPARE-BIONIC.py
```

The preparer pins the donor branch/commit, keeps Git-LFS smudge off, pulls only
selected Bionic/FEX/WOW payload objects, imports only the payload namespace into
Cerberus assets and reruns `VERIFY-BIONIC.py --require-payload`. Donor Java is
kept under `work/` for audit/reference and never compiled into the app.

Optional Windows launcher: `PREPARE-BIONIC.bat`.

## Build/device gate

Primary command:

```text
python BUILD-BIONIC.py
```

The build flow configures the Android SDK, synchronizes the exact AArch64
`libomp.so` from the configured NDK, requires prepared Bionic payloads, reruns
source/runtime validation, runs Gradle `assembleDebug`, finds the resulting APK
recursively, copies it into `output/`, size-checks it and reports SHA-256.

Required Android toolchain declared by this kit:

- Android SDK platform 35
- Build Tools 35.0.0
- NDK 24.0.8215888
- CMake 3.22.1

Optional Windows launcher: `BUILD-BIONIC.bat`.

The repaired source kit was source-validated in the packaging environment, but
the packaging environment does not contain the complete Android SDK/NDK or the
prepared donor payload, so final Gradle/APK/device validation must still be run
on the build machine and target device.
