import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/whatsapp_messages?id=gte.100&order=id.asc", headers=headers)
with urllib.request.urlopen(req) as resp:
    msgs = json.loads(resp.read().decode())

print(f"Total messages returned: {len(msgs)}")
for m in msgs:
    print("="*60)
    print(f"ID: {m['id']} | Time: {m['created_at']} | Status: {m['status']} | Type: {m['message_type']} | Order: {m.get('inventory_order_id')}")
    print(f"  Body: {m.get('message_body')}")
    print(f"  Media ID: {m.get('media_id')}")
    print(f"  Error: {m.get('error') or m.get('error_message')}")
    print(f"  Parsed: {json.dumps(m.get('parsed_result'), indent=2) if m.get('parsed_result') else None}")

