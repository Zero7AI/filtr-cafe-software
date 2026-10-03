import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

candidates = [
    "gemini-3.1-flash-lite",
    "gemini-3.5-flash-lite",
    "gemini-3.7-flash",
    "gemini-3.8-flash",
    "gemini-3.6-flash",
    "gemini-flash-lite-latest",
    "gemini-3-flash-preview",
    "gemini-2.5-flash-lite"
]

for m in candidates:
    url = f"{SUPABASE_URL}/functions/v1/whatsapp-webhook?action=diag&model={m}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            status = data.get('apiStatus')
            if status == 200:
                print(f"✅ {m}: SUCCESS 200! Result: {data.get('testData')}")
            else:
                err = data.get('testData', {}).get('error', {})
                print(f"❌ {m}: Status {status} - {err.get('message')}")
    except Exception as e:
        print(f"⚠️ {m}: Exception {e}")
