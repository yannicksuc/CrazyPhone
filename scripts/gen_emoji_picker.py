"""Regenerates src/main/resources/crazyphone_data/emoji_picker.json, the emoji drawer's categories.

Usage: python scripts/gen_emoji_picker.py <repo root> <emoji-test.txt> <output json>
emoji-test.txt comes from https://unicode.org/Public/emoji/<version>/emoji-test.txt (Unicode's own grouping
and order). Only emojis the bundled Pixel Twemoji font draws, and that the shortcode index can name, are
kept; skin tone variants are left out to keep the drawer compact (they stay typeable as :shortcodes:).
"""
import json, re, sys
repo, test_txt, out = sys.argv[1], sys.argv[2], sys.argv[3]
sc = json.load(open(repo + '/src/main/resources/crazyphone_data/emoji_shortcodes.json', encoding='utf-8'))
font = json.load(open(repo + '/src/main/resources/assets/minecraft/font/default.json', encoding='utf-8'))
glyphs = set()
for p in font['providers']:
    if 'twemoji' not in p.get('file', ''):
        continue
    for row in p.get('chars', []):
        for ch in row:
            if ch != '\x00':
                glyphs.add(ch)
by_value = {}
for k, v in sc.items():
    by_value.setdefault(v, []).append(k)
key_to_value = dict(sc)
GROUPS = {'Smileys & Emotion': 'smileys', 'People & Body': 'people', 'Animals & Nature': 'nature',
          'Food & Drink': 'food', 'Travel & Places': 'travel', 'Activities': 'activities',
          'Objects': 'objects', 'Symbols': 'symbols', 'Flags': 'flags'}
def norm(name):
    name = name.lower().replace('’', '').replace("'", '').replace('“', '').replace('”', '')
    name = name.replace('&', 'and').replace('#', 'hash').replace('*', 'asterisk')
    name = re.sub(r'[^a-z0-9]+', '_', name).strip('_')
    return name
cats = {v: [] for v in GROUPS.values()}
seen = set()
group = None
skin = re.compile(r'1F3F[B-F]')
for line in open(test_txt, encoding='utf-8'):
    if line.startswith('# group:'):
        group = GROUPS.get(line.split(':', 1)[1].strip())
        continue
    if not line.strip() or line.startswith('#') or group is None:
        continue
    m = re.match(r'([0-9A-F ]+);\s*(\S+)\s*#\s*\S+\s+E[0-9.]+\s+(.*)$', line.strip())
    if not m:
        continue
    cps, status, name = m.group(1).split(), m.group(2), m.group(3)
    if status != 'fully-qualified' or skin.search(' '.join(cps)):
        continue
    seq = ''.join(chr(int(c, 16)) for c in cps)
    bare = seq.replace('️', '')
    value = None
    if len(bare) == 1 or (len(bare) == 2 and 0xD800 <= ord(bare[0]) <= 0xDBFF):
        if bare in by_value:
            value = bare
    if value is None:
        n = norm(name)
        cand = [':' + n + ':', ':flag_' + n.replace('flag_', '') + ':']
        ri = [int(c, 16) for c in cps]
        if len(ri) == 2 and all(0x1F1E6 <= c <= 0x1F1FF for c in ri):
            cand.insert(0, ':flag_' + ''.join(chr(ord('a') + c - 0x1F1E6) for c in ri) + ':')
        if n.startswith('keycap_'):
            digit = n[len('keycap_'):]
            words = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine']
            if digit.isdigit() and len(digit) == 1:
                cand.insert(0, ':' + words[int(digit)] + ':')
            cand.append(':keycap_' + digit + ':')
            cand.append(':' + digit + ':')
        if n.startswith('flag_'):
            cand.append(':' + n[len('flag_'):] + ':')
        for c in cand:
            if c in key_to_value:
                value = key_to_value[c]
                break
    if value is None or value in seen or value not in glyphs:
        continue
    seen.add(value)
    keys = by_value[value]
    canon = ':' + norm(name) + ':'
    code = canon if canon in keys else min(keys, key=lambda k: (len(k), k))
    cats[group].append(code)
ICONS = {'smileys': ':grinning:', 'people': ':wave:', 'nature': ':dog:', 'food': ':pizza:', 'travel': ':red_car:',
         'activities': ':soccer:', 'objects': ':bulb:', 'symbols': ':heart:', 'flags': ':checkered_flag:'}
for k, ic in ICONS.items():
    assert ic in key_to_value, ic
res = {'_comment': 'Generated: emoji order and categories from Unicode emoji-test.txt 15.1 (unicode.org), names are Pixel Twemoji 9x shortcodes (crazyphone_data/emoji_shortcodes.json). Skin tone variants omitted.',
       'categories': [{'id': k, 'icon': ICONS[k], 'emojis': v} for k, v in cats.items()]}
json.dump(res, open(out, 'w', encoding='utf-8', newline='\n'), ensure_ascii=True, indent=1)
print({k: len(v) for k, v in cats.items()}, 'total', sum(len(v) for v in cats.values()), 'of', len(by_value))
