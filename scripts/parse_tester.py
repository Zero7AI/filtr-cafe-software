import urllib.request
import json
import sys
import base64

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

# 1. Fetch catalog
req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_items?is_active=eq.true", headers=headers)
with urllib.request.urlopen(req) as resp:
    items = json.loads(resp.read().decode())

catalog_str = "\n".join([f"- {it['name']} ({it.get('category')}) | Unit: {it.get('unit')} | Pack: {it.get('standard_quantity')} | Default Price: ₹{it.get('default_price')} | ID: {it['id']}" for it in items])

# Get GEMINI_API_KEY from edge function diag test or call edge function
# Let's test calling Gemini via the Edge Function
def parse_via_edge(prompt, media_b64=None, mime="image/jpeg", model="gemini-3.1-flash-lite"):
    # We can use the edge function with an action or test endpoint
    pass

print(f"Catalog loaded: {len(items)} items.")
