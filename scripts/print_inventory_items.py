import urllib.request
import json

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/inventory_items?select=id,name,unit,standard_quantity,default_price&order=id.asc", headers=headers)
items = json.loads(urllib.request.urlopen(req).read().decode())
for i in items:
    print(f"ID {i['id']:2d}: {i['name']:32s} | unit: {i['unit']:6s} | stdQty: {i['standard_quantity']:8.2f} | price: Rs.{i['default_price']}")
