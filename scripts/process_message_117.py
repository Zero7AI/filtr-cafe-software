import urllib.request
import json
import sys
sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=representation"
}

def post(table, data):
    body = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{table}", data=body, headers=headers, method="POST")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

def patch(table, data):
    body = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{table}", data=body, headers=headers, method="PATCH")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

def get(table):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{table}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

# 1. Check if order INV-20260921-002 already exists
existing = get("inventory_orders?order_id=eq.INV-20260921-002")
if existing:
    print("Order INV-20260921-002 already exists:", existing)
else:
    # 2. Insert order header
    order_data = {
        "order_id": "INV-20260921-002",
        "timestamp": 1789987464583,
        "supplier_name": "WhatsApp Order",
        "total_amount": 270.0,
        "notes": "Via WhatsApp | Ice 3 packet - 270rs",
        "sync_status": "SYNCED"
    }
    res_order = post("inventory_orders", order_data)
    print("Created Order Header:", res_order)

    # 3. Insert order item
    item_data = {
        "order_id": "INV-20260921-002",
        "item_name": "Ice Cubes",
        "quantity": 3,
        "unit": "packet",
        "unit_price": 90.0,
        "subtotal": 270.0
    }
    res_item = post("inventory_order_items", item_data)
    print("Created Order Item:", res_item)

    # 4. Update stock for Ice Cubes (ID 9)
    inv_item = get("inventory_items?id=eq.9")[0]
    current_stock = float(inv_item.get("current_stock") or 0.0)
    new_stock = current_stock + 3.0
    res_inv = patch("inventory_items?id=eq.9", {"current_stock": new_stock})
    print(f"Updated Stock for Ice Cubes (ID 9): {current_stock} -> {new_stock} packs")

# 5. Update message 117 in whatsapp_messages
parsed_result = {
    "is_inventory": True,
    "supplier_name": "WhatsApp Order",
    "total_amount": 270.0,
    "notes": "Ice 3 packet - 270rs",
    "items": [
        {
            "item_name": "Ice Cubes",
            "quantity": 3,
            "unit": "packet",
            "unit_price": 90.0,
            "subtotal": 270.0,
            "catalog_item_id": 9
        }
    ]
}

res_msg = patch("whatsapp_messages?id=eq.117", {
    "status": "saved",
    "inventory_order_id": "INV-20260921-002",
    "parsed_result": parsed_result,
    "error_message": None
})
print("Updated WhatsApp Message 117:", res_msg)

print("\n--- Verification of Today's Inventory Orders (Sep 21, 2026) ---")
today_orders = get("inventory_orders?order_id=gte.INV-20260921&order=order_id.asc")
for o in today_orders:
    oid = o["order_id"]
    its = get(f"inventory_order_items?order_id=eq.{oid}")
    it_str = ", ".join([f"{it['item_name']} ({it['quantity']} {it['unit']} @ Rs.{it['unit_price']})" for it in its])
    print(f"[{oid}] {o['supplier_name']} | Rs.{o['total_amount']} | Notes: {o['notes']}")
    print(f"   Items: {it_str}")
