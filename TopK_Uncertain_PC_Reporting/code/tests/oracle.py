from fractions import Fraction as F
from itertools import combinations
import collections

def oracle(rows,k):
    tx=collections.defaultdict(dict)
    for tid,item,p in rows:tx[tid][item]=F(str(p))
    items=sorted(set(i for t in tx.values() for i in t));sigma={i:sum((t.get(i,F(0)) for t in tx.values()),F(0)) for i in items}
    order=sorted(items,key=lambda i:(-sigma[i],i));rank={i:n for n,i in enumerate(order)}
    cap={};proxy={};classes={}
    for tid,t in tx.items():
        sequence=sorted(t,key=rank.get)
        for n,a in enumerate(sequence):
            prefix=tuple(sequence[:n]);ps=sorted((t[i] for i in prefix),reverse=True)
            cap[tid,a]=t[a]*(ps[0] if ps else 0)
            key=(a,prefix);classes[tid,a]=key
            proxy[key]=max(proxy.get(key,F(0)),ps[1] if len(ps)>1 else F(0))
    exact={};bound={}
    for r in range(1,len(items)+1):
        for x in combinations(items,r):
            supporting=[(tid,t) for tid,t in tx.items() if all(i in t and t[i]>0 for i in x)]
            if not supporting:continue
            s=F(0)
            for tid,t in supporting:
                prod=F(1)
                for i in x:prod*=t[i]
                s+=prod
            exact[x]=s
            if r==1:bound[x]=s
            else:
                a=max(x,key=rank.get)
                bound[x]=sum((cap[tid,a]*(proxy[classes[tid,a]]**(r-2)) for tid,t in supporting),F(0))
    def top(scores):return sorted(scores,key=lambda x:(-scores[x],len(x),x))[:k]
    return exact,bound,top(exact),top(bound)
def line(case,algo):
    return '|'.join([case['id'],algo,str(case['k']),str(case['n']),str(case['m']),';'.join(','.join(map(str,r)) for r in case['rows'])])
