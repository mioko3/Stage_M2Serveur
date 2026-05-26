from pathlib import Path
p = Path('app/data/courutilisation/lots.json')
text = p.read_text(encoding='utf-8')
print(text[:1200])
print('---')
pos = 0
count = 0
while True:
    idx = text.find('"nbPers"', pos)
    if idx == -1:
        break
    print('nbPers at', idx, repr(text[max(0, idx-40):idx+60]))
    pos = idx + 1
    count += 1
    if count > 20:
        break
