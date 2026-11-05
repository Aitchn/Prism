#!/usr/bin/env python3
"""Generates the YAML content and placeholder textures of the Mekanism rebuild's resource chains.

Run from validation/mekanism:  python3 tools/generate_content.py
It writes every file it is responsible for (items, blocks, recipes, textures); hand-written files that are not listed
here are left alone. Needs Pillow for the textures. The tables below are the single place to read what stage 1 adds.
"""
import os
import random
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:  # content still gets written; textures need Pillow
    Image = None

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
PRISM = ROOT / "prism"
TEX = ROOT / "assets/mekanism/textures"

# ---------------------------------------------------------------------------------------------- tables

# metals: id -> (colour, vanilla ingot or None, vanilla ore item or None). Own metals have an ore stand-in item.
OWN = {"osmium": (110, 160, 215), "tin": (205, 215, 225), "lead": (70, 75, 100), "uranium": (110, 190, 60)}
VANILLA = {"iron": ((215, 215, 215), "minecraft:iron_ingot", "minecraft:raw_iron"),
           "gold": ((240, 205, 60), "minecraft:gold_ingot", "minecraft:raw_gold"),
           "copper": ((205, 125, 85), "minecraft:copper_ingot", "minecraft:raw_copper")}
ALLOYS = {"steel": (95, 100, 110), "bronze": (190, 130, 60), "refined_obsidian": (150, 80, 210),
          "refined_glowstone": (250, 215, 110)}
DUSTS = {"coal": (40, 40, 45), "charcoal": (70, 55, 45), "lapis": (40, 70, 190), "diamond": (110, 225, 230),
         "emerald": (60, 200, 100), "quartz": (235, 230, 220), "obsidian": (60, 30, 90)}
NAMES = {"refined_obsidian": "Refined Obsidian", "refined_glowstone": "Refined Glowstone"}


def title(i):
    return NAMES.get(i, i.replace("_", " ").title())


def colour(metal):
    return OWN.get(metal) or VANILLA[metal][0] if metal in OWN or metal in VANILLA else ALLOYS[metal]


# machines: id -> (energy capacity, max receive, tank capacity, signature item for the crafting recipe, colour)
NEW_MACHINES = {
    "osmium_compressor": (20000, 2000, 0, "minecraft:piston", (90, 130, 190)),
    "combiner": (20000, 2000, 0, "minecraft:hopper", (190, 150, 90)),
    "metallurgic_infuser": (20000, 2000, 0, "minecraft:cauldron", (170, 90, 90)),
    "precision_sawmill": (20000, 2000, 0, "minecraft:stonecutter", (150, 120, 80)),
    "energized_smelter": (20000, 2000, 0, "minecraft:furnace", (220, 120, 60)),
}
CUBES = {"basic": (2_000_000, 8_192), "advanced": (8_000_000, 32_768), "elite": (32_000_000, 131_072),
         "ultimate": (128_000_000, 524_288)}

# ---------------------------------------------------------------------------------------------- writers

written = set()


def put(path, text):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    written.add(path)


def item(i, name=None, extra=""):
    put(PRISM / f"items/{i}.yml", f'base: minecraft:paper\ncomponents:\n  prism:display_name: "{name or title(i)}"\n{extra}')


def block_item(i, name=None):
    item(i, name, f"  prism:places_block: mekanism:{i}\n")


def static_block(i, comment):
    put(PRISM / f"blocks/{i}.yml", f"# Key: mekanism:{i}. {comment}\nbase: minecraft:barrier\ndrop: mekanism:{i}\n")
    block_item(i)


def machine_block(i, spec):
    cap, recv, tank, _sig, _col = spec
    put(PRISM / f"blocks/{i}.yml",
        f"# Key: mekanism:{i}. Two looks (block/{i}_idle.png and block/{i}_active.png); recipes: prism/recipes/machine/{i}/.\n"
        f"base: minecraft:barrier\ndrop: mekanism:{i}\ncomponents:\n  prism:variants: [idle, active]\nbehaviors:\n"
        f"  - type: mekanism:machine\n    machine: {i}\n    energy_capacity: {cap}\n    max_receive: {recv}\n"
        f"    tank_capacity: {tank}\n")
    block_item(i)


def mrecipe(machine, name, inp, out, count=1, ticks=200, energy=50, input2=None, chem_in=None, in_count=1,
            in2_count=1, chem_in2=None, chem_out=None):
    """One machine recipe file. inp/out may be None (gas-only steps); chem_* are (chemical, mB) pairs."""
    def stack(s, n):
        return s if n == 1 else f"{{item: {s}, count: {n}}}"
    text = f"type: mekanism:machine\nmachine: {machine}\n"
    if inp:
        text += f"input: {stack(inp, in_count)}\n"
    if input2:
        text += f"input2: {stack(input2, in2_count)}\n"
    if out:
        text += f"output: {stack(out, count)}\n"
    if chem_in:
        text += f"chemical_in: {{chemical: {chem_in[0]}, amount: {chem_in[1]}}}\n"
    if chem_in2:
        text += f"chemical_in2: {{chemical: {chem_in2[0]}, amount: {chem_in2[1]}}}\n"
    if chem_out:
        text += f"chemical_out: {{chemical: {chem_out[0]}, amount: {chem_out[1]}}}\n".replace(
            "chemical_out: {", "chemical_out:\n  - {")
    text += f"ticks: {ticks}\nenergy: {energy}\n"
    put(PRISM / f"recipes/machine/{machine}/{name}.yml", text)


def shaped(name, rows, key, result, count=1):
    keys = "".join(f"  {k}: {v}\n" for k, v in key.items())
    res = result if count == 1 else f"{{item: {result}, count: {count}}}"
    put(PRISM / f"recipes/crafting/{name}.yml",
        "type: prism:shaped\npattern:\n" + "".join(f'  - "{r}"\n' for r in rows) + f"key:\n{keys}result: {res}\n")


def shapeless(name, ingredients, result, count=1):
    res = result if count == 1 else f"{{item: {result}, count: {count}}}"
    put(PRISM / f"recipes/crafting/{name}.yml",
        f"type: prism:shapeless\ningredients: [{', '.join(ingredients)}]\nresult: {res}\n")


def cooking(kind, dust, ingot):
    name = dust.split(":")[1]
    put(PRISM / f"recipes/{kind}/{name}.yml",
        f"type: prism:{'smelting' if kind == 'smelting' else 'blasting'}\ninput: {dust}\nresult: {ingot}\nexperience: 0.7\n")


# ---------------------------------------------------------------------------------------------- textures

def rng(seed):
    return random.Random(seed)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def new_img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def save(img, path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    written.add(path)


def tex_item(name, kind, col):
    if Image is None:
        return
    img, d, r = new_img(), None, rng(name)
    d = ImageDraw.Draw(img)
    dark, light = shade(col, 0.6), shade(col, 1.25)
    if kind == "dust":
        for y in range(7, 14):
            w = (y - 5)
            for x in range(8 - w // 2 - 1, 8 + w // 2 + 2):
                d.point((x, y), fill=col if r.random() > 0.25 else light)
    elif kind == "dirty_dust":
        for y in range(7, 14):
            w = (y - 5)
            for x in range(8 - w // 2 - 1, 8 + w // 2 + 2):
                d.point((x, y), fill=shade(col, 0.75) if r.random() > 0.3 else (90, 70, 50))
    elif kind == "clump":
        d.ellipse((3, 4, 12, 12), fill=col, outline=dark)
        for _ in range(8):
            d.point((r.randint(4, 11), r.randint(5, 11)), fill=light)
    elif kind == "shard":
        d.polygon([(8, 2), (12, 9), (8, 14), (4, 9)], fill=col, outline=dark)
        d.line((8, 3, 8, 13), fill=light)
    elif kind == "ingot":
        d.polygon([(2, 10), (5, 5), (14, 5), (11, 10)], fill=light, outline=dark)
        d.rectangle((2, 10, 11, 12), fill=col, outline=dark)
    elif kind == "nugget":
        d.ellipse((6, 6, 10, 10), fill=col, outline=dark)
    elif kind == "ore":
        d.rectangle((1, 1, 14, 14), fill=(125, 125, 130), outline=(80, 80, 85))
        for _ in range(9):
            x, y = r.randint(2, 12), r.randint(2, 12)
            d.rectangle((x, y, x + 1, y + 1), fill=col)
    elif kind == "gem":
        d.polygon([(8, 2), (13, 7), (8, 14), (3, 7)], fill=col, outline=dark)
    elif kind == "alloy":
        d.rectangle((3, 3, 12, 12), fill=col, outline=dark)
        d.line((3, 8, 12, 8), fill=light)
        d.line((8, 3, 8, 12), fill=light)
    elif kind == "circuit":
        d.rectangle((2, 2, 13, 13), fill=(30, 110, 60), outline=(15, 60, 35))
        d.line((4, 5, 11, 5), fill=col)
        d.line((4, 9, 11, 9), fill=col)
        d.rectangle((6, 6, 9, 8), fill=shade(col, 0.8))
    elif kind == "upgrade":
        d.rectangle((2, 3, 13, 12), fill=(45, 45, 55), outline=(20, 20, 25))
        d.polygon([(8, 4), (11, 8), (5, 8)], fill=col)
        d.rectangle((7, 8, 9, 11), fill=col)
    save(img, TEX / f"item/{name}.png")


def tex_block(name, col, pattern="plain"):
    if Image is None:
        return
    img, r = Image.new("RGBA", (16, 16), col), rng(name)
    d = ImageDraw.Draw(img)
    for _ in range(40):
        d.point((r.randint(0, 15), r.randint(0, 15)), fill=shade(col, r.choice([0.85, 1.12])))
    d.rectangle((0, 0, 15, 15), outline=shade(col, 0.6))
    if pattern == "storage":
        d.rectangle((2, 2, 13, 13), outline=shade(col, 1.3))
        d.line((2, 8, 13, 8), fill=shade(col, 0.7))
    save(img, TEX / f"block/{name}.png")
    save(img, TEX / f"item/{name}.png")


def tex_machine(name, col):
    if Image is None:
        return
    for look in ("idle", "active"):
        img = Image.new("RGBA", (16, 16), (70, 72, 78, 255))
        d = ImageDraw.Draw(img)
        d.rectangle((0, 0, 15, 15), outline=(40, 42, 48))
        d.rectangle((3, 3, 12, 9), fill=shade(col, 1.0 if look == "active" else 0.55), outline=(30, 30, 34))
        for x in range(4, 12, 2):
            d.line((x, 11, x, 13), fill=(200, 230, 120) if look == "active" else (60, 70, 50))
        save(img, TEX / f"block/{name}_{look}.png")
    tex_item(name, "ore", col)  # replaced below by a proper icon
    img = Image.open(TEX / f"block/{name}_idle.png").copy()
    save(img, TEX / f"item/{name}.png")


def tex_cube(name, col):
    if Image is None:
        return
    img = Image.new("RGBA", (16, 16), shade(col, 0.5))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 15, 15), outline=shade(col, 0.3))
    d.rectangle((3, 3, 12, 12), fill=shade(col, 1.1), outline=shade(col, 0.4))
    d.rectangle((6, 6, 9, 9), fill=(255, 240, 160))
    save(img, TEX / f"block/{name}.png")
    save(img, TEX / f"item/{name}.png")


# ---------------------------------------------------------------------------------------------- generation

def gen():
    # ---- the resource chains of all seven metals
    for metal in list(OWN) + list(VANILLA):
        col = OWN.get(metal) or VANILLA[metal][0]
        if metal in OWN:
            ore, ingot = f"mekanism:{metal}_ore", f"mekanism:{metal}_ingot"
        else:
            ore, ingot = VANILLA[metal][2], VANILLA[metal][1]
        dust = f"mekanism:{metal}_dust"
        dirty, clump, shard = (f"mekanism:{metal}_{k}" for k in ("dirty_dust", "clump", "shard"))
        for k, kind in (("dust", "dust"), ("dirty_dust", "dirty_dust"), ("clump", "clump"), ("shard", "shard")):
            item(f"{metal}_{k}")
            tex_item(f"{metal}_{k}", kind, col)
        if metal in OWN:
            item(f"{metal}_ore")
            item(f"{metal}_ingot")
            tex_item(f"{metal}_ore", "ore", col)
            tex_item(f"{metal}_ingot", "ingot", col)
        # 1x, 2x, 3x, 4x processing, as in Mekanism
        mrecipe("enrichment_chamber", f"{metal}_ore", ore, dust, 2)
        mrecipe("purification_chamber", f"{metal}_ore", ore, clump, 3, 300, chem_in=("oxygen", 1))
        mrecipe("chemical_injection_chamber", f"{metal}_ore", ore, shard, 4, 300, chem_in=("hydrogen_chloride", 10))
        mrecipe("chemical_injection_chamber", f"{metal}_crystal", f"mekanism:{metal}_crystal", shard, 1, 300,
                chem_in=("hydrogen_chloride", 10))
        mrecipe("purification_chamber", f"{metal}_shard", shard, clump, 1, 300, chem_in=("oxygen", 1))
        mrecipe("crusher", f"{metal}_clump", clump, dirty)
        mrecipe("enrichment_chamber", f"{metal}_dirty_dust", dirty, dust)
        mrecipe("crusher", f"{metal}_ingot", ingot, dust)
        mrecipe("energized_smelter", f"{metal}_dust", dust, ingot, ticks=160, energy=40)
        mrecipe("combiner", f"{metal}_ore", dust, ore, 1, 300, input2="minecraft:cobblestone", in2_count=2)
        cooking("smelting", dust, ingot)
        cooking("blasting", dust, ingot)
        if metal in OWN:
            # Ore blocks cannot be generated in the world yet (Prism row 36): stand-in recipes make the ore items.
            pass
    # stand-ins for world-generated ore
    shapeless("osmium_ore", ["minecraft:raw_iron", "minecraft:lapis_lazuli"], "mekanism:osmium_ore")
    shapeless("tin_ore", ["minecraft:raw_copper", "minecraft:clay_ball"], "mekanism:tin_ore")
    shapeless("lead_ore", ["minecraft:raw_iron", "minecraft:coal"], "mekanism:lead_ore")
    shapeless("uranium_ore", ["minecraft:raw_gold", "minecraft:slime_ball"], "mekanism:uranium_ore")

    # ---- plain dusts of vanilla materials
    for k, col in DUSTS.items():
        item(f"{k}_dust")
        tex_item(f"{k}_dust", "dust", col)
    mrecipe("crusher", "coal", "minecraft:coal", "mekanism:coal_dust")
    mrecipe("crusher", "charcoal", "minecraft:charcoal", "mekanism:charcoal_dust")
    mrecipe("crusher", "lapis", "minecraft:lapis_lazuli", "mekanism:lapis_dust")
    mrecipe("crusher", "diamond", "minecraft:diamond", "mekanism:diamond_dust")
    mrecipe("crusher", "emerald", "minecraft:emerald", "mekanism:emerald_dust")
    mrecipe("crusher", "quartz", "minecraft:quartz", "mekanism:quartz_dust")
    mrecipe("crusher", "obsidian", "minecraft:obsidian", "mekanism:obsidian_dust", ticks=400)
    mrecipe("crusher", "cobblestone", "minecraft:cobblestone", "minecraft:gravel")
    mrecipe("crusher", "gravel", "minecraft:gravel", "minecraft:sand")

    # ---- alloys: steel and bronze (metallurgic infuser), refined obsidian and glowstone (osmium compressor)
    item("enriched_iron")
    tex_item("enriched_iron", "dust", (150, 150, 165))
    mrecipe("metallurgic_infuser", "enriched_iron", "minecraft:iron_ingot", "mekanism:enriched_iron",
            input2="minecraft:coal")
    mrecipe("metallurgic_infuser", "enriched_iron_dust", "mekanism:iron_dust", "mekanism:enriched_iron",
            input2="mekanism:coal_dust")
    for a in ("steel", "bronze"):
        item(f"{a}_dust")
        tex_item(f"{a}_dust", "dust", ALLOYS[a])
    mrecipe("metallurgic_infuser", "steel_dust", "mekanism:enriched_iron", "mekanism:steel_dust",
            input2="minecraft:coal", ticks=300)
    mrecipe("metallurgic_infuser", "bronze_dust", "minecraft:copper_ingot", "mekanism:bronze_dust",
            input2="mekanism:tin_dust", ticks=300)
    for a in ALLOYS:
        item(f"{a}_ingot")
        tex_item(f"{a}_ingot", "ingot", ALLOYS[a])
    for a in ("steel", "bronze"):
        cooking("smelting", f"mekanism:{a}_dust", f"mekanism:{a}_ingot")
        cooking("blasting", f"mekanism:{a}_dust", f"mekanism:{a}_ingot")
        mrecipe("energized_smelter", f"{a}_dust", f"mekanism:{a}_dust", f"mekanism:{a}_ingot", ticks=160, energy=40)
    mrecipe("osmium_compressor", "refined_obsidian", "mekanism:obsidian_dust", "mekanism:refined_obsidian_ingot",
            ticks=300, input2="mekanism:osmium_dust")
    mrecipe("osmium_compressor", "refined_glowstone", "minecraft:glowstone_dust", "mekanism:refined_glowstone_ingot",
            ticks=300, input2="mekanism:osmium_dust")

    # ---- nuggets and storage blocks (iron, gold and copper use the vanilla ones)
    for m, col in {**OWN, **ALLOYS}.items():
        ing = f"mekanism:{m}_ingot"
        nug = f"mekanism:{m}_nugget"
        blk = f"mekanism:{m}_block"
        item(f"{m}_nugget")
        tex_item(f"{m}_nugget", "nugget", col)
        static_block(f"{m}_block", "Storage block of nine ingots.")
        tex_block(f"{m}_block", col, "storage")
        shapeless(f"{m}_nugget", [ing], nug, 9)
        shaped(f"{m}_ingot_from_nuggets", ["NNN", "NNN", "NNN"], {"N": nug}, ing)
        shaped(f"{m}_block", ["III", "III", "III"], {"I": ing}, blk)
        shapeless(f"{m}_ingot_from_block", [blk], ing, 9)

    # ---- infused alloys and circuits
    for k, col, a, b in (("infused", (200, 60, 60), "minecraft:iron_ingot", "minecraft:redstone"),
                         ("reinforced", (90, 200, 220), "mekanism:alloy_infused", "mekanism:diamond_dust"),
                         ("atomic", (190, 90, 230), "mekanism:alloy_reinforced", "mekanism:refined_obsidian_ingot")):
        item(f"alloy_{k}", f"{k.title()} Alloy")
        tex_item(f"alloy_{k}", "alloy", col)
        mrecipe("metallurgic_infuser", f"alloy_{k}", a, f"mekanism:alloy_{k}", input2=b, ticks=300)
    circuits = {
        "basic": (["RRR", "ROR", "RRR"], {"R": "minecraft:redstone", "O": "mekanism:osmium_ingot"}),
        "advanced": ([" A ", "CRC", " A "], {"A": "mekanism:alloy_infused", "C": "mekanism:basic_control_circuit",
                                              "R": "minecraft:redstone"}),
        "elite": ([" A ", "CGC", " A "], {"A": "mekanism:alloy_reinforced", "C": "mekanism:advanced_control_circuit",
                                           "G": "minecraft:gold_ingot"}),
        "ultimate": ([" A ", "CDC", " A "], {"A": "mekanism:alloy_atomic", "C": "mekanism:elite_control_circuit",
                                              "D": "minecraft:diamond"}),
    }
    for k, (rows, key) in circuits.items():
        item(f"{k}_control_circuit", f"{k.title()} Control Circuit")
        tex_item(f"{k}_control_circuit", "circuit", {"basic": (190, 190, 190), "advanced": (230, 90, 90),
                                                      "elite": (90, 210, 230), "ultimate": (210, 110, 240)}[k])
        shaped(f"{k}_control_circuit", rows, key, f"mekanism:{k}_control_circuit")

    # ---- upgrades (installed by right clicking a machine, up to eight of each)
    for k, col, key in (("speed", (240, 240, 120), "minecraft:glass"), ("energy", (240, 120, 120), "minecraft:gold_ingot")):
        item(f"{k}_upgrade", f"{k.title()} Upgrade")
        tex_item(f"{k}_upgrade", "upgrade", col)
        shaped(f"{k}_upgrade", [" G ", "RAR", " G "], {"G": key, "R": "minecraft:redstone",
                                                        "A": "mekanism:alloy_infused"}, f"mekanism:{k}_upgrade")

    # ---- new machines and their recipes
    for m, spec in NEW_MACHINES.items():
        machine_block(m, spec)
        tex_machine(m, spec[4])
        shaped(m, ["IXI", "OCO", "IXI"], {"I": "minecraft:iron_ingot", "X": spec[3], "O": "mekanism:osmium_ingot",
                                          "C": "mekanism:steel_casing"}, f"mekanism:{m}")
    logs = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "cherry", "mangrove")
    for w in logs:
        mrecipe("precision_sawmill", f"{w}_log", f"minecraft:{w}_log", f"minecraft:{w}_planks", 6, 100)
    mrecipe("precision_sawmill", "planks_to_sticks", "minecraft:oak_planks", "minecraft:stick", 6, 60)
    mrecipe("energized_smelter", "sand", "minecraft:sand", "minecraft:glass", ticks=160, energy=40)
    mrecipe("energized_smelter", "cobblestone", "minecraft:cobblestone", "minecraft:stone", ticks=160, energy=40)
    mrecipe("energized_smelter", "clay_ball", "minecraft:clay_ball", "minecraft:brick", ticks=160, energy=40)

    # ---- energy cubes (batteries: they take energy from cables and give it back)
    prev = None
    for tier, (cap, rate) in CUBES.items():
        name = f"{tier}_energy_cube" if tier != "basic" else "energy_cube"
        col = {"basic": (150, 150, 160), "advanced": (220, 90, 90), "elite": (80, 190, 220),
               "ultimate": (200, 100, 230)}[tier]
        put(PRISM / f"blocks/{name}.yml",
            f"# Key: mekanism:{name}. A battery: {cap:,} J, takes and gives {rate:,} J per tick through cables.\n"
            f"base: minecraft:barrier\ndrop: mekanism:{name}\nbehaviors:\n  - type: mekanism:machine\n"
            f"    machine: {name}\n    energy_capacity: {cap}\n    max_receive: {rate}\n    max_extract: {rate}\n")
        block_item(name, f"{tier.title()} Energy Cube")
        tex_cube(name, col)
        if prev is None:
            shaped(name, ["IRI", "OCO", "IRI"], {"I": "minecraft:iron_ingot", "R": "minecraft:redstone_block",
                                                 "O": "mekanism:osmium_ingot", "C": "mekanism:steel_casing"},
                   f"mekanism:{name}")
        else:
            c = {"advanced": "mekanism:alloy_infused", "elite": "mekanism:alloy_reinforced",
                 "ultimate": "mekanism:alloy_atomic"}[tier]
            shaped(name, ["AGA", "ECE", "AGA"], {"A": c, "G": "minecraft:gold_ingot", "E": f"mekanism:{prev}",
                                                 "C": "mekanism:basic_control_circuit"}, f"mekanism:{name}")
        prev = name


# ---------------------------------------------------------------------------------------------- stage 2

CHEM_MACHINES = {  # id -> (tank capacity, signature item, colour)
    "chemical_oxidizer": (10000, "minecraft:bucket", (120, 190, 120)),
    "chemical_infuser": (10000, "minecraft:glass_bottle", (190, 190, 90)),
    "chemical_washer": (10000, "minecraft:water_bucket", (90, 150, 200)),
    "chemical_crystallizer": (10000, "minecraft:amethyst_shard", (170, 120, 210)),
    "chemical_dissolution_chamber": (10000, "minecraft:brewing_stand", (200, 110, 60)),
    "pressurized_reaction_chamber": (10000, "minecraft:observer", (110, 110, 160)),
}
SLURRY_METALS = list(OWN) + list(VANILLA)


def gen2():
    for m, (tank, sig, col) in CHEM_MACHINES.items():
        machine_block(m, (20000, 2000, tank, sig, col))
        tex_machine(m, col)
        shaped(m, ["IXI", "BCB", "IXI"], {"I": "minecraft:iron_ingot", "X": sig, "B": "mekanism:basic_control_circuit",
                                         "C": "mekanism:steel_casing"}, f"mekanism:{m}")
    # storage tanks: the machine behavior as a buffer
    for name, buffer, cap, what in (("chemical_tank", "any", 64000, "any chemical"), ("fluid_tank", "water", 32000, "water")):
        put(PRISM / f"blocks/{name}.yml",
            f"# Key: mekanism:{name}. Stores {what}; pipes fill it and drain it.\nbase: minecraft:barrier\n"
            f"drop: mekanism:{name}\nbehaviors:\n  - type: mekanism:machine\n    machine: {name}\n"
            f"    energy_capacity: 1\n    max_receive: 1\n    tank_capacity: {cap}\n    buffer: {buffer}\n")
        block_item(name)
        tex_cube(name, (120, 170, 200) if buffer == "any" else (70, 110, 200))
        shaped(name, ["IGI", "GCG", "IGI"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass",
                                             "C": "mekanism:steel_casing"}, f"mekanism:{name}")
    # the tool that sets which tank a face of a machine uses
    item("configurator")
    tex_item("configurator", "upgrade", (80, 200, 230))
    shaped("configurator", [" A ", "AIA", " I "], {"A": "mekanism:alloy_infused", "I": "minecraft:iron_ingot"},
           "mekanism:configurator")
    # gases made from items
    for k, col in (("sulfur", (230, 220, 70)), ("salt", (240, 240, 245))):
        item(f"{k}_dust" if k == "sulfur" else k)
        tex_item(f"{k}_dust" if k == "sulfur" else k, "dust", col)
    # stand-ins for world-generated salt and sulfur
    shapeless("sulfur_dust", ["minecraft:gunpowder", "mekanism:coal_dust"], "mekanism:sulfur_dust", 2)
    shapeless("salt", ["minecraft:bone_meal", "minecraft:sand"], "mekanism:salt", 2)
    mrecipe("chemical_oxidizer", "salt", "mekanism:salt", None, chem_out=("chlorine", 200), ticks=100)
    mrecipe("chemical_oxidizer", "sulfur_dust", "mekanism:sulfur_dust", None, chem_out=("sulfur_dioxide", 200),
            ticks=100)
    mrecipe("chemical_infuser", "hydrogen_chloride", None, None, chem_in=("hydrogen", 100), chem_in2=("chlorine", 100),
            chem_out=("hydrogen_chloride", 200), ticks=60)
    mrecipe("chemical_infuser", "sulfur_trioxide", None, None, chem_in=("sulfur_dioxide", 100),
            chem_in2=("oxygen", 50), chem_out=("sulfur_trioxide", 100), ticks=60)
    mrecipe("chemical_infuser", "sulfuric_acid", None, None, chem_in=("sulfur_trioxide", 100), chem_in2=("water", 100),
            chem_out=("sulfuric_acid", 100), ticks=60)
    # the 5x chain: ore -> dirty slurry -> clean slurry -> crystals (five per ore)
    for metal in SLURRY_METALS:
        ore = f"mekanism:{metal}_ore" if metal in OWN else VANILLA[metal][2]
        col = OWN.get(metal) or VANILLA[metal][0]
        item(f"{metal}_crystal")
        tex_item(f"{metal}_crystal", "gem", col)
        mrecipe("chemical_dissolution_chamber", f"{metal}_ore", ore, None, chem_in=("sulfuric_acid", 10),
                chem_out=(f"dirty_{metal}_slurry", 1000), ticks=400, energy=60)
        mrecipe("chemical_washer", f"{metal}_slurry", None, None, chem_in=(f"dirty_{metal}_slurry", 1000),
                chem_in2=("water", 100), chem_out=(f"clean_{metal}_slurry", 1000), ticks=100)
        mrecipe("chemical_crystallizer", f"{metal}_crystal", None, f"mekanism:{metal}_crystal",
                chem_in=(f"clean_{metal}_slurry", 200), ticks=200)
    # plastics: bio fuel -> substrate -> ethylene -> HDPE
    for k, col in (("bio_fuel", (110, 160, 60)), ("substrate", (150, 120, 70)), ("hdpe_pellet", (230, 230, 235))):
        item(k)
        tex_item(k, "dust" if k != "hdpe_pellet" else "nugget", col)
    for k in ("hdpe_sheet", "hdpe_rod"):
        item(k)
        tex_item(k, "alloy" if k == "hdpe_sheet" else "ingot", (235, 235, 240))
    for crop in ("wheat", "carrot", "potato", "beetroot", "sugar_cane", "melon_slice"):
        mrecipe("crusher", f"bio_fuel_{crop}", f"minecraft:{crop}", "mekanism:bio_fuel", 2 if crop in ("wheat", "sugar_cane") else 1, 100)
    mrecipe("pressurized_reaction_chamber", "substrate", "mekanism:bio_fuel", "mekanism:substrate", 1,
            chem_in=("water", 100), chem_in2=("hydrogen", 100), ticks=200, in_count=2)
    mrecipe("pressurized_reaction_chamber", "ethylene", "mekanism:substrate", None, 1, chem_in=("hydrogen", 100),
            chem_in2=("water", 100), chem_out=("ethylene", 100), ticks=200)
    mrecipe("pressurized_reaction_chamber", "hdpe_pellet", None, "mekanism:hdpe_pellet", 1,
            chem_in=("ethylene", 100), chem_in2=("oxygen", 100), ticks=300)
    shaped("hdpe_sheet", ["PP", "PP"], {"P": "mekanism:hdpe_pellet"}, "mekanism:hdpe_sheet")
    shapeless("hdpe_rod", ["mekanism:hdpe_sheet"], "mekanism:hdpe_rod", 2)


if __name__ == "__main__":
    if Image is None:
        print("Pillow is missing: textures were not generated")
    gen()
    gen2()
    print(f"wrote {len(written)} files")
