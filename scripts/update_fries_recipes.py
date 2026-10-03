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

print("--- 1. Updating Fries Price to Rs.280 for 2.5kg ---")
patch("inventory_items?name=eq.French%20Fries%20(Frozen%209mm)", {
    "default_price": 280.0,
    "standard_quantity": 2.5,
    "unit": "kg"
})

# Update Fries Pouch / Box price to Rs.4.50
patch("inventory_items?name=eq.Fries%20Pouch%20/%20Box", {
    "default_price": 4.50
})

# Add utility & fork if not already present
items_to_add = [
    {"name": "Fryer Electricity & Utility", "category": "Utility", "unit": "serving", "standard_quantity": 1.0, "default_price": 1.50, "current_stock": 10000.0, "is_active": True},
    {"name": "Wooden Fries Fork", "category": "Packaging", "unit": "pcs", "standard_quantity": 1.0, "default_price": 0.40, "current_stock": 1000.0, "is_active": True},
    {"name": "Salt & Pepper Seasoning", "category": "Food", "unit": "g", "standard_quantity": 100.0, "default_price": 25.0, "current_stock": 5.0, "is_active": True}
]

existing = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}
for it in items_to_add:
    if it["name"] not in existing:
        res = post("inventory_items", it)
        existing[it["name"]] = res[0]["id"]
        print(f"Created item: {it['name']} (ID {res[0]['id']})")
    else:
        print(f"Item exists: {it['name']} (ID {existing[it['name']]})")

inv_map = {i["name"]: i["id"] for i in get("inventory_items?select=id,name")}

print("\n--- 2. Setting Up Recipes for Salted Fries & Peri Peri Fries ---")
# Menu IDs: 11 = Salted Fries, 12 = Peri Peri Fries
delete("inventory_usage?menu_item_id=eq.11")
delete("inventory_usage?menu_item_id=eq.12")

# Salted Fries recipe
salted_usages = [
    {"menu_item_id": 11, "menu_item_name": "Salted Fries", "inventory_item_id": inv_map["French Fries (Frozen 9mm)"], "inventory_item_name": "French Fries (Frozen 9mm)", "quantity_used": 200.0, "unit": "g"},
    {"menu_item_id": 11, "menu_item_name": "Salted Fries", "inventory_item_id": inv_map["Sunflower Oil"], "inventory_item_name": "Sunflower Oil", "quantity_used": 18.4, "unit": "g"},
    {"menu_item_id": 11, "menu_item_name": "Salted Fries", "inventory_item_id": inv_map["Fryer Electricity & Utility"], "inventory_item_name": "Fryer Electricity & Utility", "quantity_used": 1.0, "unit": "serving"},
    {"menu_item_id": 11, "menu_item_name": "Salted Fries", "inventory_item_id": inv_map["Salt & Pepper Seasoning"], "inventory_item_name": "Salt & Pepper Seasoning", "quantity_used": 2.0, "unit": "g"},
    {"menu_item_id": 11, "menu_item_name": "Salted Fries", "inventory_item_id": inv_map["Fries Pouch / Box"], "inventory_item_name": "Fries Pouch / Box", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 11, "menu_item_name": "Salted Fries", "inventory_item_id": inv_map["Tissue Paper"], "inventory_item_name": "Tissue Paper", "quantity_used": 2.0, "unit": "pcs"},
    {"menu_item_id": 11, "menu_item_name": "Salted Fries", "inventory_item_id": inv_map["Wooden Fries Fork"], "inventory_item_name": "Wooden Fries Fork", "quantity_used": 1.0, "unit": "pcs"}
]

# Peri Peri Fries recipe
peri_usages = [
    {"menu_item_id": 12, "menu_item_name": "Peri Peri Fries", "inventory_item_id": inv_map["French Fries (Frozen 9mm)"], "inventory_item_name": "French Fries (Frozen 9mm)", "quantity_used": 200.0, "unit": "g"},
    {"menu_item_id": 12, "menu_item_name": "Peri Peri Fries", "inventory_item_id": inv_map["Sunflower Oil"], "inventory_item_name": "Sunflower Oil", "quantity_used": 18.4, "unit": "g"},
    {"menu_item_id": 12, "menu_item_name": "Peri Peri Fries", "inventory_item_id": inv_map["Fryer Electricity & Utility"], "inventory_item_name": "Fryer Electricity & Utility", "quantity_used": 1.0, "unit": "serving"},
    {"menu_item_id": 12, "menu_item_name": "Peri Peri Fries", "inventory_item_id": inv_map["Peri Peri Seasoning"], "inventory_item_name": "Peri Peri Seasoning", "quantity_used": 6.0, "unit": "g"},
    {"menu_item_id": 12, "menu_item_name": "Peri Peri Fries", "inventory_item_id": inv_map["Fries Pouch / Box"], "inventory_item_name": "Fries Pouch / Box", "quantity_used": 1.0, "unit": "pcs"},
    {"menu_item_id": 12, "menu_item_name": "Peri Peri Fries", "inventory_item_id": inv_map["Tissue Paper"], "inventory_item_name": "Tissue Paper", "quantity_used": 2.0, "unit": "pcs"},
    {"menu_item_id": 12, "menu_item_name": "Peri Peri Fries", "inventory_item_id": inv_map["Wooden Fries Fork"], "inventory_item_name": "Wooden Fries Fork", "quantity_used": 1.0, "unit": "pcs"}
]

post("inventory_usage", salted_usages)
post("inventory_usage", peri_usages)
print("Successfully inserted recipes for Salted Fries & Peri Peri Fries!")
