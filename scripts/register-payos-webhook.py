"""Register the running application's public HTTPS webhook without logging credentials."""
import argparse
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.request
from urllib.parse import urlparse


def property_value(text, name):
    match = re.search(r"^" + re.escape(name) + r"\s*=\s*(.*)$", text, re.M)
    value = match.group(1).strip() if match else ""
    env = re.fullmatch(r"\$\{([^:}]+)(?::(.*))?\}", value)
    return os.environ.get(env.group(1), env.group(2) or "") if env else value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("public_url", help="Public HTTPS origin of the running application")
    args = parser.parse_args()
    origin = args.public_url.rstrip("/")
    parsed = urlparse(origin)
    if parsed.scheme != "https" or not parsed.hostname or parsed.path or parsed.query or parsed.fragment or parsed.username:
        parser.error("Provide an HTTPS origin without path, query, or credentials")
    root = Path(__file__).resolve().parents[1]
    properties = (root / "com_be/src/main/resources/application.properties").read_text(encoding="utf-8")
    client = property_value(properties, "payos.client-id")
    key = property_value(properties, "payos.api-key")
    if not client or not key:
        raise SystemExit("PayOS merchant credentials are missing")
    webhook = origin + "/api/tuition-payment/payos-webhook"
    request = urllib.request.Request(
        "https://api-merchant.payos.vn/confirm-webhook",
        data=json.dumps({"webhookUrl": webhook}).encode(),
        headers={"Content-Type": "application/json", "User-Agent": "TalentCenter/1.0",
                 "x-client-id": client, "x-api-key": key},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=45) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit(f"PayOS registration failed (HTTP {error.code}); check backend/tunnel logs") from None
    if result.get("code") != "00":
        raise SystemExit(f"PayOS did not confirm webhook (code {result.get('code')})")
    print("PayOS confirmed webhook:", webhook)


if __name__ == "__main__":
    main()
