# Cerberus Winlator V8.2.0 — Runtime architecture

```
Cerberus UI / Containers
        |
CerberusRuntimeProfile
        |
CerberusRuntimeResolver
        +-- Classic / x86_64 -> Box64 -> GLIBC RootFS
        |
        +-- Bionic / x86_64 -> Box64 -> isolated Bionic RootFS
        |
        +-- Bionic / ARM64EC -> FEXCore 64
                              +-- FEXCore 32 (HODLL=libwow64fex.dll)
                              +-- WOWBox64 32 (HODLL=wowbox64.dll)
```

The family (`Wine` or `Proton`) is orthogonal to CPU translation. Architecture
owns backend selection.

## Ownership boundaries

`source/Cerberus/app/src/main` is the application. `reference/BionicDonor` is
never added as a Gradle source root. `IMPORT-BIONIC-DONOR.py` whitelists only
runtime payload extensions/names and copies selected donor Java only into
`work/donor-code-reference/` for audit/reference.

## Prefix transition

`CerberusPrefixManager` prepares the destination prefix before touching the
current one. Engine, architecture, family or Bionic runtime changes force a
fresh prefix when required. The previous prefix is retained as
`.wine-rollback-<engine>-<arch>-<timestamp>`. Failed commits restore the old
prefix and stale Cerberus component markers are invalidated on runtime changes.

## Components

Wine/Proton, FEXCore, WOWBox64, DXVK and VKD3D WCP files are stored in
Cerberus-owned stores. WCP content is detected by file magic and can be
ZIP-compatible or TAR compressed with XZ/Zstandard. Archive entries are
normalized (`profile.json` and `./profile.json`) and extracted through
canonical destination guards that reject traversal outside the selected store,
Bionic root or Windows prefix.
