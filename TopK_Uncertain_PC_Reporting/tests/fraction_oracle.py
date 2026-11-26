#!/usr/bin/env python3
"""Independent exact Fraction oracle; enumerate each transaction's nonempty subsets."""
import itertools, random, tempfile, subprocess, collections, json, os
from pathlib import Path
from fractions import Fraction
ROOT=Path(__file__).resolve().parents[1]
def oracle(p):
    lines=[x.split() for x in p.read_text().splitlines() if x.strip() and not x.lstrip().startswith('#')]
    tx=collections.defaultdict(dict)
    for t,i,v in lines[1:]:tx[int(t)][int(i)]=Fraction(v)
    scores=collections.defaultdict(Fraction)
    for t in tx.values():
        for k in range(1,len(t)+1):
            for x in itertools.combinations(sorted(t),k):
                q=Fraction(1)
                for i in x:q*=t[i]
                scores[x]+=q
    return sorted(scores.items(),key=lambda x:(-x[1],len(x[0]),x[0]))
def main():
    rng=random.Random(119156);cases=[]
    with tempfile.TemporaryDirectory() as tmp:
        tmp=Path(tmp)
        def add(p,ks):cases.append((p,ks,oracle(p)))
        def make(n,rows,ks):
            rng.shuffle(rows);p=tmp/f'{len(cases)}.hui';p.write_text(f'{n} {len({r[1] for r in rows})}\n'+''.join(f'{t} {i} {v}\n' for t,i,v in rows));add(p,ks)
        add(ROOT/'examples/paper_table1.hui',[4,6,8,3,10,6,15,1,20]);add(ROOT/'examples/pufp_counterexample.hui',[7,1,4,8,20,7])
        make(0,[],[1,2,1]);make(100,[(1,-9,'0.00000000000000000001'),(100,2147483647,'1')],[1,2,5,1])
        for vals in itertools.product([0,1],repeat=9):
            make(3,[(i//3+1,i%3+1,'1') for i,v in enumerate(vals) if v],[1,3,7,2,10,1])
        for _ in range(300):
            n=rng.randint(1,12);m=rng.randint(1,8);ids=rng.sample(range(-30,101),m);tids=sorted(rng.sample(range(1,80),n));density=rng.choice([.1,.4,.7,1.0]);pool=rng.choice([['1'],['0.1','0.2','0.5','1'],['0.000000000000001','0.00421','0.9999999','1'],['0.33','0.97','0.0001','0.4']]);rows=[(t,i,rng.choice(pool)) for t in tids for i in ids if rng.random()<density];make(max(tids),rows,[1,rng.randint(2,10),rng.randint(1,8),20,3,100,rng.randint(1,30),300,1])
        manifest=tmp/'cases.tsv';manifest.write_text(''.join(f'{p}\t{",".join(map(str,ks))}\n' for p,ks,_ in cases))
        res=subprocess.run(['java','-Xmx1g','-ea','-cp',str(ROOT/'topk-benchmark.jar')+os.pathsep+str(ROOT/'build/tests'),'itufp.FractionDriver',str(manifest)],capture_output=True,text=True,check=True,timeout=120)
        expected={(c,i,mode,k) for c,(_,ks,_) in enumerate(cases) for i,k in enumerate(ks) for mode in ('interactive','fresh')};seen=set()
        for line in res.stdout.splitlines():
            c,i,mode,k,raw=line.split('\t');c,i,k=int(c),int(i),int(k);key=c,i,mode,k;assert key in expected and key not in seen;seen.add(key)
            actual=[]
            for p in raw.split(';') if raw else []:
                ids,v=p.split(':');actual.append((tuple(map(int,ids.split())),Fraction(v)))
            assert actual==cases[c][2][:k],(key,actual,cases[c][2][:k])
        assert seen==expected
        bad=['1 1\n1 1 0\n','1 1\n1 1 1.1\n','1 1\n1 1 .5\n1 1 .6\n','1 1\n2 1 1\n','1 2\n1 1 1\n','-1 0\n','1 1\n1 1 NaN\n','1 1\n0 1 1\n','', '1 1\n1 1\n']
        for j,x in enumerate(bad):
            p=tmp/f'bad{j}.hui';p.write_text(x);out=tmp/f'bad{j}.tsv';r=subprocess.run(['java','-cp',str(ROOT/'topk-benchmark.jar'),'itufp.Main',str(p),'1',str(out)],capture_output=True);assert r.returncode!=0 and not out.exists()
        print(json.dumps({'status':'PASS','cases':len(cases),'queries':len(seen),'fresh_queries':len(seen)//2,'interactive_queries':len(seen)//2,'exact_matches':len(seen),'invalid_inputs_rejected':len(bad),'structural_checks':res.stderr.strip(),'published_BC_partial_resume':'PASS','seed':119156},indent=2))
if __name__=='__main__':main()
