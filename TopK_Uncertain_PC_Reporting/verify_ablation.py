#!/usr/bin/env python3
"""Verify all four cap/stopping combinations against independent rational oracle."""
from pathlib import Path
from fractions import Fraction
import sys,random,subprocess,json,collections,itertools
ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'code/tests'))
from oracle import oracle,line

def main():
    rng=random.Random(20261126);cases=[]
    for index,values in enumerate(itertools.product([None,'.5','1'],repeat=6)):
        rows=[(slot//3+1,slot%3+1,p) for slot,p in enumerate(values) if p is not None]
        for k in (1,3,7):cases.append(dict(id=f'exhaustive_{index}_{k}',rows=rows,k=k,n=2,m=len({i for t,i,p in rows})))
    for j in range(180):
        ids=[-3,0,8,10,31,100,2147483647][:rng.randint(2,7)];n=rng.randint(2,16)
        rows=[(t,i,str(rng.randint(1,10)/10)) for t in range(1,n+1) for i in ids if rng.random()<.7];rng.shuffle(rows)
        for k in (1,5,40):cases.append(dict(id=f'random_{j}_{k}',rows=rows,k=k,n=n,m=len({i for t,i,p in rows})))
    requests=[(c,f'adaptive:{str(early).lower()}:{str(cap).lower()}') for c in cases for cap in (False,True) for early in (False,True)]
    ret=subprocess.run(['java','-Xmx1g','-cp',str(ROOT/'code/build/classes'),'OptimizedAuditRunner'],input='\n'.join(line(c,m) for c,m in requests)+'\n',capture_output=True,text=True,check=True,timeout=180)
    rows=[json.loads(l) for l in ret.stdout.splitlines()];assert len(rows)==len(requests)
    cache={};totals=collections.Counter();prune=collections.Counter()
    for (case,mode),out in zip(requests,rows):
        assert 'error' not in out,out
        if case['id'] not in cache:
            exact,_,top,_=oracle(case['rows'],case['k']);cache[case['id']]=[(x,exact[x]) for x in top]
        got=[(tuple(r['items']),Fraction(r['score'])) for r in out['heap']]
        assert got==cache[case['id']],(case,mode,got,cache[case['id']])
        stats=out['stats'];assert stats['backend']=='tenths-long',(case,mode,stats)
        early=mode.split(':')[1]=='true';cap=mode.split(':')[2]=='true';assert stats['earlyEnabled']==early and stats['capEnabled']==cap
        assert cap or stats['capChecks']==stats['capRejections']==0
        assert early or stats['earlyRejections']==0
        assert stats['exactCandidateJoins']<=stats['eligiblePairs']
        totals[mode]+=1;prune['capRejections']+=stats['capRejections'];prune['earlyRejections']+=stats['earlyRejections']
    assert prune['capRejections']>0 and prune['earlyRejections']>0
    print(json.dumps({'status':'PASS','cases':len(cases),'queries':len(requests),'counts':dict(totals),'pruning':dict(prune)},indent=2))
if __name__=='__main__':main()
