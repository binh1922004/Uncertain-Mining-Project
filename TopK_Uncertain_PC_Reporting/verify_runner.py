#!/usr/bin/env python3
"""End-to-end smoke, resume, deadline, hashes, reference check and subset test."""
from pathlib import Path
from tempfile import TemporaryDirectory
import run as r
import json,subprocess,sys,types

def invoke(*args):return subprocess.run([sys.executable,str(r.ROOT/'run.py'),*map(str,args)],capture_output=True,text=True)
with TemporaryDirectory(prefix='topk_runner_test_') as tmp:
    tmp=Path(tmp);out=tmp/'smoke'
    first=invoke('--smoke','--out',out,'--xmx','512m');assert first.returncode==0,first.stdout+first.stderr
    receipts=r.receipts(out);assert len(receipts)==10 and all(x['status']=='completed' for x in receipts.values())
    second=invoke('--smoke','--out',out,'--xmx','512m');assert second.returncode==0 and r.receipts(out)==receipts and '[smoke]' not in second.stdout
    altered=invoke('--smoke','--out',out,'--xmx','768m');assert altered.returncode!=0 and 'Environment/build/protocol changed' in altered.stderr
    assert len(r.plan(['E1','E2','E3','E4'],r.load(r.MANIFEST)['datasets']))==516
    d=r.load(r.MANIFEST)['datasets'][0];f=tmp/(d['name']+'.hui');f.write_text('1 1\n1 1 1\n')
    try:r.full_inputs([d],tmp);raise AssertionError('Bad input accepted')
    except ValueError as e:assert 'SHA-256 mismatch' in str(e)
    # Directly run the same engine on a tiny deadline. No mock results.
    timed=tmp/'timed';res=invoke('--smoke','--out',timed,'--xmx','512m','--deadline','0.001');assert res.returncode==0,res.stdout+res.stderr
    times=r.receipts(timed);assert len(times)==10 and all(x['status']=='wall_timeout' for x in times.values())
    assert all('time_mean' not in x for x in r.load(timed/'summary.json'))
    src=r.ROOT/'tests/smoke.hui';smoke_ref=out/'smoke_reference.json';inp=(src,smoke_ref,r.digest(src),r.digest(smoke_ref));subout=tmp/'subsets';subout.mkdir()
    inputs=[r.subset(inp,p,subout) for p in (25,50,75)]
    assert [int(p[0].read_text().split()[0]) for p in inputs]==[175,350,525]
    for scale,data in zip((25,50,75),inputs):assert r.subset(inp,scale,subout)==data
    # Corrupt an exact output: the verifier must reject it even when metrics appear valid.
    j=next(x for x in receipts.values() if x['algorithm']=='TUFP');path=tmp/'badpatterns.txt';path.write_text('1 : 999999\n')
    try:r.check_output(j,path,src,smoke_ref,{'size':1});raise AssertionError('Bad output accepted')
    except ValueError:pass
    # Tampering of persisted logs must be detected during resume.
    saved=out/j['attempt_dir']/'stdout.log';saved.write_text(saved.read_text()+'tampered\n')
    try:r.receipts(out);raise AssertionError('Tampered log accepted')
    except ValueError as e:assert 'hash mismatch' in str(e)
    print(json.dumps({'status':'PASS','smoke_runs':len(receipts),'resume_no_duplicate':True,'environment_change_rejected':True,'main_plan_runs':516,'bad_input_hash_rejected':True,'actual_timeout_runs':len(times),'timeout_excluded_from_mean':True,'nested_subset_sizes':[175,350,525],'derived_resume_verified':True,'bad_exact_output_rejected':True,'tampered_receipt_log_rejected':True},indent=2))
