#!/usr/bin/env python3
"""Apply the Hypixel Bed Wars parity config changes to a BedWars1058 plugin folder.

Usage:
    apply-hypixel-parity.py CONFIG_DIR [--dry-run]

CONFIG_DIR is a *copy* of plugins/BedWars1058 (the folder holding shop.yml,
upgrades2.yml, config.yml and Languages/). Run it with the server stopped,
because the plugin writes its in-memory config back on shutdown.

The YAML is edited as text so comments, ordering and formatting survive.
Every edit is idempotent: it is skipped when the file already has the new
value and fails with a message when neither the old nor the new value is
found. All edits are computed first; files are written only when every
edit succeeded (all-or-nothing). --dry-run prints a unified diff instead.

Standard library only.
"""

import argparse
import difflib
import os
import re
import shutil
import sys


class EditError(Exception):
    pass


# --------------------------------------------------------------------------
# minimal indentation-aware YAML navigation (block style, as Bukkit writes it)
# --------------------------------------------------------------------------

def _indent(line):
    return len(line) - len(line.lstrip(' '))


def _is_content(line):
    s = line.strip()
    return s != '' and not s.startswith('#')


def _key_re(key):
    # key may be quoted by the YAML writer
    k = re.escape(key)
    return re.compile(r"^(\s*)(?:%s|'%s'|\"%s\"):(\s|$)" % (k, k, k))


def block_end(lines, idx):
    """End (exclusive) of the block owned by the key at lines[idx].

    For a key whose value is a list written at the same indent as the key
    (`key:\\n- a`), list items belong to the block too.
    """
    base = _indent(lines[idx])
    i = idx + 1
    while i < len(lines):
        line = lines[i]
        if _is_content(line):
            ind = _indent(line)
            if ind < base:
                break
            if ind == base and not line.lstrip(' ').startswith('- '):
                break
        i += 1
    # do not swallow trailing blank/comment lines that precede the next key
    while i - 1 > idx and not _is_content(lines[i - 1]):
        i -= 1
    return i


def find_key(lines, path, start=0, end=None, indent=None):
    """Index of the line holding the last key of `path` (list of keys)."""
    if end is None:
        end = len(lines)
    key = path[0]
    rx = _key_re(key)
    for i in range(start, end):
        m = rx.match(lines[i])
        if not m:
            continue
        ind = len(m.group(1))
        if indent is not None and ind != indent:
            continue
        if indent is None and start == 0 and ind != 0:
            continue
        if len(path) == 1:
            return i
        bend = block_end(lines, i)
        child_indent = None
        for j in range(i + 1, bend):
            if _is_content(lines[j]):
                child_indent = _indent(lines[j])
                break
        if child_indent is None or child_indent <= ind:
            return None
        return find_key(lines, path[1:], i + 1, bend, child_indent)
    return None


def require_key(lines, path, fname):
    idx = find_key(lines, path)
    if idx is None:
        raise EditError("%s: key '%s' not found" % (fname, '.'.join(path)))
    return idx


def scalar_value(line):
    return line.split(':', 1)[1].strip()


# --------------------------------------------------------------------------
# edit operations; each returns a short description or None when skipped
# --------------------------------------------------------------------------

def set_scalar(f, path, old, new):
    lines = f.lines
    idx = require_key(lines, path, f.name)
    cur = scalar_value(lines[idx])
    if cur == str(new):
        return None
    if cur != str(old):
        raise EditError("%s: '%s' is %r, expected %r (old) or %r (new)" %
                        (f.name, '.'.join(path), cur, str(old), str(new)))
    prefix = lines[idx].split(':', 1)[0]
    lines[idx] = '%s: %s\n' % (prefix, new)
    return "%s: %s -> %s" % ('.'.join(path), old, new)


def rename_key(f, parent_path, old, new):
    lines = f.lines
    if find_key(lines, parent_path + [new]) is not None:
        return None
    idx = find_key(lines, parent_path + [old])
    if idx is None:
        raise EditError("%s: neither '%s' nor '%s' found" %
                        (f.name, '.'.join(parent_path + [old]), '.'.join(parent_path + [new])))
    lines[idx] = lines[idx].replace(old + ':', new + ':', 1)
    return "%s: renamed %s -> %s" % ('.'.join(parent_path), old, new)


def insert_scalar_after(f, sibling_path, key, value):
    """Add `key: value` right after the sibling key (same indent) unless present."""
    lines = f.lines
    parent = sibling_path[:-1]
    if find_key(lines, parent + [key]) is not None:
        return None
    idx = require_key(lines, sibling_path, f.name)
    ind = _indent(lines[idx])
    end = block_end(lines, idx)
    lines.insert(end, '%s%s: %s\n' % (' ' * ind, key, value))
    return "%s: added %s: %s" % ('.'.join(parent), key, value)


def insert_block_after(f, sibling_path, key, block_text):
    """Insert a whole block (text, already indented for the sibling level) after a sibling key."""
    lines = f.lines
    parent = sibling_path[:-1]
    if find_key(lines, parent + [key]) is not None:
        return None
    idx = require_key(lines, sibling_path, f.name)
    end = block_end(lines, idx)
    new = [l + '\n' for l in block_text.rstrip('\n').split('\n')]
    lines[end:end] = new
    return "%s: added %s" % ('.'.join(parent) or '<root>', key)


def replace_block(f, path, new_key, block_text):
    """Replace the block at `path` with `block_text` (whose first key is new_key)."""
    lines = f.lines
    parent = path[:-1]
    if find_key(lines, parent + [new_key]) is not None:
        if find_key(lines, path) is not None:
            raise EditError("%s: both '%s' and '%s' exist" %
                            (f.name, '.'.join(path), '.'.join(parent + [new_key])))
        return None
    idx = require_key(lines, path, f.name)
    end = block_end(lines, idx)
    new = [l + '\n' for l in block_text.rstrip('\n').split('\n')]
    lines[idx:end] = new
    return "replaced %s with %s" % ('.'.join(path), '.'.join(parent + [new_key]))


def _list_items(lines, idx):
    end = block_end(lines, idx)
    return [i for i in range(idx + 1, end) if lines[i].lstrip(' ').startswith('- ')], end


def _item_text(line):
    s = line.lstrip(' ')[2:].strip()
    if len(s) >= 2 and s[0] == s[-1] and s[0] in "'\"":
        s = s[1:-1]
        if line.lstrip(' ')[2:].strip()[0] == "'":
            s = s.replace("''", "'")
    return s


def _item_line(template_line, text):
    ind = _indent(template_line)
    raw = template_line.lstrip(' ')[2:].strip()
    plain_ok = re.match(r"^[A-Za-z0-9_][A-Za-z0-9_,.\- ]*$", text) is not None
    if raw and raw[0] not in "'\"" and plain_ok:
        # keep the unquoted style of the list being edited
        return "%s- %s\n" % (' ' * ind, text)
    return "%s- '%s'\n" % (' ' * ind, text.replace("'", "''"))


def replace_list_item(f, path, old, new):
    lines = f.lines
    idx = require_key(lines, path, f.name)
    items, _ = _list_items(lines, idx)
    texts = [_item_text(lines[i]) for i in items]
    if new in texts and old not in texts:
        return None
    if old not in texts:
        raise EditError("%s: list '%s' has neither %r nor %r (has %r)" %
                        (f.name, '.'.join(path), old, new, texts))
    i = items[texts.index(old)]
    lines[i] = _item_line(lines[i], new)
    return "%s: %r -> %r" % ('.'.join(path), old, new)


def insert_list_item_after(f, path, after, new):
    lines = f.lines
    idx = require_key(lines, path, f.name)
    items, _ = _list_items(lines, idx)
    texts = [_item_text(lines[i]) for i in items]
    if new in texts:
        return None
    if after not in texts:
        raise EditError("%s: list '%s' has no %r to insert after (has %r)" %
                        (f.name, '.'.join(path), after, texts))
    i = items[texts.index(after)]
    lines.insert(i + 1, _item_line(lines[i], new))
    return "%s: added %r" % ('.'.join(path), new)


def replace_list(f, path, old_items, new_items):
    """Replace a whole list value, given the exact expected old items."""
    lines = f.lines
    idx = require_key(lines, path, f.name)
    items, _ = _list_items(lines, idx)
    texts = [_item_text(lines[i]) for i in items]
    if texts == new_items:
        return None
    if texts != old_items:
        raise EditError("%s: list '%s' is %r, expected %r (old) or %r (new)" %
                        (f.name, '.'.join(path), texts, old_items, new_items))
    template = lines[items[0]]
    lines[items[0]:items[-1] + 1] = [_item_line(template, t) for t in new_items]
    return "%s: list updated" % '.'.join(path)


# --------------------------------------------------------------------------

class YFile:
    def __init__(self, root, rel):
        self.name = rel
        self.path = os.path.join(root, rel)
        if not os.path.isfile(self.path):
            raise EditError("%s: file not found at %s" % (rel, self.path))
        with open(self.path, encoding='utf-8') as fh:
            self.original = fh.read()
        self.lines = self.original.splitlines(keepends=True)
        if self.lines and not self.lines[-1].endswith('\n'):
            self.lines[-1] += '\n'

    def text(self):
        return ''.join(self.lines)


CUSHIONED_BOOTS = """\
upgrade-cushioned-boots:
  tier-1:
    currency: diamond
    cost: 1
    cost-4s: 2
    display-item:
      material: IRON_BOOTS
      data: 0
      amount: 1
      enchanted: false
    receive:
    - 'enchant-item: PROTECTION_FALL,1,armor'
  tier-2:
    currency: diamond
    cost: 2
    cost-4s: 4
    display-item:
      material: IRON_BOOTS
      data: 0
      amount: 2
      enchanted: false
    receive:
    - 'enchant-item: PROTECTION_FALL,2,armor'"""

TRACKER = """\
    tracker:
      content-settings:
        content-slot: 32
        is-permanent: false
        is-downgradable: false
        requires-enemy-beds-destroyed: true
      content-tiers:
        tier1:
          tier-item:
            material: COMPASS
            data: 0
            amount: 1
            enchanted: false
          tier-settings:
            cost: 2
            currency: emerald
          buy-items:
            compass:
              material: COMPASS
              data: 0
              amount: 1"""

TRAP_PRICES = """\
  trap-prices:
  - 1
  - 2
  - 4"""


def edits_upgrades(f, log):
    d = 'default-upgrades-settings'
    log(replace_list_item(f, [d, 'menu-content'], 'upgrade-dragon,15', 'upgrade-cushioned-boots,15'))
    log(insert_block_after(f, [d, 'trap-increment-price'], 'trap-prices', TRAP_PRICES))

    costs_4s = {
        'upgrade-swords': [8],
        'upgrade-armor': [5, 10, 20, 30],
        'upgrade-miner': [4, 6],
        'upgrade-forge': [4, 8, 12, 16],
        'upgrade-heal-pool': [3],
    }
    for upgrade, values in costs_4s.items():
        for n, v in enumerate(values, 1):
            log(insert_scalar_after(f, [upgrade, 'tier-%d' % n, 'cost'], 'cost-4s', v))

    log(replace_list_item(f, ['upgrade-heal-pool', 'tier-1', 'receive'],
                          'player-effect: REGENERATION,1,0,base', 'player-effect: REGENERATION,0,0,base'))
    log(replace_block(f, ['upgrade-dragon'], 'upgrade-cushioned-boots', CUSHIONED_BOOTS))

    log(replace_list_item(f, ['base-trap-1', 'receive'],
                          'player-effect: BLINDNESS,1,5,enemy', 'player-effect: BLINDNESS,0,8,enemy'))
    log(replace_list_item(f, ['base-trap-1', 'receive'],
                          'player-effect: SLOW,1,5,enemy', 'player-effect: SLOW,0,8,enemy'))
    log(insert_list_item_after(f, ['base-trap-2', 'receive'],
                               'player-effect: SPEED,1,15,base', 'player-effect: JUMP,1,15,base'))
    log(replace_list_item(f, ['base-trap-4', 'receive'],
                          'player-effect: SLOW_DIGGING,1,15,enemy', 'player-effect: SLOW_DIGGING,0,10,enemy'))


def edits_shop(f, log):
    cc = 'category-content'
    blocks = ['blocks-category', cc]
    # End Stone x12, Ladders x8 (same price)
    log(set_scalar(f, blocks + ['stone', 'content-tiers', 'tier1', 'tier-item', 'amount'], 16, 12))
    log(set_scalar(f, blocks + ['stone', 'content-tiers', 'tier1', 'buy-items', 'stone', 'amount'], 16, 12))
    log(set_scalar(f, blocks + ['ladder', 'content-tiers', 'tier1', 'tier-item', 'amount'], 16, 8))
    log(set_scalar(f, blocks + ['ladder', 'content-tiers', 'tier1', 'buy-items', 'ladder', 'amount'], 16, 8))
    # Knockback stick 5 gold
    log(set_scalar(f, ['melee-category', cc, 'stick', 'content-tiers', 'tier1', 'tier-settings', 'cost'], 10, 5))
    # Chainmail 24 iron
    log(set_scalar(f, ['armor-category', cc, 'chainmail', 'content-tiers', 'tier1', 'tier-settings', 'cost'], 40, 24))

    tools = ['tools-category', cc]
    pick1 = tools + ['pickaxe', 'content-tiers', 'tier1']
    log(set_scalar(f, pick1 + ['tier-item', 'enchanted'], 'false', 'true'))
    log(insert_scalar_after(f, pick1 + ['buy-items', 'wooden-pickaxe', 'amount'], 'enchants', 'DIG_SPEED 1'))

    # Axe: wood E1 (10 iron), stone E1 (10 iron), iron E2 (3 gold), diamond E3 (6 gold)
    axe = tools + ['axe', 'content-tiers']
    log(set_scalar(f, axe + ['tier2', 'tier-item', 'material'], 'IRON_AXE', 'STONE_AXE'))
    log(rename_key(f, axe + ['tier2', 'buy-items'], 'iron-axe', 'stone-axe'))
    log(set_scalar(f, axe + ['tier2', 'buy-items', 'stone-axe', 'material'], 'IRON_AXE', 'STONE_AXE'))
    log(set_scalar(f, axe + ['tier3', 'tier-item', 'material'], 'GOLDEN_AXE', 'IRON_AXE'))
    log(rename_key(f, axe + ['tier3', 'buy-items'], 'gold-axe', 'iron-axe'))
    log(set_scalar(f, axe + ['tier3', 'buy-items', 'iron-axe', 'material'], 'GOLDEN_AXE', 'IRON_AXE'))
    for tier, cost, cur, ench in (('tier1', 10, 'iron', 'DIG_SPEED 1'), ('tier2', 10, 'iron', 'DIG_SPEED 1'),
                                  ('tier3', 3, 'gold', 'DIG_SPEED 2'), ('tier4', 6, 'gold', 'DIG_SPEED 3')):
        check_value(f, axe + [tier, 'tier-settings', 'cost'], cost)
        check_value(f, axe + [tier, 'tier-settings', 'currency'], cur)
        item = {'tier1': 'wooden-axe', 'tier2': 'stone-axe', 'tier3': 'iron-axe', 'tier4': 'diamond-axe'}[tier]
        check_value(f, axe + [tier, 'buy-items', item, 'enchants'], ench)
    check_value(f, tools + ['axe', 'content-settings', 'is-downgradable'], 'true')

    # Potions: amplifiers are used raw (0 = level I)
    pot = ['potions-category', cc]
    log(set_scalar(f, pot + ['jump-potion', 'content-tiers', 'tier1', 'buy-items', 'jump', 'potion'],
                   'JUMP 45 5', 'JUMP 45 4'))
    log(set_scalar(f, pot + ['speed-potion', 'content-tiers', 'tier1', 'buy-items', 'speed', 'potion'],
                   'SPEED 45 2', 'SPEED 45 1'))
    log(set_scalar(f, pot + ['invisibility', 'content-tiers', 'tier1', 'buy-items', 'invisibility', 'potion'],
                   'INVISIBILITY 30 1', 'INVISIBILITY 30 0'))

    util = ['utility-category', cc]
    log(set_scalar(f, util + ['bridge-egg', 'content-tiers', 'tier1', 'tier-settings', 'cost'], 3, 1))
    log(insert_block_after(f, util + ['tower'], 'tracker', TRACKER))


def check_value(f, path, expected):
    idx = require_key(f.lines, path, f.name)
    cur = scalar_value(f.lines[idx])
    if cur != str(expected):
        raise EditError("%s: '%s' is %r, expected %r" % (f.name, '.'.join(path), cur, str(expected)))


def edits_config(f, log):
    log(set_scalar(f, ['countdowns', 'next-event-game-end'], 120, 600))
    log(insert_scalar_after(f, ['countdowns', 'next-event-game-end'], 'next-event-extra-dragon', 300))


LANG = {
    'Languages/messages_en.yml': [
        ('upgrades-base-trap-lore-1',
         ['&7Inflicts Blindness and Slowness', '&7for 5 seconds.', ''],
         ['&7Inflicts Blindness and Slowness', '&7for 8 seconds.', '']),
        ('upgrades-base-trap-lore-2',
         ['&7Grants Speed I for 15 seconds to', '&7allied players near your base.', ''],
         ['&7Grants Speed II and Jump Boost II', '&7for 15 seconds to allied players', '&7near your base.', '']),
    ],
    'Languages/messages_ru.yml': [
        ('upgrades-base-trap-lore-1',
         ['&7Накладывает Слепоту и Медлительность', '&7на 5 секунд.', ''],
         ['&7Накладывает Слепоту и Медлительность', '&7на 8 секунд.', '']),
        ('upgrades-base-trap-lore-2',
         ['&7Даёт вам и вашим союзникам', '&7скорость I на 15 секунд.', ''],
         ['&7Даёт вам и вашим союзникам', '&7Скорость II и Прыгучесть II', '&7на 15 секунд.', '']),
    ],
}


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n\n')[0])
    ap.add_argument('config_dir', help='copy of plugins/BedWars1058')
    ap.add_argument('--dry-run', action='store_true', help='print a diff, write nothing')
    args = ap.parse_args()
    root = args.config_dir

    files = []
    try:
        for rel, fn in (('upgrades2.yml', edits_upgrades), ('shop.yml', edits_shop), ('config.yml', edits_config)):
            f = YFile(root, rel)
            files.append(f)
            changes = []
            fn(f, lambda c: changes.append(c) if c else None)
            f.changes = changes
        for rel, entries in LANG.items():
            if not os.path.isfile(os.path.join(root, rel)):
                print('note: %s missing, skipped (the plugin creates it with the new defaults)' % rel)
                continue
            f = YFile(root, rel)
            files.append(f)
            changes = []
            for key, old, new in entries:
                c = replace_list(f, [key], old, new)
                if c:
                    changes.append(c)
            f.changes = changes
    except EditError as e:
        print('ERROR: %s' % e, file=sys.stderr)
        print('Nothing was written.', file=sys.stderr)
        return 1

    total = 0
    for f in files:
        new = f.text()
        if new == f.original:
            print('%s: already up to date' % f.name)
            continue
        total += len(f.changes)
        print('%s: %d change(s)' % (f.name, len(f.changes)))
        for c in f.changes:
            print('  - %s' % c)
        if args.dry_run:
            sys.stdout.writelines(difflib.unified_diff(
                f.original.splitlines(keepends=True), new.splitlines(keepends=True),
                fromfile='a/' + f.name, tofile='b/' + f.name))
    if args.dry_run:
        print('dry run: %d change(s), nothing written' % total)
        return 0
    for f in files:
        new = f.text()
        if new != f.original:
            tmp = f.path + '.parity-tmp'
            with open(tmp, 'w', encoding='utf-8') as fh:
                fh.write(new)
            shutil.copymode(f.path, tmp)
            os.replace(tmp, f.path)
    print('applied %d change(s)' % total)
    return 0


if __name__ == '__main__':
    sys.exit(main())
