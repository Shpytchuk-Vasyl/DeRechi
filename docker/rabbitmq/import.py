import base64
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DEFINITIONS = Path(__file__).with_name("definitions.json")

source = sys.argv[1]
classic = "--classic" in sys.argv
if source.startswith("amqp"):
    url = urllib.parse.urlparse(source)
    host = url.hostname
    user, password = urllib.parse.unquote(url.username), urllib.parse.unquote(url.password)
    vhost = urllib.parse.unquote(url.path.lstrip("/")) or "/"
else:
    env = {}
    for line in Path(source).read_text(encoding="utf-8").splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            env[key.strip()] = value.strip().strip('"').strip("'")
    host, user, password = env["RABBITMQ_HOST"], env["RABBITMQ_USER"], env["RABBITMQ_PASSWORD"]
    vhost = env.get("RABBITMQ_VHOST") or "/"
print(f"broker {host}, vhost {vhost}")

api = f"https://{host}/api"
auth = "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()


def quote(name):
    return urllib.parse.quote(name, safe="")


def call(method, path, body):
    request = urllib.request.Request(api + path, data=json.dumps(body).encode(), method=method,
                                     headers={"Authorization": auth, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request) as response:
            print(f"  ok   {method} {path} ({response.status})")
    except urllib.error.HTTPError as e:
        print(f"  FAIL {method} {path} ({e.code}): {e.read().decode()}")
        sys.exit(1)


v = quote(vhost)
definitions = json.loads(DEFINITIONS.read_text(encoding="utf-8"))

print("exchanges")
for exchange in definitions["exchanges"]:
    call("PUT", f"/exchanges/{v}/{quote(exchange['name'])}",
         {key: exchange[key] for key in ("type", "durable", "auto_delete", "internal", "arguments")})

print("queues")
for queue in definitions["queues"]:
    arguments = dict(queue["arguments"])
    if classic:
        arguments.pop("x-queue-type", None)
    call("PUT", f"/queues/{v}/{quote(queue['name'])}",
         {"durable": queue["durable"], "auto_delete": queue["auto_delete"], "arguments": arguments})

print("bindings")
for binding in definitions["bindings"]:
    call("POST", f"/bindings/{v}/e/{quote(binding['source'])}/q/{quote(binding['destination'])}",
         {"routing_key": binding["routing_key"], "arguments": binding["arguments"]})

print("done")
