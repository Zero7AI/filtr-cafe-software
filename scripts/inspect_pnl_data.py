import urllib.request
import json
from datetime import datetime, timezone

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

# Fetch all orders from Supabase
req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/orders?select=*,order_items(*)&order=timestamp.asc", headers=headers)
with urllib.request.urlopen(req) as resp:
    orders = json.loads(resp.read().decode())

print(f"Total orders in Supabase: {len(orders)}")

# Check orders in September 2026
# Sept 1 2026 00:00:00 IST = 1788201000000 or let's check exact timestamps
sept_orders = []
total_sept_revenue = 0.0
for o in orders:
    ts = o.get('timestamp') or 0
    # convert ts ms to datetime
    dt = datetime.fromtimestamp(ts / 1000.0, timezone.utc)
    # Check if in Sept 2026
    if dt.year == 2026 and dt.month == 9:
        sept_orders.append(o)
        total_sept_revenue += float(o.get('total_amount') or 0.0)

print(f"September 2026 orders count: {len(sept_orders)}")
print(f"September 2026 total revenue (sum of total_amount): {total_sept_revenue}")

# Daily breakdown
daily = {}
for o in sept_orders:
    ts = o.get('timestamp') or 0
    dt = datetime.fromtimestamp(ts / 1000.0)
    day_str = dt.strftime('%Y-%m-%d')
    daily.setdefault(day_str, {'count': 0, 'revenue': 0.0})
    daily[day_str]['count'] += 1
    daily[day_str]['revenue'] += float(o.get('total_amount') or 0.0)

print("\n--- Daily Revenue in September 2026 ---")
for day, d in sorted(daily.items()):
    print(f"{day}: {d['count']:3d} orders | Rs {d['revenue']:8.2f}")

# Check fixed costs in Supabase
try:
    req_fc = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/fixed_costs?select=*&order=month_key.asc", headers=headers)
    with urllib.request.urlopen(req_fc) as resp_fc:
        fixed_costs = json.loads(resp_fc.read().decode())
    print(f"\nFixed costs in Supabase: {len(fixed_costs)}")
    for fc in fixed_costs:
        print(f"Month: {fc.get('month_key')} | Total: {fc.get('total_monthly_fixed_cost')} | Rent: {fc.get('shop_rent')} | Staff: {fc.get('staff_salaries')}")
except Exception as e:
    print("Could not fetch fixed_costs:", e)
