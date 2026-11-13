"""usage: python mcp.py <runDir> '<json list of [tool, args]>' [outprefix]  -> prints text results, saves images"""
import json, subprocess, sys, base64, os, threading, queue
run, calls = sys.argv[1], json.loads(sys.argv[2]); prefix = sys.argv[3] if len(sys.argv) > 3 else None
p = subprocess.Popen(['node', os.path.expanduser('~/.claude/skills/minecraft-mod-dev/mcpfabric.mjs'), run],
                     stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True, encoding='utf-8')
q = queue.Queue()
threading.Thread(target=lambda: [q.put(l) for l in p.stdout], daemon=True).start()
def send(o): p.stdin.write(json.dumps(o) + '\n'); p.stdin.flush()
def wait(i):
    while True:
        m = json.loads(q.get(timeout=120))
        if m.get('id') == i: return m
send({"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"cli","version":"1"}}})
wait(0); send({"jsonrpc":"2.0","method":"notifications/initialized"})
n = 0
for i, (tool, args) in enumerate(calls, 1):
    if tool == 'sleep':
        import time; time.sleep(args); continue
    send({"jsonrpc":"2.0","id":i,"method":"tools/call","params":{"name":tool,"arguments":args}})
    r = wait(i)
    if 'error' in r: print(tool, 'ERROR', r['error']); continue
    for c in r['result'].get('content', []):
        if c['type'] == 'image':
            n += 1; f = f"{prefix or 'shot'}{'' if n == 1 else '_' + str(n)}.png"
            open(f, 'wb').write(base64.b64decode(c['data'])); print(tool, '->', f)
        else: print(tool, ':', c.get('text', '')[-4000:])
p.stdin.close(); p.terminate()
