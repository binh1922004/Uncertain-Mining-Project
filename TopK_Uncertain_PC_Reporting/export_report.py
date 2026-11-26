#!/usr/bin/env python3
"""Publication-oriented tables with complete plan coverage and no imputed results.
Standalone: python export_report.py --out results
"""
import argparse, math, statistics as st
from pathlib import Path
import run as r

GROUP=('phase','dataset','scale','k','algorithm','variant','mode','cap','early','reps')
COUNTERS=('eligiblePairs','exactCandidateJoins','probabilityMultiplications','metadataJoins','metadataPairs','capChecks','capRejections','capRejectedPairs','pufpRejections','ppfStops','earlyRejections','tidPairMerges','tidRecordsMerged','newLists','resumedLists','reusedComplete','bitmapBlocks','arrayBlocks','sharedTidReuses','probabilityLookups','capPowerCacheHits','branchPrunes','heapOffers','maxDepth')
STATUSES=('completed','wall_timeout','oom','process_error','invalid_output','unsupported_fallback')
def csv(path,rows,fields):r.csvout(path,rows,fields)
def valid_stats(rs,field):
    vs=[x['metrics'].get('stats',{}).get(field) for x in rs]
    return vs if vs and all(type(v) is int and v>=0 for v in vs) else []
def numbers(d,tag,vs):
    d.update({tag+'_mean':st.mean(vs),tag+'_sd':st.stdev(vs) if len(vs)>1 else 0,tag+'_min':min(vs),tag+'_max':max(vs)})
def key(j):return tuple(j[f] for f in GROUP)
def ratio(a,b):return a/b if b>0 else None
def export(out):
    out=Path(out);dest=out/'publication';dest.mkdir(exist_ok=True);plan=r.load(out/'plan.json');receipts=r.receipts(out);jobs={j['id']:j for j in plan}
    if len(jobs)!=len(plan):raise ValueError('Duplicate planned job')
    if not set(receipts)<=set(jobs):raise ValueError('Unplanned terminal receipt')
    for jid,rec in receipts.items():
        if any(rec.get(k)!=v for k,v in jobs[jid].items()):raise ValueError('Receipt/job mismatch '+jid)
    groups={};coverage=[];flat=[]
    for j in plan:
        rec=receipts.get(j['id']);groups.setdefault(key(j),[]).append(j)
        status=rec['status'] if rec else 'pending'
        if status not in (*STATUSES,'pending'):raise ValueError('Unknown terminal status '+status)
        if rec is None:
            prep=out/'derived'/'preparation'/f"{j['dataset']}_p{j['scale']}_top{100 if j['scale']!=100 else j['k']}.json"
            if prep.exists() and r.load(prep)['status'] in ('reference_error','reference_timeout'):status='blocked_reference'
        row={**j,'status':status};coverage.append(row)
        if rec:
            row.update({k:rec.get(k) for k in ('verification','input_sha256','reference_sha256','patterns_sha256','wall_seconds','error','attempt_dir','exit_code')})
            m=rec.get('metrics',{});row.update({f:m.get(f) for f in ('backend','seconds','heap_mib','size')});row.update({f:rec.get(f) for f in ('jaccard','overlap_at_k')})
            # Raw diagnostic counters of failed or unsupported runs must not become evidence.
            if status=='completed':
                stats=m.get('stats',{})
                for c in COUNTERS:
                    v=stats.get(c)
                    if type(v) is int and v>=0:row[c]=v
                if j['algorithm']=='TUFP':row['tufp_conditional_rejections']=row.get('capRejections')
                if j['algorithm']=='TidNTUFP':row['tid_cap_rejections']=row.get('capRejections')
                if j['algorithm']=='ITUFP':row['itu_pufp_rejections']=row.get('pufpRejections')
        flat.append(row)
    group_rows=[]
    for k,js in groups.items():
        d=dict(zip(GROUP,k));d['scheduled']=len(js);good=[receipts[j['id']] for j in js if j['id'] in receipts and receipts[j['id']]['status']=='completed'];attempted=[receipts[j['id']] for j in js if j['id'] in receipts]
        d.update(attempted=len(attempted),pending=len(js)-len(attempted),**{s:sum(x['status']==s for x in attempted) for s in STATUSES})
        hashes={x.get('patterns_sha256') for x in good};same=bool(good) and len(hashes)==1 and None not in hashes
        d['repeat_identity']='yes' if same else 'no' if good else 'not_tested';d['verification']=';'.join(sorted({x['verification'] for x in good}));d['backends']=';'.join(sorted({x['metrics']['backend'] for x in good}))
        d['valid_group']=len(good)==d['scheduled']==d['reps'] and same and len({(x['input_sha256'],x['reference_sha256']) for x in good})==1
        if d['valid_group']:d.update(input_sha256=good[0]['input_sha256'],reference_sha256=good[0]['reference_sha256'])
        if same:
            d['jaccard']=good[0]['jaccard'];d['overlap_at_k']=good[0]['overlap_at_k']
        if d['valid_group'] and d['phase']!='E3':
            for metric,tag in [('seconds','time'),('heap_mib','heap')]:
                vs=[x['metrics'][metric] for x in good]
                if all(math.isfinite(v) and v>=0 for v in vs):numbers(d,tag,vs)
        if d['valid_group']:
            for c in COUNTERS:
                vs=valid_stats(good,c)
                if vs:
                    numbers(d,c,vs);d[c+'_identical']=len(set(vs))==1
            if d['algorithm']=='TidNTUFP' and d['backends']=='tenths-long' and d['cap'] and d.get('capChecks_identical') and d.get('capRejections_identical'):
                d['cap_rejection_rate']=ratio(d['capRejections_mean'],d['capChecks_mean'])
        group_rows.append(d)
    fields=list(GROUP)+['scheduled','attempted','pending',*STATUSES,'repeat_identity','valid_group','verification','backends','input_sha256','reference_sha256','jaccard','overlap_at_k']+[f'{tag}_{s}' for tag in ('time','heap') for s in ('mean','sd','min','max')]+[f'{c}_{s}' for c in COUNTERS for s in ('mean','sd','min','max','identical')]+['cap_rejection_rate']
    rawfields=list(plan[0])+['status','verification','input_sha256','reference_sha256','patterns_sha256','wall_seconds','error','attempt_dir','exit_code','backend','seconds','heap_mib','size','jaccard','overlap_at_k',*COUNTERS,'tufp_conditional_rejections','tid_cap_rejections','itu_pufp_rejections']
    csv(dest/'01_coverage_all_jobs.csv',coverage,list(plan[0])+['status']);csv(dest/'02_raw_runs_all.csv',flat,rawfields);csv(dest/'03_groups_all.csv',group_rows,fields)
    for suite,label in [('E1','04_E1_overall'),('E2','06_E2_ablation_and_storage'),('E4','08_E4_scalability')]:csv(dest/(label+'.csv'),[g for g in group_rows if g['phase']==suite],fields)
    # Pairwise exact comparison, no pooled or imputed times.
    e1={(g['dataset'],g['k'],g['algorithm']):g for g in group_rows if g['phase']=='E1'};pairs=[]
    for (d,k,alg),g in e1.items():
        if alg not in ('TUFP','ITUFP'):continue
        t=e1.get((d,k,'TidNTUFP'),{});v={'dataset':d,'k':k,'baseline':alg,'baseline_completed':g['completed'],'tid_completed':t.get('completed',0),'comparable':False}
        if g['valid_group'] and t.get('valid_group') and g.get('verification')==t.get('verification')=='exact_ordered' and (g['input_sha256'],g['reference_sha256'])==(t['input_sha256'],t['reference_sha256']):
            v.update(comparable=True,baseline_time=g['time_mean'],tid_time=t['time_mean'],speedup=ratio(g['time_mean'],t['time_mean']),baseline_heap=g['heap_mean'],tid_heap=t['heap_mean'],heap_reduction_percent=(1-t['heap_mean']/g['heap_mean'])*100 if g['heap_mean']>0 else None,nonoverlap_time=t['time_max']<g['time_min'],nonoverlap_heap=t['heap_max']<g['heap_min'])
        pairs.append(v)
    pairfields=['dataset','k','baseline','baseline_completed','tid_completed','comparable','baseline_time','tid_time','speedup','baseline_heap','tid_heap','heap_reduction_percent','nonoverlap_time','nonoverlap_heap'];csv(dest/'05_E1_exact_pairwise.csv',pairs,pairfields)
    ablations=[]; e2={(g['dataset'],g['variant']):g for g in group_rows if g['phase']=='E2'}
    comparisons=[('cap0_stop0','cap1_stop0','cap_without_stopping'),('cap0_stop1','cap1_stop1','cap_with_stopping'),('cap0_stop0','cap0_stop1','stopping_without_cap'),('cap1_stop0','cap1_stop1','stopping_with_cap'),('cap0_stop0','cap1_stop1','both'),('array','cap1_stop1','array_vs_adaptive'),('bitmap','cap1_stop1','bitmap_vs_adaptive'),('array','bitmap','array_vs_bitmap')]
    for dataset in sorted({d for d,v in e2}):
        for left,right,contrast in comparisons:
            if (dataset,left) not in e2 or (dataset,right) not in e2:continue
            a,b=e2[dataset,left],e2[dataset,right];row={'dataset':dataset,'k':a['k'],'contrast':contrast,'left':left,'right':right,'comparable':False}
            if a['valid_group'] and b['valid_group'] and a['backends']==b['backends']=='tenths-long' and (a['input_sha256'],a['reference_sha256'])==(b['input_sha256'],b['reference_sha256']):
                row.update(comparable=True,left_time=a['time_mean'],right_time=b['time_mean'],time_ratio=ratio(a['time_mean'],b['time_mean']),left_heap=a['heap_mean'],right_heap=b['heap_mean'])
                for c in ('probabilityMultiplications','exactCandidateJoins','eligiblePairs'):
                    if a.get(c+'_identical') and b.get(c+'_identical'):
                        x,y=a[c+'_mean'],b[c+'_mean'];row.update({c+'_left':x,c+'_right':y,c+'_reduction_percent':100*(1-y/x) if x>0 else None})
            ablations.append(row)
    csv(dest/'06b_E2_contrasts.csv',ablations,['dataset','k','contrast','left','right','comparable','left_time','right_time','time_ratio','left_heap','right_heap']+[c+s for c in ('probabilityMultiplications','exactCandidateJoins','eligiblePairs') for s in ('_left','_right','_reduction_percent')])
    e3=[v for v in flat if v['phase']=='E3'];csv(dest/'07_E3_workload.csv',e3,rawfields)
    caprows=[g for g in group_rows if g['phase']=='E1' and g['algorithm']=='TidNTUFP'];csv(dest/'09_E1_cap_filtering.csv',caprows,fields)
    # Measured input subset statistics, not hypothetical; use only valid receipts.
    scale_rows=[]
    for p in sorted((out/'derived').glob('*_p[0-9]*.json')):
        meta=r.load(p)
        if 'transactions' in meta:scale_rows.append({'dataset':p.stem.rsplit('_p',1)[0],**meta})
    inputstats=r.load(out/'input_statistics.json') if (out/'input_statistics.json').exists() else {}
    for d in sorted({g['dataset'] for g in group_rows if g['phase']=='E4' and g['scale']==100}):
        if d in inputstats:
            m=inputstats[d];scale_rows.append({**m,'scale':100,'source_sha256':m['input_sha256'],'reference_sha256':next((g['reference_sha256'] for g in group_rows if g['phase']=='E4' and g['dataset']==d and g['scale']==100 and 'reference_sha256' in g),None)})
    csv(dest/'10_E4_subset_provenance.csv',sorted(scale_rows,key=lambda x:(x['dataset'],x['scale'])),['dataset','source_sha256','seed','scale','transactions','items','item_occurrences','mean_length','density_percent','input_sha256','reference_sha256'])
    csv(dest/'11_dataset_statistics.csv',list(inputstats.values()),['dataset','transactions','items','item_occurrences','mean_length','density_percent','min_length','max_length','empty_transactions','probability_counts','input_sha256'])
    status={s:sum(v['status']==s for v in coverage) for s in (*STATUSES,'pending','blocked_reference')};status['scheduled']=len(plan)
    status['terminal']=len(receipts);status['all_scheduled_terminal']=len(receipts)==len(plan);status['all_measurements_valid']=all(g['valid_group'] for g in group_rows);status['invalid_groups']=sum(not g['valid_group'] for g in group_rows)
    status['phase_counts']={p:{'scheduled':sum(j['phase']==p for j in plan),**{s:sum(v['phase']==p and v['status']==s for v in coverage) for s in (*STATUSES,'pending','blocked_reference')}} for p in sorted({j['phase'] for j in plan})}
    smoke=all(j['phase']=='smoke' for j in plan)
    expected=r.plan(['E1','E2','E3','E4'],r.load(r.MANIFEST)['datasets']) if not smoke else plan
    expected_ids={j['id'] for j in expected};status['full_protocol_expected']=len(expected_ids);status['not_scheduled_from_full_protocol']=len(expected_ids-set(jobs));status['full_protocol_terminal']=expected_ids<=set(receipts);status['full_protocol_all_valid']=status['full_protocol_terminal'] and status['all_measurements_valid'];status['full_protocol_outcomes_ready']=status['full_protocol_terminal'] and not any(status[s] for s in ('process_error','invalid_output','unsupported_fallback')) and not any(g['repeat_identity']=='no' for g in group_rows)
    status['cap_totals']={'planned_groups':len(caprows),'eligible_groups':sum('cap_rejection_rate' in g and g['cap_rejection_rate'] is not None for g in caprows)}
    valid=[g for g in caprows if g.get('cap_rejection_rate') is not None];checks=sum(int(g['capChecks_mean']) for g in valid);rejects=sum(int(g['capRejections_mean']) for g in valid)
    status['cap_totals'].update(checks=checks,rejections=rejects,weighted_rejection_rate=ratio(rejects,checks))
    r.write(dest/'00_readiness.json',status);return status
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();print(export(a.out.resolve()))
