import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

url = f"{SUPABASE_URL}/functions/v1/whatsapp-webhook?action=diag"
print(f"Calling: {url}")

try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode())
        print("Response from Edge Function diag:")
        print(json.dumps(res, indent=2))
except Exception as e:
    print(f"Error calling diag: {e}")
    if hasattr(e, 'read'):
        print("Body:", e.read().decode())
