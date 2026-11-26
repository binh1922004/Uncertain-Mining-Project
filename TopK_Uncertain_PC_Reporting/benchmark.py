#!/usr/bin/env python3
"""Simple benchmark runner driven by experiments.yml (no SHA-256 or reference files required)."""

import argparse
import csv
import json
import re
import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
JAR = ROOT / "topk-benchmark.jar"
DEFAULT_ALGORITHMS = ["NTUFP", "TUFP", "ITUFP", "TidNTUFP"]


def load_config(path: Path) -> dict:
    """Load experiments.yml using PyYAML if available, else a minimal built-in parser."""
    text = path.read_text(encoding="utf-8")
    try:
        import yaml  # type: ignore

        return yaml.safe_load(text) or {}
    except ImportError:
        pass

    # Minimal fallback parser for the experiments.yml structure
    config: dict = {"experiments": [], "settings": {}}
    section = None
    current_exp = None
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if line == "experiments:":
            section = "experiments"
            continue
        if line == "settings:":
            section = "settings"
            current_exp = None
            continue
        if section == "experiments":
            if line.startswith("- "):
                current_exp = {}
                config["experiments"].append(current_exp)
                line = line[2:].strip()
            if current_exp is not None and ":" in line:
                k, v = [p.strip() for p in line.split(":", 1)]
                if v.startswith("[") and v.endswith("]"):
                    current_exp[k] = [int(x.strip()) for x in v[1:-1].split(",") if x.strip()]
                else:
                    current_exp[k] = v.strip("\"'")
        elif section == "settings" and ":" in line:
            k, v = [p.strip() for p in line.split(":", 1)]
            v_clean = v.strip("\"'")
            if v.startswith("[") and v.endswith("]"):
                config["settings"][k] = [x.strip().strip("\"'") for x in v[1:-1].split(",") if x.strip()]
            elif re.fullmatch(r"\d+", v_clean):
                config["settings"][k] = int(v_clean)
            else:
                config["settings"][k] = v_clean
    return config


def resolve_dataset_path(base_dir: Path, dataset: str) -> Path:
    """Resolve <dataset>.hui inside base_dir (or exact name if already has extension)."""
    p = base_dir / dataset
    if p.is_file():
        return p
    return base_dir / f"{dataset}.hui"


def run_single(
    algorithm: str,
    dataset_path: Path,
    k: int,
    patterns_path: Path,
    xms: str,
    xmx: str,
    sample_ms: int,
    timeout_s: float,
) -> dict:
    """Run one fresh JVM execution of topk-benchmark.jar and parse RESULT_JSON."""
    cmd = [
        "java",
        f"-Xms{xms}",
        f"-Xmx{xmx}",
        "-jar",
        str(JAR),
        algorithm,
        str(dataset_path),
        str(k),
        str(patterns_path),
        "adaptive",
        "true",
        "true",
        str(sample_ms),
    ]
    start = time.monotonic()
    status = "error"
    metrics = {}
    error_msg = ""

    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout_s,
        )
        wall_seconds = time.monotonic() - start
        stdout, stderr = proc.stdout, proc.stderr

        if proc.returncode == 0:
            json_lines = [
                line[len("RESULT_JSON ") :]
                for line in stdout.splitlines()
                if line.startswith("RESULT_JSON ")
            ]
            if len(json_lines) == 1:
                metrics = json.loads(json_lines[0])
                status = "completed"
            else:
                status = "invalid_output"
                error_msg = "Missing RESULT_JSON in stdout"
        elif "OutOfMemoryError" in (stdout + stderr) or "Could not reserve enough space" in stderr:
            status = "oom"
            error_msg = "Java OutOfMemoryError"
        else:
            status = "process_error"
            error_msg = (stderr.strip() or stdout.strip()).splitlines()[-1] if (stderr or stdout).strip() else f"exit code {proc.returncode}"
    except subprocess.TimeoutExpired:
        wall_seconds = time.monotonic() - start
        status = "wall_timeout"
        error_msg = f"Timed out after {timeout_s}s"
    except FileNotFoundError:
        wall_seconds = time.monotonic() - start
        status = "process_error"
        error_msg = "java not found in PATH"

    return {
        "status": status,
        "backend": metrics.get("backend", ""),
        "seconds": metrics.get("seconds", ""),
        "heap_mib": metrics.get("heap_mib", ""),
        "size": metrics.get("size", ""),
        "wall_seconds": round(wall_seconds, 6),
        "error": error_msg,
    }


def write_csv(path: Path, rows: list[dict], fieldnames: list[str]) -> None:
    tmp = path.with_suffix(".tmp")
    with tmp.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    tmp.replace(path)


def build_summary(runs: list[dict]) -> list[dict]:
    groups: dict[tuple, list[dict]] = {}
    for r in runs:
        key = (r["dataset"], int(r["k"]), r["algorithm"])
        groups.setdefault(key, []).append(r)

    summary_rows = []
    for (dataset, k, algorithm), rs in sorted(groups.items()):
        ok = [r for r in rs if r["status"] == "completed"]
        times = [float(r["seconds"]) for r in ok if r["seconds"] != ""]
        heaps = [float(r["heap_mib"]) for r in ok if r["heap_mib"] != ""]
        row = {
            "dataset": dataset,
            "k": k,
            "algorithm": algorithm,
            "total_runs": len(rs),
            "completed": len(ok),
            "timeouts": sum(r["status"] == "wall_timeout" for r in rs),
            "oom": sum(r["status"] == "oom" for r in rs),
            "errors": sum(r["status"] not in ("completed", "wall_timeout", "oom") for r in rs),
            "time_mean": round(statistics.mean(times), 6) if times else "",
            "time_sd": round(statistics.stdev(times), 6) if len(times) > 1 else (0.0 if times else ""),
            "time_min": round(min(times), 6) if times else "",
            "time_max": round(max(times), 6) if times else "",
            "heap_mean": round(statistics.mean(heaps), 4) if heaps else "",
            "heap_sd": round(statistics.stdev(heaps), 4) if len(heaps) > 1 else (0.0 if heaps else ""),
            "heap_min": round(min(heaps), 4) if heaps else "",
            "heap_max": round(max(heaps), 4) if heaps else "",
        }
        summary_rows.append(row)
    return summary_rows


RUN_FIELDS = [
    "dataset",
    "k",
    "algorithm",
    "run",
    "status",
    "backend",
    "seconds",
    "heap_mib",
    "size",
    "wall_seconds",
    "error",
]
SUMMARY_FIELDS = [
    "dataset",
    "k",
    "algorithm",
    "total_runs",
    "completed",
    "timeouts",
    "oom",
    "errors",
    "time_mean",
    "time_sd",
    "time_min",
    "time_max",
    "heap_mean",
    "heap_sd",
    "heap_min",
    "heap_max",
]


def main() -> int:
    parser = argparse.ArgumentParser(description="Simple benchmark runner reading experiments.yml")
    parser.add_argument("--config", type=Path, default=ROOT / "experiments.yml", help="Path to experiments.yml")
    parser.add_argument("--algorithms", nargs="+", choices=DEFAULT_ALGORITHMS, help="Algorithms to run")
    parser.add_argument("--resume", action="store_true", help="Skip runs already present in runs.csv")
    parser.add_argument("--dry-run", action="store_true", help="Print plan and check dataset files without running")
    args = parser.parse_args()

    cfg = load_config(args.config)
    experiments = cfg.get("experiments") or []
    settings = cfg.get("settings") or {}

    num_runs = int(settings.get("numberOfRuns", 3))
    base_dir = Path(settings.get("datasetBasePath", ROOT / "data")).expanduser()
    out_dir = Path(settings.get("outputPath", ROOT / "results")).expanduser()
    if not out_dir.is_absolute():
        out_dir = (ROOT / out_dir).resolve()
    sample_ms = int(settings.get("sampleMs", 5))
    timeout_s = float(settings.get("timeoutSeconds", 900))
    xmx = str(settings.get("xmx", "4g"))
    xms = str(settings.get("xms", "128m"))
    algorithms = args.algorithms or settings.get("algorithms") or DEFAULT_ALGORITHMS

    jobs = []
    for exp in experiments:
        dataset = exp["dataset"]
        for k in exp.get("thresholds", []):
            for alg in algorithms:
                for rep in range(1, num_runs + 1):
                    jobs.append((dataset, int(k), alg, rep))

    print(f"Config: {args.config}")
    print(f"Datasets base path: {base_dir}")
    print(f"Output path: {out_dir}")
    print(f"Algorithms: {algorithms} | Runs/config: {num_runs} | Total jobs: {len(jobs)}")
    print(f"JVM: -Xms{xms} -Xmx{xmx} | Timeout: {timeout_s}s | Sample: {sample_ms}ms")

    if args.dry_run:
        seen = set()
        for exp in experiments:
            ds = exp["dataset"]
            if ds in seen:
                continue
            seen.add(ds)
            p = resolve_dataset_path(base_dir, ds)
            print(f"  [{'OK' if p.is_file() else 'MISSING'}] {ds} -> {p}")
        return 0

    if not JAR.is_file():
        print(f"Error: {JAR} not found. Run 'python3 build.py' first.", file=sys.stderr)
        return 1

    out_dir.mkdir(parents=True, exist_ok=True)
    patterns_dir = out_dir / "patterns"
    patterns_dir.mkdir(exist_ok=True)
    runs_csv = out_dir / "runs.csv"
    summary_csv = out_dir / "summary.csv"

    all_runs: list[dict] = []
    done_keys: set[tuple] = set()
    if args.resume and runs_csv.is_file():
        with runs_csv.open(encoding="utf-8", newline="") as f:
            for row in csv.DictReader(f):
                all_runs.append(row)
                done_keys.add((row["dataset"], int(row["k"]), row["algorithm"], int(row["run"])))
        print(f"Resuming: {len(done_keys)} completed/recorded runs loaded from {runs_csv}")

    try:
        for idx, (dataset, k, alg, rep) in enumerate(jobs, 1):
            if (dataset, k, alg, rep) in done_keys:
                continue
            ds_path = resolve_dataset_path(base_dir, dataset)
            if not ds_path.is_file():
                print(f"[{idx}/{len(jobs)}] SKIP missing dataset: {ds_path}", file=sys.stderr)
                continue

            pat_path = patterns_dir / f"{dataset}_k{k}_{alg}_r{rep}.txt"
            print(f"[{idx}/{len(jobs)}] {dataset} k={k} {alg} run {rep}/{num_runs} ...", end=" ", flush=True)
            res = run_single(alg, ds_path, k, pat_path, xms, xmx, sample_ms, timeout_s)
            row = {"dataset": dataset, "k": k, "algorithm": alg, "run": rep, **res}
            all_runs.append(row)
            write_csv(runs_csv, all_runs, RUN_FIELDS)
            write_csv(summary_csv, build_summary(all_runs), SUMMARY_FIELDS)

            if res["status"] == "completed":
                print(f"OK ({res['seconds']:.4f}s, {res['heap_mib']:.2f} MiB)")
            else:
                print(f"{res['status'].upper()} ({res['error']})")
    except KeyboardInterrupt:
        print("\nInterrupted. Progress saved; re-run with --resume to continue.", file=sys.stderr)
        return 130

    print(f"\nDone. Results saved to:\n  - {runs_csv}\n  - {summary_csv}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
