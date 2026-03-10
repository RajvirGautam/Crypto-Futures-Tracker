import os
import re
import sys
files = [f for f in os.listdir('app/src/main/res/layout') if f.endswith('.xml') and f.startswith('widget_')]
for file in files:
  path = os.path.join('app/src/main/res/layout', file)
  with open(path, 'r') as f: content = f.read()
  new_content = re.sub(r'(<LinearLayout[^>]*android:orientation="vertical")', r'\1
        android:paddingStart="12dp"
        android:paddingEnd="12dp"', content, count=1)
  new_content = re.sub(r'android:paddingTop="7dp"', 'android:paddingTop="14dp"', new_content)
  new_content = re.sub(r'android:paddingBottom="7dp"', 'android:paddingBottom="14dp"', new_content)
  with open(path, 'w') as f: f.write(new_content)
