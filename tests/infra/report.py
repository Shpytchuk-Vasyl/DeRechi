"""Builds index.html and summary.md for one run of tests/run.sh.

Reads run.json, integration/TEST-*.xml (surefire) and load/summary.json (k6 --summary-export).
"""
import glob
import html
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

root = sys.argv[1] if len(sys.argv) > 1 else "."


def load_json(path):
    try:
        with open(os.path.join(root, path), encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


run = load_json("run.json") or {}

# --- integration -----------------------------------------------------------------------------

cases = []
for path in sorted(glob.glob(os.path.join(root, "integration", "TEST-*.xml"))):
    for case in ET.parse(path).getroot().iter("testcase"):
        problem = case.find("failure") if case.find("failure") is not None else case.find("error")
        status = "skipped" if case.find("skipped") is not None else ("failed" if problem is not None else "passed")
        message = ""
        if problem is not None:
            raw = problem.get("message") or (problem.text or "").strip().split("\n\tat ")[0]
            message = re.sub(r"^Assertion condition defined as a Lambda expression in \S+\s*", "", raw)
            message = " ".join(message.split())[:300]
        cases.append({
            "class": case.get("classname", "").rsplit(".", 1)[-1],
            "name": case.get("name", ""),
            "time": float(case.get("time") or 0),
            "status": status,
            "message": message,
        })

integration_ran = run.get("only") != "load"
integration_ok = integration_ran and bool(cases) and all(c["status"] != "failed" for c in cases) \
    and run.get("integration_exit", 0) == 0

# --- load ------------------------------------------------------------------------------------

summary = load_json("load/summary.json")
metrics = (summary or {}).get("metrics", {})
thresholds = []
for name, metric in metrics.items():
    for expression, crossed in (metric.get("thresholds") or {}).items():
        stat = expression.split("<")[0].split(">")[0]
        value = metric.get(stat, metric.get("value"))
        thresholds.append({"metric": name, "threshold": expression, "value": value, "crossed": bool(crossed)})
thresholds.sort(key=lambda t: (not t["crossed"], t["metric"]))

load_ran = run.get("only") != "integration"
load_ok = load_ran and summary is not None and not any(t["crossed"] for t in thresholds) \
    and run.get("load_exit", 0) == 0


def metric(name, key):
    return metrics.get(name, {}).get(key)


def ms(value):
    return "–" if value is None else f"{value:,.0f} ms"


def num(value, digits=0):
    return "–" if value is None else f"{value:,.{digits}f}"


def pct(value):
    return "–" if value is None else f"{value * 100:.2f}%"


def shown(t):
    if t["value"] is None:
        return "–"
    if "rate" in t["threshold"]:
        return pct(t["value"])
    return ms(t["value"])


# Per-kind latency exists only where thresholds created the sub-metrics (not in smoke).
if metric("http_req_duration{kind:read}", "p(95)") is not None:
    latencies = [("p95 read", ms(metric("http_req_duration{kind:read}", "p(95)"))),
                 ("p95 write", ms(metric("http_req_duration{kind:write}", "p(95)")))]
else:
    latencies = [("p95", ms(metric("http_req_duration", "p(95)"))),
                 ("p99", ms(metric("http_req_duration", "p(99)")))]

overall_ok = (integration_ok or not integration_ran) and (load_ok or not load_ran)
passed = sum(c["status"] == "passed" for c in cases)
failed = sum(c["status"] == "failed" for c in cases)
skipped = sum(c["status"] == "skipped" for c in cases)
crossed = sum(t["crossed"] for t in thresholds)

# --- summary.md and stdout -------------------------------------------------------------------

lines = [
    f"# Tests {run.get('id', '')}: {'PASSED' if overall_ok else 'FAILED'}",
    "",
    f"commit `{run.get('commit', '?')}`, load profile `{run.get('profile', '?')}`, rate {run.get('rate', '?')}/s, "
    f"duration {run.get('duration', '?')}, seed {run.get('items', '?')} + {run.get('items', '?')} notices",
    "",
]
if integration_ran:
    lines.append(f"Integration: {passed} passed, {failed} failed, {skipped} skipped")
    lines += [f"  FAILED {c['class']}.{c['name']}: {c['message']}" for c in cases if c["status"] == "failed"]
else:
    lines.append("Integration: not run")
if load_ran and summary is not None:
    lines.append(f"Load: {num(metric('http_reqs', 'count'))} requests, {num(metric('http_reqs', 'rate'), 1)}/s, "
                 + ", ".join(f"{label} {value}" for label, value in latencies) + ", "
                 f"{crossed} of {len(thresholds)} thresholds crossed")
    lines += [f"  CROSSED {t['metric']} {t['threshold']} (was {shown(t)})" for t in thresholds if t["crossed"]]
elif load_ran:
    lines.append("Load: no summary, k6 did not finish")
else:
    lines.append("Load: not run")

with open(os.path.join(root, "summary.md"), "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
print("\n".join(lines))

# --- index.html ------------------------------------------------------------------------------

e = html.escape
badge = lambda ok: f'<span class="badge {"ok" if ok else "bad"}">{"passed" if ok else "failed"}</span>'

case_rows = "".join(
    f'<tr class="{c["status"]}"><td>{e(c["class"])}</td><td>{e(c["name"])}'
    + (f'<div class="msg">{e(c["message"])}</div>' if c["message"] else "")
    + f'</td><td class="num">{c["time"]:.2f} s</td><td><span class="dot {c["status"]}"></span>{c["status"]}</td></tr>'
    for c in cases)

threshold_rows = "".join(
    f'<tr class="{"failed" if t["crossed"] else "passed"}"><td>{e(t["metric"])}</td><td><code>{e(t["threshold"])}</code></td>'
    f'<td class="num">{shown(t)}</td><td><span class="dot {"failed" if t["crossed"] else "passed"}"></span>'
    f'{"crossed" if t["crossed"] else "ok"}</td></tr>'
    for t in thresholds)

stats = [
    ("Requests", num(metric("http_reqs", "count"))),
    ("Requests / s", num(metric("http_reqs", "rate"), 1)),
    *latencies,
    ("HTTP errors", pct(metric("http_req_failed", "value"))),
    ("GraphQL errors", pct(metric("graphql_errors", "value"))),
]
stat_tiles = "".join(f'<div class="tile"><div class="label">{e(k)}</div><div class="value">{e(v)}</div></div>'
                     for k, v in stats)

if not integration_ran:
    integration_html = "<p class=\"muted\">Not run (<code>--only load</code>).</p>"
elif not cases:
    integration_html = "<p class=\"bad-text\">No results: the test run did not produce surefire reports.</p>"
else:
    integration_html = (f'<p>{passed} passed, {failed} failed, {skipped} skipped</p>'
                        f'<table><thead><tr><th>Class</th><th>Test</th><th class="num">Time</th><th>Result</th></tr>'
                        f'</thead><tbody>{case_rows}</tbody></table>')

if not load_ran:
    load_html = "<p class=\"muted\">Not run (<code>--only integration</code>).</p>"
elif summary is None:
    load_html = "<p class=\"bad-text\">No summary: k6 did not finish.</p>"
else:
    k6_link = ('<p><a href="load/report.html">Full k6 report</a> (charts over time, per-request breakdown)</p>'
               if os.path.exists(os.path.join(root, "load", "report.html")) else "")
    load_html = (f'<div class="tiles">{stat_tiles}</div>{k6_link}'
                 f'<h3>Thresholds: {crossed} of {len(thresholds)} crossed</h3>'
                 f'<table><thead><tr><th>Metric</th><th>Threshold</th><th class="num">Value</th><th>Result</th></tr>'
                 f'</thead><tbody>{threshold_rows}</tbody></table>')

page = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>DeRechi tests {e(run.get('id', ''))}</title>
<style>
:root {{ --bg:#fff; --fg:#1b1f24; --muted:#5b6470; --line:#e3e6ea; --tile:#f5f7f9; --ok:#1a7f37; --bad:#c62828; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#111418; --fg:#e6e8eb; --muted:#9aa3ad; --line:#2a2f36; --tile:#1a1e24; --ok:#3fb950; --bad:#f2615c; }} }}
body {{ background:var(--bg); color:var(--fg); font:15px/1.5 system-ui,-apple-system,Segoe UI,sans-serif; margin:0; padding:24px 16px; }}
main {{ max-width:1000px; margin:0 auto; }}
h1 {{ font-size:22px; margin:0 0 4px; }} h2 {{ font-size:18px; margin:32px 0 8px; }} h3 {{ font-size:15px; margin:20px 0 8px; }}
.muted {{ color:var(--muted); }} .bad-text {{ color:var(--bad); }}
.badge {{ font-size:13px; padding:2px 10px; border-radius:999px; color:#fff; vertical-align:middle; margin-left:8px; }}
.badge.ok {{ background:var(--ok); }} .badge.bad {{ background:var(--bad); }}
table {{ width:100%; border-collapse:collapse; font-size:14px; }}
th, td {{ text-align:left; padding:6px 8px; border-bottom:1px solid var(--line); vertical-align:top; }}
th {{ color:var(--muted); font-weight:600; }} .num {{ text-align:right; white-space:nowrap; }}
.dot {{ display:inline-block; width:8px; height:8px; border-radius:50%; margin-right:6px; background:var(--muted); }}
.dot.passed {{ background:var(--ok); }} .dot.failed {{ background:var(--bad); }}
tr.failed td:first-child {{ border-left:3px solid var(--bad); }}
.msg {{ color:var(--bad); font-size:13px; margin-top:2px; word-break:break-word; }}
.tiles {{ display:grid; grid-template-columns:repeat(auto-fit,minmax(140px,1fr)); gap:8px; }}
.tile {{ background:var(--tile); border-radius:8px; padding:10px 12px; }}
.tile .label {{ color:var(--muted); font-size:12px; }} .tile .value {{ font-size:18px; font-weight:600; }}
code {{ font-size:13px; }} a {{ color:inherit; }}
.wrap {{ overflow-x:auto; }}
</style>
</head>
<body><main>
<h1>DeRechi tests {e(run.get('id', ''))}{badge(overall_ok)}</h1>
<p class="muted">commit <code>{e(str(run.get('commit', '?')))}</code> · load profile <code>{e(str(run.get('profile', '?')))}</code>,
rate {e(str(run.get('rate', '?')))}/s, duration {e(str(run.get('duration', '?')))} ·
seed {e(str(run.get('items', '?')))} lost + {e(str(run.get('items', '?')))} found notices</p>

<h2>Integration{badge(integration_ok) if integration_ran else ''}</h2>
<div class="wrap">{integration_html}</div>

<h2>Load{badge(load_ok) if load_ran else ''}</h2>
<div class="wrap">{load_html}</div>
</main></body>
</html>
"""
with open(os.path.join(root, "index.html"), "w", encoding="utf-8") as f:
    f.write(page)
