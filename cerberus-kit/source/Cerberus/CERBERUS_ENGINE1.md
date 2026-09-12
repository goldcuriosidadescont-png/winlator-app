# Cerberus Winlator 0.2.0-ENGINE-1

## Scope

ENGINE-1 introduces a read-only Cerberus hardware/profile layer. It does not change Box64, Wine, graphics drivers, CPU affinity, environment variables, scheduler behavior, memory policy, or thermal behavior.

## Detection

- Android manufacturer/model/device/board/hardware.
- `Build.SOC_MANUFACTURER` / `Build.SOC_MODEL` on Android 12+.
- Existing Winlator EGL/GPU renderer through `GPUHelper`.
- Existing Adreno model parser through `GPUHelper.getAdrenoModelId()`.
- Resolver profiles: `SM8550`, `ADRENO_7XX`, `ADRENO`, `GENERIC`.

## SM8550 rule

The SM8550 profile is selected when the SoC/board/hardware fingerprint contains `sm8550`, or when the detected Adreno model is `740`.

## User-visible validation

The About dialog now displays engine version, resolved device profile, detected SoC and GPU.
