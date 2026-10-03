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

def get(ep):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers)
    return json.loads(urllib.request.urlopen(req).read().decode())

def post(ep, data):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", data=json.dumps(data).encode(), headers=headers, method="POST")
    return json.loads(urllib.request.urlopen(req).read().decode())

def patch(ep, data):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", data=json.dumps(data).encode(), headers=headers, method="PATCH")
    return json.loads(urllib.request.urlopen(req).read().decode())

def delete(ep):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, method="DELETE")
    return urllib.request.urlopen(req).read().decode()

print("--- 1. Updating Condensed Milk in inventory_items ---")
# User requirement: Rs.80 for 210 grams
patch("inventory_items?name=eq.Condensed%20Milk", {
    "unit": "g",
    "standard_quantity": 210.0,
    "default_price": 80.0
})
print("Updated Condensed Milk: unit='g', standard_quantity=210.0, default_price=80.0 (Rs.0.381 / g)")

# Also update Whipping Cream to Rs.135 / L as per bills (or keep 130)
patch("inventory_items?name=eq.Whipping%20Cream", {
    "default_price": 135.0
})
print("Updated Whipping Cream default_price to Rs.135.0 / L")

# Update Roasted Coffee Beans to Rs.650 / kg
patch("inventory_items?name=eq.Roasted%20Coffee%20Beans", {
    "default_price": 650.0
})
print("Updated Roasted Coffee Beans default_price to Rs.650.0 / kg")

print("\n--- 2. Updating Milk and Adding Whipping Cream to 5 Milk-Based Iced Coffees ---")
# Milk base = 1L Nandini Gold (Rs.70) + 250g Whipping Cream (Rs.135) = 1250 ml
# Per 120 ml serving: 96 ml Milk + 24 ml Whipping Cream
milk_drinks = [
    ("Hazelnut Iced Coffee", 3),
    ("Vanila Iced Coffee", 2),
    ("Caramel Iced Coffee", 4),
    ("classic", 2418),
    ("Vietnamese Iced Coffee", 5)
]

# Fetch Whipping Cream inv id
inv_items = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}
cream_id = inv_items["Whipping Cream"]
milk_id = inv_items["Milk (Nandini Gold)"]

# First update existing Milk usage quantity to 96 ml
for drink_name, menu_id in milk_drinks:
    patch(f"inventory_usage?menu_item_id=eq.{menu_id}&inventory_item_id=eq.{milk_id}", {
        "quantity_used": 96.0,
        "unit": "ml"
    })
    print(f"Updated Milk for '{drink_name}' to 96 ml")

# Remove any previous whipping cream usage for these drinks to prevent duplicates
for drink_name, menu_id in milk_drinks:
    delete(f"inventory_usage?menu_item_id=eq.{menu_id}&inventory_item_id=eq.{cream_id}")

# Add 24 ml Whipping Cream usage for each of the 5 drinks
new_cream_usages = []
for drink_name, menu_id in milk_drinks:
    new_cream_usages.append({
        "menu_item_id": menu_id,
        "menu_item_name": drink_name,
        "inventory_item_id": cream_id,
        "inventory_item_name": "Whipping Cream",
        "quantity_used": 24.0,
        "unit": "ml"
    })

post("inventory_usage", new_cream_usages)
print(f"Added 24 ml Whipping Cream usage to {len(milk_drinks)} drinks.")

print("\n--- 3. Adding New Inventory Master Items for Food & Beverages ---")
new_master_items = [
    {"name": "French Fries (Frozen 9mm)", "category": "Food", "unit": "kg", "standard_quantity": 2.5, "default_price": 270.0, "current_stock": 5.0, "is_active": True},
    {"name": "Peri Peri Seasoning", "category": "Food", "unit": "g", "standard_quantity": 200.0, "default_price": 120.0, "current_stock": 1.0, "is_active": True},
    {"name": "Sandwich Bread", "category": "Food", "unit": "slices", "standard_quantity": 12.0, "default_price": 65.0, "current_stock": 5.0, "is_active": True},
    {"name": "Fresh Paneer", "category": "Dairy", "unit": "kg", "standard_quantity": 1.0, "default_price": 360.0, "current_stock": 2.0, "is_active": True},
    {"name": "Sweet Corn", "category": "Food", "unit": "kg", "standard_quantity": 1.0, "default_price": 70.0, "current_stock": 3.0, "is_active": True},
    {"name": "Mozzarella Cheese (Diced)", "category": "Dairy", "unit": "kg", "standard_quantity": 1.0, "default_price": 480.0, "current_stock": 2.0, "is_active": True},
    {"name": "Veeba White Cheese / Mayo", "category": "Food", "unit": "kg", "standard_quantity": 1.0, "default_price": 185.0, "current_stock": 2.0, "is_active": True},
    {"name": "Veeba Chocolate Syrup 1.2kg", "category": "Syrups", "unit": "kg", "standard_quantity": 1.2, "default_price": 200.0, "current_stock": 1.0, "is_active": True},
    {"name": "Sunflower Oil", "category": "Food", "unit": "g", "standard_quantity": 800.0, "default_price": 152.0, "current_stock": 3.0, "is_active": True},
    {"name": "Butter (Nutralite)", "category": "Dairy", "unit": "g", "standard_quantity": 500.0, "default_price": 90.0, "current_stock": 2.0, "is_active": True},
    {"name": "Water Bottle (250ml)", "category": "Beverages", "unit": "pcs", "standard_quantity": 1.0, "default_price": 4.58, "current_stock": 48.0, "is_active": True},
    {"name": "Hot Paper Cup 8oz", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 2.5, "current_stock": 200.0, "is_active": True},
    {"name": "Hot Cup Lid 8oz", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 1.6, "current_stock": 200.0, "is_active": True},
    {"name": "Sandwich Paper Wrap / Box", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 3.0, "current_stock": 500.0, "is_active": True},
    {"name": "Fries Pouch / Box", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 2.0, "current_stock": 500.0, "is_active": True}
]

existing_inv = {item['name']: item for item in get("inventory_items?select=*")}
for item in new_master_items:
    if item["name"] in existing_inv:
        print(f"Item already exists in master: {item['name']} (ID {existing_inv[item['name']]['id']})")
    else:
        created = post("inventory_items", item)
        print(f"Created master item: {item['name']} (ID {created[0]['id']})")

# Add direct usage for Water Bottle (Menu ID 152)
inv_items = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}
wb_inv_id = inv_items.get("Water Bottle (250ml)")
if wb_inv_id:
    delete("inventory_usage?menu_item_id=eq.152")
    post("inventory_usage", [{
        "menu_item_id": 152,
        "menu_item_name": "Water Bottle",
        "inventory_item_id": wb_inv_id,
        "inventory_item_name": "Water Bottle (250ml)",
        "quantity_used": 1.0,
        "unit": "pcs"
    }])
    print("Configured recipe usage for 'Water Bottle' (Menu ID 152) -> 1 pc Water Bottle (250ml)")

print("\n--- DONE UPDATING SUPABASE ---")
