import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"

headers = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}"
}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_order_items?select=item_name,unit_price,quantity,unit,subtotal", headers=headers)
with urllib.request.urlopen(req) as resp:
    items = json.loads(resp.read().decode())
    print(f"Total inventory items bought across bills: {len(items)}")
    unique_items = {}
    for it in items:
        name = it.get('item_name', '').strip()
        if name not in unique_items:
            unique_items[name] = []
        unique_items[name].append((it.get('quantity'), it.get('unit'), it.get('unit_price'), it.get('subtotal')))

    for name, purchases in sorted(unique_items.items()):
        print(f"\nItem: {name}")
        for p in purchases:
            print(f"   Qty: {p[0]} {p[1]} @ Rs.{p[2]} = Rs.{p[3]}")
