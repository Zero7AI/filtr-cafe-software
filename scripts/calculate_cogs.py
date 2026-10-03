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

# Group by menu_item_id
grouped = {}
for u in usages:
    mid = u["menu_item_id"]
    grouped.setdefault(mid, []).append(u)

def calc_cost(qty, consumed_unit, inv_item):
    u1 = consumed_unit.strip().lower()
    u2 = inv_item["unit"].strip().lower()
    
    if u1 == u2:
        norm_qty = qty
    elif u1 == "g" and u2 in ["kg", "kilogram", "kgs"]:
        norm_qty = qty / 1000.0
    elif u1 in ["kg", "kgs"] and u2 == "g":
        norm_qty = qty * 1000.0
    elif u1 == "ml" and u2 in ["l", "liter", "liters", "litre", "litres"]:
        norm_qty = qty / 1000.0
    elif u1 in ["l", "liter", "litres"] and u2 == "ml":
        norm_qty = qty * 1000.0
    else:
        norm_qty = qty
        
    pack_size = inv_item["standard_quantity"] if inv_item["standard_quantity"] > 0 else 1.0
    packs = norm_qty / pack_size
    cost = packs * inv_item["default_price"]
    return cost

print("=== VERIFIED RECIPES & FINANCIAL COGS SUMMARY ===")
for mid, u_list in sorted(grouped.items(), key=lambda x: menus[x[0]]["name"] if x[0] in menus else ""):
    menu_item = menus.get(mid)
    if not menu_item:
        continue
    
    total_cogs = 0.0
    print(f"\n--- {menu_item['name']} (ID {mid}) | Selling Price: Rs.{menu_item['price']} ---")
    for u in u_list:
        inv_item = invs.get(u["inventory_item_id"])
        if not inv_item:
            continue
        c = calc_cost(u["quantity_used"], u["unit"], inv_item)
        total_cogs += c
        print(f"  • {inv_item['name']}: {u['quantity_used']} {u['unit']} -> Rs.{c:.2f}")
    
    margin_rs = menu_item['price'] - total_cogs
    margin_pct = (margin_rs / menu_item['price']) * 100 if menu_item['price'] > 0 else 0
    print(f"  => TOTAL COGS: Rs.{total_cogs:.2f} | PROFIT MARGIN: Rs.{margin_rs:.2f} ({margin_pct:.1f}%)")
