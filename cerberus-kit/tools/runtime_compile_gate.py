#!/usr/bin/env python3
from pathlib import Path
import shutil, subprocess, tempfile, sys

KIT = Path(__file__).resolve().parent.parent
SRC = KIT / 'source' / 'Cerberus' / 'app' / 'src' / 'main' / 'java'
STUBS = KIT / 'tools' / 'runtime_compile_gate' / 'stubs'
JAVAC = shutil.which('javac')
if not JAVAC:
    print('[FAIL] javac nao encontrado. JDK 17+ e obrigatorio para o compile gate.')
    raise SystemExit(2)

excluded_stubs = {STUBS / 'com/winlator/core/EnvVars.java', STUBS / 'com/winlator/core/ProcessHelper.java', STUBS / 'com/winlator/core/TarCompressorUtils.java'}
sources = sorted(x for x in STUBS.rglob('*.java') if x not in excluded_stubs)
runtime = sorted((SRC / 'com' / 'winlator' / 'cerberus' / 'runtime').glob('*.java'))
launcher = SRC / 'com' / 'winlator' / 'xenvironment' / 'components' / 'CerberusBionicProgramLauncherComponent.java'
if not runtime or not launcher.is_file():
    print('[FAIL] Runtime Cerberus Bionic incompleto.')
    raise SystemExit(3)
core = [SRC / 'com/winlator/core/EnvVars.java', SRC / 'com/winlator/core/ProcessHelper.java', SRC / 'com/winlator/core/TarCompressorUtils.java']
if any(not x.is_file() for x in core):
    print('[FAIL] Core process/environment sources ausentes.')
    raise SystemExit(4)
sources += runtime + core + [launcher]

with tempfile.TemporaryDirectory(prefix='cerberus-v820-javac-') as td:
    out = Path(td) / 'classes'; out.mkdir()
    cmd = [JAVAC, '-encoding', 'UTF-8', '-d', str(out)] + [str(x) for x in sources]
    p = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if p.stdout.strip():
        print(p.stdout.rstrip())
    if p.returncode != 0:
        print(f'[FAIL] Cerberus runtime compile gate falhou (javac exit {p.returncode}).')
        raise SystemExit(p.returncode)
print(f'[PASS] Cerberus Runtime Java compile gate: {len(runtime)} runtime classes + Bionic launcher + ProcessHelper/EnvVars/TarCompressorUtils.')
