import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_items?order=id.asc", headers=headers)
with urllib.request.urlopen(req) as resp:
    items = json.loads(resp.read().decode())
    print(f"Total inventory items: {len(items)}")
    for it in items:
        print(f"ID {it['id']:2d} | {it['name']:<35} | Cat: {it.get('category',''):<15} | Unit: {it.get('unit',''):<5} | Pack: {it.get('standard_quantity')} | Price: ₹{it.get('default_price'):<6} | Stock: {it.get('current_stock')}")
