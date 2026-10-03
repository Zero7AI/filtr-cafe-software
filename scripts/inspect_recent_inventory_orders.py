import urllib.request
import json
import sys
sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req_orders = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_orders?order_id=gte.INV-20260916&select=*&order=order_id.asc", headers=headers)
with urllib.request.urlopen(req_orders) as resp:
    orders = json.loads(resp.read().decode())

req_items = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_order_items?order_id=gte.INV-20260916&select=*&order=order_id.asc", headers=headers)
with urllib.request.urlopen(req_items) as resp:
    items = json.loads(resp.read().decode())

items_by_order = {}
for it in items:
    items_by_order.setdefault(it['order_id'], []).append(it)

print(f"Total inventory orders from Sep 16+: {len(orders)}")
for o in orders:
    oid = o['order_id']
    print(f"\n[{oid}] Supplier: {o.get('supplier_name')} | Date: {o.get('invoice_date')} | Total: Rs.{o.get('total_amount')}")
    print(f"  Notes: {o.get('notes')}")
    if oid in items_by_order:
        for it in items_by_order[oid]:
            print(f"    - {it['item_name']} | Qty: {it['quantity']} {it['unit']} @ Rs.{it['unit_price']} = Rs.{it['subtotal']}")
    else:
        print("    (No items)")
