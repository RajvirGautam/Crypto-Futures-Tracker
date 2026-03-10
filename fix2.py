import os
for f in os.listdir('app/src/main/res/layout'):
    if f.endswith('.xml') and f.startswith('widget_'):
        path = os.path.join('app/src/main/res/layout', f)
        with open(path, 'r') as file: content = file.read()
        content = content.replace('android:paddingTop="7dp"', 'android:paddingTop="14dp"')
        content = content.replace('android:paddingBottom="7dp"', 'android:paddingBottom="14dp"')
        old_ll = 'android:orientation="vertical"
        android:paddingTop="14dp"'
        new_ll = 'android:orientation="vertical"
        android:paddingStart="14dp"
        android:paddingEnd="14dp"
        android:paddingTop="14dp"'
        content = content.replace(old_ll, new_ll)
        with open(path, 'w') as file: file.write(content)

