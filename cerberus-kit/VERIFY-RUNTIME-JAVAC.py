#!/usr/bin/env python3
import subprocess,sys
from pathlib import Path
raise SystemExit(subprocess.call([sys.executable,str(Path(__file__).resolve().parent/'tools'/'runtime_compile_gate.py')]))
