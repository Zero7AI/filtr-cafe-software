import urllib.request
import json
import sys
from datetime import datetime

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def fetch(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

orders = fetch("inventory_orders?order=order_id.asc&limit=1000")
items = fetch("inventory_order_items?limit=5000")

order_items_map = {}
for it in items:
    order_items_map.setdefault(it['order_id'], []).append(it)

print(f"=== CURRENT CLEANED INVENTORY ORDERS (Total: {len(orders)}) ===")
total_spend = 0.0
for o in orders:
    total_spend += float(o['total_amount'])
    its = order_items_map.get(o['order_id'], [])
    its_str = ", ".join([f"{i['item_name']} ({i['quantity']} {i.get('unit','')} @ ₹{i.get('unit_price',0)})" for i in its])
    ts_str = datetime.fromtimestamp(o['timestamp']/1000).strftime('%Y-%m-%d %H:%M') if o.get('timestamp') else ""
    print(f"{o['order_id']} | {ts_str} | ₹{float(o['total_amount']):<8.2f} | Supp: {o.get('supplier_name', ''):<20} | Items: {its_str}")

print(f"\nTOTAL PROCUREMENT SPEND: ₹{total_spend:,.2f}")

print("\n=== RECENT WHATSAPP MESSAGES (Last 10) ===")
recent_msgs = fetch("whatsapp_messages?order=id.desc&limit=10")
for m in reversed(recent_msgs):
    print(f"ID {m['id']} [{m['created_at'][:19]}] Type: {m['message_type']:<5} Status: {m['status']:<12} Order: {str(m.get('inventory_order_id')):<16} Note/Error: {m.get('error_message') or (m.get('parsed_result') or {}).get('notes')}")

