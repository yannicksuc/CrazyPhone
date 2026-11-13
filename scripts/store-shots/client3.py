import subprocess, os, sys
c = open(r'C:\Users\yanni\code\CrazyPhone\.dev-launch\shots\servercmd.txt', encoding='utf-8-sig').read().strip()
c = c.replace('serverRunVmArgs.txt', 'client3RunVmArgs.txt').replace('serverRunProgramArgs.txt', 'client3RunProgramArgs.txt')
env = dict(os.environ, CRAZYPHONE_DEV_COMMANDS='1')
log = open(r'C:\Users\yanni\code\CrazyPhone\.dev-launch\logs\1.21.1-client3.log', 'w')
p = subprocess.Popen(c, cwd=r'C:\Users\yanni\code\CrazyPhone\versions\1.21.1\run-client3', env=env, stdout=log, stderr=subprocess.STDOUT,
                     creationflags=0x08000000 | 0x00000200)
open(r'C:\Users\yanni\code\CrazyPhone\.dev-launch\shots\client3.pid', 'w').write(str(p.pid)); print(p.pid)
