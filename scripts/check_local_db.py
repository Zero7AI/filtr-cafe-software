import sqlite3

for db_name in ['filtr_coffee_db', 'db_copy.db', 'filtr_device.db']:
    try:
        conn = sqlite3.connect(db_name)
        cursor = conn.cursor()
        cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
        tables = [r[0] for r in cursor.fetchall()]
        print(f"\n--- {db_name} tables ---")
        print(tables)
        if 'inventory_items' in tables:
            cursor.execute("SELECT * FROM inventory_items")
            rows = cursor.fetchall()
            print(f"inventory_items ({len(rows)}):")
            for r in rows[:10]:
                print(r)
        if 'inventory_usage' in tables:
            cursor.execute("SELECT * FROM inventory_usage")
            rows = cursor.fetchall()
            print(f"inventory_usage ({len(rows)}):")
            for r in rows[:10]:
                print(r)
        conn.close()
    except Exception as e:
        print(f"Error reading {db_name}: {e}")
