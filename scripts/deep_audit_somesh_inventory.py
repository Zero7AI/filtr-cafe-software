import urllib.request
import json
import sys
from datetime import datetime
from collections import defaultdict

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def fetch_supabase(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

print("=== 1. FETCHING ALL WHATSAPP MESSAGES ===")
msgs = fetch_supabase("whatsapp_messages?order=id.asc&limit=1000")
print(f"Total WhatsApp messages in DB: {len(msgs)}")

somesh_msgs = [m for m in msgs if "9907839311" in (m.get('from_number') or '')]
print(f"Total messages from Somesh (9907839311): {len(somesh_msgs)}")

status_counts = defaultdict(int)
type_counts = defaultdict(int)
for m in somesh_msgs:
    status_counts[m.get('status', 'unknown')] += 1
    type_counts[m.get('message_type', 'unknown')] += 1

print("\n--- Message Status Breakdown ---")
for k, v in sorted(status_counts.items()):
    print(f"  {k}: {v}")

print("\n--- Message Type Breakdown ---")
for k, v in sorted(type_counts.items()):
    print(f"  {k}: {v}")

image_msgs = [m for m in somesh_msgs if m.get('message_type') == 'image']
print(f"\nTotal image messages: {len(image_msgs)}")
img_status = defaultdict(int)
for m in image_msgs:
    img_status[m.get('status', 'unknown')] += 1
print("Image status breakdown:")
for k, v in sorted(img_status.items()):
    print(f"  {k}: {v}")

print("\n=== 2. ALL IMAGE MESSAGES DETAILS ===")
for m in image_msgs:
    p = m.get('parsed_result') or {}
    print(f"ID {m['id']} | {m['created_at'][:19]} | Status: {m.get('status')} | Order: {m.get('inventory_order_id')} | MediaID: {p.get('media_id')} | Error: {m.get('error_message')}")
    if p.get('items'):
        print(f"   Items: {p.get('items')}")
    elif p.get('notes'):
        print(f"   Notes: {p.get('notes')}")

print("\n=== 3. FETCHING ALL INVENTORY ORDERS ===")
orders = fetch_supabase("inventory_orders?order=order_id.asc&limit=1000")
items = fetch_supabase("inventory_order_items?limit=5000")

order_items_map = defaultdict(list)
for it in items:
    order_items_map[it['order_id']].append(it)

print(f"Total Inventory Orders in DB: {len(orders)}")
for o in orders:
    its = order_items_map[o['order_id']]
    item_str = ", ".join([f"{i['item_name']} ({i['quantity']} {i.get('unit','')} @ ₹{i.get('unit_price',0)})" for i in its])
    ts_str = datetime.fromtimestamp(o['timestamp']/1000).strftime('%Y-%m-%d %H:%M') if o.get('timestamp') else "No timestamp"
    print(f"{o['order_id']} | {ts_str} | ₹{o['total_amount']} | Supp: {o.get('supplier_name')} | Notes: {o.get('notes')} | Items: [{item_str}]")

print("\n=== 4. CHECKING FOR DUPLICATE INVENTORY ORDERS ===")
# Group orders by amount and items
amount_map = defaultdict(list)
for o in orders:
    its = order_items_map[o['order_id']]
    its_tuple = tuple(sorted([(i['item_name'].lower().strip(), round(float(i['quantity']), 2), round(float(i.get('subtotal', 0)), 2)) for i in its]))
    amount_map[(round(float(o['total_amount']), 2), its_tuple)].append(o)

duplicates_found = False
for (amt, its_tup), ord_list in amount_map.items():
    if len(ord_list) > 1:
        duplicates_found = True
        print(f"\n⚠️ POTENTIAL DUPLICATE ORDER GROUP (Total: ₹{amt}):")
        for o in ord_list:
            ts_str = datetime.fromtimestamp(o['timestamp']/1000).strftime('%Y-%m-%d %H:%M') if o.get('timestamp') else ""
            print(f"   - {o['order_id']} | {ts_str} | Supp: {o.get('supplier_name')} | Notes: {o.get('notes')}")

if not duplicates_found:
    print("No exact duplicate orders found based on (amount + items).")

print("\n=== 5. CHECKING REPEATED / DUPLICATE MESSAGES FROM SOMESH ===")
body_map = defaultdict(list)
for m in somesh_msgs:
    body = (m.get('message_body') or '').strip().lower()
    if body and body != "[image received]" and len(body) > 3:
        body_map[body].append(m)

for body, m_list in body_map.items():
    if len(m_list) > 1:
        print(f"\n⚠️ REPEATED MESSAGE TEXT ({len(m_list)} times): '{body}'")
        for m in m_list:
            print(f"   - ID {m['id']} [{m['created_at'][:19]}] Status: {m['status']} OrderID: {m.get('inventory_order_id')}")

