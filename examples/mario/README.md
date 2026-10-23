# JNative Runner — a side-scrolling platformer for macOS

Written entirely in JNative (native, no JVM): window, input, 60 Hz fixed-step physics,
pixel-art sprites baked at start-up, chiptune audio synthesized into WAV files.

    ./run.sh              # build if needed, then play
    ./run.sh --selftest   # 44 deterministic gameplay checks (jump heights, speed caps, blocks, enemies, flag…)

Controls: ←/→ or A/D move · Z / Space / ↑ / W / K jump (hold for height) · X / Shift / J run (and throw fireballs with the fire flower) · ↓ / S crouch (big) · P pause · Enter start.

Physics follow the classic platformer constants (px/frame at 60 Hz): walk cap 1.5625, run cap 2.5625,
skid braking, jump velocity and gravity chosen by speed (stand ≈ 4 tiles high, running ≈ 5), variable jump height,
terminal fall speed 4. Art, level layout and music are original.

Courses: 1-1 overworld, 1-2 underground (brick tunnel, pillars, long stampede). Power and fire carry over between courses.
Power-ups: starman (10 s invincible, kills on touch; blocks in 1-1 and 1-2) · mushroom → big; a power block hit while big yields a fire flower (white/red hero, up to two bouncing fireballs; hits turn you small again).
Warp pipes: stand centred on a pipe and press ↓ (1-1: 3rd pipe, 1-2: pipe at column 70) to drop into a coin room; its exit pipe brings you back further along.
