#!/usr/bin/env python3
"""Separate process for generation of derived independent references."""
import sys
from pathlib import Path
import run as r

def cached_input(data,scale,k,out):
    source,old,sha,rh=data; folder=out/'derived'
    if scale!=100:
        name=source.stem; f=folder/f'{name}_p{scale}.hui'; ref=folder/f'{name}_p{scale}_top100.json'; receipt=folder/f'{name}_p{scale}.json'
        meta=r.load(receipt)
        if (meta['source_sha256'],meta['scale'],meta['seed'])!=(sha,scale,20261126):raise ValueError('Subset provenance mismatch')
        if r.digest(f)!=meta['input_sha256'] or r.digest(ref)!=meta['reference_sha256'] or r.load(ref)['input_sha256']!=meta['input_sha256']:raise ValueError('Subset hash mismatch')
        return f,ref,meta['input_sha256'],meta['reference_sha256']
    ref=folder/f'{source.stem}_top{k}.json'; receipt=folder/f'{source.stem}_top{k}_receipt.json'; meta=r.load(receipt)
    if (meta['input_sha256'],meta['old_reference_sha256'],meta['k'])!=(sha,rh,k) or r.digest(ref)!=meta['reference_sha256']:raise ValueError('Extended reference provenance mismatch')
    new=r.read_ranked(ref);oldrows=r.read_ranked(old)
    if len(new)!=k or new[:len(oldrows)]!=oldrows or r.load(ref)['input_sha256']!=sha:raise ValueError('Extended reference does not preserve historical prefix')
    return source,ref,sha,meta['reference_sha256']

def main():
    source,old,sha,rh,scale,k,out=sys.argv[1:];source,old,out=Path(source),Path(old),Path(out);scale,k=int(scale),int(k); data=(source,old,sha,rh)
    if r.digest(source)!=sha or r.digest(old)!=rh:raise ValueError('Source/reference hash mismatch')
    if scale!=100:r.subset(data,scale,out);return
    folder=out/'derived';folder.mkdir(exist_ok=True);ref=folder/f'{source.stem}_top{k}.json';receipt=folder/f'{source.stem}_top{k}_receipt.json'
    if receipt.exists():cached_input(data,scale,k,out);return
    oracle=r.import_ref(); matrix,ids,stats=oracle.inspect_load(source);rows,search=oracle.exact_topk(matrix,ids,k);historical=r.read_ranked(old)
    if len(rows)!=k or rows[:len(historical)]!=historical:raise ValueError('New exact reference does not match historical prefix')
    r.write(ref,{'input_sha256':sha,'ranked':r.ref_records(rows),'search_stats':search,'dataset_stats':stats})
    r.write(receipt,{'input_sha256':sha,'old_reference_sha256':rh,'k':k,'reference_sha256':r.digest(ref)})
    cached_input(data,scale,k,out)
if __name__=='__main__':main()
