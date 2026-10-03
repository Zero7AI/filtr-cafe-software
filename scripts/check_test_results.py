import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/whatsapp_messages?message_id=like.wamid.TEST_*", headers=headers)
with urllib.request.urlopen(req) as resp:
    test_msgs = json.loads(resp.read().decode())

print(f"Test messages processed: {len(test_msgs)}")
for m in test_msgs:
    print(f"ID: {m['message_id']} | Status: {m.get('status')} | Order: {m.get('inventory_order_id')}")
    print(f"   Parsed: {m.get('parsed_result')}")
    print(f"   Error: {m.get('error_message')}")
    print("-" * 50)
