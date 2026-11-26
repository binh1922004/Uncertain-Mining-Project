#!/usr/bin/env python3
"""Independent exact-rational regression suite. Run from any working directory."""
from pathlib import Path
from fractions import Fraction
from itertools import product
from collections import Counter
import hashlib, json, random, subprocess, sys
from oracle import oracle, line

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "verification"
REPORT.mkdir(exist_ok=True)
CLASSES = ROOT / "build" / "classes"


def command(args, **kwargs):
    return subprocess.run(args, capture_output=True, text=True, timeout=120, **kwargs)


def make_cases():
    cases = json.loads((ROOT / "audit_cases.json").read_text())
    for c in cases: c["group"] = "previous_audit"
    # All 3^6 databases: 2 transactions x 3 possible items, each absent/.5/1.
    for index, values in enumerate(product([None, ".5", "1"], repeat=6)):
        rows = [(slot // 3 + 1, slot % 3 + 1, p) for slot, p in enumerate(values) if p is not None]
        for k in (1, 3, 7):
            cases.append(dict(id=f"exhaustive_{index}_k{k}", group="exhaustive", rows=rows, k=k, n=2, m=len({r[1] for r in rows})))
    precision = [
        ("underflow_400", [(1, i, "1e-400") for i in (1,2,3)]),
        ("long_decimal", [(1, -1, ".12345678901234567890123456789"), (1, 20, ".98765432109876543210987654321"), (2,-1,".12345678901234567890123456789")]),
        ("scientific", [(2,10,"1E-12"),(1,20,"5E-11"),(1,10,"2E-12"),(2,20,"3E-13")]),
        ("signed_ids", [(1,-2147483648,".9"),(1,0,".8"),(1,2147483647,".7")]),
        ("equal_probabilities", [(t,i,".3333333333333333333333333333333333333333") for t in (1,2) for i in (1,2,3,4)]),
        ("mixed_magnitudes", [(1,1,"1"),(1,2,"1e-100"),(1,3,"1e-200"),(1,4,"1e-400")]),
        ("tiny_with_ties", [(t,i,"1e-30") for t in (1,2) for i in (10,20,30)])]
    for name, rows in precision:
        cases.append(dict(id=name,group="precision",rows=rows,k=100,n=max(r[0] for r in rows),m=len({r[1] for r in rows})))
    rng = random.Random(20261112)
    ids = [-11, 0, 10, 40, 100, 1000, 2147483647]
    for index in range(20):
        rows = [(t,i,rng.choice([".01", ".17", ".33", ".5", ".83", "1"])) for t in range(1,9) for i in ids if rng.random() < .65]
        present={r[1] for r in rows}
        rows += [(1,i,".5") for i in ids if i not in present]
        rng.shuffle(rows)
        for k in (1,7,14,127):
            cases.append(dict(id=f"shuffled_{index}_k{k}",group="shuffled",rows=rows,k=k,n=8,m=7))
    return cases


def oracle_tests():
    cases=make_cases()
    lines=[line(c,a) for c in cases for a in ("w","h","t")]
    result=command(["java","-Xmx512m","-cp",str(CLASSES),"AuditRunner"],input="\n".join(lines)+"\n")
    if result.returncode: raise RuntimeError(result.stderr)
    outputs=[json.loads(s) for s in result.stdout.splitlines()]
    assert len(outputs)==len(lines),(len(outputs),len(lines))
    (REPORT / "oracle_outputs.jsonl").write_text(result.stdout)
    (REPORT / "cases.json").write_text(json.dumps(cases,separators=(",",":")))
    counts=Counter(); failures=[]; cap_rejections=0; merged_pairs=0; cap_rejected_pairs=0
    for index,c in enumerate(cases):
        exact,bound,top_exact,top_bound=oracle(c["rows"],c["k"])
        assert all(bound[x]>=s for x,s in exact.items()),("invalid cap in independent oracle",c)
        for offset,algorithm in enumerate(("w","h","t")):
            output=outputs[3*index+offset];counts[c["group"]+"_runs"]+=1;counts[algorithm+"_runs"]+=1
            if "error" in output:
                failures.append(dict(case=c["id"],algorithm=algorithm,error=output));continue
            raw=output["output"]["heap"]
            observed={tuple(x["items"]):Fraction(x["score"]) for x in raw}
            scores,expected=(bound,top_bound) if algorithm=="w" else (exact,top_exact)
            correct=len(observed)==len(raw) and set(observed)==set(expected) and all(observed[x]==scores[x] for x in observed)
            if algorithm in ("w","h"):correct &= output["output"]["return_size"]==len(expected)
            if not correct:failures.append(dict(case=c["id"],algorithm=algorithm,expected=[list(x) for x in expected],actual=output))
            stats=output["output"].get("stats",{})
            if algorithm=="h":
                assert stats["metadataPairs"]==stats["capRejectedPairs"]+stats["tidPairMerges"],("cap-before-TID accounting",c)
                cap_rejections+=stats["capRejections"];merged_pairs+=stats["tidPairMerges"];cap_rejected_pairs+=stats["capRejectedPairs"]
    assert cap_rejections>0 and cap_rejected_pairs>0,"Suite must exercise an actual cap rejection"
    (REPORT / "failures.json").write_text(json.dumps(failures,indent=2))
    assert not failures,failures[:3]
    return dict(queries=len(cases),miner_runs=len(lines),failures=0,groups=dict(counts),hybrid_cap_rejections=cap_rejections,hybrid_rejected_metadata_pairs=cap_rejected_pairs,hybrid_materialized_pairs=merged_pairs)


def io_tests():
    folder=ROOT/"build"/"input_tests";folder.mkdir(exist_ok=True)
    valid={
        "standard":"1 2\n1 1 .9\n1 2 .8\n",
        "whitespace":"  1   2  \n1\t1\t.9\n1  2  .8\n",
        "comments":"# test\n\n1 2\n# records\n1 1 .9\n1 2 .8\n",
        "unsorted":"2 2\n2 20 .8\n1 10 .9\n1 20 .8\n2 10 .9\n",
        "tiny":"1 2\n1 10 1e-400\n1 20 0.12345678901234567890123456789\n",
        "empty":"0 0\n",
        "empty_transactions":"9 0\n",
        "gapped_tid":"8 1\n8 -1 .5\n"}
    invalid={
        "duplicate":"1 1\n1 1 .9\n1 1 .9\n",
        "large_probability":"1 1\n1 1 2\n",
        "negative_probability":"1 1\n1 1 -.1\n",
        "zero_probability":"1 1\n1 1 0\n",
        "infinity":"1 1\n1 1 Infinity\n",
        "nan":"1 1\n1 1 NaN\n",
        "negative_tid":"1 1\n-1 1 .9\n",
        "zero_tid":"1 1\n0 1 .9\n",
        "tid_past_header":"1 1\n2 1 .9\n",
        "wrong_item_count":"1 2\n1 1 .9\n",
        "missing_header":"",
        "header_arity":"1 2 3\n",
        "negative_header":"-1 2\n",
        "record_arity":"1 1\n1 1 .5 extra\n",
        "nonnumeric":"1 1\n1 1 abc\n"}
    records=[]
    for good, entries in ((True,valid),(False,invalid)):
        for name,content in entries.items():
            file=folder/(name+".hui");file.write_text(content)
            result=command(["java","-Xmx256m","-cp",str(CLASSES),"AuditRunner","file",str(file),"10"])
            if good:
                assert result.returncode==0,(name,result.stderr)
                parsed=[json.loads(s) for s in result.stdout.splitlines()]
                assert len(parsed)==4 and all("error" not in row for row in parsed),(name,parsed)
                # Verify all parser-produced outputs against original decimal strings.
                rows=[tuple(line.split()) for line in content.splitlines() if line.strip() and not line.strip().startswith("#")][1:]
                rows=[(int(t),int(i),p) for t,i,p in rows]
                exact,bound,te,tb=oracle(rows,10)
                for algorithm,entry in zip(("w","h","t"),parsed[1:]):
                    observed={tuple(x["items"]):Fraction(x["score"]) for x in entry["output"]["heap"]}
                    scores,expected=(bound,tb) if algorithm=="w" else (exact,te)
                    assert observed=={x:scores[x] for x in expected},(name,algorithm)
            else: assert result.returncode!=0,("accepted invalid input",name)
            records.append(dict(name=name,expected_valid=good,passed=True,exit_code=result.returncode))
    result=command(["java","-cp",str(CLASSES),"AuditRunner","file",str(folder/"missing.hui"),"10"])
    assert result.returncode!=0;records.append(dict(name="missing_file",passed=True))
    # All three public entry points reject nonpositive k before mining.
    for main in ("algo.WUIP.MainWUIP","algo.TNUFPHybridSolution.MainTNUFPHybrid","algo.TUFP.MainTUFP"):
        for k in ("0","-1"):
            result=command(["java","-cp",str(CLASSES),main,"--input",str(folder/"standard.hui"),"--k",k])
            assert result.returncode!=0;records.append(dict(name=main+"_k"+k,passed=True))
    (REPORT/"input_validation.json").write_text(json.dumps(records,indent=2))
    return len(records)


def cli_tests():
    folder=ROOT/"build"/"cli_tests";folder.mkdir(exist_ok=True)
    file=folder/"case.hui";file.write_text("1 3\n1 1 .9\n1 2 .8\n1 3 .1\n")
    mains={"algo.WUIP.MainWUIP":"w","algo.TNUFPHybridSolution.MainTNUFPHybrid":"h","algo.TUFP.MainTUFP":"t","algo.MainNTUFP":"w","algo.MainTidNTUFP":"h"}
    rows=[(1,1,".9"),(1,2,".8"),(1,3,".1")];exact,bound,te,tb=oracle(rows,10)
    logs={}
    for main,algorithm in mains.items():
        out=folder/(main+".txt")
        result=command(["java","-Xms32m","-Xmx256m","-cp",str(CLASSES),main,"--input",str(file),"--k","10","--runs","2","--warmup","1","--sample-ms","1","--output",str(out)])
        assert result.returncode==0,(main,result.stderr)
        assert "Size: 7" in result.stdout and "RESULT_END" in result.stdout
        memory=[float(s.split(":",1)[1]) for s in result.stdout.splitlines() if s.startswith("SampledMaxUsedHeapMiB:")]
        assert len(memory)==2 and all(m>0 for m in memory)
        average=float(next(s.split(":",1)[1] for s in result.stdout.splitlines() if s.startswith("AvgMemory:")))
        assert abs(average-sum(memory)/2)<1e-9
        observed=[]
        for row in out.read_text().splitlines():
            items,score=row.split(" : ");observed.append((tuple(map(int,items.split())),Fraction(score)))
        scores,expected=(bound,tb) if algorithm=="w" else (exact,te)
        assert observed==[(x,scores[x]) for x in expected],(main,observed)
        logs[main]=result.stdout
    (REPORT/"cli_logs.json").write_text(json.dumps(logs,indent=2))
    return len(mains)


def main():
    result=command([sys.executable,str(ROOT/"build.py")]);assert result.returncode==0,result.stderr
    print(result.stdout.strip(),flush=True)
    summary=oracle_tests();print("Oracle runs:",summary["miner_runs"],"failures:",summary["failures"],flush=True)
    result=command(["java","-Xmx256m","-cp",str(CLASSES),"RegressionProbe",str(ROOT/"build")])
    (REPORT/"api_probe.log").write_text(result.stdout+result.stderr)
    assert result.returncode==0,result.stdout+result.stderr
    summary["api_assertions"]=int(next(s.split("=",1)[1] for s in result.stdout.splitlines() if s.startswith("PROBE_CHECKS=")))
    summary["input_validation_cases"]=io_tests();summary["cli_entrypoints"]=cli_tests()
    summary["java_version"]=command(["java","-version"]).stderr.strip()
    summary["source_sha256"]={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/"src").rglob("*.java"))}
    summary["scope"]="Synthetic finite tests; no real-dataset runtime, memory, or manuscript performance claims reproduced."
    (REPORT/"summary.json").write_text(json.dumps(summary,indent=2))
    (REPORT/"compile.log").write_text((ROOT/"build"/"compile.log").read_text())
    print(json.dumps({k:v for k,v in summary.items() if k not in ("source_sha256", "java_version")},indent=2),flush=True)

if __name__=="__main__":main()
