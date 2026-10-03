import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

models = ["gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.5-flash-lite", "gemini-1.5-flash-8b", "gemini-flash-latest"]

for m in models:
    url = f"{SUPABASE_URL}/functions/v1/whatsapp-webhook?action=diag&model={m}"
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req) as resp:
            res = json.loads(resp.read().decode())
            print(f"Model: {m} -> Status: {res.get('apiStatus')}")
            if res.get('apiStatus') == 200:
                print(f"  SUCCESS! Data: {res.get('testData')}")
            else:
                err = res.get('testData', {}).get('error', {})
                print(f"  Error {err.get('code')}: {err.get('message')}")
    except Exception as e:
        print(f"Model: {m} -> Exception: {e}")
