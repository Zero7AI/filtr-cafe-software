import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/whatsapp_messages?id=gte.10&id=lte.85&from_number=eq.919907839311&select=*&order=id.asc", headers=headers)
with urllib.request.urlopen(req) as resp:
    msgs = json.loads(resp.read().decode())

print(f"Total messages (10-85): {len(msgs)}")
for m in msgs:
    body = m.get('message_body') or ''
    parsed = m.get('parsed_result')
    print(f"[{m['created_at'][:19]}] ID:{m['id']:2d} Type:{m['message_type']:5s} | Body: {body}")
    if parsed and isinstance(parsed, dict) and parsed.get('items'):
        items_summary = ", ".join([f"{it.get('item_name')} ({it.get('quantity')} {it.get('unit')} @ Rs.{it.get('unit_price')})" for it in parsed['items']])
        print(f"    -> Parsed items: {items_summary}")
