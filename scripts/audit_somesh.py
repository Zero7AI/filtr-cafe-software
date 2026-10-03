import urllib.request
import json
import sys
sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/whatsapp_messages?from_number=eq.919907839311&order=id.asc", headers=headers)
with urllib.request.urlopen(req) as resp:
    msgs = json.loads(resp.read().decode())

print("Total messages from Somesh:", len(msgs))

unprocessed = [m for m in msgs if m.get('status') in ['received', 'retry_pending', 'error']]
print("\n--- UNPROCESSED MESSAGES ---")
print("Count:", len(unprocessed))
for m in unprocessed:
    print(f"ID {m['id']} [{m['created_at'][:19]}] Type:{m['message_type']} Status:{m['status']} -> {m.get('message_body')}")

print("\n--- RECENT PARSE_FAILED MESSAGES (Last 15) ---")
failed = [m for m in msgs if m.get('status') == 'parse_failed']
for m in failed[-15:]:
    notes = (m.get('parsed_result') or {}).get('notes')
    body = (m.get('message_body') or '').replace('\n', ' ')
    print(f"ID {m['id']} [{m['created_at'][:19]}] Body: {body[:50]} -> Notes: {notes}")
