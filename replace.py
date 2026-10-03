import sys

js_path = "/home/abhijeet/.gemini/antigravity/brain/d1581d73-bdfe-4d9c-8c1d-86eb3c364b6c/FILTR_Apps_Script_v5.js"
kt_path = "/home/abhijeet/Desktop/filtr-billing/app/src/main/java/com/example/data/remote/GoogleSheetsSyncService.kt"

with open(js_path, "r") as f:
    js_content = f.read()

with open(kt_path, "r") as f:
    lines = f.readlines()

new_block = '    companion object {\n        const val GOOGLE_APPS_SCRIPT_TEMPLATE = """\n' + js_content + '"""\n    }\n'

# lines 521 to 1098 are indices 520 to 1098
lines[520:1098] = [new_block]

with open(kt_path, "w") as f:
    f.writelines(lines)
