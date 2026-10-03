import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}",
    "Content-Type": "application/json"
}

# IDs to remove (duplicates with 0 recipes)
dup_ids = list(range(2419, 2435)) + list(range(2787, 2803))
print(f"Targeting {len(dup_ids)} duplicate menu items for deletion: {dup_ids}")

# Delete from Supabase menu_items
req = urllib.request.Request(
    f"{SUPABASE_URL}/rest/v1/menu_items?id=in.({','.join(map(str, dup_ids))})",
    headers=headers,
    method="DELETE"
)

try:
    with urllib.request.urlopen(req) as resp:
        print(f"Supabase delete status: {resp.status}")
except Exception as e:
    print(f"Error during delete: {e}")

# Verify remaining items
req2 = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/menu_items?select=*&order=id.asc", headers=headers)
with urllib.request.urlopen(req2) as resp2:
    remaining = json.loads(resp2.read().decode())

print(f"\nRemaining menu_items in Supabase: {len(remaining)}")
for it in remaining:
    print(f"ID: {it['id']:4d} | Name: {it.get('name'):25s} | Cat: {it.get('category'):15s} | Price: {it.get('price')}")
