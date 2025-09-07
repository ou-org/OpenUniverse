<head>
    <meta name='doc-spec' content="OpenUniverseSpecVer-1.01">
    <meta name='doc-disabled' content="false">
    <meta name="title" content="Hello, Universe!">
    <meta name="author" content="k2Xzny">
    <meta name="keywords" content="OpenUniverse, example, markdown">
</head>

# Hello Universe (Root + Trigger + Plugins) Example.

This example demonstrates how **OpenUniverse** combines metadata, triggers, and export plugins to form a **resilient event orchestration pipeline**.

It shows how **custom Python scripts** can be integrated into the JSON-driven spec system to produce live event streams while maintaining safety nets for failures.

* Shows how **spec documents + custom plugins** combine into a working system.
* Demonstrates **event scheduling, live streaming, and error handling** in one pipeline.
* Provides a **minimal but complete blueprint** for building more advanced orchestration cases.

---

# Create Git Repository and Make Initial Commit

Follow these steps to initialize a Git repository and commit your repo files.

**1. Create repository directory**

```bash
mkdir -p /path/to/your/repo
```

**2. Navigate to your repo directory and initialize Git**

```bash
cd /path/to/your/repo
git init
```

**3. Configure your Git username and email (if not set)**

```bash
git config user.name "Your Name"
git config user.email "you@example.com"
```

**4. Copy your files to repository directory**

Copy `HelloUniverse.md` (this file) into `/path/to/your/repo`

**5. Stage all files for commit**

```bash
git add .
```

**6. Commit the staged files**

```bash
git commit -m "Initial commit"
```
---

## 1. Root Document (`root.json`)

Acts as **Coordinator** — decides what runs, where events go, and what to do on errors.

* Defines the **entrypoint** of the orchestration.
* Uses tags and priorities to select which triggers are active.
* Configures two export mechanisms:

  * **SSE target** → streams processed events live.
  * **DMQ fallback** → stores undeliverable events in an NDJSON file.


---

## 2. Trigger Document (`health-trigger.json`)

Acts as **Event generator** — produces recurring events based on cron-like rules.

* Declares a **scheduler** that generates events:

  * Every **5 seconds** → `health_ou` (OpenUniverse health).
  * Every **10 seconds** → `health_jvm` (JVM runtime health).
* Because its `tags` match the Root’s `triggers`, it is automatically picked up.

---

## 3. Export Target Plugin (`scripts/sse.py`)

Acts as **Live event delivery** — makes monitoring possible via browser or dashboards.

* Small Python HTTP server implementing **Server-Sent Events**.
* Streams JSON events in real time to any client connected to `/events`.
* Uses a persistent event ID counter for reliability.

---

## 4. Dead Message Queue (DMQ) Plugin (`scripts/dmq.py`)

Acts as **Safety net** — ensures no event is lost, even if primary delivery fails.

* Simple Python sink that writes failed events to a file.
* Uses NDJSON format so the data is easy to reprocess later.

---

## 5. End-to-End Flow

```
[ health-trigger.json ]
   ↓ (cron: every 5s / 10s)
[ Root.json orchestration ]
   ↓
 ┌───────────────────────────┐
 │ Export Target: SSE (live) │  --->  http://localhost:8080/events
 └───────────────────────────┘
   ↓ (if fail)
 ┌───────────────────────────┐
 │ Export DMQ: /tmp/my-dmq   │  --->  my-dmq.ndjson
 └───────────────────────────┘
```

---

## Root Document

`root.json`

```json
   {
      "head": {
         "doc_spec": "OpenUniverseSpecVer-1.01",
         "doc_type": "root",
         "name": "Root",
         "disabled": false,
         "description": "Controls execution of OpenUniverse and JVM health triggers using tag and priority filters.",
         "tags": [
            "example",
            "hello"
         ]
      },
      "triggers": "tags:(example AND hello AND monitoring) AND attr.priority:[03 TO *]",
      "export_targets": [
         {
          "id": "export_target_1",
            "disabled": false,
            "command": {
               "cmd": "#scripts/sse.py",
               "args": [
                  "localhost",
                  "8080"
               ]
            }
         }
      ],
      "export_dmqs": [
         {
            "cmd": "#scripts/dmq.py",
            "args": [
               "/tmp/my-dmq.ndjson"
            ]
         }
      ]
   }
```

### How It Works?

* **`head`** – identifies this JSON as the root of the orchestration flow. It defines metadata, tags, and description.
* **`triggers`** – declares which triggers should be activated. Here, it requires documents tagged with `example`, `hello`, and `monitoring`, and with priority ≥ `03`.
* **`export_targets`** – defines where successfully processed events should be delivered. In this case, events are streamed over **Server-Sent Events (SSE)** via the Python script `scripts/sse.py`.
* **`export_dmqs`** – defines a **Dead Message Queue (DMQ)** handler. If events cannot be processed or exported, they are written into `/tmp/my-dmq.ndjson` using the script `scripts/dmq.py`.

## Trigger Document

`health-trigger.json`

```json
   {
      "head": {
         "doc_spec": "OpenUniverseSpecVer-1.01",
         "doc_type": "triggers/event_scheduler",
         "name": "heartbit_trigger",
         "description": "Schedules recurring health events for OpenUniverse and JVM monitoring.",
         "tags": [
            "example",
            "hello",
            "health",
            "system",
            "monitoring"
         ],
         "attr": {
            "priority": "07"
         }
      },
      "schedulers_list": [
         {
            "description": "Runs every 5 seconds. Produce OpenUniverse instance health data.",
            "event_type": "health_ou",
            "cron_expression": "0/5 * * * * ?"
         },
         {
            "description": "Runs every 10 seconds. Produce JVM instance health data.",
            "event_type": "health_jvm",
            "cron_expression": "0/10 * * * * ?"
         }
      ]
   }
```


### How It Works?

This trigger document **schedules recurring events**:

* Every **5 seconds** → emits an event of type `health_ou` (health data for OpenUniverse itself).
* Every **10 seconds** → emits an event of type `health_jvm` (health data for the JVM runtime).

Because this document has `tags` that match the **root’s trigger expression**, OpenUniverse will automatically activate it.


## Export Target Plugin

`scripts/sse.py` `+x`

```python
#!/usr/bin/env python3
import sys
import json
import socket
import threading
import os

EVENT_ID_FILE = "/tmp/event_id_counter.txt"

def read_event_id_counter():
    if os.path.exists(EVENT_ID_FILE):
        with open(EVENT_ID_FILE, "r") as f:
            try:
                return int(f.read().strip())
            except ValueError:
                return 0
    return 0

def write_event_id_counter(counter):
    with open(EVENT_ID_FILE, "w") as f:
        f.write(str(counter))

def filter_event(json_obj, path):
    event_type = path.strip("/").split("/")[1:]
    if event_type:
        event_type = event_type[0]
        return json_obj.get("event_event_type_s") == event_type
    return True

def safe_send(client_socket, message):
    try:
        client_socket.send(message.encode("utf-8"))
        print("0", flush=True)   # success
    except Exception as e:
        print(f"ERROR: {str(e)}", flush=True)   # delivery failed
        raise

def event_stream(path, client_socket):
    event_id_counter = read_event_id_counter()
    while True:
        try:
            line = sys.stdin.readline().strip()
            if not line:
                break
            json_obj = json.loads(line)
            if filter_event(json_obj, path):
                event_id_counter += 1
                event_id = event_id_counter
                event_name = json_obj.get("event_event_type_s", "message")
                message = (
                    f"id: {event_id}\n"
                    f"event: {event_name}\n"
                    f"data: {json.dumps(json_obj)}\n\n"
                )
                safe_send(client_socket, message)
                write_event_id_counter(event_id_counter)
        except (json.JSONDecodeError, ValueError) as e:
            error_message = f"data: {{\"error\": \"Invalid JSON: {str(e)}\"}}\n\n"
            try:
                safe_send(client_socket, error_message)
            except Exception:
                break
        except Exception:
            break

def handle_client(client_socket, client_address):
    try:
        request = client_socket.recv(1024).decode("utf-8")
        if not request:
            return
        print(f"Request received: {request}")
        lines = request.split("\r\n")
        method, path, _ = lines[0].split()
        if method == "GET" and path.startswith("/events"):
            safe_send(client_socket, "HTTP/1.1 200 OK\r\n")
            safe_send(client_socket, "Content-Type: text/event-stream\r\n")
            safe_send(client_socket, "Cache-Control: no-cache\r\n")
            safe_send(client_socket, "Connection: keep-alive\r\n")
            safe_send(client_socket, "\r\n")
            event_stream(path, client_socket)
        else:
            print(f"Path not found: {path}")
            safe_send(client_socket, "HTTP/1.1 404 Not Found\r\n")
            safe_send(client_socket, "\r\n")
    finally:
        client_socket.close()

def start_server(host, port):
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind((host, port))
    server_socket.listen(5)
    print(f"Server started on {host}:{port}")
    while True:
        client_socket, client_address = server_socket.accept()
        print(f"Connection from {client_address}")
        client_handler = threading.Thread(target=handle_client, args=(client_socket, client_address))
        client_handler.start()


if len(sys.argv) < 3:
    print(f"Usage: {sys.argv[0]} <host> <port>", file=sys.stderr)
    sys.exit(1)

host = sys.argv[1]
port = int(sys.argv[2])
start_server(host, port)
```

### How It Works?

The **SSE plugin**:

* Starts a small **HTTP server** that listens on the given host/port.
* Accepts **GET requests** on `/events`.
* Reads **JSON events from stdin** (coming from OpenUniverse).
* Streams them to connected clients in **Server-Sent Events** format.
* Uses a persistent counter (`event_id_counter.txt`) so event IDs keep increasing across restarts.

This makes it possible to **visualize event streams in real time** from any browser or monitoring client.


## Dead Message Queue (DMQ) Plugin

`scripts/dmq.py` `+x`

```python
#!/usr/bin/env python3
import sys

if len(sys.argv) < 2:
    print(f"Usage: {sys.argv[0]} <output_file>", file=sys.stderr)
    sys.exit(1)

output_file = sys.argv[1]

with open(output_file, "w") as f:
    for line in sys.stdin:
        f.write(line)
        f.flush()
```

### How It Works?

The **DMQ plugin** acts as a safety net:

* If an event cannot be delivered to any **export target**, OpenUniverse reroutes it here.
* This script simply **writes each failed event into a local file** (`/tmp/my-dmq.ndjson`).
* Keeping it in **NDJSON (newline-delimited JSON)** format makes it easy to reprocess later.

---

## Putting It All Together

1. OpenUniverse loads `root.json`.
2. It evaluates `triggers` and finds `health-trigger.json` (because tags match).
3. The trigger schedules **two event types**:

   * `health_ou` → every 5s
   * `health_jvm` → every 10s
4. These events flow into the pipeline:

   * If delivered successfully → streamed live via `scripts/sse.py` at `http://localhost:8080/events`.
   * If delivery fails → written into `/tmp/my-dmq.ndjson` by `scripts/dmq.py`.

---

## Summary

This example shows how OpenUniverse:

* **Schedules recurring events** with JSON-based trigger definitions.
* **Exports real-time events** using a Python-based SSE plugin.
* **Handles failures gracefully** using a DMQ writer plugin.

With this setup, you get both **live streaming monitoring** and **reliable error handling** — a minimal but complete orchestration pipeline.
