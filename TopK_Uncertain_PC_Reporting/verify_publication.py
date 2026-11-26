#!/usr/bin/env python3
"""End-to-end publication exports on synthetic data only."""
import csv,json,subprocess,sys,types
from pathlib import Path
from tempfile import TemporaryDirectory
import run as r
from export_report import export

def rows(p):
    with p.open(encoding='utf-8') as f:return list(csv.DictReader(f))

def main():
    full=r.plan(['E1','E2','E3','E4'],r.load(r.MANIFEST)['datasets'])
    assert len(full)==516==len({j['id'] for j in full})
    assert {p:sum(j['phase']==p for j in full) for p in ('E1','E2','E3','E4')}==dict(E1=360,E2=48,E3=36,E4=72)
    with TemporaryDirectory(prefix='publication_test_') as td:
        root=Path(td);pending=root/'pending';pending.mkdir();(pending/'runs').mkdir();r.write(pending/'plan.json',full)
        assert export(pending)['pending']==516
        for name,n in [('04_E1_overall',120),('05_E1_exact_pairwise',60),('06_E2_ablation_and_storage',16),('07_E3_workload',36),('08_E4_scalability',24)]:assert len(rows(pending/'publication'/(name+'.csv')))==n
        smoke=root/'smoke';subprocess.run([sys.executable,str(r.ROOT/'run.py'),'--smoke','--out',str(smoke),'--xmx','512m'],check=True,stdout=subprocess.DEVNULL)
        assert export(smoke)['completed']==10 and export(smoke)['full_protocol_all_valid']
        src=root/'synthetic.hui';src.write_text('4 10\n'+''.join(f'{t} {i} {((i+t)%10+1)/10:.1f}\n' for t in range(1,5) for i in range(1,11)))
        oracle=r.import_ref();matrix,ids,_=oracle.inspect_load(src);prefix,_=oracle.exact_topk(matrix,ids,700)
        old=root/'top700.json';r.write(old,{'input_sha256':r.digest(src),'ranked':r.ref_records(prefix)})
        out=root/'mini';out.mkdir();(out/'runs').mkdir();data=src,old,r.digest(src),r.digest(old)
        a=types.SimpleNamespace(reference_deadline=30,out=out,xms='128m',xmx='512m',deadline=30)
        new=r.prepared_input(data,100,900,out,a);assert len(r.read_ranked(new[1]))==900 and r.read_ranked(new[1])[:700]==prefix
        assert r.prepared_input(data,100,900,out,a)==new
        subs=[r.prepared_input(data,s,100,out,a) for s in (25,50,75)];assert [int(x[0].read_text().split()[0]) for x in subs]==[1,2,3]
        timeout=root/'timeout';timeout.mkdir()
        try:r.prepared_input(data,100,900,timeout,types.SimpleNamespace(reference_deadline=.001));raise AssertionError('Expected reference timeout')
        except RuntimeError:pass
        assert r.load(timeout/'derived/preparation/synthetic_p100_top900.json')['status']=='reference_timeout'
        jlist=r.plan(['E1','E2','E3','E4'],[{'name':'connect','k':[100]}]);selected=[j for j in jlist if j['phase']=='E1' or (j['phase']=='E2' and j['variant'] in ('cap0_stop0','cap1_stop1','array','bitmap')) or (j['phase']=='E3' and j['k']==100) or (j['phase']=='E4' and j['scale'] in (25,100))]
        r.write(out/'plan.json',selected);r.save_input_stats(out,{('connect',100):new})
        for j in selected:
            f,ref,ih,rh=subs[0] if j['scale']==25 else new
            folder=out/'runs'/j['id']/'attempt_test';folder.parent.mkdir(parents=True)
            v=r.execute(j,f,ref,ih,rh,a,folder);assert v['status']=='completed',v
        state=export(out);assert state['completed']==len(selected) and state['all_measurements_valid'] and not state['full_protocol_terminal']
        assert all(x['comparable']=='True' for x in rows(out/'publication/05_E1_exact_pairwise.csv'))
        contrasts=rows(out/'publication/06b_E2_contrasts.csv');assert contrasts and all(x['comparable']=='True' for x in contrasts)
        assert len(rows(out/'publication/07_E3_workload.csv'))==3 and len(rows(out/'publication/08_E4_scalability.csv'))==6
        print(json.dumps(dict(status='PASS',full_plan=516,empty_plan_coverage=516,smoke_completed=10,extended_reference_prefix=700,extended_reference_size=900,reference_timeout_detected=True,fixture_JVM_runs=len(selected),paired_tables_verified=True,partial_suite_not_marked_full=True),indent=2))
if __name__=='__main__':main()
