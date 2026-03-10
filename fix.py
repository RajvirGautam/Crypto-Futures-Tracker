import os
import re
files = [f for f in os.listdir('app/src/main/res/layout') if f.endswith('.xml')]
for file in files:
  path = os.path.join('app/src/main/res/layout', file)
  with open(path, 'r') as f: content = f.read()
  new_content = re.sub(r'\s*<ImageView\s+android:id="@\+id/widgetBg".*?/>', '', content, flags=re.DOTALL)
  with open(path, 'w') as f: f.write(new_content)
