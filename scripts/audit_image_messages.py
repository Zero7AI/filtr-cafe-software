import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def fetch_supabase(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

msgs = fetch_supabase("whatsapp_messages?from_number=eq.919907839311&message_type=eq.image&order=id.asc")
print(f"Total image messages from Somesh: {len(msgs)}")

for m in msgs:
    p = m.get('parsed_result') or {}
    items = p.get('items', [])
    notes = p.get('notes', '')
    media_id = p.get('media_id')
    inv_id = m.get('inventory_order_id')
    print(f"\nID: {m['id']} | Created: {m['created_at'][:19]} | Status: {m['status']} | Order: {inv_id}")
    print(f"  Media ID: {media_id}")
    print(f"  Error: {m.get('error_message')}")
    if items:
        print(f"  Supplier: {p.get('supplier_name')}, Total: ₹{p.get('total_amount')}")
        for it in items:
            print(f"    - {it.get('item_name')}: {it.get('quantity')} {it.get('unit')} @ ₹{it.get('unit_price')} = ₹{it.get('subtotal')}")
    if notes:
        print(f"  Notes: {notes}")
