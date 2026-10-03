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

def api_request(method, endpoint, data=None):
    url = f"{SUPABASE_URL}/rest/v1/{endpoint}"
    req_body = json.dumps(data).encode('utf-8') if data is not None else None
    req = urllib.request.Request(url, data=req_body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read().decode('utf-8')
            return json.loads(content) if content else {}
    except urllib.error.HTTPError as e:
        print(f"HTTP Error {e.code} on {method} {endpoint}: {e.read().decode('utf-8')}")
        raise e

print("=== STARTING DATABASE RECONCILIATION ===")

# 1. Inspect INV-20260904-003 (₹2510 duplicate of INV-20260903-002)
print("\n1. Deleting duplicate INV-20260904-003...")
items_0904 = api_request("GET", "inventory_order_items?order_id=eq.INV-20260904-003")
print(f"Items in INV-20260904-003: {items_0904}")
api_request("DELETE", "inventory_order_items?order_id=eq.INV-20260904-003")
api_request("DELETE", "inventory_orders?order_id=eq.INV-20260904-003")
print("✅ INV-20260904-003 successfully removed.")

# 2. Inspect INV-20260915-003 (₹130 duplicate of INV-20260916-001)
print("\n2. Deleting duplicate INV-20260915-003...")
items_0915 = api_request("GET", "inventory_order_items?order_id=eq.INV-20260915-003")
print(f"Items in INV-20260915-003: {items_0915}")
api_request("DELETE", "inventory_order_items?order_id=eq.INV-20260915-003")
api_request("DELETE", "inventory_orders?order_id=eq.INV-20260915-003")
print("✅ INV-20260915-003 successfully removed.")

# Check Bread inventory stock
bread_items = api_request("GET", "inventory_items?name=ilike.*Bread*")
for b in bread_items:
    print(f"Bread stock: ID {b['id']} | Name: {b['name']} | Current stock: {b.get('current_stock')}")
    # Reduce stock by 1 pack if it was double counted
    new_stock = max(0, (b.get('current_stock') or 0) - 1)
    api_request("PATCH", f"inventory_items?id=eq.{b['id']}", {"current_stock": new_stock})
    print(f"✅ Adjusted Bread stock from {b.get('current_stock')} to {new_stock}")

# 3. Reconcile INV-20260902-003 to KE Estimate 2739 (950 pc @ 4.50 = ₹4,275.00)
print("\n3. Reconciling INV-20260902-003 to KE Estimate 2739...")
api_request("PATCH", "inventory_orders?order_id=eq.INV-20260902-003", {
    "total_amount": 4275,
    "supplier_name": "KE ESTIMATE 25 (Invoice 2739)",
    "notes": "KE Estimate 2739 dated 02/09/2026 | ABHINAV U SHAPE CUSTOMISED 12 OZ With Lid (950 pc @ ₹4.50 = ₹4,275)"
})
# Update item
items_0902 = api_request("GET", "inventory_order_items?order_id=eq.INV-20260902-003")
if items_0902:
    it_id = items_0902[0]['id']
    api_request("PATCH", f"inventory_order_items?id=eq.{it_id}", {
        "item_name": "ABHINAV U SHAPE CUSTOMISED 12 OZ With Lid",
        "quantity": 950,
        "unit": "pc",
        "unit_price": 4.50,
        "subtotal": 4275
    })
print("✅ INV-20260902-003 successfully updated to ₹4,275.00.")

print("\n=== RECONCILIATION COMPLETED SUCCESSFULLY ===")
