import os
import sys
import json
import urllib.request
import urllib.error

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"

TURSO_URL = "https://filtr-backup-db-filtr-coffee.aws-ap-south-1.turso.io/v2/pipeline"
TURSO_TOKEN = "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhIjoicnciLCJpYXQiOjE3OTQ5MDc0NzMsImlkIjoiMDFhMWU4ZDEtZDIwMS03NDY2LTgyZTEtZjlhZGJjOTljYzkzIiwia2lkIjoidFdRNTFoaFNrcGJpN19CMms4bWlQWklGVGV3bE1fTGt2VVl0UldVQWhpcyIsInJpZCI6ImZkMTgwNmQzLTJlMGYtNDNlNS1hODg4LWZmNjk4YjUzZjMzYSJ9.9Gxe_yTP8gOYtWOZ2QyPh_T6XIluOAJbfN8S0LF7ludxFGyKVqsOmVBhMswXbGoKb7nx_joW7zbxlygq-7RQAQ"

def fetch_supabase(table, limit=1000, offset=0):
    url = f"{SUPABASE_URL}/rest/v1/{table}?select=*&limit={limit}&offset={offset}"
    req = urllib.request.Request(url, headers={
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}"
    })
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode('utf-8'))
    except Exception as e:
        print(f"Error fetching {table}: {e}")
        return []

def execute_turso_batch(stmts):
    if not stmts:
        return
    requests = [{"type": "execute", "stmt": s} for s in stmts]
    requests.append({"type": "close"})
    
    payload = json.dumps({"requests": requests}).encode('utf-8')
    req = urllib.request.Request(TURSO_URL, data=payload, headers={
        "Authorization": f"Bearer {TURSO_TOKEN}",
        "Content-Type": "application/json"
    })
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            for res in data.get("results", []):
                if res.get("type") == "error":
                    print("Turso batch error:", res.get("error"))
    except urllib.error.HTTPError as e:
        print("HTTP Error:", e.read().decode('utf-8'))
    except Exception as e:
        print("Turso request failed:", e)

def to_turso_val(val):
    if val is None:
        return {"type": "null"}
    elif isinstance(val, bool):
        return {"type": "integer", "value": "1" if val else "0"}
    elif isinstance(val, int):
        return {"type": "integer", "value": str(val)}
    elif isinstance(val, float):
        return {"type": "float", "value": val}
    elif isinstance(val, (dict, list)):
        return {"type": "text", "value": json.dumps(val)}
    else:
        return {"type": "text", "value": str(val)}

def sync_table(table_name, columns, conflict_col=None):
    print(f"\n--- Syncing table: {table_name} ---")
    offset = 0
    total_synced = 0
    batch_size = 200
    
    while True:
        rows = fetch_supabase(table_name, limit=batch_size, offset=offset)
        if not rows:
            break
            
        stmts = []
        for r in rows:
            col_names = []
            args = []
            for c in columns:
                if c in r:
                    col_names.append(c)
                    args.append(to_turso_val(r[c]))
                    
            placeholders = ", ".join(["?"] * len(col_names))
            cols_str = ", ".join(col_names)
            sql = f"INSERT OR REPLACE INTO {table_name} ({cols_str}) VALUES ({placeholders})"
            stmts.append({"sql": sql, "args": args})
            
        execute_turso_batch(stmts)
        total_synced += len(rows)
        offset += len(rows)
        print(f"  Synced {total_synced} rows so far...")
        if len(rows) < batch_size:
            break
            
    print(f"Finished {table_name}: {total_synced} total rows backed up.")

if __name__ == "__main__":
    print("Starting snapshot backup from Supabase to Turso...")
    
    sync_table("menu_items", ['id', 'name', 'category', 'price', 'description', 'is_available'])
    sync_table("customers", ['id', 'name', 'contact_number', 'total_orders', 'total_spent', 'last_order_timestamp', 'created_at'])
    sync_table("upi_accounts", ['id', 'vpa', 'payee_name', 'is_default', 'note'])
    sync_table("inventory_items", ['id', 'name', 'category', 'unit', 'standard_quantity', 'default_price', 'current_stock', 'is_active'])
    sync_table("inventory_orders", ['order_id', 'timestamp', 'supplier_name', 'total_amount', 'notes', 'sync_status'])
    sync_table("inventory_order_items", ['id', 'order_id', 'item_name', 'quantity', 'unit', 'unit_price', 'subtotal'])
    sync_table("inventory_usage", ['id', 'menu_item_id', 'menu_item_name', 'inventory_item_id', 'inventory_item_name', 'quantity_used', 'unit'])
    sync_table("whatsapp_messages", ['id', 'message_id', 'from_number', 'message_type', 'message_body', 'parsed_result', 'inventory_order_id', 'status', 'error_message', 'created_at'])
    sync_table("sync_metadata", ['key', 'value', 'updated_at'])
    
    sync_table("orders", ['order_id', 'customer_name', 'contact_number', 'total_amount', 'timestamp', 'is_made', 'is_paid', 'is_delivered', 'sync_status', 'notes', 'payment_mode', 'completed_timestamp', 'created_at'])
    sync_table("order_items", ['id', 'order_id', 'menu_item_id', 'item_name', 'item_price', 'quantity', 'subtotal', 'completed_quantity', 'completed_timestamp'])

    print("\n[SUCCESS] Turso backup synchronization complete!")
