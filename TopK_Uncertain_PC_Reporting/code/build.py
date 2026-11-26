#!/usr/bin/env python3
"""Compile production and test Java sources with JDK 21+, without external dependencies."""
from pathlib import Path
import subprocess
import sys
ROOT = Path(__file__).resolve().parent
classes = ROOT / 'build' / 'classes'
classes.mkdir(parents=True, exist_ok=True)
sources = sorted((ROOT / 'src').rglob('*.java')) + sorted((ROOT / 'tests').glob('*.java'))
command = ['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-Xlint:all', '-d', str(classes)]
result = subprocess.run(command + [str(p) for p in sources], capture_output=True, text=True)
(ROOT / 'build' / 'compile.log').write_text(result.stdout + result.stderr)
if result.returncode:
    print(result.stdout + result.stderr, file=sys.stderr)
    raise SystemExit(result.returncode)
print(f'Compiled {len(sources)} Java files; diagnostics: build/compile.log')
