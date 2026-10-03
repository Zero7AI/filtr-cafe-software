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

def delete(ep):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{ep}", headers=headers, method="DELETE")
    return urllib.request.urlopen(req).read().decode()

print("--- 1. Adding Bill A008666 (INV-20260918-004) ---")
# Check if already exists
existing = get("inventory_orders?order_id=eq.INV-20260918-004")
if not existing:
    order1 = {
        "order_id": "INV-20260918-004",
        "timestamp": 1788595200000,
        "supplier_name": "SHRI-AMRAPUR",
        "total_amount": 1850.0,
        "notes": "Estimate No: A008666, Date: 18/09/2026, Bill Status: CREDIT",
        "sync_status": "SYNCED"
    }
    post("inventory_orders", order1)
    print("Created inventory order INV-20260918-004")

    order1_items = [
        {"order_id": "INV-20260918-004", "item_name": "PERI PERI SPRINKLER 250GM", "quantity": 3.0, "unit": "PKT", "unit_price": 135.0, "subtotal": 405.0},
        {"order_id": "INV-20260918-004", "item_name": "SPCEFIELD DESI CHAT MSL 500GM", "quantity": 1.0, "unit": "PCS", "unit_price": 220.0, "subtotal": 220.0},
        {"order_id": "INV-20260918-004", "item_name": "NANDINI GOLD 1LTR <Net>", "quantity": 12.0, "unit": "PCS", "unit_price": 68.33, "subtotal": 820.0},
        {"order_id": "INV-20260918-004", "item_name": "WHIPPING CREAM 1LTR(GOLD)", "quantity": 3.0, "unit": "PCS", "unit_price": 135.0, "subtotal": 405.0}
    ]
    post("inventory_order_items", order1_items)
    print("Added 4 items for INV-20260918-004")
else:
    print("INV-20260918-004 already exists")

print("\n--- 2. Updating Bill A007798 (INV-20260903-002) with exact items ---")
patch("inventory_orders?order_id=eq.INV-20260903-002", {
    "supplier_name": "SHRI-AMRAPUR",
    "notes": "Estimate No: A007798, Date: 03/09/2026, Bill Status: CREDIT"
})

delete("inventory_order_items?order_id=eq.INV-20260903-002")

order2_items = [
    {"order_id": "INV-20260903-002", "item_name": "MM DICED MOZARELLA 1KG", "quantity": 1.0, "unit": "PCS", "unit_price": 480.0, "subtotal": 480.0},
    {"order_id": "INV-20260903-002", "item_name": "NANDINI GOLD 1LTR <Net>", "quantity": 24.0, "unit": "PCS", "unit_price": 68.33, "subtotal": 1640.0},
    {"order_id": "INV-20260903-002", "item_name": "WHIPPING CREAM 1LTR(GOLD)", "quantity": 3.0, "unit": "PCS", "unit_price": 130.0, "subtotal": 390.0}
]
post("inventory_order_items", order2_items)
print("Updated INV-20260903-002 with exact items from bill A007798")

print("\n--- DONE UPDATING INVENTORY ORDERS ---")
