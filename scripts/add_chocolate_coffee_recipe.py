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

inv_map = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}

print("--- Setting up Iced Chocolate Coffee (Menu ID 119) ---")
delete("inventory_usage?menu_item_id=eq.119")

choc_usages = [
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Condensed Milk"], "inventory_item_name": "Condensed Milk", "quantity_used": 10.0, "unit": "g"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Veeba Chocolate Syrup 1.2kg"], "inventory_item_name": "Veeba Chocolate Syrup 1.2kg", "quantity_used": 20.0, "unit": "g"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Milk (Nandini Gold)"], "inventory_item_name": "Milk (Nandini Gold)", "quantity_used": 96.0, "unit": "ml"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Whipping Cream"], "inventory_item_name": "Whipping Cream", "quantity_used": 24.0, "unit": "ml"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Roasted Coffee Beans"], "inventory_item_name": "Roasted Coffee Beans", "quantity_used": 18.75, "unit": "g"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Ice Cubes"], "inventory_item_name": "Ice Cubes", "quantity_used": 100.0, "unit": "g"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["350ml Cup"], "inventory_item_name": "350ml Cup", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Cup Lid"], "inventory_item_name": "Cup Lid", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Thick Straw"], "inventory_item_name": "Thick Straw", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Straw Cover / Sleeve"], "inventory_item_name": "Straw Cover / Sleeve", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 119, "menu_item_name": "Iced Chocolate Coffee", "inventory_item_id": inv_map["Tissue Paper"], "inventory_item_name": "Tissue Paper", "quantity_used": 1.0, "unit": "pcs"}
]

res = post("inventory_usage", choc_usages)
print(f"Successfully configured Iced Chocolate Coffee with {len(res)} ingredients!")
