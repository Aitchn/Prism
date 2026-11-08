#!/usr/bin/env python3
"""Checks Prism's borrowed block state families (LookPool) against the game's own blockstate files.

For every family this verifies that
  1. the model our replacement blockstate file gives each real state is the model the game gives it,
  2. every borrowed state resolves to the same model, rotation and variant as the state real blocks are shown as
     (its canonical form), so a real vanilla block never looks different, and
  3. the properties and values of the block in the game are the ones the catalogue enumerates.

Usage:
  java -cp <prism-core test classes> com.aitchn.prism.core.look.PoolDump pool.json
  python3 tools/verify-borrowed-families.py pool.json <assets-dir> <blocks-summary.json>

<assets-dir> holds assets/minecraft/blockstates/*.json of the Minecraft version (for example a checkout of the
misode/mcmeta "<version>-assets" tag); <blocks-summary.json> is that project's summary/blocks/data.json for the same
version. Exits non-zero and prints every difference if anything does not match.
"""
import itertools
import json
import os
import sys


def cond_match(when, state):
    if 'OR' in when:
        return any(cond_match(w, state) for w in when['OR'])
    if 'AND' in when:
        return all(cond_match(w, state) for w in when['AND'])
    return all(state.get(k) in str(v).split('|') for k, v in when.items())


def signature(blockstate, state):
    """What the game draws for `state`: the matching variant, or the list of matching multipart parts."""
    if 'variants' in blockstate:
        matches = []
        for key, value in blockstate['variants'].items():
            pairs = [p.split('=') for p in key.split(',')] if key else []
            if all(state.get(k) == v for k, v in pairs):
                matches.append((len(pairs), json.dumps(value, sort_keys=True)))
        if not matches:
            return 'NO MATCH'
        return max(matches)[1]
    return json.dumps([p['apply'] for p in blockstate['multipart'] if 'when' not in p or cond_match(p['when'], state)],
                      sort_keys=True)


def parse(key):
    return dict(pair.split('=') for pair in key.split(',')) if key else {}


def main():
    pool_file, assets, summary_file = sys.argv[1:4]
    pool = json.load(open(pool_file))
    summary = json.load(open(summary_file))
    problems = []
    for family in pool:
        name = family['block'].split(':')[1]
        path = os.path.join(assets, 'assets/minecraft/blockstates', name + '.json')
        if not os.path.exists(path) or name not in summary:
            problems.append('%s: not in this game version' % name)
            continue
        blockstate = json.load(open(path))
        props, _default = summary[name]
        # 3. properties and values
        ours = {}
        for entry in family['states']:
            for k, v in parse(entry['state']).items():
                ours.setdefault(k, set()).add(v)
        theirs = {k: set(v if isinstance(v, list) else [v]) for k, v in props.items()}
        for k in set(ours) | set(theirs):
            if k not in theirs:
                problems.append('%s: catalogue has property %s the game lacks' % (name, k))
            elif k not in ours:
                problems.append('%s: game has property %s the catalogue lacks' % (name, k))
            elif not ours[k] <= theirs[k]:
                problems.append('%s: values of %s the game lacks: %s' % (name, k, sorted(ours[k] - theirs[k])))
        # 1. our model for each real state equals the game's
        for entry in family['states']:
            state = parse(entry['state'])
            game = signature(blockstate, state)
            if game == 'NO MATCH':
                problems.append('%s[%s]: the game has no variant' % (name, entry['state']))
                continue
            drawn = json.loads(game)
            if isinstance(drawn, list):
                drawn = drawn[0]
            want = {'model': entry['model'], 'x': entry['x'], 'y': entry['y']}
            have = {'model': drawn['model'], 'x': drawn.get('x', 0), 'y': drawn.get('y', 0)}
            if want != have:
                problems.append('%s[%s]: replacement draws %s, the game draws %s' % (name, entry['state'], want, have))
        # 2. borrowed state looks like its canonical form
        by_key = {e['state']: e for e in family['states']}
        for key in family['borrowable'] + ([family['shell']] if family['shell'] else []):
            entry = by_key.get(key)
            if entry is None:
                problems.append('%s[%s]: borrowable state does not exist' % (name, key))
                continue
            if signature(blockstate, parse(key)) != signature(blockstate, parse(entry['canonical'])):
                problems.append('%s[%s]: looks different from its canonical form [%s]' % (name, key, entry['canonical']))
        print('%-18s %4d states, %4d borrowable, class %s' % (name, len(family['states']), len(family['borrowable']),
                                                           family['class']))
    for problem in problems:
        print('MISMATCH', problem)
    print('FAILED' if problems else 'OK: %d families match the game' % len(pool))
    sys.exit(1 if problems else 0)


if __name__ == '__main__':
    main()
