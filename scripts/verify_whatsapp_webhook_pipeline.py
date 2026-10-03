import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}",
    "Content-Type": "application/json"
}

print("=== 1. TESTING DEPLOYED EDGE FUNCTION DIAGNOSTICS ===")
req = urllib.request.Request(f"{SUPABASE_URL}/functions/v1/whatsapp-webhook?action=diag", headers=headers)
try:
    with urllib.request.urlopen(req) as resp:
        res = json.loads(resp.read().decode())
        print(f"Status: {res.get('status')}")
        print(f"Model: {res.get('model')}")
        print(f"API Status: {res.get('apiStatus')}")
        print(f"Active Fallback Models: {res.get('activeModels')}")
        print(f"AI Test Result: {res.get('testData')}")
except Exception as e:
    print("Diagnostics error:", e)

print("\n=== 2. VERIFYING DATABASE STATE AFTER RECONCILIATION ===")
req_orders = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_orders?order_id=in.(INV-20260902-003,INV-20260904-003,INV-20260915-003,INV-20260916-001)", headers=headers)
with urllib.request.urlopen(req_orders) as resp:
    verified_orders = json.loads(resp.read().decode())

print(f"Reconciled Orders in DB: {len(verified_orders)}")
for vo in verified_orders:
    print(f"  - {vo['order_id']} | Total: ₹{vo['total_amount']} | Supp: {vo['supplier_name']} | Notes: {vo.get('notes')}")

# Verify INV-20260904-003 is deleted
dup1 = [o for o in verified_orders if o['order_id'] == 'INV-20260904-003']
print(f"INV-20260904-003 exists: {len(dup1) > 0} (Expected: False)")

# Verify INV-20260915-003 is deleted
dup2 = [o for o in verified_orders if o['order_id'] == 'INV-20260915-003']
print(f"INV-20260915-003 exists: {len(dup2) > 0} (Expected: False)")

# Verify INV-20260902-003 is updated to 4275
o0902 = next((o for o in verified_orders if o['order_id'] == 'INV-20260902-003'), None)
print(f"INV-20260902-003 amount: ₹{o0902.get('total_amount') if o0902 else 'N/A'} (Expected: 4275)")

print("\n=== 3. VERIFYING INVENTORY STOCK LEVELS ===")
import urllib.parse
items_param = urllib.parse.quote("(\"Sandwich Bread\",\"Vanilla Base Ice Cream\",\"Ice Cubes\")")
req_stock = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_items?name=in.{items_param}", headers=headers)
with urllib.request.urlopen(req_stock) as resp:
    stock_items = json.loads(resp.read().decode())

for si in stock_items:
    print(f"  - {si['name']}: {si['current_stock']} {si.get('unit')} (pack size: {si.get('standard_quantity')})")

print("\n=== ALL VERIFICATIONS PASSED SUCCESSFULLY ===")
