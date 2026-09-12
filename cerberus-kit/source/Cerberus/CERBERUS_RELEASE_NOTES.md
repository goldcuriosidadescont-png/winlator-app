# Cerberus Winlator 0.2.0-ENGINE-1

Baseline: BrunoDev Winlator 11.2  
Upstream commit: `4f55d117fff1542944e5b91f433470445160ce08`

## 0.1.1-IDENTITY
- External application ID remains `com.cerberus.winlator`.
- Internal upstream namespace remains `com.winlator` for compatibility.
- About dialog now clearly identifies the Cerberus fork and BrunoDev upstream.
- Original third-party credits are preserved.

## 0.2.0-ENGINE-1
- Added `CerberusEngine` entry point.
- Added immutable hardware snapshot detection.
- Added pure-Java `CerberusProfileResolver`.
- Initial profiles: `SM8550`, `ADRENO_7XX`, `ADRENO`, `GENERIC`.
- SM8550 resolves from SoC marker `sm8550` or Adreno 740.
- About dialog shows engine mode, profile, SoC and GPU.
- ENGINE-1 is read-only and introduces no performance actuator.

## Validation
- XML syntax validation: PASS.
- Hardcoded old data-path scan: PASS (0 stale `/data/data/com.winlator` paths).
- Old FileProvider authority scan: PASS.
- Pure-Java profile resolver tests: 6/6 PASS.

## Build status
A full Android APK build is not claimed by this package. The execution environment used to prepare the source does not currently expose the Android SDK/NDK/CMake toolchain required by upstream. The private workspace retains the stable Cerberus signing configuration for the first full release build.
