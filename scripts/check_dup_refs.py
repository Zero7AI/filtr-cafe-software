import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

dup_ids = list(range(2419, 2435)) + list(range(2787, 2803))

# Check inventory_usage
req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_usage?menu_item_id=in.({','.join(map(str, dup_ids))})", headers=headers)
with urllib.request.urlopen(req) as resp:
    usages = json.loads(resp.read().decode())
print(f"inventory_usage rows pointing to duplicate IDs: {len(usages)}")

# Check order_items
req2 = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/order_items?menu_item_id=in.({','.join(map(str, dup_ids))})", headers=headers)
try:
    with urllib.request.urlopen(req2) as resp2:
        o_items = json.loads(resp2.read().decode())
    print(f"order_items rows pointing to duplicate IDs: {len(o_items)}")
except Exception as e:
    print("order_items check:", e)
