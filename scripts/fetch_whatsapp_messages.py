import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/whatsapp_messages?select=*&order=created_at.desc&limit=100", headers=headers)
try:
    with urllib.request.urlopen(req) as resp:
        msgs = json.loads(resp.read().decode())
    print(f"Total whatsapp_messages found: {len(msgs)}")
    for m in msgs:
        print("="*60)
        print(f"ID: {m.get('id')} | Time: {m.get('created_at')} | Status: {m.get('status')}")
        print(f"From: {m.get('from_number')} | Type: {m.get('message_type')}")
        print(f"Body: {m.get('message_body')}")
        if m.get('parsed_items'):
            print(f"Parsed: {json.dumps(m.get('parsed_items'), indent=2)}")
        if m.get('media_id'):
            print(f"Media ID: {m.get('media_id')}")
        if m.get('error'):
            print(f"Error: {m.get('error')}")
except Exception as e:
    print("Error querying whatsapp_messages:", e)
