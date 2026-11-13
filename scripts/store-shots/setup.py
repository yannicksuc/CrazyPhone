"""Prepares versions/1.21.1 for store screenshots (undo with teardown.py)."""
import os, shutil, json, uuid, re, zipfile, glob, sys
V = r'C:\Users\yanni\code\CrazyPhone\versions\1.21.1'
S = r'C:\Users\yanni\code\CrazyPhone\.dev-launch\shots'
MF = r'C:\Users\yanni\code\mcpfabric\versions\1.21.1-neoforge\build\libs\mcpfabric-neoforge-0.4.1+1.21.1.jar'
skin2 = sys.argv[1] if len(sys.argv) > 1 else 'pmc-mastu-claquette.png'
for d, keep in (('run', None), ('run-server', None)):
    off = os.path.join(V, d, 'mods-off-shots'); os.makedirs(off, exist_ok=True)
    for j in glob.glob(os.path.join(V, d, 'mods', '*.jar')):
        n = os.path.basename(j)
        if any(n.startswith(p) for p in ('create-', 'sable', 'jei', 'xaero')):
            shutil.move(j, off)
for d, port in (('run', 25615), ('run-client2', 25616)):
    mods = os.path.join(V, d, 'mods'); os.makedirs(mods, exist_ok=True)
    shutil.copy(os.path.join(S, 'dl', 'sodium.jar'), mods); shutil.copy(os.path.join(S, 'dl', 'iris.jar'), mods)
    dst = os.path.join(mods, os.path.basename(MF))
    with zipfile.ZipFile(MF) as zi, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zo:
        for it in zi.infolist():
            data = zi.read(it.filename)
            if it.filename == 'META-INF/neoforge.mods.toml':
                data = re.sub(r'versionRange\s*=\s*"\[21\.1\.\d+,?\)?"', 'versionRange="[21.1.0,)"', data.decode()).encode()
            zo.writestr(it, data)
    if d == 'run-client2':
        for j in glob.glob(os.path.join(V, 'run', 'mods', 'voicechat-*.jar')): shutil.copy(j, mods)
    sp = os.path.join(V, d, 'shaderpacks'); os.makedirs(sp, exist_ok=True)
    shutil.copy(os.path.join(S, 'dl', 'ComplementaryReimagined_r5.9.3.zip'), sp)
    pack = os.path.join(V, d, 'resourcepacks', 'CrazyPhoneShotsSkins', 'assets', 'minecraft', 'textures', 'entity', 'player', 'wide')
    os.makedirs(pack, exist_ok=True)
    shutil.copy(os.path.join(S, 'skins', 'lordfinn.png'), os.path.join(pack, 'zuri.png'))
    shutil.copy(os.path.join(S, 'skins', skin2), os.path.join(pack, 'makena.png'))
    shutil.copy(os.path.join(S, 'skins', 'pmc-foxiefern.png'), os.path.join(pack, 'ari.png'))
    shutil.copy(os.path.join(S, 'skins', 'pmc-samouss.png'), os.path.join(pack, 'efe.png'))
    open(os.path.join(V, d, 'resourcepacks', 'CrazyPhoneShotsSkins', 'pack.mcmeta'), 'w').write('{"pack":{"pack_format":34,"description":"CrazyPhone shots skins"}}')
    cfg = os.path.join(V, d, 'config'); os.makedirs(cfg, exist_ok=True)
    json.dump({"host": "127.0.0.1", "port": port, "token": uuid.uuid4().hex, "requireAuth": True, "callTimeoutMs": 8000,
               "enableWorldWrite": True, "enableCommands": True, "enablePlayerControl": True, "enableVision": True},
              open(os.path.join(cfg, 'mcpfabric.config.json'), 'w'), indent=2)
    open(os.path.join(cfg, 'iris.properties'), 'w').write('shaderPack=ComplementaryReimagined_r5.9.3.zip\nenableShaders=true\n')
    vc = os.path.join(cfg, 'voicechat', 'voicechat-client.properties')
    if os.path.exists(vc):
        if not os.path.exists(vc + '.before-shots'): shutil.copy(vc, vc + '.before-shots')
        t = open(vc).read().replace('hide_icons=false', 'hide_icons=true').replace('show_nametag_icons=true', 'show_nametag_icons=false')
        open(vc, 'w').write(t)
    opt = os.path.join(V, d, 'options.txt')
    lines = open(opt, encoding='utf-8').read().splitlines() if os.path.exists(opt) else []
    if os.path.exists(opt) and not os.path.exists(opt + '.before-shots'): shutil.copy(opt, opt + '.before-shots')
    want = {'fullscreen': 'false', 'soundCategory_master': '0.0', 'pauseOnLostFocus': 'false', 'guiScale': '4', 'renderDistance': '16',
            'resourcePacks': '["vanilla","file/CrazyPhoneShotsSkins"]', 'tutorialStep': 'none', 'onboardAccessibility': 'false',
            'skipMultiplayerWarning': 'true', 'joinedFirstServer': 'true', 'fov': '0.0'}
    out, seen = [], set()
    for l in lines:
        k = l.split(':', 1)[0]
        if k in want: out.append(f'{k}:{want[k]}'); seen.add(k)
        else: out.append(l)
    out += [f'{k}:{v}' for k, v in want.items() if k not in seen]
    open(opt, 'w', encoding='utf-8').write('\n'.join(out) + '\n')
sp = os.path.join(V, 'run-server', 'server.properties')
if not os.path.exists(sp + '.before-shots'): shutil.copy(sp, sp + '.before-shots')
t = re.sub(r'level-name=.*', 'level-name=shots2', open(sp).read()); open(sp, 'w').write(t)
print('setup ok, skin2 =', skin2)
