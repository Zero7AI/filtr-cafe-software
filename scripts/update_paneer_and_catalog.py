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

def post(ep, data):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, data=json.dumps(data).encode(), method="POST")
    return json.loads(urllib.request.urlopen(req).read().decode())

def patch(ep, data):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, data=json.dumps(data).encode(), method="PATCH")
    return json.loads(urllib.request.urlopen(req).read().decode())

def delete(ep):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, method="DELETE")
    return urllib.request.urlopen(req).read().decode()

print("--- 1. Updating Master Catalog from WhatsApp Invoices ---")
# Update Sandwich Bread: 16 slices per Rs.65 pack
patch("inventory_items?id=eq.18", {"standard_quantity": 16.0, "default_price": 65.0, "unit": "slices"})
print("Updated ID 18 (Sandwich Bread): 16 slices @ Rs.65")

# Update Ice Cubes: Rs.90 per 5kg packet
patch("inventory_items?id=eq.9", {"default_price": 90.0, "standard_quantity": 5.0, "unit": "kg"})
print("Updated ID 9 (Ice Cubes): 5kg @ Rs.90")

# Update Ice Cream: Rs.405 per 2.5kg tub
patch("inventory_items?id=eq.7", {"default_price": 405.0, "standard_quantity": 2.5, "unit": "kg"})
print("Updated ID 7 (Vanilla Ice Cream): 2.5kg @ Rs.405")

# Update Condensed Milk: Rs.76 per 210g tin
patch("inventory_items?id=eq.8", {"default_price": 76.0, "standard_quantity": 210.0, "unit": "g"})
print("Updated ID 8 (Condensed Milk): 210g @ Rs.76")

# Update Coffee Beans: Rs.682.50 per kg (Rs.13,650 for 20kg incl. GST from Beanster Invoice R26/725)
patch("inventory_items?id=eq.1", {"default_price": 682.50, "standard_quantity": 1.0, "unit": "kg"})
print("Updated ID 1 (Coffee Beans): 1kg @ Rs.682.50 (Beanster Raipur Invoice R26/725)")

print("\n--- 2. Adding New Master Inventory Items for Sandwiches ---")
new_items = [
    {
        "id": 40,
        "name": "Sandwich Chutney (Mint Coriander)",
        "category": "Food",
        "unit": "serving",
        "standard_quantity": 1.0,
        "default_price": 5.0,
        "current_stock": 50.0,
        "is_active": True
    },
    {
        "id": 41,
        "name": "Sandwich Veggies (Cucumber & Onion)",
        "category": "Food",
        "unit": "serving",
        "standard_quantity": 1.0,
        "default_price": 2.5,
        "current_stock": 50.0,
        "is_active": True
    },
    {
        "id": 42,
        "name": "Sandwich Seasoning (Oregano & Chili Flakes)",
        "category": "Food",
        "unit": "serving",
        "standard_quantity": 1.0,
        "default_price": 0.8,
        "current_stock": 100.0,
        "is_active": True
    }
]

for item in new_items:
    try:
        res = post("inventory_items", item)
        print(f"Inserted item ID {item['id']}: {item['name']}")
    except Exception as e:
        print(f"Item ID {item['id']} already exists or error: {e}")

print("\n--- 3. Configuring Recipe for Paneer Sandwich (Menu ID 16) ---")
# Remove any existing usages for menu_item_id 16 to be clean
delete("inventory_usage?menu_item_id=eq.16")

paneer_recipe = [
    # 2 slices of bread (out of 16 slices @ Rs.65 = Rs.8.125)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 18, "inventory_item_name": "Sandwich Bread", "quantity_used": 2.0, "unit": "slices"},
    # 50g fresh paneer (Rs.360/kg = Rs.18.00)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 19, "inventory_item_name": "Fresh Paneer", "quantity_used": 50.0, "unit": "g"},
    # 20g butter (Rs.90/500g = Rs.3.60)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 25, "inventory_item_name": "Butter (Nutralite)", "quantity_used": 20.0, "unit": "g"},
    # Mint coriander sandwich chutney (Rs.5.00/serving)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 40, "inventory_item_name": "Sandwich Chutney (Mint Coriander)", "quantity_used": 1.0, "unit": "serving"},
    # Black olives: 7 pieces = 22.5g (1/20th of 450g bottle @ Rs.130 = Rs.6.50)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 39, "inventory_item_name": "Black Olives (Sliced 450g)", "quantity_used": 22.5, "unit": "g"},
    # Veeba Thousand Island Dressing: 20g (Rs.195/kg = Rs.3.90)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 37, "inventory_item_name": "Veeba Thousand Island Dressing 1kg", "quantity_used": 20.0, "unit": "g"},
    # Veggies: Cucumber & Onion 6-7 slices (Rs.2.50)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 41, "inventory_item_name": "Sandwich Veggies (Cucumber & Onion)", "quantity_used": 1.0, "unit": "serving"},
    # Seasoning: Oregano & Chili Flakes (Rs.0.80)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 42, "inventory_item_name": "Sandwich Seasoning (Oregano & Chili Flakes)", "quantity_used": 1.0, "unit": "serving"},
    # Sandwich Wrap / Box (Rs.3.00)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 29, "inventory_item_name": "Sandwich Paper Wrap / Box", "quantity_used": 1.0, "unit": "pcs"},
    # 2 Tissue papers (Rs.0.60)
    {"menu_item_id": 16, "menu_item_name": "Paneer Sandwich", "inventory_item_id": 15, "inventory_item_name": "Tissue Paper", "quantity_used": 2.0, "unit": "pcs"}
]

for r in paneer_recipe:
    res = post("inventory_usage", r)
    print(f"Added usage: {r['inventory_item_name']} -> {r['quantity_used']} {r['unit']}")

print("\n=== PANEER SANDWICH RECIPE SAVED SUCCESSFULLY! ===")
