import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/whatsapp_messages?id=gte.80&select=*&order=id.asc", headers=headers)
with urllib.request.urlopen(req) as resp:
    msgs = json.loads(resp.read().decode())

print(f"Total messages: {len(msgs)}\n")
for m in msgs:
    print(f"ID: {m['id']} | Date: {m['created_at']} | Type: {m['message_type']} | Status: {m['status']}")
    print(f"Body: {m.get('message_body')}")
    print(f"Order ID: {m.get('inventory_order_id')}")
    if m.get('parsed_result'):
        print(f"Parsed: {json.dumps(m['parsed_result'], indent=2)}")
    if m.get('error_message'):
        print(f"Error: {m['error_message']}")
    print("-" * 50)
