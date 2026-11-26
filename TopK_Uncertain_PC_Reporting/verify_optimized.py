from pathlib import Path
from fractions import Fraction
import sys,json,subprocess,random,collections,time
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'code/tests'))
from run_tests import make_cases
from oracle import oracle,line

def main():
    cases=make_cases();rng=random.Random(20261112)
    for index in range(60):
        ids=[-2147483648,0,3,81,2147483647][:rng.randint(2,5)]
        rows=[(t,i,str(rng.randint(1,10)/10)) for t in range(1,13) for i in ids if rng.random()<.65]
        rng.shuffle(rows)
        for k in [1,5,50]:cases.append(dict(id=f'optimized_random_{index}_{k}',rows=rows,k=k,n=12,m=len({i for t,i,p in rows})))
    requests=[]
    for c in cases:
        for mode in ['array:true','bitmap:true','adaptive:true']:
            requests.append((c,mode))
    for c in cases[-180:]:requests.append((c,'adaptive:false'))
    # Reaches the checked-long scale limit, then restarts the original exact miner.
    requests.append((dict(id='checked_long_overflow_fallback',rows=[(1,i,'1') for i in range(1,20)],k=1,n=1,m=19),'adaptive:true'))
    started=time.monotonic()
    command=['java','-Xms32m','-Xmx1g','-cp',str(ROOT/'code/build/classes'),'OptimizedAuditRunner']
    result=subprocess.run(command,input='\n'.join(line(c,mode) for c,mode in requests)+'\n',capture_output=True,text=True,timeout=180)
    (ROOT/'verification').mkdir(exist_ok=True)
    (ROOT/'verification/oracle_outputs.jsonl').write_text(result.stdout)
    (ROOT/'verification/oracle_stderr.log').write_text(result.stderr)
    assert result.returncode==0,result.stderr
    outputs=[json.loads(v) for v in result.stdout.splitlines()];assert len(outputs)==len(requests)
    counts=collections.Counter();failures=[];cache={};bitmap=0;early=0
    for (case,mode),out in zip(requests,outputs):
        if 'error' in out:failures.append((case['id'],mode,out));continue
        if case['id']=='checked_long_overflow_fallback':expected=[((1,),Fraction(1))];assert out['stats']['backend']=='original-decimal-fallback'
        else:
            if case['id'] not in cache:
                exact,bound,top_exact,top_bound=oracle(case['rows'],case['k']);cache[case['id']]=[(x,exact[x]) for x in top_exact]
            expected=cache[case['id']]
        actual=[(tuple(x['items']),Fraction(x['score'])) for x in out['heap']]
        if actual!=expected:failures.append((case['id'],mode,{'expected':str(expected),'actual':str(actual)}))
        counts[out['stats']['backend']]+=1;bitmap+=out['stats']['bitmapBlocks'];early+=out['stats']['earlyRejections']
    summary={'queries':len(requests),'failures':len(failures),'backends':dict(counts),'bitmap_blocks_exercised':bitmap,'early_rejections_exercised':early,'seconds':time.monotonic()-started,'scope':'Exact ordered itemsets and rational scores against independent exhaustive oracle; all three TID modes; early on/off; arbitrary decimals and checked-long overflow fallback.'}
    (ROOT/'verification/failures.json').write_text(json.dumps(failures,indent=2))
    (ROOT/'verification/summary.json').write_text(json.dumps(summary,indent=2))
    print(json.dumps(summary,indent=2),flush=True)
    assert not failures,failures[:3]
    assert bitmap>0 and early>0

if __name__=='__main__':main()
