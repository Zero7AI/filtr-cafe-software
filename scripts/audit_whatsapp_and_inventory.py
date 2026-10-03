import urllib.request
import json
import sys
sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

# Get whatsapp messages >= 85
req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/whatsapp_messages?id=gte.85&order=id.asc", headers=headers)
with urllib.request.urlopen(req) as resp:
    msgs = json.loads(resp.read().decode())

# Get inventory orders
req_orders = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_orders?order=order_id.asc", headers=headers)
with urllib.request.urlopen(req_orders) as resp:
    orders = json.loads(resp.read().decode())
order_map = {o['order_id']: o for o in orders}

# Get inventory order items
req_items = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_order_items?order=order_id.asc", headers=headers)
with urllib.request.urlopen(req_items) as resp:
    items = json.loads(resp.read().decode())
items_map = {}
for it in items:
    items_map.setdefault(it['order_id'], []).append(it)

print(f"=== WhatsApp Messages & Inventory Order Audit (ID >= 85) ===\n")
for m in msgs:
    oid = m.get('inventory_order_id')
    status = m.get('status')
    body = (m.get('message_body') or '').replace('\n', ' ')[:60]
    mid = m['id']
    mtype = m.get('message_type') or ''
    mtime = (m.get('created_at') or '')[:16]
    print(f"ID: {mid:3d} | {mtime} | {mtype:8s} | Status: {status:12s} | Order: {str(oid):16s} | {body}")
    if oid and oid in order_map:
        o = order_map[oid]
        its = items_map.get(oid, [])
        it_str = ', '.join([f"{it['item_name']} ({it['quantity']} {it['unit']})" for it in its])
        print(f"     -> [ORDER IN DB] Supplier: {o.get('supplier_name')} | Rs.{o.get('total_amount')} | Items: {it_str}")
    elif status == 'parse_failed':
        pr = m.get('parsed_result') or {}
        print(f"     -> [PARSED AS NON-INVENTORY] Notes: {pr.get('notes') or m.get('error_message')}")
    elif status == 'received':
        print(f"     -> [UNPROCESSED / STUCK IN RECEIVED!]")
    print("-" * 80)
