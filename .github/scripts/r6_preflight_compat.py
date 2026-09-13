#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3 or sys.argv[1] not in {"before", "after"}:
    raise SystemExit("usage: r6_preflight_compat.py <before|after> <Cerberus source>")

mode = sys.argv[1]
root = Path(sys.argv[2]).resolve()
p = root / "app/src/main/java/com/winlator/cerberus/runtime/CerberusRuntimePreflight.java"
s = p.read_text(encoding="utf-8")

needle = '        RootFS bionic=RootFS.findBionic(context);File root=bionic.getRootDir();File prefix=new File(container.getRootDir(),".wine");\n'
recovery = '        if(!CerberusPrefixManager.recoverInterruptedInitialization(context,container,p))return "Falha ao recuperar prefix Bionic interrompido";\n'
marker = '        // R6_COMPAT_SENTINEL_BEGIN\n'
endmarker = '        // R6_COMPAT_SENTINEL_END\n'

if mode == "before":
    if needle in s:
        print("[PASS] R6 preflight compatibility baseline already present")
        raise SystemExit(0)
    anchor = '        if (!p.isBionic()) return null;\n'
    if anchor not in s:
        raise SystemExit("[FAIL] R3 preflight Bionic anchor not found")
    shim = (
        marker
        + '        if (false) {\n'
        + needle
        + '        }\n'
        + endmarker
    )
    s = s.replace(anchor, anchor + '\n' + shim, 1)
    p.write_text(s, encoding="utf-8")
    print("[PASS] R6 preflight compatibility sentinel installed")
    raise SystemExit(0)

# after: remove the dead compatibility block after APPLY-R6 inserted its expected
# recovery call, then install that recovery call in the real R3 execution path.
start = s.find(marker)
end = s.find(endmarker)
if start < 0 or end < 0 or end < start:
    raise SystemExit("[FAIL] R6 preflight compatibility sentinel missing")
end += len(endmarker)
s = s[:start] + s[end:]

actual_anchor = '        File prefix = new File(container.getRootDir(), ".wine");\n'
if actual_anchor not in s:
    raise SystemExit("[FAIL] R3 real prefix-validation anchor not found")
if recovery not in s:
    s = s.replace(actual_anchor, recovery + '\n' + actual_anchor, 1)

p.write_text(s, encoding="utf-8")
print("[PASS] R6 interrupted-prefix recovery wired into real R3 preflight")
