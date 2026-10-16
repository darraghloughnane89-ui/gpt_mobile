#!/usr/bin/env bash
# Aggregates unit and instrumented JUnit XML into docs/TEST_REPORT.md.
# Exits 1 when any test failed or when no results were found.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${root}"

python3 - "${root}" <<'PY'
import json
import os
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
unit_root = root / "app" / "build" / "test-results" / "testDebugUnitTest"
instrumented_root = root / "app" / "build" / "outputs" / "androidTest-results" / "connected"
report_path = root / "docs" / "TEST_REPORT.md"

FEATURE_RULES = (
    ("smoke.chat", "chat"),
    ("smoke.bots", "bots"),
    ("smoke.projects", "projects"),
    ("smoke.share", "share"),
    ("smoke.widget", "widget"),
    ("smoke.voice", "voice"),
    ("smoke.settings", "settings"),
    ("smoke.vault", "vault"),
    ("smoke.migration", "migration"),
    (".chat.", "chat"),
    (".home.", "home"),
    (".setting.", "settings"),
    (".bot.", "bots"),
    (".project.", "projects"),
    (".voice.", "voice"),
    (".phone.", "share"),
    (".obsidian.", "vault"),
    (".database.", "migration"),
    (".security.", "vault"),
    (".backup.", "vault"),
)
FEATURE_ORDER = [
    "chat",
    "bots",
    "projects",
    "share",
    "widget",
    "voice",
    "settings",
    "vault",
    "migration",
    "home",
    "other",
]


def sha():
    env = os.environ.get("GITHUB_SHA")
    if env:
        return env
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "unknown"


def device_lines():
    path = root / "app" / "build" / "smoke-artifacts" / "device.txt"
    if not path.is_file():
        return ["not recorded"]
    return [line.strip() for line in path.read_text(encoding="utf-8", errors="replace").splitlines() if line.strip()]


def sidecar():
    mapping = {}
    for path in (root / "app" / "build").glob("**/smoke-features.jsonl"):
        for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError:
                continue
            classname = record.get("class")
            method = record.get("method")
            feature = record.get("feature")
            if classname and method and feature:
                mapping[(classname, method)] = feature
    return mapping


def feature_for(classname, method, features):
    tagged = features.get((classname, method))
    if tagged:
        return tagged
    for needle, feature in FEATURE_RULES:
        if needle in classname:
            return feature
    return "other"


def status_of(case):
    if case.find("failure") is not None:
        return "failed"
    if case.find("error") is not None:
        return "error"
    if case.find("skipped") is not None:
        return "skipped"
    return "passed"


def detail_of(case):
    node = case.find("failure")
    if node is None:
        node = case.find("error")
    if node is None:
        return ""
    text = (node.get("message") or "") + "\n" + (node.text or "")
    text = " ".join(text.split())
    return text[:500]


def load_cases(directory, suite):
    cases = {}
    if not directory.exists():
        return cases
    for path in sorted(directory.rglob("*.xml")):
        try:
            tree = ET.parse(path)
        except ET.ParseError:
            continue
        for case in tree.iter("testcase"):
            classname = case.get("classname") or "unknown"
            name = case.get("name") or "unknown"
            key = (suite, classname, name)
            current = {
                "suite": suite,
                "classname": classname,
                "name": name,
                "status": status_of(case),
                "detail": detail_of(case),
            }
            previous = cases.get(key)
            rank = {"passed": 0, "skipped": 1, "failed": 2, "error": 3}
            if previous is None or rank[current["status"]] >= rank[previous["status"]]:
                cases[key] = current
    return cases


def tally(cases):
    counts = {"passed": 0, "failed": 0, "error": 0, "skipped": 0}
    for case in cases:
        counts[case["status"]] += 1
    counts["total"] = len(cases)
    return counts


def main():
    features = sidecar()
    grouped = {}
    grouped.update(load_cases(unit_root, "unit"))
    grouped.update(load_cases(instrumented_root, "instrumented"))
    cases = list(grouped.values())
    for case in cases:
        case["feature"] = feature_for(case["classname"], case["name"], features)

    by_suite = {
        "unit": tally([case for case in cases if case["suite"] == "unit"]),
        "instrumented": tally([case for case in cases if case["suite"] == "instrumented"]),
    }
    overall = tally(cases)
    by_feature = {}
    for case in cases:
        bucket = by_feature.setdefault(case["feature"], {"passed": 0, "failed": 0, "error": 0, "skipped": 0})
        bucket[case["status"]] += 1

    ordered = [name for name in FEATURE_ORDER if name in by_feature]
    ordered.extend(sorted(name for name in by_feature if name not in ordered))

    lines = [
        "# Test report",
        "",
        f"- SHA: `{sha()}`",
        f"- Device: {', '.join(device_lines())}",
        "",
        "## Summary",
        "",
        "| Suite | Passed | Failed | Errors | Skipped | Total |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for suite in ("unit", "instrumented"):
        counts = by_suite[suite]
        lines.append(
            f"| {suite} | {counts['passed']} | {counts['failed']} | {counts['error']} | {counts['skipped']} | {counts['total']} |"
        )
    lines.append(
        f"| total | {overall['passed']} | {overall['failed']} | {overall['error']} | {overall['skipped']} | {overall['total']} |"
    )
    lines.extend(
        [
            "",
            "## Features",
            "",
            "| Feature | Passed | Failed | Errors | Skipped |",
            "| --- | ---: | ---: | ---: | ---: |",
        ]
    )
    if not ordered:
        lines.append("| none | 0 | 0 | 0 | 0 |")
    for name in ordered:
        counts = by_feature[name]
        lines.append(
            f"| {name} | {counts['passed']} | {counts['failed']} | {counts['error']} | {counts['skipped']} |"
        )

    failures = [case for case in cases if case["status"] in ("failed", "error")]
    lines.extend(["", "## Failures", ""])
    if not failures:
        lines.append("None.")
    else:
        for case in failures:
            lines.append(f"### {case['feature']} / {case['classname']}.{case['name']}")
            lines.append("")
            lines.append("```")
            lines.append(case["detail"] or case["status"])
            lines.append("```")
            lines.append("")

    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    print(f"Wrote {report_path}")
    print(
        f"passed={overall['passed']} failed={overall['failed']} "
        f"errors={overall['error']} skipped={overall['skipped']} total={overall['total']}"
    )
    if overall["total"] == 0 or overall["failed"] or overall["error"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
PY
