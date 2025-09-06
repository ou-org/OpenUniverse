# Send data to Apache Solr

This guide explains how to configure and run an Apache Solr server using the
**slim release**. You'll start Solr in cloud mode, create a collection, and
access the admin interface.

## Download Apache Solr

Visit the official download page:

[https://solr.apache.org/downloads.html](https://solr.apache.org/downloads.html)

Download the slim binary release:

- `solr-9.8.1-slim.tgz` (You may verify using PGP or SHA512 files)

To download and extract:

```bash
wget https://downloads.apache.org/solr/solr/9.8.1/solr-9.8.1-slim.tgz
tar -xzf solr-9.8.1-slim.tgz
```

## Start Solr in Cloud Mode

Run the following commands:

```bash
cd solr-9.8.1/bin
./solr start --cloud
```

This will start Solr on port `8983` by default.

## Delete a Collection (if exists)

Use the Collections API to delete a collection named `my_collection`:

```bash
curl "http://localhost:8983/solr/admin/collections?action=DELETE&name=my_collection"
```

## Create a Collection

Use the Collections API to create a collection named `my_collection`:

```bash
curl --request POST \
  --url http://localhost:8983/api/collections \
  --header 'Content-Type: application/json' \
  --data '{
    "name": "my_collection",
    "numShards": 1,
    "replicationFactor": 1
  }'
```

## Access the Admin Panel

Open your browser and go to:

```
http://localhost:8983
```

This is the Solr Admin UI, where you can view collections and query documents.

## Write Simple Python Script for Sending Data to a Solr Server

This script reads JSON lines from standard input (e.g., piped from a file or
another command) and sends each one as a document to an Apache Solr server for
indexing. It takes the Solr URL as a command-line argument and commits each
document immediately after sending. If a line isn’t valid JSON or if the request
fails, it prints an error message to standard error.

<code>send-to-solr.py</code>

```python
#!/usr/bin/env python3

import sys
import json
import requests

def send_to_solr(solr_url, json_obj):
    headers = {'Content-Type': 'application/json'}
    try:
        response = requests.post(f"{solr_url}/update?commit=true", json=[json_obj], headers=headers)
        response.raise_for_status()
        print(f"Successfully sent: {json_obj}")
    except requests.exceptions.RequestException as e:
        print(f"Failed to send {json_obj}: {e}", file=sys.stderr)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python script.py <solr_url>", file=sys.stderr)
        sys.exit(1)
    
    solr_url = sys.argv[1]  # Get Solr URL from command line argument
    
    for line in sys.stdin:
        try:
            json_obj = json.loads(line.strip())
            send_to_solr(solr_url, json_obj)
        except json.JSONDecodeError as e:
            print(f"Invalid JSON: {line.strip()} - {e}", file=sys.stderr)
```

Make the file executable:

```bash
chmod +x send-to-solr.py
```

Test our program:

```bash
cat some_data_to_send.json | send-to-solr.py http://localhost:8983/api/collections/my_collection
```

## Set up our script as export target

<code>root.json</code>

```json
{
   "head":{
      "doc_type":"root",
      "name":"Root",
      "disabled":false,
      "description":"This is root document",
      }
   },
   "export_targets":[
      {
         "id":"target1",
         "disabled":false,
         "command": {
             "cmd":"~/ou/my-repo/my-scripts/send-to-solr.py",
             "args":[
                "http://localhost:8983/api/collections/my_collection"
             ]
        }
      }
   ],
   ...
}
```

## References

- Five Minutes to Searching:
  [https://solr.apache.org/guide/solr/latest/getting-started/tutorial-five-minutes.html](https://solr.apache.org/guide/solr/latest/getting-started/tutorial-five-minutes.html)

- Solr Documentation:
  [https://solr.apache.org/guide/](https://solr.apache.org/guide/)
