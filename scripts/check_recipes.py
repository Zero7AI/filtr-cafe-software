import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"

headers = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}"
}

def fetch(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

print("=== MENU ITEMS ===")
menu = fetch("menu_items?select=id,name,category,price")
for m in sorted(menu, key=lambda x: x['name']):
    print(f"ID {m['id']}: {m['name']} ({m['category']}) - Rs.{m['price']}")

print("\n=== INVENTORY ITEMS ===")
try:
    inv = fetch("inventory_items?select=*")
    for i in sorted(inv, key=lambda x: x['name']):
        print(f"ID {i['id']}: {i['name']} ({i.get('category','')}) | unit: {i.get('unit','')} | stdQty: {i.get('standard_quantity','')} | price: {i.get('default_price','')} | stock: {i.get('current_stock','')}")
except Exception as e:
    print("Error fetching inventory_items:", e)

print("\n=== INVENTORY USAGE ===")
try:
    usages = fetch("inventory_usage?select=*")
    print(f"Total usages: {len(usages)}")
    for u in usages:
        print(f"Usage ID {u.get('id')}: Menu '{u.get('menu_item_name')}' (ID {u.get('menu_item_id')}) -> Inv '{u.get('inventory_item_name')}' (ID {u.get('inventory_item_id')}): {u.get('quantity_used')} {u.get('unit')}")
except Exception as e:
    print("Error fetching inventory_usage:", e)
