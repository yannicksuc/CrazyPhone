"""Undoes setup.py."""
import os, shutil, glob
V = r'C:\Users\yanni\code\CrazyPhone\versions\1.21.1'
for d in ('run', 'run-client2'):
    for n in ('sodium.jar', 'iris.jar', 'mcpfabric-neoforge-0.4.1+1.21.1.jar'):
        p = os.path.join(V, d, 'mods', n)
        if os.path.exists(p): os.remove(p)
    shutil.rmtree(os.path.join(V, d, 'resourcepacks', 'CrazyPhoneShotsSkins'), ignore_errors=True)
    shutil.rmtree(os.path.join(V, d, 'shaderpacks'), ignore_errors=True)
    for n in ('iris.properties', 'mcpfabric.config.json'):
        p = os.path.join(V, d, 'config', n)
        if os.path.exists(p): os.remove(p)
    for p in (os.path.join(V, d, 'config', 'voicechat', 'voicechat-client.properties'), os.path.join(V, d, 'options.txt')):
        if os.path.exists(p + '.before-shots'): shutil.move(p + '.before-shots', p)
for j in glob.glob(os.path.join(V, 'run-client2', 'mods', 'voicechat-*.jar')): os.remove(j)
for d in ('run', 'run-server'):
    off = os.path.join(V, d, 'mods-off-shots')
    for j in glob.glob(os.path.join(off, '*.jar')): shutil.move(j, os.path.join(V, d, 'mods'))
    if os.path.isdir(off): os.rmdir(off)
sp = os.path.join(V, 'run-server', 'server.properties')
if os.path.exists(sp + '.before-shots'): shutil.move(sp + '.before-shots', sp)
print('teardown ok')
