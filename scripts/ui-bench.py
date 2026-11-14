"""
UI test bench: plays the same phone scenario on every version node and lays the screenshots out side by side.

    python scripts/ui-bench.py                     # every node below
    python scripts/ui-bench.py 1.21.1 26.3-fabric  # only these
    python scripts/ui-bench.py --jobs 4            # 4 nodes at a time (default 3)

For each node: prepares the dev client's options (windowed, muted, no pause when unfocused, GUI scale 2, no
tutorial toast), starts the dev server with CRAZYPHONE_DEV_COMMANDS=1 and the dev client with CRAZYPHONE_BENCH=1
(kept behind the other windows, the real mouse is never used), waits for client/BenchRunner to finish its scenario,
then stops both and copies the screenshots and the report to .dev-launch/bench/<node>/.

At the end: .dev-launch/bench/<node>.png (one sheet per node), .dev-launch/bench/compare.png (each step across
all nodes) and .dev-launch/bench/summary.txt (each node's report, missing screenshots and hooks that did not run).
"""
import os
import shutil
import subprocess
import sys
import time

from PIL import Image, ImageDraw

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NODES = ['1.20.1', '1.21.1', '1.21.10', '26.1', '26.2', '26.3', '1.21.1-fabric', '26.1-fabric', '26.2-fabric', '26.3-fabric']
STEPS = ['home', 'home_hover', 'conversation_rest', 'conversation_rest_hover', 'conversation_typing', 'conversation_plus',
         'drawer_smileys', 'drawer_flags', 'conversation_sent', 'conversation_header_hover', 'chat_and_held_phone']
OUT = os.path.join(REPO, '.dev-launch', 'bench')
CLIENT_OPTIONS = {
    'fullscreen': 'false', 'soundCategory_master': '0.0', 'pauseOnLostFocus': 'false', 'guiScale': '2',
    'tutorialStep': 'none', 'onboardAccessibility': 'false', 'skipMultiplayerWarning': 'true', 'joinedFirstServer': 'true',
}
SERVER_TIMEOUT = 420
BENCH_TIMEOUT = 600
CLIENT_JOIN_TIMEOUT = 420
# The scenario takes a screenshot every few seconds (the first after ~5 s in the world plus the setup steps).
STEP_TIMEOUT = 60
# Nodes benched at the same time (python scripts/ui-bench.py --jobs N ...).
DEFAULT_JOBS = 3


PLAYER = 'LordFinn'


def offline_uuid(name):
    """The UUID an offline-mode server gives this name (Java's UUID.nameUUIDFromBytes("OfflinePlayer:" + name))."""
    import hashlib
    import uuid
    h = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode('utf-8')).digest())
    h[6] = (h[6] & 0x0F) | 0x30
    h[8] = (h[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(h)))


def ensure_op(node):
    """The dev client joins offline: its player must be an operator under its offline UUID (an entry written with
    the account's online UUID does not count, the bench commands were then all refused)."""
    import json
    path = os.path.join(REPO, 'versions', node, 'run-server', 'ops.json')
    ops = []
    if os.path.exists(path):
        try:
            with open(path, encoding='utf-8') as f:
                ops = json.load(f)
        except ValueError:
            ops = []
    wanted = offline_uuid(PLAYER)
    if not any(o.get('uuid') == wanted for o in ops):
        ops.append({'uuid': wanted, 'name': PLAYER, 'level': 4, 'bypassesPlayerLimit': False})
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(ops, f, indent=2)


# The bench's own world: flat (generated in a blink, the same light and background on every node), no structures
# nor animals, recreated each run so every screenshot starts from the same state. dev-launch then puts its
# dev-defaults pack in it (eternal day, clear weather, peaceful). The dev world ("world") is left alone.
BENCH_LEVEL = 'bench-flat'
BENCH_SERVER_PROPERTIES = {
    'level-name': BENCH_LEVEL, 'level-type': 'minecraft:flat', 'generate-structures': 'false',
    'spawn-animals': 'false', 'spawn-npcs': 'false', 'spawn-monsters': 'false', 'view-distance': '4',
    'simulation-distance': '4',
}


def use_bench_world(node):
    """Points the node's server at the bench world (a fresh one) and returns the previous server.properties, to
    put back afterwards (None when there was none)."""
    run_server = os.path.join(REPO, 'versions', node, 'run-server')
    os.makedirs(run_server, exist_ok=True)
    path = os.path.join(run_server, 'server.properties')
    original = None
    lines = []
    if os.path.exists(path):
        with open(path, encoding='utf-8', errors='replace') as f:
            original = f.read()
        lines = original.splitlines()
    seen = set()
    for i, line in enumerate(lines):
        key = line.split('=', 1)[0]
        if key in BENCH_SERVER_PROPERTIES:
            lines[i] = key + '=' + BENCH_SERVER_PROPERTIES[key]
            seen.add(key)
    lines += [k + '=' + v for k, v in BENCH_SERVER_PROPERTIES.items() if k not in seen]
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
    shutil.rmtree(os.path.join(run_server, BENCH_LEVEL), ignore_errors=True)
    return original


def restore_server_properties(node, original):
    path = os.path.join(REPO, 'versions', node, 'run-server', 'server.properties')
    if original is None:
        return
    # dev-launch may have rewritten its own keys meanwhile: only the bench's ones go back to what they were.
    before = {l.split('=', 1)[0]: l for l in original.splitlines() if '=' in l}
    with open(path, encoding='utf-8', errors='replace') as f:
        lines = f.read().splitlines()
    out = []
    for line in lines:
        key = line.split('=', 1)[0]
        if key in BENCH_SERVER_PROPERTIES:
            if key in before:
                out.append(before[key])
        else:
            out.append(line)
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out) + '\n')


def ps(command, env=None):
    full = dict(os.environ)
    if env:
        full.update(env)
    # No pipes: the game processes started by dev-launch inherit the handles, and a captured output would make
    # this call wait for the game itself to exit (the bench then hung after starting the server).
    return subprocess.run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command],
                          cwd=REPO, env=full, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def prepare_options(run_dir):
    os.makedirs(run_dir, exist_ok=True)
    path = os.path.join(run_dir, 'options.txt')
    lines = []
    if os.path.exists(path):
        with open(path, encoding='utf-8', errors='replace') as f:
            lines = f.read().splitlines()
    seen = set()
    for i, line in enumerate(lines):
        key = line.split(':', 1)[0]
        if key in CLIENT_OPTIONS:
            lines[i] = key + ':' + CLIENT_OPTIONS[key]
            seen.add(key)
    lines += [k + ':' + v for k, v in CLIENT_OPTIONS.items() if k not in seen]
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')


def wait_for(predicate, timeout):
    end = time.time() + timeout
    while time.time() < end:
        if predicate():
            return True
        time.sleep(3)
    return False


def read_log(node, kind):
    """A node's dev-launch log with its error stream (Gradle prints BUILD FAILED and the compile errors there)."""
    text = ''
    for suffix in ('', '.err'):
        try:
            with open(os.path.join(REPO, '.dev-launch', 'logs', '%s-%s.log%s' % (node, kind, suffix)), encoding='utf-8', errors='replace') as f:
                text += f.read()
        except OSError:
            pass
    return text


def server_ready(node):
    text = read_log(node, 'server')
    # Ready, or over: a build failure, a crash (port taken...) or the Gradle run ending.
    return any(m in text for m in ('Done (', 'BUILD FAILED', 'Failed to initialize server', 'BUILD SUCCESSFUL'))


def joined(node):
    log = os.path.join(REPO, '.dev-launch', 'logs', node + '-server.log')
    try:
        with open(log, encoding='utf-8', errors='replace') as f:
            return 'joined the game' in f.read()
    except OSError:
        return False


CLIENT_FAILURES = ('Connection refused', 'Failed to connect', 'Failed to Quick Play', 'Timed out', 'Disconnected',
                   'lost connection', '---- Minecraft Crash Report', 'BUILD FAILED', 'Exception in thread "Render thread"')
# A login answered by nothing (the client stuck during the handshake) gives up after this long.
LOGIN_TIMEOUT = 40


def client_problem(node, connecting_since):
    """Why the client cannot get into the game, as soon as its log shows it (None while all is fine)."""
    text = read_log(node, 'client')
    for marker in CLIENT_FAILURES:
        i = text.find(marker)
        if i >= 0:
            start = text.rfind('\n', 0, i) + 1
            end = text.find('\n', i)
            return 'client: ' + text[start:end if end >= 0 else None].strip()[:200]
    # connecting_since None: in the world already, only the failures above count.
    if connecting_since is not None and 'Connecting to' in text:
        if connecting_since[0] is None:
            connecting_since[0] = time.time()
        elif time.time() - connecting_since[0] > LOGIN_TIMEOUT:
            return 'client: login not answered after %ds (stuck during the handshake)' % LOGIN_TIMEOUT
    return None


def run_node(node):
    run_dir = os.path.join(REPO, 'versions', node, 'run')
    shots = os.path.join(run_dir, 'screenshots')
    dest = os.path.join(OUT, node)
    shutil.rmtree(dest, ignore_errors=True)
    os.makedirs(dest, exist_ok=True)
    if os.path.isdir(shots):
        for name in os.listdir(shots):
            if name.startswith('bench_'):
                os.remove(os.path.join(shots, name))
    prepare_options(run_dir)
    ensure_op(node)
    ps('.\\scripts\\dev-launch.ps1 stop %s all' % node)
    log = os.path.join(REPO, '.dev-launch', 'logs', node + '-server.log')
    # Both logs of the previous run go first: their old failures must not be read as this run's.
    for old in (log, os.path.join(REPO, '.dev-launch', 'logs', node + '-client.log')):
        if os.path.exists(old):
            os.remove(old)
    print('[%s] server...' % node, flush=True)
    ps('.\\scripts\\dev-launch.ps1 server %s' % node, {'CRAZYPHONE_DEV_COMMANDS': '1'})
    if not wait_for(lambda: server_ready(node), SERVER_TIMEOUT):
        return 'server did not start'
    text = read_log(node, 'server')
    if 'Done (' not in text:
        ps('.\\scripts\\dev-launch.ps1 stop %s all' % node)
        errors = [l.strip() for l in text.splitlines() if ': error:' in l][:3]
        if 'BUILD FAILED' in text:
            return 'server failed: build failed' + ''.join('\n    ' + e[-220:] for e in errors)
        return 'server failed: crashed or stopped (see its log)'
    print('[%s] client...' % node, flush=True)
    ps('& .\\scripts\\ui-bench-client.ps1 -Repo . -Version %s -Kind client' % node,
       {'CRAZYPHONE_DEV_COMMANDS': '1', 'CRAZYPHONE_BENCH': '1'})
    report = os.path.join(shots, 'bench_report.txt')
    # Fail fast: the client must show up on the server within CLIENT_JOIN_TIMEOUT, then each screenshot is printed
    # as it lands so a stuck scenario is visible right away.
    connecting_since = [None]
    problem = [None]

    def in_game():
        if joined(node):
            return True
        problem[0] = client_problem(node, connecting_since)
        return problem[0] is not None
    if not wait_for(in_game, CLIENT_JOIN_TIMEOUT) or problem[0]:
        ps('.\\scripts\\dev-launch.ps1 stop %s all' % node)
        return problem[0] or 'client never joined the server'
    print('[%s] client joined, scenario running' % node, flush=True)
    seen = set()
    last_new = [time.time()]

    def progress():
        if os.path.isdir(shots):
            for name in sorted(os.listdir(shots)):
                if name.startswith('bench_') and name not in seen:
                    seen.add(name)
                    last_new[0] = time.time()
                    print('[%s]   %s' % (node, name), flush=True)
        if os.path.exists(report):
            return True
        problem[0] = client_problem(node, None)
        if problem[0] is None and time.time() - last_new[0] > STEP_TIMEOUT:
            problem[0] = 'scenario stuck: no screenshot for %ds after %s' % (STEP_TIMEOUT, sorted(seen)[-1] if seen else 'joining')
        return problem[0] is not None
    ok = wait_for(progress, BENCH_TIMEOUT) and problem[0] is None
    time.sleep(3)
    ps('.\\scripts\\dev-launch.ps1 stop %s all' % node)
    if os.path.isdir(shots):
        for name in os.listdir(shots):
            if name.startswith('bench_'):
                shutil.copy2(os.path.join(shots, name), os.path.join(dest, name))
    return 'ok' if ok else (problem[0] or 'scenario did not finish (no report)')


def sheet(node):
    dest = os.path.join(OUT, node)
    tiles = []
    for step in STEPS:
        path = os.path.join(dest, 'bench_%s.png' % step)
        tiles.append((step, Image.open(path).convert('RGB') if os.path.exists(path) else None))
    w, h = 854, 480
    cols = 3
    rows = (len(tiles) + cols - 1) // cols
    out = Image.new('RGB', (cols * w, rows * (h + 24)), (30, 30, 30))
    draw = ImageDraw.Draw(out)
    for i, (step, im) in enumerate(tiles):
        x, y = (i % cols) * w, (i // cols) * (h + 24)
        draw.text((x + 6, y + 4), '%s  %s' % (node, step), fill=(255, 255, 255))
        if im is not None:
            out.paste(im.resize((w, h)), (x, y + 24))
        else:
            draw.text((x + 20, y + 200), 'MISSING', fill=(255, 80, 80))
    out.save(os.path.join(OUT, node + '.png'))


def compare(nodes):
    # Each step: the phone area of every node side by side (centre crop).
    cw, ch = 300, 440
    out = Image.new('RGB', (len(nodes) * cw, len(STEPS) * (ch + 20)), (30, 30, 30))
    draw = ImageDraw.Draw(out)
    for r, step in enumerate(STEPS):
        for c, node in enumerate(nodes):
            x, y = c * cw, r * (ch + 20)
            draw.text((x + 4, y + 4), '%s %s' % (node, step), fill=(255, 255, 255))
            path = os.path.join(OUT, node, 'bench_%s.png' % step)
            if os.path.exists(path):
                im = Image.open(path).convert('RGB').resize((854, 480))
                out.paste(im.crop((277, 20, 277 + cw, 20 + ch)), (x, y + 20))
    out.save(os.path.join(OUT, 'compare.png'))


def bench_node(node):
    started = time.time()
    original = use_bench_world(node)
    try:
        status = run_node(node)
    finally:
        restore_server_properties(node, original)
    sheet(node)
    report = os.path.join(OUT, node, 'bench_report.txt')
    lines = open(report, encoding='utf-8').read().splitlines() if os.path.exists(report) else []
    missing = [s for s in STEPS if not os.path.exists(os.path.join(OUT, node, 'bench_%s.png' % s))]
    block = ['== %s: %s (%ds)' % (node, status, time.time() - started)]
    block += ['  ' + l for l in lines if 'FAILED' in l or 'DID NOT RUN' in l or 'NOT CREATED' in l]
    if missing:
        block.append('  missing screenshots: ' + ', '.join(missing))
    print('\n'.join(block), flush=True)
    return block


def main():
    args = sys.argv[1:]
    jobs = DEFAULT_JOBS
    if '--jobs' in args:
        k = args.index('--jobs')
        jobs = int(args[k + 1])
        del args[k:k + 2]
    nodes = args or NODES
    os.makedirs(OUT, exist_ok=True)
    # A clean start: whatever a previous run or a manual test left running goes first.
    for other in NODES + ['1.20.4', '1.20.1-fabric']:
        ps('.\\scripts\\dev-launch.ps1 stop %s all' % other)
    started = time.time()
    # Every node has its own ports and run directories (dev-launch), so several run at once; each node's
    # server + client take about 3 GB, hence a few at a time rather than all of them.
    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(max_workers=jobs) as pool:
        blocks = list(pool.map(bench_node, nodes))
    summary = [line for block in blocks for line in block]
    # Every node with results, so rerunning one node still gives the full comparison.
    compare([n for n in NODES if os.path.isdir(os.path.join(OUT, n))])
    with open(os.path.join(OUT, 'summary.txt'), 'w', encoding='utf-8') as f:
        f.write('\n'.join(summary) + '\n')
    print('\n'.join(summary))
    print('total %ds with %d at a time' % (time.time() - started, jobs))

if __name__ == '__main__':
    main()
