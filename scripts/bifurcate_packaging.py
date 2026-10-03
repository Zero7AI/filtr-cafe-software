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

def post(endpoint, data):
    req = urllib.request.Request(
        f"{SUPABASE_URL}/rest/v1/{endpoint}",
        data=json.dumps(data).encode(),
        headers=headers,
        method="POST"
    )
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

def get(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

def delete(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers, method="DELETE")
    with urllib.request.urlopen(req) as resp:
        return resp.read().decode()

print("--- 1. Adding Bifurcated Packaging Items into inventory_items ---")
packaging_items = [
    {"name": "350ml Cup", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 4.0, "current_stock": 1000.0, "is_active": True},
    {"name": "Cup Lid", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 1.0, "current_stock": 1000.0, "is_active": True},
    {"name": "Thick Straw", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 0.5, "current_stock": 1000.0, "is_active": True},
    {"name": "Straw Cover / Sleeve", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 0.2, "current_stock": 1000.0, "is_active": True},
    {"name": "Tissue Paper", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 0.3, "current_stock": 2000.0, "is_active": True}
]

existing_inv = {item['name']: item for item in get("inventory_items?select=*")}
pack_map = {}

for p in packaging_items:
    if p["name"] in existing_inv:
        p_id = existing_inv[p["name"]]["id"]
        print(f"Item exists: {p['name']} (ID {p_id})")
        pack_map[p["name"]] = p_id
    else:
        res = post("inventory_items", p)
        p_id = res[0]["id"]
        print(f"Inserted item: {p['name']} (ID {p_id})")
        pack_map[p["name"]] = p_id

print("\n--- 2. Replacing Old Combined Packaging in inventory_usage ---")
# Remove old combined packaging from usage table
delete("inventory_usage?inventory_item_name=like.*Packaging*")
print("Removed old combined packaging entries.")

# Get all menu items currently configured with recipes
drinks = [
    "Hazelnut Iced Coffee",
    "Vanila Iced Coffee",
    "Caramel Iced Coffee",
    "classic",
    "Vanila Frappe",
    "Hazelnut Frappe",
    "Caramel Frappe",
    "Just Frappe",
    "Vietnamese Iced Coffee",
    "OG Vietnamese",
    "Americano"
]

menu_map = {m["name"]: m["id"] for m in get("menu_items?select=id,name")}

new_usage_entries = []
for drink_name in drinks:
    m_id = menu_map.get(drink_name)
    if not m_id:
        continue
    
    # Add each bifurcated packaging item
    for pack_name, cost in [
        ("350ml Cup", 4.0),
        ("Cup Lid", 1.0),
        ("Thick Straw", 0.5),
        ("Straw Cover / Sleeve", 0.2),
        ("Tissue Paper", 0.3)
    ]:
        new_usage_entries.append({
            "menu_item_id": m_id,
            "menu_item_name": drink_name,
            "inventory_item_id": pack_map[pack_name],
            "inventory_item_name": pack_name,
            "quantity_used": 1.0,
            "unit": "pcs"
        })

if new_usage_entries:
    res = post("inventory_usage", new_usage_entries)
    print(f"Successfully added {len(res)} bifurcated packaging components across {len(drinks)} drinks!")

print("\n--- 3. Recipe Inventory Summary for 'Hazelnut Iced Coffee' ---")
h_usages = get("inventory_usage?menu_item_name=eq.Hazelnut%20Iced%20Coffee&select=*")
for u in h_usages:
    print(f"  + {u['inventory_item_name']}: {u['quantity_used']} {u['unit']}")
