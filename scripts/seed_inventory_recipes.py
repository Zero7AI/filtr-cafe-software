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

print("--- 1. Checking existing inventory_items ---")
existing_inv = {item['name']: item for item in get("inventory_items?select=*")}

inventory_items_master = [
    {
        "name": "Roasted Coffee Beans",
        "category": "Coffee",
        "unit": "kg",
        "standard_quantity": 1.0,
        "default_price": 682.5,
        "current_stock": 20.0,
        "is_active": True
    },
    {
        "name": "Milk (Nandini Gold)",
        "category": "Dairy",
        "unit": "L",
        "standard_quantity": 1.0,
        "default_price": 70.0,
        "current_stock": 24.0,
        "is_active": True
    },
    {
        "name": "Whipping Cream",
        "category": "Dairy",
        "unit": "L",
        "standard_quantity": 1.0,
        "default_price": 130.0,
        "current_stock": 4.0,
        "is_active": True
    },
    {
        "name": "Monin Vanilla Syrup",
        "category": "Syrups",
        "unit": "L",
        "standard_quantity": 1.0,
        "default_price": 750.0,
        "current_stock": 2.0,
        "is_active": True
    },
    {
        "name": "Monin Hazelnut Syrup",
        "category": "Syrups",
        "unit": "L",
        "standard_quantity": 1.0,
        "default_price": 750.0,
        "current_stock": 3.0,
        "is_active": True
    },
    {
        "name": "Monin Caramel Syrup",
        "category": "Syrups",
        "unit": "L",
        "standard_quantity": 1.0,
        "default_price": 750.0,
        "current_stock": 2.0,
        "is_active": True
    },
    {
        "name": "Vanilla Base Ice Cream",
        "category": "Dairy / Frozen",
        "unit": "kg",
        "standard_quantity": 2.5,
        "default_price": 410.0,
        "current_stock": 5.0,
        "is_active": True
    },
    {
        "name": "Condensed Milk",
        "category": "Dairy",
        "unit": "kg",
        "standard_quantity": 1.0,
        "default_price": 200.0,
        "current_stock": 10.0,
        "is_active": True
    },
    {
        "name": "Ice Cubes",
        "category": "Consumables",
        "unit": "kg",
        "standard_quantity": 5.0,
        "default_price": 100.0,
        "current_stock": 5.0,
        "is_active": True
    },
    {
        "name": "Drink Packaging (350ml Cup+Lid+Straw+Tissue)",
        "category": "Packaging",
        "unit": "pcs",
        "standard_quantity": 1.0,
        "default_price": 5.0,
        "current_stock": 500.0,
        "is_active": True
    }
]

inv_map = {}
for item in inventory_items_master:
    if item["name"] in existing_inv:
        inv_id = existing_inv[item["name"]]["id"]
        print(f"Existing item: '{item['name']}' with ID {inv_id}")
        inv_map[item["name"]] = inv_id
    else:
        res = post("inventory_items", item)
        inv_id = res[0]["id"]
        print(f"Inserted item: '{item['name']}' with ID {inv_id}")
        inv_map[item["name"]] = inv_id

print("\n--- 2. Menu Items & Recipes Setup ---")
menu_items = {m["name"]: m["id"] for m in get("menu_items?select=id,name")}

# Define recipes: MenuItemName -> list of (InventoryItemName, QuantityUsed, Unit)
recipes = {
    # 1. Hazelnut Iced Coffee
    "Hazelnut Iced Coffee": [
        ("Monin Hazelnut Syrup", 15.0, "ml"),
        ("Milk (Nandini Gold)", 120.0, "ml"),
        ("Ice Cubes", 100.0, "g"),
        ("Roasted Coffee Beans", 14.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 2. Vanila Iced Coffee
    "Vanila Iced Coffee": [
        ("Monin Vanilla Syrup", 15.0, "ml"),
        ("Milk (Nandini Gold)", 120.0, "ml"),
        ("Ice Cubes", 100.0, "g"),
        ("Roasted Coffee Beans", 14.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 3. Caramel Iced Coffee
    "Caramel Iced Coffee": [
        ("Monin Caramel Syrup", 15.0, "ml"),
        ("Milk (Nandini Gold)", 120.0, "ml"),
        ("Ice Cubes", 100.0, "g"),
        ("Roasted Coffee Beans", 14.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 4. Classic Iced Coffee
    "classic": [
        ("Milk (Nandini Gold)", 120.0, "ml"),
        ("Ice Cubes", 100.0, "g"),
        ("Roasted Coffee Beans", 14.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 5. Vanila Frappe
    "Vanila Frappe": [
        ("Vanilla Base Ice Cream", 250.0, "g"),
        ("Monin Vanilla Syrup", 15.0, "ml"),
        ("Roasted Coffee Beans", 14.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 6. Hazelnut Frappe
    "Hazelnut Frappe": [
        ("Vanilla Base Ice Cream", 250.0, "g"),
        ("Monin Hazelnut Syrup", 15.0, "ml"),
        ("Roasted Coffee Beans", 14.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 7. Caramel Frappe
    "Caramel Frappe": [
        ("Vanilla Base Ice Cream", 250.0, "g"),
        ("Monin Caramel Syrup", 15.0, "ml"),
        ("Roasted Coffee Beans", 14.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 8. Just Frappe
    "Just Frappe": [
        ("Vanilla Base Ice Cream", 250.0, "g"),
        ("Roasted Coffee Beans", 14.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 9. Vietnamese Iced Coffee
    "Vietnamese Iced Coffee": [
        ("Condensed Milk", 25.0, "g"),
        ("Milk (Nandini Gold)", 120.0, "ml"),
        ("Ice Cubes", 100.0, "g"),
        ("Roasted Coffee Beans", 14.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 10. OG Vietnamese
    "OG Vietnamese": [
        ("Condensed Milk", 35.0, "g"),
        ("Roasted Coffee Beans", 21.0, "g"),
        ("Ice Cubes", 150.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ],
    # 11. Americano
    "Americano": [
        ("Roasted Coffee Beans", 16.3, "g"),
        ("Ice Cubes", 100.0, "g"),
        ("Drink Packaging (350ml Cup+Lid+Straw+Tissue)", 1.0, "pcs")
    ]
}

# Clear any previous usages to avoid duplicates
delete("inventory_usage?id=gt.0")
print("Cleared old inventory_usage table.")

usage_inserts = []
for menu_name, ingredients in recipes.items():
    menu_id = menu_items.get(menu_name)
    if not menu_id:
        print(f"Warning: Menu item '{menu_name}' not found in menu_items table!")
        continue

    print(f"\nAdding recipe for '{menu_name}' (Menu ID {menu_id}):")
    for inv_name, qty, unit in ingredients:
        inv_id = inv_map.get(inv_name)
        if not inv_id:
            print(f"  Error: Inv item '{inv_name}' not found in inv_map!")
            continue

        usage_obj = {
            "menu_item_id": menu_id,
            "menu_item_name": menu_name,
            "inventory_item_id": inv_id,
            "inventory_item_name": inv_name,
            "quantity_used": qty,
            "unit": unit
        }
        usage_inserts.append(usage_obj)
        print(f"  + {inv_name}: {qty} {unit}")

if usage_inserts:
    inserted_usages = post("inventory_usage", usage_inserts)
    print(f"\nSuccessfully inserted {len(inserted_usages)} recipe components into inventory_usage!")

print("\n--- 3. Verification ---")
final_usages = get("inventory_usage?select=*")
print(f"Total active recipes components in Supabase: {len(final_usages)}")
for u in final_usages:
    print(f"  {u['menu_item_name']} -> {u['inventory_item_name']}: {u['quantity_used']} {u['unit']}")
