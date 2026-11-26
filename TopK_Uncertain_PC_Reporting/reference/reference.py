"""Direct exact-rational reference, independent of all Java mining structures."""
from pathlib import Path
from fractions import Fraction as F
from collections import Counter
from array import array
import numpy as np
import heapq,hashlib,json,sys


def inspect_load(path):
    ids=[];slots={};tokens={};counts=Counter();sha=hashlib.sha256();previous=0;seen=set();duplicates=0;monotone=True;rows=0;outside=0
    with Path(path).open('rb') as stream:
        header=stream.readline();sha.update(header);n,m=map(int,header.split());matrix=np.zeros((m,n),dtype=np.uint8);lengths=array('I',[0])*(n+1)
        for number,line in enumerate(stream,2):
            sha.update(line);t,i,p=line.split();tid,item=int(t),int(i)
            if p not in tokens:
                value=F(p.decode())*10
                if value.denominator!=1 or not 0<value<=10:raise ValueError(f'{number}: input not positive tenths probability')
                tokens[p]=int(value)
            if not 1<=tid<=n:raise ValueError('TID outside header')
            if tid<previous:monotone=False
            if tid!=previous:seen.clear()
            if item in seen:duplicates+=1
            seen.add(item);previous=tid
            if item not in slots:
                if len(ids)>=m:raise ValueError('More distinct items than declared')
                slots[item]=len(ids);ids.append(item)
            matrix[slots[item],tid-1]=tokens[p];lengths[tid]+=1;counts[p.decode()]+=1;rows+=1
            outside+=not 1<=item<=m
    if len(ids)!=m or not monotone or duplicates:raise ValueError('Invalid header, TID order or duplicate pairs')
    lengths=lengths[1:]
    stats=dict(dataset=Path(path).stem,transactions=n,distinct_items=m,records=rows,mean_length=rows/n if n else 0,density_percent=100*rows/n/m if n and m else 0,min_length=min(lengths,default=0),max_length=max(lengths,default=0),empty_transactions=sum(v==0 for v in lengths),min_id=min(ids,default=None),max_id=max(ids,default=None),items_outside_1_to_m=sum(not 1<=i<=m for i in ids),records_outside_1_to_m=outside,tid_monotone=monotone,duplicate_pairs=duplicates,probability_counts=counts,sha256=sha.hexdigest(),valid=True)
    return matrix,ids,stats


def exact_topk(matrix,ids,k):
    n=matrix.shape[1];sigma=[int(s) for s in matrix.sum(axis=1,dtype=np.int64)];order=sorted(range(len(ids)),key=lambda i:(-sigma[i],ids[i]));heap=[]
    stats={'evaluated_extensions':0,'pruned_extensions':0,'max_depth':1,'big_integer_fallbacks':0}
    def threshold():return heap[0][0] if len(heap)==k else F(0)
    def offer(items,score):
        key=tuple(sorted(items));entry=(score,-len(key),tuple(-i for i in key),key)
        if len(heap)<k:heapq.heappush(heap,entry)
        elif entry[:3]>heap[0][:3]:heapq.heapreplace(heap,entry)
    for i in order:
        if sigma[i]:offer((ids[i],),F(sigma[i],10))
    # Fill a still-underfull heap with distinct supported pairs before DFS.
    # This is exact seeding, not a pruning assumption or Java-derived result.
    seeded=set()
    if len(heap)<k:
        filled=False
        for pos,i in enumerate(order):
            for j in order[pos+1:]:
                product=matrix[i].astype(np.int64)*matrix[j]
                total=int(product.sum(dtype=np.int64))
                if total:
                    pair=tuple(sorted((ids[i],ids[j])));offer(pair,F(total,100));seeded.add(pair)
                if len(heap)==k:filled=True;break
            if filled:break
    stats['seeded_pairs']=len(seeded)
    def search(prefix,vector,tail,score):
        stats['max_depth']=max(stats['max_depth'],len(prefix));power=10**(len(prefix)+1)
        for pos,j in enumerate(tail):
            if min(score,F(sigma[j],10))<threshold():stats['pruned_extensions']+=1;continue
            if n*power>np.iinfo(np.int64).max:
                product=vector.astype(object)*matrix[j];total=int(sum(product));stats['big_integer_fallbacks']+=1
            else:product=vector*matrix[j];total=int(product.sum(dtype=np.int64))
            stats['evaluated_extensions']+=1
            if not total:continue
            value=F(total,power)
            if value<threshold():continue
            extended=prefix+(ids[j],)
            if tuple(sorted(extended)) not in seeded:offer(extended,value)
            if pos+1<len(tail) and value>=threshold():search(extended,product,tail[pos+1:],value)
    for pos,i in enumerate(order):
        score=F(sigma[i],10)
        if score>=threshold() and pos+1<len(order):search((ids[i],),matrix[i].astype(np.int64),order[pos+1:],score)
    return sorted(((e[3],e[0]) for e in heap),key=lambda v:(-v[1],len(v[0]),v[0])),stats


def surrogate_scores(matrix,ids,patterns):
    n=matrix.shape[1];sigma=[int(s) for s in matrix.sum(axis=1,dtype=np.int64)];slots={i:s for s,i in enumerate(ids)};order=sorted(range(len(ids)),key=lambda s:(-sigma[s],ids[s]));rank={ids[s]:r for r,s in enumerate(order)}
    anchors={}
    for items in patterns:
        if len(items)>1:
            a=max(items,key=rank.get);anchors[a]=max(anchors.get(a,0),len(items))
    m1=np.zeros(n,dtype=np.uint8);m2=m1.copy();metadata={}
    for pos,slot in enumerate(order):
        a=ids[slot];row=matrix[slot]
        if a in anchors:
            proxy=np.zeros(n,dtype=np.uint8)
            if anchors[a]>=3:
                signatures=np.ascontiguousarray(np.packbits(matrix[order[:pos]]>0,axis=0).T);active=np.flatnonzero(row);groups={}
                for t in active:
                    key=signatures[t].tobytes();groups[key]=max(groups.get(key,0),int(m2[t]))
                for t in active:proxy[t]=groups[signatures[t].tobytes()]
            metadata[a]=(m1.astype(np.int64),proxy.astype(np.int64))
        m2=np.where(row>=m1,m1,np.maximum(m2,row));m1=np.maximum(m1,row)
    out={}
    for items in patterns:
        if len(items)==1:out[items]=F(sigma[slots[items[0]]],10);continue
        a=max(items,key=rank.get);first,proxy=metadata[a];mask=np.ones(n,dtype=bool);power=10**len(items)
        for i in items:mask &= matrix[slots[i]]>0
        if n*power>np.iinfo(np.int64).max:values=matrix[slots[a]].astype(object)*first*(proxy.astype(object)**(len(items)-2));total=sum(values[mask])
        else:values=matrix[slots[a]].astype(np.int64)*first*(proxy**(len(items)-2));total=values[mask].sum(dtype=np.int64)
        out[items]=F(int(total),power)
    return out


def from_rows(rows):
    ids=sorted({i for t,i,p in rows});n=max((t for t,i,p in rows),default=0);slots={i:s for s,i in enumerate(ids)};matrix=np.zeros((len(ids),n),dtype=np.uint8)
    for t,i,p in rows:
        value=F(str(p))*10
        if value.denominator!=1 or not 0<value<=10:raise ValueError('not tenths')
        matrix[slots[i],t-1]=int(value)
    return matrix,ids


def selftest(code):
    sys.path.insert(0,str(code/'tests'));from oracle import oracle
    queries=json.loads((code/'audit_cases.json').read_text());count=0;score_count=0;seen=set()
    for c in queries:
        try:matrix,ids=from_rows(c['rows'])
        except ValueError:continue
        exact,bound,expected,_=oracle(c['rows'],c['k']);observed,_=exact_topk(matrix,ids,c['k']);assert observed==[(x,exact[x]) for x in expected],c['id'];count+=1
        key=json.dumps(c['rows'])
        if key not in seen:
            computed=surrogate_scores(matrix,ids,list(bound));assert computed==bound,c['id'];score_count+=len(bound);seen.add(key)
    return dict(exact_reference_queries=count,surrogate_score_checks=score_count)
