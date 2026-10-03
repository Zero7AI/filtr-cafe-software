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

print("=== Checking around ID 31 (03-Sep) ===")
m31 = fetch_supabase("whatsapp_messages?id=gte.29&id=lte.35&order=id.asc")
for m in m31:
    print(f"ID {m['id']} [{m['created_at'][:19]}] Type: {m['message_type']} Status: {m['status']} Order: {m.get('inventory_order_id')} Body: {m.get('message_body')}")

print("\n=== Checking around ID 50 (08-Sep) ===")
m50 = fetch_supabase("whatsapp_messages?id=gte.48&id=lte.53&order=id.asc")
for m in m50:
    print(f"ID {m['id']} [{m['created_at'][:19]}] Type: {m['message_type']} Status: {m['status']} Order: {m.get('inventory_order_id')} Body: {m.get('message_body')}")

print("\n=== Checking around ID 53 (09-Sep) ===")
m53 = fetch_supabase("whatsapp_messages?id=gte.52&id=lte.57&order=id.asc")
for m in m53:
    print(f"ID {m['id']} [{m['created_at'][:19]}] Type: {m['message_type']} Status: {m['status']} Order: {m.get('inventory_order_id')} Body: {m.get('message_body')}")

print("\n=== Checking around ID 68 (13-Sep) ===")
m68 = fetch_supabase("whatsapp_messages?id=gte.66&id=lte.71&order=id.asc")
for m in m68:
    print(f"ID {m['id']} [{m['created_at'][:19]}] Type: {m['message_type']} Status: {m['status']} Order: {m.get('inventory_order_id')} Body: {m.get('message_body')}")

