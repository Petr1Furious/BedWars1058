# scripts

## apply-hypixel-parity.py

Brings an existing `plugins/BedWars1058` config folder in line with the Hypixel-parity build
(branch `bw-parity`). The plugin's built-in defaults only apply to fresh installs, so a server that
already has `shop.yml` / `upgrades2.yml` needs this once.

```bash
# server stopped (the plugin writes its config back on shutdown)
cp -a plugins/BedWars1058 /tmp/BedWars1058.new
python3 scripts/apply-hypixel-parity.py /tmp/BedWars1058.new --dry-run   # review the diff
python3 scripts/apply-hypixel-parity.py /tmp/BedWars1058.new
# then swap the folder in (keep the old one as a backup) and start the server
```

Python 3, standard library only. It edits the YAML as text, so comments and key order survive.
Each edit checks the current value: already-new values are skipped (the script is idempotent),
anything unexpected aborts with the file and key named, and nothing is written unless every edit
succeeded.

What it changes:

| file | change |
|---|---|
| `upgrades2.yml` | `cost-4s` on every upgrade tier (3s/4s prices: Sharpness 8; Protection 5/10/20/30; Haste 4/6; Forge 4/8/12/16; Heal Pool 3; Cushioned Boots 2/4) |
| | `trap-prices: [1, 2, 4]` |
| | Heal Pool Regeneration I (`REGENERATION,0,0,base`) |
| | `upgrade-dragon` (Dragon Buff) replaced by `upgrade-cushioned-boots` (Feather Falling I/II, 1/2 diamonds) in the menu and the file |
| | It's a Trap: Blindness I + Slowness I, 8 s; Counter-Offensive: + Jump Boost II; Miner Fatigue: Mining Fatigue I, 10 s |
| `shop.yml` | wooden pickaxe Efficiency I; axes wood/stone/iron/diamond (E1/E1/E2/E3); Speed II, Jump V, Invisibility I potions |
| | chainmail 24 iron, knockback stick 5 gold, 12 end stone, 8 ladders, bridge egg 1 emerald |
| | `tracker` (compass, 2 emeralds, slot 32 of utility, `requires-enemy-beds-destroyed: true`) |
| `config.yml` | `countdowns.next-event-game-end: 600` (game ends at 50:00), `countdowns.next-event-extra-dragon: 300` |
| `Languages/messages_en.yml`, `messages_ru.yml` | trap descriptions for the new durations/effects |

New message keys (Cushioned Boots, tracker, extra-dragon announcement, the bed-gate message) are added
by the plugin itself on start-up.

How the 3s/4s prices are picked: when a game starts, if any team has 3 or more players the arena uses
the `cost-4s` prices for the whole game, otherwise `cost`. A tier without `cost-4s` costs the same in
both. `trap-prices-4s` may be set next to `trap-prices` if trap prices should differ too.
