import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

# Let's check if there are any other tables in Supabase that might contain logs or phone_number_id
req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/?apikey={KEY}")
try:
    with urllib.request.urlopen(req) as resp:
        data = json.loads(resp.read().decode())
        print("Definitions/tables in Supabase OpenAPI spec:")
        definitions = data.get('definitions', {})
        for k in definitions.keys():
            print("  -", k)
except Exception as e:
    print("Error:", e)
