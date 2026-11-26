#!/usr/bin/env python3
"""Sequential fresh-JVM experiments, resumable receipts, exact/reference checks."""
from pathlib import Path
from fractions import Fraction as F
import argparse,csv,hashlib,json,math,os,platform,random,re,shutil,statistics,subprocess,sys,time,uuid
ROOT=Path(__file__).resolve().parent
JAR=ROOT/'topk-benchmark.jar'
MANIFEST=ROOT/'provenance/input_manifest.json'
METHODS=['NTUFP','TUFP','ITUFP','TidNTUFP']
EXACT=['TUFP','ITUFP','TidNTUFP']
SCALING=['connect','T10I4D100K']
ABLATION=['chess','connect','T10I4D100K']

def digest(p):
    h=hashlib.sha256()
    with Path(p).open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
def load(p):return json.loads(Path(p).read_text(encoding='utf-8'))
def write(p,o):
    p=Path(p); t=p.with_suffix(p.suffix+'.tmp');t.write_text(json.dumps(o,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf-8');os.replace(t,p)
def rank_key(x):return (-x[1],len(x[0]),x[0])
def read_ranked(p):
    rows=[(tuple(x['items']),F(x['numerator'],x['denominator'])) for x in load(p)['ranked']]
    validate_rank(rows);return rows
def validate_rank(rows):
    assert all(x and x==tuple(sorted(set(x))) and s>0 for x,s in rows),'invalid itemset or score'
    assert len({x for x,s in rows})==len(rows),'duplicate patterns'
    assert rows==sorted(rows,key=rank_key),'wrong ranking order'
def patterns(p):
    rows=[]
    with Path(p).open(encoding='utf-8') as f:
        for line in f:
            ids,score=line.strip().split(' : '); rows.append((tuple(map(int,ids.split())),F(score)))
    validate_rank(rows);return rows
def ref_records(rows):return [{'items':list(x),'numerator':s.numerator,'denominator':s.denominator} for x,s in rows]
def import_ref():
    sys.path.insert(0,str(ROOT/'reference'))
    import reference
    return reference

def plan(suites,chosen,smoke=False):
    records=[]
    def add(phase,d,k,a,rep,mode='adaptive',early=True,cap=True,scale=100,variant='default'):
        for r in range(1,rep+1):
            key=f'{phase}__{d}__p{scale}__k{k}__{a}__{variant}__r{r}'
            records.append(dict(id=key,phase=phase,dataset=d,k=k,algorithm=a,rep=r,reps=rep,mode=mode,early=early,cap=cap,scale=scale,variant=variant))
    if smoke:
        for a in METHODS:add('smoke','smoke',6,a,1)
        for cap in (False,True):
            for early in (False,True):add('smoke','smoke',6,'TidNTUFP',1,early=early,cap=cap,variant=f'cap{int(cap)}_stop{int(early)}')
        for m in ('array','bitmap'):add('smoke','smoke',6,'TidNTUFP',1,mode=m,variant=m)
        return records
    # Rotate algorithm order at each configuration to reduce systematic order effects.
    if 'E1' in suites:
        for idx,d in enumerate(chosen):
            for j,k in enumerate(sorted(set(d['k']) | ({900} if d['name']=='mushrooms' else set()))):
                for method in METHODS:add('E1',d['name'],k,method,3)
    names={d['name'] for d in chosen}
    if 'E2' in suites:
        for d in ABLATION:
            if d not in names:continue
            for cap in (False,True):
                for early in (False,True):add('E2',d,300,'TidNTUFP',3,early=early,cap=cap,variant=f'cap{int(cap)}_stop{int(early)}')
        for d in SCALING:
            if d in names:
                for mode in ('array','bitmap'):add('E2',d,300,'TidNTUFP',3,mode=mode,variant=mode)
    if 'E3' in suites:
        for d in chosen:
            for k in (100,300):
                for a in EXACT:add('E3',d['name'],k,a,1)
    if 'E4' in suites:
        for d in SCALING:
            if d not in names:continue
            for size in (25,50,75,100):
                for a in EXACT:add('E4',d,100,a,3,scale=size)
    # Interleave repetitions within each dataset/k/scale block; rotate the
    # variant/algorithm order by block and repetition. No change to job IDs.
    groups={}
    for rec in records:
        groups.setdefault((rec['phase'],rec['dataset'],rec['k'],rec['scale']),[]).append(rec)
    ordered=[]
    for i,rs in enumerate(groups.values()):
        for rep in sorted({r['rep'] for r in rs}):
            block=[r for r in rs if r['rep']==rep]; shift=(i+rep-1)%len(block)
            ordered.extend(block[shift:]+block[:shift])
    return ordered

def validate_build():
    b=load(ROOT/'build_manifest.json');assert digest(JAR)==b['jar_sha256'],'JAR hash mismatch; run build.py'
    actual={str(p.relative_to(ROOT)):digest(p) for p in sorted((ROOT/'code/src').rglob('*.java'))}
    assert actual==b['sources'],'source files differ from JAR manifest; run build.py'
    return b['jar_sha256']

def environment(a):
    ver=subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True).strip()
    cpu=platform.processor()
    if Path('/proc/cpuinfo').is_file():
        cpu=next((l.split(':',1)[1].strip() for l in Path('/proc/cpuinfo').read_text().splitlines() if l.startswith('model name')),cpu)
    elif platform.system()=='Darwin':
        try:cpu=subprocess.check_output(['sysctl','-n','machdep.cpu.brand_string'],text=True).strip()
        except Exception:pass
    return dict(platform=platform.platform(),machine=platform.machine(),cpu=cpu,logical_cpus=os.cpu_count(),java=ver,java_path=shutil.which('java'),python=platform.python_version(),jar_sha256=validate_build(),runner_sha256=digest(Path(__file__)),reference_code_sha256=digest(ROOT/'reference/reference.py'),manifest_sha256=digest(MANIFEST),report_sha256=digest(ROOT/'export_report.py'),reference_worker_sha256=digest(ROOT/'prepare_reference.py'),reference_deadline_s=a.reference_deadline,initial_heap=a.xms,max_heap=a.xmx,deadline_s=a.deadline,sample_ms=5,warmup=0,smoke=a.smoke)

def full_inputs(chosen,data):
    result={}
    for d in chosen:
        name=d['name']; f=data/(name+'.hui'); ref=ROOT/'reference'/Path(d['reference']).name
        if not f.is_file():raise FileNotFoundError(f'Missing {f}. Copy original uncertain .hui file; do not regenerate probabilities.')
        sha=digest(f)
        if sha!=d['input_sha256']:raise ValueError(f'Input SHA-256 mismatch: {f}\nexpected {d["input_sha256"]}\nactual   {sha}')
        if digest(ref)!=d['reference_sha256']:raise ValueError(f'Reference SHA-256 mismatch: {ref}')
        meta=load(ref);rr=read_ranked(ref)
        if meta['input_sha256']!=sha or len(rr)<max(d['k']):raise ValueError('Reference metadata mismatch')
        result[(name,100)]=(f,ref,sha,digest(ref))
    return result

def save_input_stats(out,inputs):
    """Record validated input statistics without allocating a dense probability matrix."""
    from collections import Counter
    path=out/'input_statistics.json'; allstats=load(path) if path.exists() else {}
    for (name,scale),(source,ref,sha,rh) in inputs.items():
        if name in allstats:
            if allstats[name]['input_sha256']!=sha:raise ValueError('Input statistics provenance mismatch')
            continue
        with source.open(encoding='utf-8') as f:
            n,m=map(int,f.readline().split());counts=Counter();items=set();occ=0;lengths=[0]*n
            for line in f:
                t,i,p=line.split();t,i=int(t),int(i)
                if not 1<=t<=n:raise ValueError('TID outside header')
                lengths[t-1]+=1;items.add(i);counts[p]+=1;occ+=1
        if len(items)!=m:raise ValueError('Input item count mismatch')
        allstats[name]={'dataset':name,'transactions':n,'items':m,'item_occurrences':occ,'mean_length':occ/n if n else 0,'density_percent':100*occ/n/m if n and m else 0,'min_length':min(lengths,default=0),'max_length':max(lengths,default=0),'empty_transactions':sum(v==0 for v in lengths),'probability_counts':dict(counts),'input_sha256':sha}
    write(path,allstats)

def smoke_inputs(out):
    f=ROOT/'tests/smoke.hui'; ref=out/'smoke_reference.json'; reference=import_ref(); matrix,ids,stats=reference.inspect_load(f); rows,_=reference.exact_topk(matrix,ids,6)
    o={'input_sha256':digest(f),'ranked':ref_records(rows)}
    if ref.exists():assert load(ref)==o,'smoke reference changed'
    else:write(ref,o)
    return {('smoke',100):(f,ref,digest(f),digest(ref))}

def subset(data,scale,out):
    source,_,sha,_=data; name=source.stem; folder=out/'derived';folder.mkdir(exist_ok=True)
    file=folder/f'{name}_p{scale}.hui'; ref=folder/f'{name}_p{scale}_top100.json'; receipt=folder/f'{name}_p{scale}.json'
    # Same permutation for all sizes; fixed seed and floor sample size. Preserve original probabilities.
    if receipt.exists():
        meta=load(receipt)
        assert meta['source_sha256']==sha and meta['scale']==scale and meta['seed']==20261126 and digest(file)==meta['input_sha256'] and digest(ref)==meta['reference_sha256'],'Derived input/reference mismatch'
        assert load(ref)['input_sha256']==meta['input_sha256']
        return file,ref,meta['input_sha256'],meta['reference_sha256']
    with source.open(encoding='utf-8') as f:n,m=map(int,f.readline().split())
    size=n*scale//100; seq=list(range(1,n+1));random.Random(20261126).shuffle(seq)
    selected=sorted(seq[:size]);index={old:new for new,old in enumerate(selected,1)};items=set();body=file.with_suffix('.body')
    with source.open(encoding='utf-8') as f, body.open('w',encoding='utf-8',newline='\n') as w:
        next(f)
        for line in f:
            tid,item,p=line.split(); t=index.get(int(tid))
            if t is not None:items.add(int(item));w.write(f'{t} {item} {p}\n')
    with file.open('w',encoding='utf-8',newline='\n') as w,body.open(encoding='utf-8') as r:
        w.write(f'{size} {len(items)}\n');shutil.copyfileobj(r,w)
    body.unlink();reference=import_ref();print(f'Generating independent exact reference for {name} {scale}% ({size} transactions)',flush=True)
    matrix,ids,stats=reference.inspect_load(file);rr,search=reference.exact_topk(matrix,ids,100)
    write(ref,{'input_sha256':digest(file),'ranked':ref_records(rr),'search_stats':search,'dataset_stats':stats})
    meta={'source_sha256':sha,'seed':20261126,'scale':scale,'transactions':size,'items':len(items),'item_occurrences':stats['records'],'mean_length':stats['mean_length'],'density_percent':stats['density_percent'],'input_sha256':digest(file),'reference_sha256':digest(ref)};write(receipt,meta)
    return file,ref,meta['input_sha256'],meta['reference_sha256']

def prepared_input(data,scale,k,out,a):
    source,ref,sha,rh=data
    if scale==100 and k<=len(read_ranked(ref)):return data
    folder=out/'derived';folder.mkdir(exist_ok=True)
    label=f'{source.stem}_p{scale}_top{100 if scale!=100 else k}'
    logs=folder/'preparation';logs.mkdir(exist_ok=True)
    log=logs/(label+'.log'); status=logs/(label+'.json')
    cmd=[sys.executable,str(ROOT/'prepare_reference.py'),str(source),str(ref),sha,rh,str(scale),str(k),str(out)]
    start=time.monotonic(); result={'dataset':source.stem,'scale':scale,'k':k,'status':'reference_error','command':cmd}
    try:
        with log.open('a',encoding='utf-8') as lf:
            proc=subprocess.Popen(cmd,stdout=lf,stderr=subprocess.STDOUT)
            try:proc.wait(timeout=a.reference_deadline)
            except subprocess.TimeoutExpired:
                proc.kill();proc.wait();result['status']='reference_timeout'
            except KeyboardInterrupt:
                proc.kill();proc.wait();result['status']='interrupted';raise
        result['exit_code']=proc.returncode
        if proc.returncode==0:
            from prepare_reference import cached_input
            value=cached_input(data,scale,k,out);result['status']='prepared';return value
        raise RuntimeError(f'{label}: {result["status"]}; inspect {log}')
    finally:
        result['wall_seconds']=time.monotonic()-start;write(status,result)

def check_output(job,path,source,ref,metrics):
    rr=read_ranked(ref)[:job['k']]; actual=patterns(path)
    if metrics['size']!=len(actual) or len(actual)!=len(rr):raise ValueError('Wrong top-k cardinality')
    if job['algorithm']!='NTUFP':
        if actual!=rr:raise ValueError('Exact ordered output does not match independent reference')
        return {'verification':'exact_ordered','jaccard':1.0,'overlap_at_k':1.0}
    reference=import_ref();matrix,ids,_=reference.inspect_load(source)
    scores=reference.surrogate_scores(matrix,ids,[x for x,s in actual])
    if any(scores[x]!=s for x,s in actual):raise ValueError('NTUFP returned incorrect ExpSuppCap score')
    A={x for x,s in actual};B={x for x,s in rr};inter=len(A&B);union=len(A|B)
    return {'verification':'surrogate_scores_only','jaccard':inter/union if union else 1.0,'overlap_at_k':inter/len(rr) if rr else 1.0}

def execute(job,source,ref,input_hash,ref_hash,a,folder):
    folder.mkdir(parents=True,exist_ok=False);out=folder/'patterns.txt';log=folder/'stdout.log';err=folder/'stderr.log';p=None
    cmd=['java',f'-Xms{a.xms}',f'-Xmx{a.xmx}','-jar',str(JAR),job['algorithm'],str(source),str(job['k']),str(out),job['mode'],str(job['early']).lower(),str(job['cap']).lower(),'5']
    rec={**job,'input_sha256':input_hash,'reference_sha256':ref_hash,'command':cmd,'attempt_dir':str(folder.relative_to(a.out)),'status':'process_error','started_utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
    start=time.monotonic();interrupted=False
    def terminate():
        if p is not None and p.poll() is None:
            p.terminate()
            try:p.wait(timeout=5)
            except subprocess.TimeoutExpired:p.kill();p.wait()
    try:
        with log.open('w',encoding='utf-8') as so,err.open('w',encoding='utf-8') as se:
            p=subprocess.Popen(cmd,stdout=so,stderr=se)
            try:p.wait(timeout=max(0.001,a.deadline-(time.monotonic()-start)))
            except subprocess.TimeoutExpired:
                if p.poll() is None:rec['status']='wall_timeout';terminate()
    except KeyboardInterrupt:rec['status']='interrupted';terminate();interrupted=True
    except OSError as e:rec['error']=str(e);terminate()
    rec['wall_seconds']=time.monotonic()-start;rec['exit_code']=p.returncode if p is not None else None
    if rec['status'] not in ('wall_timeout','interrupted'):
        stdout=log.read_text(encoding='utf-8',errors='replace') if log.exists() else '';stderr=err.read_text(encoding='utf-8',errors='replace') if err.exists() else ''
        if p is not None and p.returncode==0:
            try:
                lines=[l[len('RESULT_JSON '):] for l in stdout.splitlines() if l.startswith('RESULT_JSON ')]
                if len(lines)!=1 or stdout.splitlines().count('RESULT_END')!=1:raise ValueError('Incomplete log')
                metrics=json.loads(lines[0]);rec['metrics']=metrics
                if metrics['algorithm']!=job['algorithm'] or metrics['k']!=job['k']:raise ValueError('Run identity mismatch')
                if not all(isinstance(metrics[v],(float,int)) and math.isfinite(metrics[v]) and metrics[v]>=0 for v in ('seconds','heap_mib')):raise ValueError('Invalid metrics')
                if not isinstance(metrics['size'],int) or not 0<=metrics['size']<=job['k']:raise ValueError('Invalid size')
                stats=metrics.get('stats',{})
                if job['algorithm'] in EXACT:
                    for field in ('eligiblePairs','exactCandidateJoins','probabilityMultiplications'):
                        if type(stats.get(field)) is not int or stats[field]<0:raise ValueError('Missing/invalid counter '+field)
                if job['algorithm']=='TidNTUFP' and metrics['backend']!='original-decimal-fallback':
                    if (stats.get('tidMode'),stats.get('capEnabled'),stats.get('earlyEnabled'))!=(job['mode'],job['cap'],job['early']):raise ValueError('Ablation flags mismatch')
                rec.update(check_output(job,out,source,ref,metrics));rec['patterns_sha256']=digest(out)
                # A decimal fallback does not honor the ablation flags or optimized counters.
                if metrics['backend']=='original-decimal-fallback' and job['algorithm']=='TidNTUFP' and job['phase'] in ('E2','E3'):rec['status']='unsupported_fallback'
                else:rec['status']='completed'
            except Exception as e:rec['status']='invalid_output';rec['error']=str(e)
            except KeyboardInterrupt:rec['status']='interrupted';interrupted=True
        elif 'OutOfMemoryError' in stdout+stderr or 'Could not reserve enough space' in stderr:rec['status']='oom'
    rec['ended_utc']=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime());rec['stdout_sha256']=digest(log) if log.is_file() else None;rec['stderr_sha256']=digest(err) if err.is_file() else None
    write(folder/'receipt.json',rec)
    if interrupted:raise KeyboardInterrupt
    return rec

def receipts(out):
    result={}
    for f in sorted((out/'runs').glob('*/*/receipt.json')):
        r=load(f)
        if r['status']=='interrupted':continue
        if r['id'] in result:raise ValueError('Multiple terminal receipts for '+r['id'])
        folder=f.parent
        for name,key in [('stdout.log','stdout_sha256'),('stderr.log','stderr_sha256'),('patterns.txt','patterns_sha256')]:
            if r.get(key) is not None and digest(folder/name)!=r[key]:raise ValueError('Receipt file hash mismatch '+str(folder/name))
        result[r['id']]=r
    return result

def csvout(path,rows,fields):
    tmp=path.with_suffix('.tmp')
    with tmp.open('w',encoding='utf-8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=fields,extrasaction='ignore');w.writeheader();w.writerows(rows)
    os.replace(tmp,path)
def summary(out,all_runs):
    values=list(all_runs.values());write(out/'runs.json',values)
    group={};flat=[]
    for r in values:
        key=(r['phase'],r['dataset'],r['scale'],r['k'],r['algorithm'],r['variant'],r['mode'],r['cap'],r['early'],r['reps']);group.setdefault(key,[]).append(r)
        m=r.get('metrics',{});flat.append({**r,**{k:v for k,v in m.items() if k!='stats'},**m.get('stats',{})})
    rows=[]
    for key,rs in sorted(group.items()):
        phase,d,scale,k,alg,variant,mode,cap,early,reps=key;good=[r for r in rs if r['status']=='completed'];s=dict(zip(('phase','dataset','scale','k','algorithm','variant','mode','cap','early','scheduled'),key));s.update(attempted=len(rs),completed=len(good))
        for st in ('wall_timeout','oom','process_error','invalid_output','unsupported_fallback'):s[st]=sum(r['status']==st for r in rs)
        hashes={r['patterns_sha256'] for r in good};s['repeat_identity']=len(hashes)<=1
        # All selected runs use a single canonical writer per algorithm; hashes ensure repeat output identity.
        if len(good)==reps and len(hashes)==1 and phase!='E3':
            for v,tag in [('seconds','time'),('heap_mib','heap')]:
                vs=[r['metrics'][v] for r in good];s.update({tag+'_mean':statistics.mean(vs),tag+'_sd':statistics.stdev(vs) if len(vs)>1 else 0,tag+'_min':min(vs),tag+'_max':max(vs)})
        if good and len(hashes)==1:s.update(jaccard=good[0]['jaccard'],overlap_at_k=good[0]['overlap_at_k'],backends=';'.join(sorted({r['metrics']['backend'] for r in good})))
        rows.append(s)
    fields=['phase','dataset','scale','k','algorithm','variant','mode','cap','early','scheduled','attempted','completed','wall_timeout','oom','process_error','invalid_output','unsupported_fallback','repeat_identity','backends','time_mean','time_sd','time_min','time_max','heap_mean','heap_sd','heap_min','heap_max','jaccard','overlap_at_k']
    csvout(out/'summary.csv',rows,fields);write(out/'summary.json',rows)
    cols=['id','phase','dataset','scale','k','algorithm','variant','rep','mode','cap','early','status','backend','seconds','heap_mib','wall_seconds','size','verification','jaccard','overlap_at_k','eligiblePairs','exactCandidateJoins','probabilityMultiplications','capChecks','capRejections','pufpRejections','ppfStops','earlyRejections','metadataJoins','metadataPairs','tidPairMerges','tidRecordsMerged','newLists','resumedLists','reusedComplete','bitmapBlocks','arrayBlocks','branchPrunes','heapOffers','maxDepth','error','attempt_dir']
    csvout(out/'runs.csv',flat,cols);csvout(out/'work_counters.csv',[r for r in flat if r['phase']=='E3'],cols)
    return rows

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--suite',nargs='+',choices=['all','E1','E2','E3','E4'],default=['E1'])
    p.add_argument('--data-dir',type=Path,default=ROOT/'data');p.add_argument('--out',type=Path,default=ROOT/'results');p.add_argument('--datasets',nargs='+');p.add_argument('--deadline',type=float,default=900);p.add_argument('--reference-deadline',type=float,default=3600);p.add_argument('--xms',default='128m');p.add_argument('--xmx',default='4g');p.add_argument('--smoke',action='store_true');p.add_argument('--dry-run',action='store_true');a=p.parse_args();a.out=a.out.resolve();a.data_dir=a.data_dir.resolve()
    if any(v<=0 or not math.isfinite(v) for v in (a.deadline,a.reference_deadline)):p.error('deadlines must be positive')
    if not all(re.fullmatch(r'[1-9]\d*[mMgG]',s) for s in (a.xms,a.xmx)):p.error('Use heap sizes like 128m or 4g')
    m=load(MANIFEST);all_names={d['name'] for d in m['datasets']}
    if a.datasets and not set(a.datasets)<=all_names:p.error('Dataset names: '+', '.join(sorted(all_names)))
    chosen=[d for d in m['datasets'] if not a.datasets or d['name'] in a.datasets]
    suites=['E1','E2','E3','E4'] if 'all' in a.suite else a.suite;jobs=plan(suites,chosen,a.smoke)
    if not jobs:p.error('No planned runs for selected datasets')
    print('Plan:',{x:sum(j['phase']==x for j in jobs) for x in sorted({j['phase'] for j in jobs})},'total',len(jobs),flush=True)
    if a.dry_run:return
    import_ref() # fail early if numpy missing
    env=environment(a);a.out.mkdir(parents=True,exist_ok=True);(a.out/'runs').mkdir(exist_ok=True)
    lock=a.out/'RUNNING.lock'
    try:fd=os.open(lock,os.O_CREAT|os.O_EXCL|os.O_WRONLY)
    except FileExistsError:p.error(f'{lock} exists. Check no runner is active before removing a stale lock.')
    try:
        with os.fdopen(fd,'w') as f:f.write(str(os.getpid()))
        ep=a.out/'environment.json'
        if ep.exists() and load(ep)!=env:raise ValueError('Environment/build/protocol changed. Use a new --out directory; do not mix measurements.')
        if not ep.exists():write(ep,env)
        # Store a cumulative plan to expose pending configurations on resume.
        pp=a.out/'plan.json';old={j['id']:j for j in load(pp)} if pp.exists() else {}
        for j in jobs:
            if j['id'] in old and old[j['id']]!=j:raise ValueError('Plan changed')
            old[j['id']]=j
        write(pp,list(old.values()))
        existing=receipts(a.out)
        from export_report import export
        prepared={};blocked={}
        summary(a.out,existing);export(a.out)
        required={j['dataset'] for j in jobs};inputs=smoke_inputs(a.out) if a.smoke else full_inputs([d for d in chosen if d['name'] in required],a.data_dir)
        save_input_stats(a.out,inputs)
        for j in jobs:
            base=inputs[(j['dataset'],100)]
            scale=j['scale']; k=j['k']; key=(j['dataset'],scale,k if scale==100 else 100)
            if key in blocked:continue
            if key not in prepared:
                try:prepared[key]=prepared_input(base,scale,k,a.out,a)
                except (RuntimeError,ValueError,OSError) as exc:
                    blocked[key]=str(exc);print('Reference preparation blocked:',exc,file=sys.stderr);export(a.out);continue
            f,ref,ih,rh=prepared[key]
            if j['id'] in existing:
                r=existing[j['id']];assert r['input_sha256']==ih and r['reference_sha256']==rh,'Resume input/reference mismatch';continue
            folder=a.out/'runs'/j['id']/('attempt_'+uuid.uuid4().hex[:12]);folder.parent.mkdir(parents=True,exist_ok=True)
            print(f'[{j["phase"]}] {j["dataset"]} {j["scale"]}% k={j["k"]} {j["algorithm"]} {j["variant"]} r{j["rep"]}',flush=True)
            r=execute(j,f,ref,ih,rh,a,folder);existing[j['id']]=r;summary(a.out,existing);export(a.out);print('  '+r['status'],flush=True)
        rows=summary(a.out,existing);export(a.out)
        if blocked:print('WARNING: reference preparation blocked some jobs. Rerun after fixing resource issue.',file=sys.stderr);return 2
        failures=[r for r in existing.values() if r['status'] in ('invalid_output','process_error','unsupported_fallback')]
        if failures or any(not r['repeat_identity'] for r in rows):print('WARNING: invalid/process/fallback or repeat mismatch. Inspect runs.csv before using results.',file=sys.stderr);return 2
        print('Results:',a.out/'summary.csv');return 0
    except KeyboardInterrupt:
        summary(a.out,receipts(a.out));
        from export_report import export
        export(a.out);print('Interrupted; rerun the same command to resume.',file=sys.stderr);return 130
    finally:lock.unlink(missing_ok=True)
if __name__=='__main__':sys.exit(main())
