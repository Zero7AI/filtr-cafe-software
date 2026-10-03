import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/whatsapp_messages?from_number=eq.919907839311&select=*&order=id.asc", headers=headers)
with urllib.request.urlopen(req) as resp:
    msgs = json.loads(resp.read().decode())

print(f"Total messages from Somesh (919907839311): {len(msgs)}\n")
for m in msgs:
    print(f"[{m['created_at'][:19]}] ID:{m['id']} Type:{m['message_type']} Status:{m['status']}")
    if m.get('message_body'):
        print(f"  Body: {m['message_body']}")
    if m.get('parsed_result'):
        print(f"  Parsed: {json.dumps(m['parsed_result'])}")
    if m.get('inventory_order_id'):
        print(f"  Order: {m['inventory_order_id']}")
    print("-" * 50)
