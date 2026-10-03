import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def get(ep):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers)
    return json.loads(urllib.request.urlopen(req).read().decode())

menus = {m["id"]: m for m in get("menu_items?select=*")}
invs = {i["id"]: i for i in get("inventory_items?select=*")}
usages = get("inventory_usage?select=*")

print(f"Total menu_items: {len(menus)}")
print(f"Total inventory_items: {len(invs)}")
print(f"Total inventory_usage rows: {len(usages)}")

errors = []
duplicates = set()
seen_pairs = {}

for u in usages:
    uid = u.get("id")
    mid = u.get("menu_item_id")
    iid = u.get("inventory_item_id")
    mname = u.get("menu_item_name")
    iname = u.get("inventory_item_name")
    qty = u.get("quantity_used")
    unit = u.get("unit")

    pair = (mid, iid)
    if pair in seen_pairs:
        duplicates.add((mid, iid, seen_pairs[pair], uid))
    seen_pairs[pair] = uid

    if mid not in menus:
        errors.append(f"Usage {uid}: menu_item_id {mid} NOT FOUND in menu_items!")
    else:
        if menus[mid]["name"] != mname:
            errors.append(f"Usage {uid}: menu_item_name '{mname}' differs from actual menu name '{menus[mid]['name']}'")

    if iid not in invs:
        errors.append(f"Usage {uid}: inventory_item_id {iid} NOT FOUND in inventory_items!")
    else:
        if invs[iid]["name"] != iname:
            errors.append(f"Usage {uid}: inventory_item_name '{iname}' differs from actual inv name '{invs[iid]['name']}'")

print(f"\nDuplicates found: {len(duplicates)}")
for d in duplicates:
    print(f"  Duplicate for menu {d[0]} and inv {d[1]} (IDs: {d[2]}, {d[3]})")

print(f"\nErrors found: {len(errors)}")
for e in errors:
    print(" ", e)

print("\n=== COMPLETE MENU AUDIT ===")
by_menu = {}
for u in usages:
    by_menu.setdefault(u["menu_item_id"], []).append(u)

for mid, m in sorted(menus.items(), key=lambda x: (x[1].get("category",""), x[1]["name"])):
    print(f"\n[Menu ID {mid}] {m['name']} ({m.get('category','')}) - Price: Rs.{m['price']}")
    if mid in by_menu:
        for u in by_menu[mid]:
            inv_item = invs.get(u["inventory_item_id"])
            p_price = inv_item["default_price"] if inv_item else "N/A"
            p_unit = inv_item["unit"] if inv_item else "N/A"
            p_std = inv_item["standard_quantity"] if inv_item else "N/A"
            print(f"    - Inv ID {u['inventory_item_id']}: {u['inventory_item_name']} | Qty: {u['quantity_used']} {u['unit']} (Master: Rs.{p_price}/{p_std}{p_unit})")
    else:
        print("    *** NO RECIPE CONFIGURED ***")
