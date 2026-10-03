import urllib.request
import json
import sys
from datetime import datetime

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=representation"
}

def get(table_and_query):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{table_and_query}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

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

print("=== RECONCILIATION AUDIT & PREPARATION ===")

# Check current stock
items = get("inventory_items?id=in.(2,3,7,9,16,35)&order=id.asc")
print("\nCurrent Stock before changes:")
for it in items:
    print(f"  ID {it['id']:2d}: {it['name']} -> {it['current_stock']} {it['unit']}")

# Check orders to delete
dups = ["INV-20260913-003", "INV-20260914-009", "INV-20260916-005", "INV-20260922-001"]
print("\nVerifying orders marked as duplicates to remove:")
for d in dups:
    ord_rows = get(f"inventory_orders?order_id=eq.{d}")
    if ord_rows:
        o = ord_rows[0]
        print(f"  Found {d}: ₹{o['total_amount']} | {o.get('supplier_name')} | {o.get('notes')}")
    else:
        print(f"  Not found: {d}")

print("\nVerifying Messages to update/link:")
m121 = get("whatsapp_messages?id=eq.121")[0]
print(f"  Msg 121: Status={m121['status']}, MediaID={m121.get('parsed_result',{}).get('media_id')}")

m119 = get("whatsapp_messages?id=eq.119")[0]
print(f"  Msg 119: Status={m119['status']}, Body='{m119['message_body']}'")

m70 = get("whatsapp_messages?id=eq.70")[0]
print(f"  Msg 70: OrderID={m70['inventory_order_id']} -> will point to INV-20260913-002")

m71 = get("whatsapp_messages?id=eq.71")[0]
print(f"  Msg 71: OrderID={m71['inventory_order_id']} -> will point to INV-20260914-002")

m89 = get("whatsapp_messages?id=eq.89")[0]
print(f"  Msg 89: OrderID={m89['inventory_order_id']} -> will point to INV-20260917-001")

m120 = get("whatsapp_messages?id=eq.120")[0]
print(f"  Msg 120: OrderID={m120['inventory_order_id']} -> will point to INV-20260920-003 (re-sent photo of 20/09)")

