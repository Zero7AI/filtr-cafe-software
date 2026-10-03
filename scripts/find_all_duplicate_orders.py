import urllib.request
import json
import sys
from collections import defaultdict
from datetime import datetime

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def fetch_supabase(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

orders = fetch_supabase("inventory_orders?order=order_id.asc")
items = fetch_supabase("inventory_order_items?limit=5000")
order_items = defaultdict(list)
for it in items:
    order_items[it['order_id']].append(it)

print(f"Total inventory orders: {len(orders)}")

# Check duplicate groups:
# 1. Same date or within 24-48 hours with exact same items and amount
# 2. Orders that Somesh resent due to 503 errors

for i, o1 in enumerate(orders):
    for j, o2 in enumerate(orders):
        if j <= i: continue
        # Check if items match
        its1 = sorted([(it['item_name'].lower().strip(), float(it['quantity']), float(it.get('subtotal',0))) for it in order_items[o1['order_id']]])
        its2 = sorted([(it['item_name'].lower().strip(), float(it['quantity']), float(it.get('subtotal',0))) for it in order_items[o2['order_id']]])
        
        # Check if amounts match
        same_amt = abs(float(o1['total_amount']) - float(o2['total_amount'])) < 0.01
        same_items = (its1 == its2 and len(its1) > 0)
        
        # Check if supplier / notes match
        if same_amt and same_items:
            print(f"\n⚠️ DUPLICATE DETECTED:")
            print(f"   Order 1: {o1['order_id']} | Date: {o1.get('order_date')} | Total: ₹{o1['total_amount']} | Supp: {o1.get('supplier_name')} | Notes: {o1.get('notes')}")
            print(f"   Order 2: {o2['order_id']} | Date: {o2.get('order_date')} | Total: ₹{o2['total_amount']} | Supp: {o2.get('supplier_name')} | Notes: {o2.get('notes')}")
            print(f"   Items: {its1}")
        elif same_amt and float(o1['total_amount']) > 500:
            # high amount identical
            print(f"\n❓ SAME AMOUNT MATCH (₹{o1['total_amount']}):")
            print(f"   Order 1: {o1['order_id']} | Date: {o1.get('order_date')} | Notes: {o1.get('notes')} | Items: {its1}")
            print(f"   Order 2: {o2['order_id']} | Date: {o2.get('order_date')} | Notes: {o2.get('notes')} | Items: {its2}")

