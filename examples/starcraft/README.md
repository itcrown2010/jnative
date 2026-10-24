# Colonists — a StarCraft‑style RTS in JNative

A real‑time strategy game written entirely in JNative (strict Java subset → LLVM → native binary).
It is *inspired by* classic 1990s RTS design (workers, minerals, supply, base building, fog of war,
**replays**). All names, art and sound are original and generated in code; no third‑party assets
are used or reproduced.

```
./run.sh              # build if needed, start the game
./run.sh --selftest   # 34 headless checks (economy, build, combat, determinism, replay, UI flow)
./run.sh --headless 900   # AI vs AI match without a window, prints the economy every 30 s
./run.sh --cards /tmp/sc  # render reference screenshots without a window
```

## Playing

| Input | Action |
|-------|--------|
| Left click / drag | select (drag = box, double click = same type on screen, Shift = add/toggle) |
| Right click | smart order: move / attack / gather / set rally point |
| `A` + click | attack‑move · `M` + click move · `S` stop · `H` hold position |
| `B` (worker) | build menu: `C` hub, `D` supply depot, `B` barracks, `R` refinery, `T` turret |
| `W` / `R` | train worker / rifleman · `Y` set rally point · `X` or `Esc` cancel |
| `Ctrl+1..9`, `1..9` | assign / recall control groups (double tap centres the camera) |
| Arrows, screen edge, minimap, wheel | scroll · `Space` jump to last alert · `P` pause · `+`/`-` speed |

Destroy every enemy structure to win. Each match is recorded automatically and written to
`./replays/*.jnrep` when it ends.

## Replays ("载入游戏历史")

Menu → `R` lists saved replays (Up/Down, Enter to play, Delete to remove). While watching:
`Space` pause, `+`/`-` speed up to 16×, `[` `]` jump ±10 s, click the progress bar to seek,
`R` restart, `1`/`2` follow a player's fog of war, `3` see everything.

This works because the simulation is fully deterministic: a replay is just the map seed plus the
list of player commands with their tick numbers (plus periodic checksums that prove a replay
reproduces the original match bit for bit). The selftest verifies that.

## Architecture

* `ScSim` – deterministic fixed‑step simulation (24 ticks/s): entities, economy, construction,
  training queues, A* pathing, combat, fog of war, victory, checksum.
* `ScCommand` / `ScReplay` – every order goes through `ScSim.submit`; replays re‑feed them.
* `ScAI` – computer opponent playing through the same command interface.
* `ScMap`, `ScPath`, `ScDefs` (data tables), `ScEntity`, `ScPlayer`.
* `ScGame` – menu / live / replay state machine, selection, orders, command card, input.
* `ScView`, `ScArt`, `ScAudio` – rendering (terrain baked into 256 px chunks, procedural sprites,
  smoothed fog, minimap, HUD), synthesized sound and music.

## Roadmap

Phase 1 (this): one race, skirmish vs AI, replays.  
Next: more unit types and tech (vehicles, spellcasters, upgrades), a second and third race,
multiple AI personalities, expansions, larger maps, LAN play (lockstep over sockets reuses the
command stream), campaign missions.
