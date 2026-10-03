import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"

headers = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=representation"
}

# Test inventory_items table
try:
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_items?limit=1", headers=headers)
    with urllib.request.urlopen(req) as resp:
        print("inventory_items response:", resp.read().decode())
except urllib.error.HTTPError as e:
    print("inventory_items error:", e.code, e.read().decode())

# Test inventory_usage table
try:
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_usage?limit=1", headers=headers)
    with urllib.request.urlopen(req) as resp:
        print("inventory_usage response:", resp.read().decode())
except urllib.error.HTTPError as e:
    print("inventory_usage error:", e.code, e.read().decode())
