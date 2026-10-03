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

def patch(table_and_query, data):
    body = json.dumps(data).encode('utf-8')
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{table_and_query}", data=body, headers=headers, method="PATCH")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

def delete(table_and_query):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{table_and_query}", headers=headers, method="DELETE")
    with urllib.request.urlopen(req) as resp:
        return resp.read().decode()

print("=== EXECUTING RECONCILIATION ===")

# 1. Delete duplicate orders and their items
dups = ["INV-20260913-003", "INV-20260914-009", "INV-20260916-005", "INV-20260922-001"]
for d in dups:
    print(f"Deleting items for {d}...")
    delete(f"inventory_order_items?order_id=eq.{d}")
    print(f"Deleting order header {d}...")
    delete(f"inventory_orders?order_id=eq.{d}")

print("✅ Duplicate orders deleted.")

# 2. Insert Real Order 1 for today (22-Sep): TNT Vanilla ₹400 from Message 121
order_121 = {
    "order_id": "INV-20260922-001",
    "timestamp": 1790074330200, # 22-Sep-2026 12:12:10 IST
    "supplier_name": "TNT Vanilla",
    "total_amount": 400.0,
    "notes": "Via WhatsApp | Date: 22/09/26 | TNT VANILLA - 1x400 - 400",
    "sync_status": "SYNCED"
}
post("inventory_orders", order_121)
item_121 = {
    "order_id": "INV-20260922-001",
    "item_name": "Vanilla Base Ice Cream",
    "quantity": 1.0,
    "unit": "pack",
    "unit_price": 400.0,
    "subtotal": 400.0
}
post("inventory_order_items", item_121)
print("✅ Created Real Order INV-20260922-001 (TNT Vanilla ₹400).")

# Update Message 121
patch("whatsapp_messages?id=eq.121", {
    "status": "saved",
    "inventory_order_id": "INV-20260922-001",
    "parsed_result": {
        "is_inventory": True,
        "supplier_name": "TNT Vanilla",
        "items": [
            {
                "item_name": "Vanilla Base Ice Cream",
                "quantity": 1.0,
                "unit": "pack",
                "unit_price": 400.0,
                "subtotal": 400.0,
                "catalog_item_id": 7
            }
        ],
        "total_amount": 400.0,
        "notes": "Date: 22/09/26 | TNT VANILLA 1x400"
    },
    "error_message": None
})
print("✅ Message 121 updated to saved.")

# 3. Insert Real Order 2 for today (22-Sep): Ice & Iced Tea ₹500 from Message 119
order_119 = {
    "order_id": "INV-20260922-002",
    "timestamp": 1790061665845, # 22-Sep-2026 08:41:05 IST
    "supplier_name": "WhatsApp Order",
    "total_amount": 500.0,
    "notes": "Via WhatsApp | Ice and peach ice tea, lemon ice tea - 500rw",
    "sync_status": "SYNCED"
}
post("inventory_orders", order_119)
item_119_a = {
    "order_id": "INV-20260922-002",
    "item_name": "Ice Cubes",
    "quantity": 1.0,
    "unit": "pack",
    "unit_price": 90.0,
    "subtotal": 90.0
}
item_119_b = {
    "order_id": "INV-20260922-002",
    "item_name": "Lemon Iced Tea & Peach Iced Tea",
    "quantity": 1.0,
    "unit": "pack",
    "unit_price": 410.0,
    "subtotal": 410.0
}
post("inventory_order_items", item_119_a)
post("inventory_order_items", item_119_b)
print("✅ Created Real Order INV-20260922-002 (Ice & Iced Tea ₹500).")

# Update Message 119
patch("whatsapp_messages?id=eq.119", {
    "status": "saved",
    "inventory_order_id": "INV-20260922-002",
    "parsed_result": {
        "is_inventory": True,
        "supplier_name": "WhatsApp Order",
        "items": [
            {
                "item_name": "Ice Cubes",
                "quantity": 1.0,
                "unit": "pack",
                "unit_price": 90.0,
                "subtotal": 90.0,
                "catalog_item_id": 9
            },
            {
                "item_name": "Lemon Iced Tea & Peach Iced Tea",
                "quantity": 1.0,
                "unit": "pack",
                "unit_price": 410.0,
                "subtotal": 410.0,
                "catalog_item_id": 35
            }
        ],
        "total_amount": 500.0,
        "notes": "Ice and peach ice tea, lemon ice tea - 500rw"
    },
    "error_message": None
})
print("✅ Message 119 updated to saved.")

# 4. Link duplicated messages to their original valid orders
patch("whatsapp_messages?id=eq.70", {
    "inventory_order_id": "INV-20260913-002",
    "error_message": "Duplicate of INV-20260913-002 (re-sent photo within 25s)"
})
patch("whatsapp_messages?id=eq.71", {
    "inventory_order_id": "INV-20260914-002",
    "error_message": "Duplicate of INV-20260914-002 (re-sent text message)"
})
patch("whatsapp_messages?id=eq.89", {
    "inventory_order_id": "INV-20260917-001",
    "error_message": "Duplicate of INV-20260917-001 (re-sent after initial Gemini 503 error)"
})
patch("whatsapp_messages?id=eq.120", {
    "inventory_order_id": "INV-20260920-003",
    "error_message": "Duplicate of INV-20260920-003 (re-sent bill photo dated 20/09/26)"
})
print("✅ Message records 70, 71, 89, 120 cleanly linked to original orders.")

# 5. Correct Stock Levels in inventory_items
patch("inventory_items?id=eq.7", {"current_stock": 2.7}) # Vanilla Ice Cream (2.7 kg)
patch("inventory_items?id=eq.9", {"current_stock": 8.55}) # Ice Cubes (8.55 kg)
print("✅ Stock levels for Vanilla Ice Cream (2.7 kg) and Ice Cubes (8.55 kg) synchronized.")

print("=== ALL RECONCILIATION TASKS COMPLETED SUCCESSFULLY ===")
