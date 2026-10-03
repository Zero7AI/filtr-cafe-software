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

def patch(endpoint, data):
    req = urllib.request.Request(
        f"{SUPABASE_URL}/rest/v1/{endpoint}",
        data=json.dumps(data).encode(),
        headers=headers,
        method="PATCH"
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

def get(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

# 1. Update Roasted Coffee Beans price to Rs.650 / kg
patch("inventory_items?name=eq.Roasted%20Coffee%20Beans", {"default_price": 650.0})
print("Updated 'Roasted Coffee Beans' default_price to Rs.650.00 / kg")

# 2. Update inventory_usage where inventory_item_name = 'Roasted Coffee Beans'
# For 60 ml decoction -> 100g / 320ml * 60ml = 18.75 g
# For 90 ml decoction (OG Vietnamese) -> 100g / 320ml * 90ml = 28.125 g
# For 70 ml decoction (Americano) -> 100g / 320ml * 70ml = 21.875 g

usages = get("inventory_usage?inventory_item_name=eq.Roasted%20Coffee%20Beans")
for u in usages:
    m_name = u["menu_item_name"]
    u_id = u["id"]
    if m_name == "OG Vietnamese":
        new_qty = 28.13
    elif m_name == "Americano":
        new_qty = 21.88
    else:
        # All standard 60 ml decoction drinks (Iced Coffee, Frappe, Vietnamese)
        new_qty = 18.75
    
    patch(f"inventory_usage?id=eq.{u_id}", {"quantity_used": new_qty})
    print(f"Updated recipe for '{m_name}': {new_qty} g Roasted Coffee Beans (ID {u_id})")

print("\n--- Current Recipes in Supabase ---")
all_u = get("inventory_usage?select=*&order=menu_item_name.asc")
for r in all_u:
    print(f"{r['menu_item_name']} -> {r['inventory_item_name']}: {r['quantity_used']} {r['unit']}")
