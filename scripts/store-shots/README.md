# Store screenshots (CurseForge / Modrinth page)

Dev-only helpers used to shoot the mod page gallery on NeoForge 1.21.1, with shaders and two or three players.

- `setup.py [skin.png]`: puts Sodium, Iris, Complementary Reimagined, MCP Fabric and an offline-skins resource
  pack into `versions/1.21.1/run*` (originals moved to `mods-off-shots/`, `*.before-shots` backups). Expects the
  downloads in `.dev-launch/shots/dl/` and skins in `.dev-launch/shots/skins/`.
- `teardown.py`: puts everything back.
- `restart.ps1 [-Server]`: (re)starts the dev server (with `CRAZYPHONE_DEV_COMMANDS=1`) and both clients behind
  the other windows. `client3.py` starts a third client (`run-client3`, `client3Run*Args.txt`).
- `mcp.py <runDir> '<json [tool, args] list>' [prefix]`: drives a client through MCP Fabric, saves screenshots.
- `rcon.py <port> <command>...`: server console.

Offline players get a default skin chosen from their UUID (`entity/player/wide|slim/<name>.png`): the resource pack
overrides those textures (LordFinn = zuri, Bouteilles = makena, FoxieFern = ari).

Dev commands (only with `CRAZYPHONE_DEV_COMMANDS=1`):

- client: `/phoneui capture enter|view|shoot|exit`, `/phoneui hud on|off`, `/phoneui reloadpacks`,
  `/phoneui perspective first|back|front`, `/phoneui hover <x> <y>|off`, `/phoneui view|edit <photoId>`, `/phoneui close`
- server: `/crazyphone register|unlock|seed [first second]|group|groupname`, `/crazyphone devmsg|devimg <conv> <sender> ...`,
  `/crazyphone devphotos <owner> [indexes to delete]`, `/crazyphone devcontact|devfav <owner> <number>`,
  `/crazyphone devuuid <number> <uuid>`, `/crazyphone devframe`, `/crazyphone call start|answer|open|hangup <conv>`
