import os
import sys
import json
import urllib.request
from collections import defaultdict

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def fetch_supabase(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

# 1. Fetch DB records
orders = fetch_supabase("inventory_orders?order=order_id.asc")
items = fetch_supabase("inventory_order_items?limit=5000")
order_items_map = defaultdict(list)
for it in items:
    order_items_map[it['order_id']].append(it)

print(f"Total DB inventory_orders: {len(orders)}")
for o in orders:
    its = order_items_map[o['order_id']]
    its_summary = ", ".join([f"{it['item_name']}: {it['quantity']}{it.get('unit','')} (₹{it.get('subtotal',0)})" for it in its])
    print(f"{o['order_id']} | {o.get('order_date')} | ₹{o.get('total_amount')} | Supp: {o.get('supplier_name')} | Notes: {o.get('notes')} | Items: [{its_summary}]")

print("\n" + "="*80)
