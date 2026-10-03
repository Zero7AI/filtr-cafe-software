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

def patch(ep, data):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, data=json.dumps(data).encode(), method="PATCH")
    return json.loads(urllib.request.urlopen(req).read().decode())

print("--- 1. Updating Inventory Item ID 26 to Water Bottle (500ml) ---")
# 24 bottles for Rs. 130 -> std_qty: 24, default_price: 130.0 (Rs. 5.4167 per bottle)
res_inv = patch("inventory_items?id=eq.26", {
    "name": "Water Bottle (500ml)",
    "category": "Beverages",
    "unit": "pcs",
    "standard_quantity": 24.0,
    "default_price": 130.0,
    "is_active": True
})
print("Updated inventory_items ID 26:")
print(json.dumps(res_inv, indent=2))

print("\n--- 2. Updating Menu Item ID 152 ---")
res_menu = patch("menu_items?id=eq.152", {
    "name": "Water Bottle (500ml)",
    "category": "BEVERAGES",
    "description": "500ml Packaged Drinking Water",
    "price": 10.0
})
print("Updated menu_items ID 152:")
print(json.dumps(res_menu, indent=2))

print("\n--- 3. Updating Recipe Usage ID 107 ---")
res_usage = patch("inventory_usage?menu_item_id=eq.152", {
    "inventory_item_id": 26,
    "inventory_item_name": "Water Bottle (500ml)",
    "menu_item_name": "Water Bottle (500ml)",
    "quantity_used": 1.0,
    "unit": "pcs"
})
print("Updated inventory_usage for menu_item_id 152:")
print(json.dumps(res_usage, indent=2))

print("\n--- 4. Costing & Profit Margin Calculation ---")
cost_per_bottle = 130.0 / 24.0
selling_price = 10.0
gross_profit = selling_price - cost_per_bottle
margin_pct = (gross_profit / selling_price) * 100

print(f"Pack Rate: Rs. 130.00 for 24 bottles (500ml)")
print(f"Unit Cost per Bottle: Rs. {cost_per_bottle:.2f}")
print(f"Selling Price: Rs. {selling_price:.2f}")
print(f"Gross Margin per Bottle: Rs. {gross_profit:.2f} ({margin_pct:.1f}%)")
print("\nUpdate complete!")
