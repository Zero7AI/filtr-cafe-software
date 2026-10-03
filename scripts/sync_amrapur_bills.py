import urllib.request
import json
from datetime import datetime

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

print("--- 1. Updating Master Inventory Pricing from Amrapur Bills ---")
# 1. Monin Syrups: Bill shows Rs.730.00 / L
patch("inventory_items?name=eq.Monin%20Vanilla%20Syrup", {"default_price": 730.0})
patch("inventory_items?name=eq.Monin%20Hazelnut%20Syrup", {"default_price": 730.0})
patch("inventory_items?name=eq.Monin%20Caramel%20Syrup", {"default_price": 730.0})
print("Updated Monin Syrups default_price to Rs.730.00 / L")

# 2. Milk (Nandini Gold): Bill shows Rs.68.33 / L
patch("inventory_items?name=eq.Milk%20(Nandini%20Gold)", {"default_price": 68.33})
print("Updated Milk (Nandini Gold) default_price to Rs.68.33 / L")

# 3. Peri Peri Seasoning: Bill shows PERI PERI SPRINKLER 250GM @ Rs.135.00
patch("inventory_items?name=eq.Peri%20Peri%20Seasoning", {
    "standard_quantity": 250.0,
    "default_price": 135.0,
    "unit": "g"
})
print("Updated Peri Peri Seasoning to 250g @ Rs.135.00 (Rs.0.54 / g)")

# 4. Add newly discovered items from bills:
new_items = [
    {"name": "Spicefield Desi Chaat Masala 500g", "category": "Food", "unit": "g", "standard_quantity": 500.0, "default_price": 220.0, "current_stock": 1.0, "is_active": True},
    {"name": "Veeba Thousand Island Dressing 1kg", "category": "Food", "unit": "kg", "standard_quantity": 1.0, "default_price": 195.0, "current_stock": 3.0, "is_active": True},
    {"name": "Veeba White Cheese CC 1kg", "category": "Food", "unit": "kg", "standard_quantity": 1.0, "default_price": 150.0, "current_stock": 1.0, "is_active": True},
    {"name": "Black Olives (Sliced 450g)", "category": "Food", "unit": "g", "standard_quantity": 450.0, "default_price": 130.0, "current_stock": 4.0, "is_active": True}
]

existing_inv = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}
for item in new_items:
    if item["name"] not in existing_inv:
        created = post("inventory_items", item)
        print(f"Created item in master: {item['name']} (ID {created[0]['id']})")
    else:
        print(f"Item already exists in master: {item['name']}")

print("\n--- 2. Inserting Missing Purchase Orders into inventory_orders & inventory_order_items ---")

# Order 1: Bill A008666 (Date: 18/09/2026)
ts_1 = int(datetime(2026, 9, 18, 14, 0, 0).timestamp() * 1000)
order1_data = {
    "supplier_name": "SHRI-AMRAPUR",
    "total_amount": 1850.0,
    "order_date": "2026-09-18",
    "notes": "Estimate No: A008666, Date: 18/09/2026, Bill Status: CREDIT",
    "timestamp": ts_1
}
created_order1 = post("inventory_orders", order1_data)
order1_id = created_order1[0]["id"]
print(f"Created inventory order A008666 (ID {order1_id})")

order1_items = [
    {"order_id": order1_id, "item_name": "PERI PERI SPRINKLER 250GM", "quantity": 3.0, "unit": "PKT", "unit_price": 135.0, "subtotal": 405.0},
    {"order_id": order1_id, "item_name": "SPCEFIELD DESI CHAT MSL 500GM", "quantity": 1.0, "unit": "PCS", "unit_price": 220.0, "subtotal": 220.0},
    {"order_id": order1_id, "item_name": "NANDINI GOLD 1LTR <Net>", "quantity": 12.0, "unit": "PCS", "unit_price": 68.33, "subtotal": 820.0},
    {"order_id": order1_id, "item_name": "WHIPPING CREAM 1LTR(GOLD)", "quantity": 3.0, "unit": "PCS", "unit_price": 135.0, "subtotal": 405.0}
]
post("inventory_order_items", order1_items)
print(f"Added {len(order1_items)} items for order A008666")

# Order 2: Bill A007798 (Date: 03/09/2026)
ts_2 = int(datetime(2026, 9, 3, 14, 0, 0).timestamp() * 1000)
order2_data = {
    "supplier_name": "SHRI-AMRAPUR",
    "total_amount": 2510.0,
    "order_date": "2026-09-03",
    "notes": "Estimate No: A007798, Date: 03/09/2026, Bill Status: CREDIT",
    "timestamp": ts_2
}
created_order2 = post("inventory_orders", order2_data)
order2_id = created_order2[0]["id"]
print(f"Created inventory order A007798 (ID {order2_id})")

order2_items = [
    {"order_id": order2_id, "item_name": "MM DICED MOZARELLA 1KG", "quantity": 1.0, "unit": "PCS", "unit_price": 480.0, "subtotal": 480.0},
    {"order_id": order2_id, "item_name": "NANDINI GOLD 1LTR <Net>", "quantity": 24.0, "unit": "PCS", "unit_price": 68.33, "subtotal": 1640.0},
    {"order_id": order2_id, "item_name": "WHIPPING CREAM 1LTR(GOLD)", "quantity": 3.0, "unit": "PCS", "unit_price": 130.0, "subtotal": 390.0}
]
post("inventory_order_items", order2_items)
print(f"Added {len(order2_items)} items for order A007798")

print("\n--- DONE SYNCING AMRAPUR BILLS ---")
