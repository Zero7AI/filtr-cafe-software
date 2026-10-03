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

def delete(ep):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, method="DELETE")
    return urllib.request.urlopen(req).read().decode()

print("--- 1. Adding Cranberry Juice / Drink to inventory_items ---")
cranberry_item = {
    "name": "Cranberry Juice / Drink",
    "category": "Beverages",
    "unit": "L",
    "standard_quantity": 1.0,
    "default_price": 140.0,
    "current_stock": 5.0,
    "is_active": True
}

existing = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}
if cranberry_item["name"] not in existing:
    res = post("inventory_items", cranberry_item)
    cran_id = res[0]["id"]
    print(f"Created Cranberry Juice / Drink (ID {cran_id})")
else:
    cran_id = existing[cranberry_item["name"]]
    print(f"Cranberry item already exists (ID {cran_id})")

inv_map = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}

print("\n--- 2. Setting up Cranberry Iced Coffe recipe (Menu ID 2586) ---")
delete("inventory_usage?menu_item_id=eq.2586")

# Setting 130 ml as balanced average of 120-150ml (leaves perfect 60ml headspace with ice and 60ml coffee in 350ml cup)
cranberry_usages = [
    {"menu_item_id": 2586, "menu_item_name": "Cranberry Iced Coffe", "inventory_item_id": cran_id, "inventory_item_name": "Cranberry Juice / Drink", "quantity_used": 130.0, "unit": "ml"},
    {"menu_item_id": 2586, "menu_item_name": "Cranberry Iced Coffe", "inventory_item_id": inv_map["Roasted Coffee Beans"], "inventory_item_name": "Roasted Coffee Beans", "quantity_used": 18.75, "unit": "g"},
    {"menu_item_id": 2586, "menu_item_name": "Cranberry Iced Coffe", "inventory_item_id": inv_map["Ice Cubes"], "inventory_item_name": "Ice Cubes", "quantity_used": 100.0, "unit": "g"},
    {"menu_item_id": 2586, "menu_item_name": "Cranberry Iced Coffe", "inventory_item_id": inv_map["350ml Cup"], "inventory_item_name": "350ml Cup", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 2586, "menu_item_name": "Cranberry Iced Coffe", "inventory_item_id": inv_map["Cup Lid"], "inventory_item_name": "Cup Lid", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 2586, "menu_item_name": "Cranberry Iced Coffe", "inventory_item_id": inv_map["Thick Straw"], "inventory_item_name": "Thick Straw", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 2586, "menu_item_name": "Cranberry Iced Coffe", "inventory_item_id": inv_map["Straw Cover / Sleeve"], "inventory_item_name": "Straw Cover / Sleeve", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 2586, "menu_item_name": "Cranberry Iced Coffe", "inventory_item_id": inv_map["Tissue Paper"], "inventory_item_name": "Tissue Paper", "quantity_used": 1.0, "unit": "pcs"}
]

res = post("inventory_usage", cranberry_usages)
print(f"Successfully configured Cranberry Iced Coffe with {len(res)} ingredients!")
