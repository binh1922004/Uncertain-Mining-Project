#!/usr/bin/env python3
"""Build a single portable Java 21 JAR from the supplied production sources."""
import hashlib,json,shutil,subprocess
from pathlib import Path
R=Path(__file__).resolve().parent
C=R/'build/classes'; C.mkdir(parents=True,exist_ok=True)
if C.exists():shutil.rmtree(C)
C.mkdir(parents=True)
sources=sorted((R/'code/src').rglob('*.java'))
subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(C)]+[str(p) for p in sources],check=True)
jar=R/'topk-benchmark.jar'
subprocess.run(['jar','--create','--file',str(jar),'--main-class','algo.UnifiedBenchmark','-C',str(C),'.'],check=True)
sha=lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
info={'jar_sha256':sha(jar),'sources':{str(p.relative_to(R)):sha(p) for p in sources},'javac':subprocess.check_output(['javac','-version'],stderr=subprocess.STDOUT,text=True).strip()}
(R/'build_manifest.json').write_text(json.dumps(info,indent=2)+'\n',encoding='utf-8')
print(f'Built {jar.name} from {len(sources)} files')
