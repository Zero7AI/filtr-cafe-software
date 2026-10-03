import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def fetch_supabase(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

print("=== Checking Msg 89 vs 90 (Ice cream ₹810) ===")
m89 = fetch_supabase("whatsapp_messages?id=in.(89,90)")
for m in m89:
    print(json.dumps(m, indent=2))

print("\n=== Checking Msg 30 vs 35 (Amrapura ₹2510) ===")
m30 = fetch_supabase("whatsapp_messages?id=in.(30,35)")
for m in m30:
    print(json.dumps(m, indent=2))
