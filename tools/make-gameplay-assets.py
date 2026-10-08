"""Reproduce the retreat's Minecraft 1.21.1 gameplay resources.

Run after make-assets.py. This script preserves the original textures, glass,
sound, and shader files and adds only the gameplay models and data.
"""

from pathlib import Path
import json

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/aero"
DATA = ROOT / "src/main/resources/data/aero"
TAGS = ROOT / "src/main/resources/data/minecraft/tags/block"

WHITE = (235, 242, 234)
SILVER = (173, 204, 206)
BLUE = (27, 105, 184)
AQUA = (74, 187, 203)
GREEN = (112, 185, 40)
FOLIAGE = (68, 143, 28)
LIGHT = (219, 253, 233)
INK = (9, 66, 98)

DECOR = (
    "porcelain", "aqua_tile", "blue_tile", "white_panel", "lawn",
    "foliage", "silver", "glow_panel", "control",
)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n")


def save_texture(name, image):
    path = ASSETS / "textures/block" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def tile():
    image = Image.new("RGB", (16, 16), WHITE)
    pen = ImageDraw.Draw(image)
    pen.line((0, 0, 15, 0), fill=SILVER)
    pen.line((0, 0, 0, 15), fill=SILVER)
    pen.line((1, 1, 15, 1), fill=LIGHT)
    pen.line((1, 1, 1, 15), fill=LIGHT)
    return image, pen


def console_texture(name, top):
    image, pen = tile()
    pen.rectangle((2, 2, 13, 13), fill=SILVER)
    pen.rectangle((3, 3, 12, 12), fill=INK)
    if name == "navigator":
        # A compass needle has an unmistakable directional silhouette.
        pen.polygon([(8, 4), (11, 10), (8, 9), (5, 10)], fill=LIGHT)
        pen.polygon([(8, 9), (9, 11), (7, 11)], fill=AQUA)
        pen.point((4, 7), fill=AQUA)
        pen.point((11, 7), fill=AQUA)
    elif name == "workshop":
        # The same four-slot grid remains readable on a console's side.
        for x in (5, 9):
            for y in (5, 9):
                pen.rectangle((x - 1, y - 1, x + 1, y + 1), fill=LIGHT)
                pen.point((x, y), fill=BLUE)
    elif name == "specimen":
        # A specimen silhouette: stem, paired leaves, and a small root bed.
        pen.line((8, 6, 8, 11), fill=LIGHT)
        pen.polygon([(8, 8), (5, 7), (4, 4), (7, 5)], fill=GREEN)
        pen.polygon([(8, 7), (9, 4), (12, 4), (11, 7)], fill=FOLIAGE)
        pen.line((5, 11, 10, 11), fill=AQUA)
    if top:
        # A small raised-looking lip, shared with the existing control tile.
        pen.line((3, 2, 12, 2), fill=LIGHT)
    save_texture(f"{name}_{'top' if top else 'side'}", image)


def console(name, item):
    console_texture(name, True)
    console_texture(name, False)
    write_json(ASSETS / "models/block" / f"{name}.json", {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": f"aero:block/{name}_top",
            "side": f"aero:block/{name}_side",
            "bottom": "aero:block/white_panel",
        },
    })
    write_json(ASSETS / "blockstates" / f"{name}.json", {
        "variants": {"": {"model": f"aero:block/{name}"}},
    })
    if item:
        write_json(ASSETS / "models/item" / f"{name}.json", {
            "parent": f"aero:block/{name}",
        })


def box(start, end, texture, top=None):
    faces = {side: {"texture": f"#{texture}"}
             for side in ("north", "east", "south", "west", "up", "down")}
    if top:
        faces["up"] = {"texture": f"#{top}", "uv": [0, 0, 16, 16]}
    return {"from": start, "to": end, "faces": faces}


def valves():
    image, pen = tile()
    # Four sockets identify where raised pipes can join the neighbouring tile.
    for bounds in ((7, 0, 8, 2), (13, 7, 15, 8),
                   (7, 13, 8, 15), (0, 7, 2, 8)):
        pen.rectangle(bounds, fill=SILVER)
    save_texture("valve_base", image)
    for lit in (False, True):
        suffix = "_lit" if lit else ""
        image = Image.new("RGB", (16, 16), AQUA if lit else BLUE)
        pen = ImageDraw.Draw(image)
        pen.rectangle((0, 0, 15, 15), outline=INK)
        pen.line((7, 0, 7, 15), fill=LIGHT if lit else SILVER)
        pen.line((8, 0, 8, 15), fill=LIGHT if lit else SILVER)
        pen.line((0, 7, 15, 7), fill=LIGHT if lit else SILVER)
        pen.line((0, 8, 15, 8), fill=LIGHT if lit else SILVER)
        save_texture(f"valve_pipe{suffix}", image)
        image = Image.new("RGB", (16, 16), LIGHT if lit else SILVER)
        pen = ImageDraw.Draw(image)
        pen.rectangle((0, 0, 15, 15), outline=INK, width=2)
        if lit:
            pen.rectangle((5, 5, 10, 10), fill=WHITE)
            pen.rectangle((7, 3, 8, 12), fill=AQUA)
            pen.rectangle((3, 7, 12, 8), fill=AQUA)
        else:
            pen.rectangle((4, 7, 11, 8), fill=INK)
        save_texture(f"valve_knob{suffix}", image)
        for elbow in (False, True):
            kind = "elbow" if elbow else "straight"
            elements = [
                box([0, 0, 0], [16, 1.5, 16], "plate", "plate"),
                box([6, 1.5, 0], [10, 3, 7], "pipe"),
            ]
            if elbow:
                elements.append(box([9, 1.5, 6], [16, 3, 10], "pipe"))
            else:
                elements.append(box([6, 1.5, 9], [10, 3, 16], "pipe"))
            # Height and silhouette also communicate the illuminated state.
            elements.append(box([6, 1.5, 6], [10, 4 if lit else 3.25, 10],
                                "knob", "knob"))
            write_json(ASSETS / "models/block" / f"valve_{kind}{suffix}.json", {
                "parent": "minecraft:block/block",
                "ambientocclusion": True,
                "textures": {
                    "particle": "aero:block/white_panel",
                    "plate": "aero:block/valve_base",
                    "pipe": f"aero:block/valve_pipe{suffix}",
                    "knob": f"aero:block/valve_knob{suffix}",
                },
                "elements": elements,
            })
    variants = {}
    for rotation in range(4):
        for elbow in (False, True):
            for lit in (False, True):
                key = (f"rotation={rotation},elbow={str(elbow).lower()},"
                       f"lit={str(lit).lower()}")
                kind = "elbow" if elbow else "straight"
                suffix = "_lit" if lit else ""
                variants[key] = {
                    "model": f"aero:block/valve_{kind}{suffix}",
                    "y": rotation * 90,
                }
    write_json(ASSETS / "blockstates/valve.json", {"variants": variants})


def drops_and_recipes():
    for name in (*DECOR, "navigator", "workshop"):
        write_json(DATA / "loot_table/blocks" / f"{name}.json", {
            "type": "minecraft:block",
            "pools": [{
                "rolls": 1,
                "bonus_rolls": 0,
                "conditions": [{"condition": "minecraft:survives_explosion"}],
                "entries": [{"type": "minecraft:item", "name": f"aero:{name}"}],
            }],
            "random_sequence": f"aero:blocks/{name}",
        })
    recipes = {
        "porcelain": (["white_concrete"] * 4 + ["quartz"], 4),
        "aqua_tile": (["white_concrete"] * 4 + ["cyan_dye", "light_blue_dye"], 4),
        "blue_tile": (["white_concrete"] * 4 + ["blue_dye"], 4),
        "white_panel": (["white_concrete"] * 4 + ["iron_nugget"], 4),
        "lawn": (["grass_block"] * 4 + ["lime_dye"], 4),
        "foliage": (["oak_leaves"] * 4 + ["lime_dye"], 4),
        "silver": (["light_gray_concrete"] * 4 + ["iron_ingot"], 4),
        "glow_panel": (["white_concrete"] * 4 + ["glowstone_dust"], 4),
        "control": (["blue_concrete", "iron_ingot", "glass", "redstone"], 1),
    }
    for name, (ingredients, count) in recipes.items():
        write_json(DATA / "recipe" / f"{name}.json", {
            "type": "minecraft:crafting_shapeless",
            "category": "building",
            "ingredients": [{"item": f"minecraft:{item}"} for item in ingredients],
            "result": {"id": f"aero:{name}", "count": count},
        })
    pickaxe = [name for name in DECOR if name not in ("lawn", "foliage")]
    for tool, names in {
        "pickaxe": pickaxe + ["navigator", "workshop", "valve", "specimen"],
        "shovel": ["lawn"],
        "hoe": ["foliage"],
    }.items():
        write_json(TAGS / "mineable" / f"{tool}.json", {
            "replace": False, "values": [f"aero:{name}" for name in names],
        })


def retreat_dimension():
    write_json(DATA / "dimension_type/retreat_type.json", {
        "fixed_time": 3200,
        "ambient_light": 0.0,
        "bed_works": True,
        "coordinate_scale": 1.0,
        "effects": "minecraft:overworld",
        "has_ceiling": False,
        "has_raids": False,
        "has_skylight": True,
        "height": 256,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 256,
        "min_y": 0,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform",
                                     "min_inclusive": 0, "max_inclusive": 0},
        "natural": True,
        "piglin_safe": False,
        "respawn_anchor_works": False,
        "ultrawarm": False,
    })
    write_json(DATA / "dimension/retreat.json", {
        "type": "aero:retreat_type",
        "generator": {
            "type": "minecraft:flat",
            "settings": {
                "biome": "minecraft:the_void",
                "features": False,
                "lakes": False,
                "layers": [{"block": "minecraft:air", "height": 1}],
                "structure_overrides": [],
            },
        },
    })


def language_and_items():
    path = ASSETS / "lang/en_us.json"
    language = json.loads(path.read_text()) if path.exists() else {}
    language.update({
        "block.aero.porcelain": "Porcelain",
        "block.aero.aqua_tile": "Aqua Tile",
        "block.aero.blue_tile": "Sapphire Tile",
        "block.aero.white_panel": "White Panel",
        "block.aero.lawn": "Lawn",
        "block.aero.foliage": "Foliage",
        "block.aero.silver": "Silver",
        "block.aero.glow_panel": "Light Panel",
        "block.aero.control": "Control",
        "block.aero.valve": "Flow Valve",
        "block.aero.navigator": "Expedition Console",
        "block.aero.workshop": "Workshop",
        "block.aero.specimen": "Specimen",
        "item.aero.field_guide": "Field Guide",
        "item.aero.return_stone": "Return Stone",
    })
    write_json(path, language)
    for name, vanilla in {"field_guide": "book", "return_stone": "amethyst_shard"}.items():
        write_json(ASSETS / "models/item" / f"{name}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"minecraft:item/{vanilla}"},
        })


if __name__ == "__main__":
    valves()
    console("navigator", True)
    console("workshop", True)
    console("specimen", False)
    drops_and_recipes()
    retreat_dimension()
    language_and_items()
    print("Generated 16 valve states, 3 consoles, 2 item models, 11 drops, "
          "9 recipes, 3 mining tags, and the retreat dimension.")
