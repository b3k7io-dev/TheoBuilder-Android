#!/usr/bin/env python3
import json
from pathlib import Path
r=Path(__file__).resolve().parents[1]
s=json.loads((r/'app/src/main/assets/api-schema.json').read_text())
assert s['schemaStatus'].startswith('partial')
assert {x['id'] for x in s['types']}=={'park','decoration','residential'}
for t in s['types']:
 assert t['sources'] and t['compatibility']
 names=[]
 for f in t['properties']:
  assert f.get('source') and f['type'] in {'text','number','boolean','texture'}
  assert f['name'] not in names;names.append(f['name'])
for p in ['app/src/main/AndroidManifest.xml','app/src/main/java/com/theobuilder/app/MainActivity.kt','app/src/main/java/com/theobuilder/app/Exporter.kt','gradlew']:
 assert (r/p).is_file(),p
print('PASS: JSON schema and source structure; Kotlin compile and game compatibility NOT tested.')
