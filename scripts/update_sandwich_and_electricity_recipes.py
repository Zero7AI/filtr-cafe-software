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
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, data=json.dumps(data).encode(), method="POST")
    return json.loads(urllib.request.urlopen(req).read().decode())

def patch(ep, data):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, data=json.dumps(data).encode(), method="PATCH")
    return json.loads(urllib.request.urlopen(req).read().decode())

def delete(ep):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, method="DELETE")
    return urllib.request.urlopen(req).read().decode()

print("================================================================")
print("1. REMOVING ELECTRICITY USAGES FROM FRIES & RECIPES")
print("================================================================")
deleted = delete("inventory_usage?inventory_item_id=eq.31")
print(f"Deleted electricity usage entries for Fryer Electricity & Utility (ID 31): {deleted}")

# Deactivate Fryer Electricity & Utility item
patch("inventory_items?id=eq.31", {"is_active": False, "default_price": 0.0})
print("Marked Fryer Electricity & Utility (ID 31) as inactive (accounted in Rs.6,000/mo fixed cost).")

print("\n================================================================")
print("2. UPDATING SEASONING PRICING TO RS. 2.00")
print("================================================================")
patch("inventory_items?id=eq.42", {"default_price": 2.00})
print("Updated ID 42 (Sandwich Seasoning: Oregano & Chili Flakes) default_price to Rs.2.00")

print("\n================================================================")
print("3. ENSURING INVENTORY ITEMS FOR SANDWICHES")
print("================================================================")
all_items = get("inventory_items?select=id,name")
inv_items = {i["name"]: i["id"] for i in all_items}

# Bombay Aloo Masala
if "Bombay Aloo Masala" not in inv_items:
    res = post("inventory_items", {
        "id": 43,
        "name": "Bombay Aloo Masala",
        "category": "Food",
        "unit": "g",
        "standard_quantity": 1000.0,
        "default_price": 50.0,
        "current_stock": 10.0,
        "is_active": True
    })
    aloo_masala_id = res[0]["id"]
    print(f"Created 'Bombay Aloo Masala' (ID {aloo_masala_id})")
else:
    aloo_masala_id = inv_items["Bombay Aloo Masala"]
    print(f"'Bombay Aloo Masala' already exists (ID {aloo_masala_id})")

# Cream Cheese
if "Cream Cheese" not in inv_items:
    res = post("inventory_items", {
        "id": 44,
        "name": "Cream Cheese",
        "category": "Food",
        "unit": "g",
        "standard_quantity": 1000.0,
        "default_price": 185.0,
        "current_stock": 5.0,
        "is_active": True
    })
    cream_cheese_id = res[0]["id"]
    print(f"Created 'Cream Cheese' (ID {cream_cheese_id})")
else:
    cream_cheese_id = inv_items["Cream Cheese"]
    print(f"'Cream Cheese' already exists (ID {cream_cheese_id})")

# Cheese Sauce / Dip
if "Cheese Sauce / Dip" not in inv_items:
    res = post("inventory_items", {
        "id": 45,
        "name": "Cheese Sauce / Dip",
        "category": "Food",
        "unit": "g",
        "standard_quantity": 1000.0,
        "default_price": 150.0,
        "current_stock": 5.0,
        "is_active": True
    })
    cheese_dip_id = res[0]["id"]
    print(f"Created 'Cheese Sauce / Dip' (ID {cheese_dip_id})")
else:
    cheese_dip_id = inv_items["Cheese Sauce / Dip"]
    print(f"'Cheese Sauce / Dip' already exists (ID {cheese_dip_id})")

# Refresh inv map
inv_map = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}

print("\n================================================================")
print("4. CONFIGURING RECIPE FOR CHEESE CORN SANDWICH (Menu ID 14)")
print("================================================================")
delete("inventory_usage?menu_item_id=eq.14")

cheese_corn_recipe = [
    # 2 slices sandwich bread (out of 16 slices @ Rs.65 = Rs.8.125)
    {"menu_item_id": 14, "menu_item_name": "Cheese Corn", "inventory_item_id": inv_map["Sandwich Bread"], "inventory_item_name": "Sandwich Bread", "quantity_used": 2.0, "unit": "slices"},
    # 70g sweet corn (Rs.70/1kg = Rs.4.90) - No electricity added as it's in fixed cost
    {"menu_item_id": 14, "menu_item_name": "Cheese Corn", "inventory_item_id": inv_map["Sweet Corn"], "inventory_item_name": "Sweet Corn", "quantity_used": 70.0, "unit": "g"},
    # 15g cream cheese (Rs.185/1kg = Rs.2.775)
    {"menu_item_id": 14, "menu_item_name": "Cheese Corn", "inventory_item_id": cream_cheese_id, "inventory_item_name": "Cream Cheese", "quantity_used": 15.0, "unit": "g"},
    # 20g mozzarella cheese (Rs.480/1kg = Rs.9.60)
    {"menu_item_id": 14, "menu_item_name": "Cheese Corn", "inventory_item_id": inv_map["Mozzarella Cheese (Diced)"], "inventory_item_name": "Mozzarella Cheese (Diced)", "quantity_used": 20.0, "unit": "g"},
    # 20g butter (Rs.90/500g = Rs.3.60)
    {"menu_item_id": 14, "menu_item_name": "Cheese Corn", "inventory_item_id": inv_map["Butter (Nutralite)"], "inventory_item_name": "Butter (Nutralite)", "quantity_used": 20.0, "unit": "g"},
    # Mint coriander chutney (Rs.5.00/sandwich)
    {"menu_item_id": 14, "menu_item_name": "Cheese Corn", "inventory_item_id": inv_map["Sandwich Chutney (Mint Coriander)"], "inventory_item_name": "Sandwich Chutney (Mint Coriander)", "quantity_used": 1.0, "unit": "serving"},
    # Oregano & chili flakes spices (Rs.2.00/sandwich)
    {"menu_item_id": 14, "menu_item_name": "Cheese Corn", "inventory_item_id": inv_map["Sandwich Seasoning (Oregano & Chili Flakes)"], "inventory_item_name": "Sandwich Seasoning (Oregano & Chili Flakes)", "quantity_used": 1.0, "unit": "serving"},
    # Sandwich Wrap / Box (Rs.3.00)
    {"menu_item_id": 14, "menu_item_name": "Cheese Corn", "inventory_item_id": inv_map["Sandwich Paper Wrap / Box"], "inventory_item_name": "Sandwich Paper Wrap / Box", "quantity_used": 1.0, "unit": "pcs"},
    # 2 Tissue papers (Rs.0.60)
    {"menu_item_id": 14, "menu_item_name": "Cheese Corn", "inventory_item_id": inv_map["Tissue Paper"], "inventory_item_name": "Tissue Paper", "quantity_used": 2.0, "unit": "pcs"}
]

res1 = post("inventory_usage", cheese_corn_recipe)
print(f"Added {len(res1)} ingredients to Cheese Corn Sandwich recipe!")

print("\n================================================================")
print("5. CONFIGURING RECIPE FOR BOMBAY ALOO SANDWICH (Menu ID 13)")
print("================================================================")
delete("inventory_usage?menu_item_id=eq.13")

bombay_aloo_recipe = [
    # 2 slices sandwich bread (out of 16 slices @ Rs.65 = Rs.8.125)
    {"menu_item_id": 13, "menu_item_name": "Bombay", "inventory_item_id": inv_map["Sandwich Bread"], "inventory_item_name": "Sandwich Bread", "quantity_used": 2.0, "unit": "slices"},
    # 70g Bombay masala / mashed aloo (Rs.50/1kg = Rs.3.50)
    {"menu_item_id": 13, "menu_item_name": "Bombay", "inventory_item_id": aloo_masala_id, "inventory_item_name": "Bombay Aloo Masala", "quantity_used": 70.0, "unit": "g"},
    # 15g cheese sauce / cheese dip (Rs.150/1kg = Rs.2.25)
    {"menu_item_id": 13, "menu_item_name": "Bombay", "inventory_item_id": cheese_dip_id, "inventory_item_name": "Cheese Sauce / Dip", "quantity_used": 15.0, "unit": "g"},
    # 20g butter on both sides (Rs.90/500g = Rs.3.60)
    {"menu_item_id": 13, "menu_item_name": "Bombay", "inventory_item_id": inv_map["Butter (Nutralite)"], "inventory_item_name": "Butter (Nutralite)", "quantity_used": 20.0, "unit": "g"},
    # Green sandwich chutney (Rs.5.00/sandwich)
    {"menu_item_id": 13, "menu_item_name": "Bombay", "inventory_item_id": inv_map["Sandwich Chutney (Mint Coriander)"], "inventory_item_name": "Sandwich Chutney (Mint Coriander)", "quantity_used": 1.0, "unit": "serving"},
    # Oregano & chili flakes spices (Rs.2.00/sandwich)
    {"menu_item_id": 13, "menu_item_name": "Bombay", "inventory_item_id": inv_map["Sandwich Seasoning (Oregano & Chili Flakes)"], "inventory_item_name": "Sandwich Seasoning (Oregano & Chili Flakes)", "quantity_used": 1.0, "unit": "serving"},
    # Sandwich Wrap / Box (Rs.3.00)
    {"menu_item_id": 13, "menu_item_name": "Bombay", "inventory_item_id": inv_map["Sandwich Paper Wrap / Box"], "inventory_item_name": "Sandwich Paper Wrap / Box", "quantity_used": 1.0, "unit": "pcs"},
    # 2 Tissue papers (Rs.0.60)
    {"menu_item_id": 13, "menu_item_name": "Bombay", "inventory_item_id": inv_map["Tissue Paper"], "inventory_item_name": "Tissue Paper", "quantity_used": 2.0, "unit": "pcs"}
]

res2 = post("inventory_usage", bombay_aloo_recipe)
print(f"Added {len(res2)} ingredients to Bombay Aloo Sandwich recipe!")

print("\n================================================================")
print("6. VERIFYING ALL SANDWICH & FRIES COGS")
print("================================================================")
inv_full = {i["id"]: i for i in get("inventory_items?select=*")}
usages_all = get("inventory_usage?select=*")

for menu_id, menu_name, price in [
    (11, "Salted Fries", 89),
    (12, "Peri Peri Fries", 99),
    (16, "Paneer Sandwich", 119),
    (14, "Cheese Corn", 99),
    (13, "Bombay", 89)
]:
    u_list = [u for u in usages_all if u["menu_item_id"] == menu_id]
    cogs = 0.0
    print(f"\n--- {menu_name} (Selling Price: Rs.{price}) ---")
    for u in u_list:
        inv = inv_full.get(u["inventory_item_id"])
        if not inv:
            print(f"  Missing inv item {u['inventory_item_id']}")
            continue
        std_qty = inv["standard_quantity"] or 1.0
        def_price = inv["default_price"] or 0.0
        q = u["quantity_used"]
        u_unit = u["unit"].lower()
        i_unit = inv["unit"].lower()

        if u_unit == "g" and i_unit == "kg":
            cost = (q / (std_qty * 1000.0)) * def_price
        elif u_unit == "ml" and i_unit == "l":
            cost = (q / (std_qty * 1000.0)) * def_price
        else:
            cost = (q / std_qty) * def_price

        cogs += cost
        print(f"  - {u['inventory_item_name']:35s}: {q:6.1f} {u['unit']:7s} -> Rs.{cost:5.2f}")

    margin = price - cogs
    margin_pct = (margin / price) * 100
    print(f"  TOTAL COGS: Rs.{cogs:.2f} | GROSS PROFIT: Rs.{margin:.2f} ({margin_pct:.1f}%)")

print("\nAll recipes verified successfully!")
