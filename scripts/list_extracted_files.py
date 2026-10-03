import os
import sys

sys.stdout.reconfigure(encoding='utf-8')

extract_dir = 'extracted_whatsapp_chat'
files = sorted(os.listdir(extract_dir))

print(f"Total files in {extract_dir}: {len(files)}\n")

for f in files:
    full_path = os.path.join(extract_dir, f)
    size = os.path.getsize(full_path)
    print(f"File: {f:<55} | Size: {size/1024:6.1f} KB")
