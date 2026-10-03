import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

# 1. Fetch menu_items
req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/menu_items?select=*&order=name.asc", headers=headers)
with urllib.request.urlopen(req) as resp:
    items = json.loads(resp.read().decode())

print(f"Total menu_items in Supabase: {len(items)}")

# 2. Fetch inventory_usage / recipes
req2 = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_usage?select=*&order=id.asc", headers=headers)
try:
    with urllib.request.urlopen(req2) as resp2:
        usages = json.loads(resp2.read().decode())
    print(f"Total inventory_usage in Supabase: {len(usages)}")
except Exception as e:
    usages = []
    print("Could not fetch inventory_usage:", e)

# Group usages by menuItemId
usages_by_menu_id = {}
for u in usages:
    usages_by_menu_id.setdefault(u.get('menu_item_id') or u.get('menuItemId'), []).append(u)

# Find duplicates
names = {}
for it in items:
    name = (it.get('name') or '').strip().lower()
    names.setdefault(name, []).append(it)

print("\n--- All 55 Menu Items by ID ---")
for it in sorted(items, key=lambda x: x['id']):
    iid = it['id']
    u_count = len(usages_by_menu_id.get(iid, []))
    print(f"ID: {iid:4d} | Name: {it.get('name', ''):25s} | Cat: {it.get('category', ''):15s} | Price: {it.get('price')} | Recipes: {u_count}")


