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

print("--- 1. Adding Lemon Iced Tea Sachet to inventory_items ---")
tea_item = {
    "name": "Lemon Iced Tea Sachet",
    "category": "Beverages",
    "unit": "pcs",
    "standard_quantity": 1.0,
    "default_price": 10.0,
    "current_stock": 100.0,
    "is_active": True
}

existing = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}
if tea_item["name"] not in existing:
    res = post("inventory_items", tea_item)
    tea_id = res[0]["id"]
    print(f"Created Lemon Iced Tea Sachet (ID {tea_id})")
else:
    tea_id = existing[tea_item["name"]]
    print(f"Lemon Iced Tea Sachet already exists (ID {tea_id})")

inv_map = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}

print("\n--- 2. Setting up Lemon ice tea recipe (Menu ID 2635) ---")
delete("inventory_usage?menu_item_id=eq.2635")

tea_usages = [
    {"menu_item_id": 2635, "menu_item_name": "Lemon ice tea", "inventory_item_id": tea_id, "inventory_item_name": "Lemon Iced Tea Sachet", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 2635, "menu_item_name": "Lemon ice tea", "inventory_item_id": inv_map["Ice Cubes"], "inventory_item_name": "Ice Cubes", "quantity_used": 100.0, "unit": "g"},
    {"menu_item_id": 2635, "menu_item_name": "Lemon ice tea", "inventory_item_id": inv_map["350ml Cup"], "inventory_item_name": "350ml Cup", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 2635, "menu_item_name": "Lemon ice tea", "inventory_item_id": inv_map["Cup Lid"], "inventory_item_name": "Cup Lid", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 2635, "menu_item_name": "Lemon ice tea", "inventory_item_id": inv_map["Thick Straw"], "inventory_item_name": "Thick Straw", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 2635, "menu_item_name": "Lemon ice tea", "inventory_item_id": inv_map["Straw Cover / Sleeve"], "inventory_item_name": "Straw Cover / Sleeve", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 2635, "menu_item_name": "Lemon ice tea", "inventory_item_id": inv_map["Tissue Paper"], "inventory_item_name": "Tissue Paper", "quantity_used": 1.0, "unit": "pcs"}
]

res = post("inventory_usage", tea_usages)
print(f"Successfully configured Lemon ice tea with {len(res)} ingredients!")
