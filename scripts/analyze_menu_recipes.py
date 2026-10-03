import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def get(ep):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers)
    return json.loads(urllib.request.urlopen(req).read().decode())

menus = get("menu_items?select=id,name,category,price,is_available")
usages = get("inventory_usage?select=menu_item_id,menu_item_name,inventory_item_name,quantity_used,unit")

usage_by_menu = {}
for u in usages:
    mid = u["menu_item_id"]
    usage_by_menu.setdefault(mid, []).append(u)

print(f"Total Menu Items: {len(menus)}")

print("\n=== MENU ITEMS WITH CONFIGURED RECIPES ===")
for m in sorted(menus, key=lambda x: (x.get("category",""), x["name"])):
    mid = m["id"]
    if mid in usage_by_menu:
        ings = ", ".join([f"{u['inventory_item_name']} ({u['quantity_used']}{u['unit']})" for u in usage_by_menu[mid]])
        print(f"[{m['category']}] {m['name']} (ID {mid}) - Rs.{m['price']}\n    Ingredients: {ings}")

print("\n=== MENU ITEMS MISSING RECIPES ===")
for m in sorted(menus, key=lambda x: (x.get("category",""), x["name"])):
    mid = m["id"]
    if mid not in usage_by_menu:
        print(f"[{m['category']}] {m['name']} (ID {mid}) - Rs.{m['price']} (Available: {m.get('is_available', True)})")
