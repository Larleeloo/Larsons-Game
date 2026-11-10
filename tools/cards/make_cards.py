"""
Larson's Game's cards: the nine classes' starting decks, the 210 cards
(common to mythic) and each class's perk tree, written to
assets/cards/cards.json - which the game reads
(com.larsons.game.cards.CardBook). The cards are edited here; run this, then
make_card_art.py for their pictures.

    python3 tools/cards/make_cards.py [assets/cards/cards.json]
    python3 tools/cards/make_card_art.py

A card's effects, one per ";", each "kind value [Ns] [xN] [melee|ranged|spell]":
a help of world/Buffs's (damage, spell_damage, melee_damage, ranged_damage,
mana_cost, stamina_cost, speed, defense, stealth, jump, breath, coins, xp - all
"times" - and extra_dice, regen_health, regen_mana, regen_stamina, lifesteal,
thorns, freeze, transfigure, poison, ammo_free, sneak, ward) lasting N seconds
(no "s": until its charges are spent, or for good on a perk), with N charges
("x3": three hits), only on blows of one kind; or at once: heal, mana,
stamina, mana_to_health, health_to_mana, draw, coins_now, xp_now, breath_now.
"""

import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

CLASSES = {
    "mage": dict(name="Mage", colour="#5d86fa", pointer="wand",
                 about="Wands and staffs: cheaper spells, frozen and transfigured foes, mana into health."),
    "bard": dict(name="Bard", colour="#f0a830", pointer="lute",
                 about="Mobility: speed, lighter stamina, higher jumps, songs that rest and lull."),
    "knight": dict(name="Knight", colour="#c8ccd8", pointer="sword",
                   about="Armour and melee: damage taken down, wards, stronger swings."),
    "warrior": dict(name="Warrior", colour="#d84a32", pointer="axe",
                    about="Raw melee: fury, cleaving extra dice, life drawn from blows."),
    "warlock": dict(name="Warlock", colour="#8a3cc8", pointer="gloved_hand",
                    about="Dark spells: hexes, drained souls, poison, health traded for mana."),
    "druid": dict(name="Druid", colour="#4aa040", pointer="leafy_branch",
                  about="Nature: regrowth, thorns, entangling roots, foes turned into beasts."),
    "rogue": dict(name="Rogue", colour="#506070", pointer="dagger",
                  about="Stealth: hidden longer, sneak attacks anywhere, poison, fuller purses."),
    "ranger": dict(name="Ranger", colour="#6a8a3a", pointer="arrow",
                   about="Bows and crossbows: steadier aim, arrows spared, volleys of extra dice."),
    "cleric": dict(name="Cleric", colour="#f4e8b0", pointer="holy_symbol",
                   about="Healing and protection: blessings, sanctuaries, divine light."),
}

# per class: the four starting cards (dealt 4, 2, 1 and 1 into her first deck),
# nine perk-tree cards (rows 1 - 3 two each, row 4 three), nine shop cards,
# and three perk-tree passives (for good, rows 1 - 3)
CARDS = {
    "mage": dict(
        start=[("Cast", "Spells cost half the mana for 5 minutes.", "mana_cost 0.5 300s"),
               ("Bind", "Spells freeze what they hit for 3 s, for 5 minutes.", "freeze 3 300s spell"),
               ("Transfigure", "The next 3 foes your spells hit become animals for 5 minutes.",
                "transfigure 300 x3 spell"),
               ("Healing Spell", "All your mana turned into health.", "mana_to_health")],
        perk=[("Arcane Focus", "Spell damage x1.3 for 5 minutes.", "spell_damage 1.3 300s"),
              ("Mana Spring", "2 mana a second for 2 minutes.", "regen_mana 2 120s"),
              ("Twin Sparks", "A die more on every spell for 3 minutes.", "extra_dice 1 180s spell"),
              ("Frost Lance", "The next 3 spells freeze for 5 s.", "freeze 5 x3 spell"),
              ("Leyline Tap", "60 mana at once.", "mana 60"),
              ("Runic Barrier", "A ward soaking up 35 damage.", "ward 35"),
              ("Spellweave", "Spell damage x1.6 for a minute.", "spell_damage 1.6 60s"),
              ("Polymorph Storm", "The next 5 foes your spells hit become animals for 2 minutes.",
               "transfigure 120 x5 spell"),
              ("Starfall", "Three dice more on spells for 30 s.", "extra_dice 3 30s spell")],
        shop=[("Ember Script", "Spell damage x1.2 for 5 minutes.", "spell_damage 1.2 300s"),
              ("Quillbright Ward", "A ward soaking up 20 damage.", "ward 20"),
              ("Aether Draught", "40 mana at once.", "mana 40"),
              ("Scholar's Lamp", "Experience x1.5 for 10 minutes.", "xp 1.5 600s"),
              ("Crystal Resonance", "Spells cost a quarter less for 10 minutes.", "mana_cost 0.75 600s"),
              ("Glyph of Haste", "Speed x1.2 for 2 minutes.", "speed 1.2 120s"),
              ("Wandwright's Trick", "No crystals used for 5 minutes.", "ammo_free 300s"),
              ("Moonlit Missive", "1 mana a second for 5 minutes.", "regen_mana 1 300s"),
              ("Prismatic Burst", "Two dice more on spells for a minute.", "extra_dice 2 60s spell")],
        passive=[("Deep Reserves", "For good: 0.3 mana a second.", "regen_mana 0.3"),
                 ("Arcane Mastery", "For good: spell damage x1.1.", "spell_damage 1.1"),
                 ("Efficient Casting", "For good: spells cost a tenth less.", "mana_cost 0.9")]),
    "bard": dict(
        start=[("Quickstep", "Speed x1.3 for 5 minutes.", "speed 1.3 300s"),
               ("Lightfoot", "Half the stamina spent for 5 minutes.", "stamina_cost 0.5 300s"),
               ("Soaring Verse", "Jumps x1.5 higher for 3 minutes.", "jump 1.5 180s"),
               ("Ballad of Rest", "100 stamina, then 3 a second for a minute.", "stamina 100; regen_stamina 3 60s")],
        perk=[("Allegro", "Speed x1.5 for a minute.", "speed 1.5 60s"),
              ("Tumbler's Refrain", "A quarter of the stamina spent for 2 minutes.", "stamina_cost 0.25 120s"),
              ("Skylark's Aria", "Jumps twice as high for a minute.", "jump 2 60s"),
              ("Rhapsody of Wind", "Speed x1.25 for 10 minutes.", "speed 1.25 600s"),
              ("Lullaby", "The next 3 foes hit stand frozen 4 s.", "freeze 4 x3"),
              ("Encore", "Draw a card.", "draw 1"),
              ("Minstrel's Purse", "Coins x2 for 10 minutes.", "coins 2 600s"),
              ("Siren's Breath", "Breath lasts x3 for 5 minutes.", "breath 3 300s"),
              ("Grand Finale", "Damage x1.5 for a minute.", "damage 1.5 60s")],
        shop=[("Wandering Tune", "Speed x1.15 for 10 minutes.", "speed 1.15 600s"),
              ("Jester's Leap", "Jumps x1.3 higher for 5 minutes.", "jump 1.3 300s"),
              ("Traveler's Hum", "1.5 stamina a second for 5 minutes.", "regen_stamina 1.5 300s"),
              ("Coin Song", "25 coins at once.", "coins_now 25"),
              ("Echoing Chord", "A die more on every roll for 2 minutes.", "extra_dice 1 120s"),
              ("Tavern Cheer", "25 health at once.", "heal 25"),
              ("Duet of Dodges", "Damage taken x0.8 for 2 minutes.", "defense 0.8 120s"),
              ("Hymn of Tides", "Breath lasts twice as long for 10 minutes.", "breath 2 600s"),
              ("Ditty of Luck", "Experience x1.3 for 10 minutes.", "xp 1.3 600s")],
        passive=[("Light Step", "For good: speed x1.08.", "speed 1.08"),
                 ("Endurance", "For good: a tenth less stamina spent.", "stamina_cost 0.9"),
                 ("Spring Heel", "For good: jumps x1.1 higher.", "jump 1.1")]),
    "knight": dict(
        start=[("Shield Wall", "Damage taken x0.7 for 5 minutes.", "defense 0.7 300s"),
               ("Valor", "Melee damage x1.3 for 5 minutes.", "melee_damage 1.3 300s"),
               ("Aegis", "A ward soaking up 40 damage.", "ward 40"),
               ("Second Wind", "40 health at once.", "heal 40")],
        perk=[("Bulwark", "Damage taken halved for a minute.", "defense 0.5 60s"),
              ("Oathblade", "Melee damage x1.5 for 2 minutes.", "melee_damage 1.5 120s"),
              ("Squire's Polish", "No whetstones, wedges or studs used for 10 minutes.", "ammo_free 600s"),
              ("Rampart", "A ward soaking up 80 damage.", "ward 80"),
              ("Charge", "Speed x1.4 and melee damage x1.3 for 30 s.", "speed 1.4 30s; melee_damage 1.3 30s"),
              ("Steadfast", "1 health a second for 5 minutes.", "regen_health 1 300s"),
              ("Heavy Blow", "Two dice more on swings for 2 minutes.", "extra_dice 2 120s melee"),
              ("Iron Resolve", "Stamina spent x0.6 for 5 minutes.", "stamina_cost 0.6 300s"),
              ("Crusader's Vow", "Damage x1.4 for 3 minutes.", "damage 1.4 180s")],
        shop=[("Polished Plate", "Damage taken x0.85 for 10 minutes.", "defense 0.85 600s"),
              ("Tourney Lance", "Melee damage x1.2 for 5 minutes.", "melee_damage 1.2 300s"),
              ("Banner of the Keep", "A ward soaking up 25 damage.", "ward 25"),
              ("Knight's Ration", "20 health and 30 stamina at once.", "heal 20; stamina 30"),
              ("Gauntlet Grip", "A die more on swings for 3 minutes.", "extra_dice 1 180s melee"),
              ("Herald's Call", "Experience x1.4 for 10 minutes.", "xp 1.4 600s"),
              ("Watchman's Rest", "Half a health a second for 10 minutes.", "regen_health 0.5 600s"),
              ("Barbican", "Damage taken x0.6 for 30 s.", "defense 0.6 30s"),
              ("Lance Rest", "Stamina spent x0.75 for 5 minutes.", "stamina_cost 0.75 300s")],
        passive=[("Plate Training", "For good: damage taken x0.92.", "defense 0.92"),
                 ("Swordsmanship", "For good: melee damage x1.1.", "melee_damage 1.1"),
                 ("Vigor", "For good: 0.15 health a second.", "regen_health 0.15")]),
    "warrior": dict(
        start=[("Fury", "Melee damage x1.25 for 5 minutes.", "melee_damage 1.25 300s"),
               ("Cleave", "A die more on swings for 3 minutes.", "extra_dice 1 180s melee"),
               ("Bloodlust", "A fifth of the melee damage you deal back as health, 3 minutes.",
                "lifesteal 0.2 180s melee"),
               ("War Cry", "Damage x1.5 for a minute.", "damage 1.5 60s")],
        perk=[("Berserk", "Melee damage x1.8, damage taken x1.2, for 30 s.",
               "melee_damage 1.8 30s; defense 1.2 30s"),
              ("Rending Strikes", "Swings poison for 30% more, 3 minutes.", "poison 0.3 180s melee"),
              ("Battle Trance", "Half the stamina spent for 3 minutes.", "stamina_cost 0.5 180s"),
              ("Earthshaker", "Three dice more on swings for 30 s.", "extra_dice 3 30s melee"),
              ("Thick Hide", "Damage taken x0.8 for 5 minutes.", "defense 0.8 300s"),
              ("Warlord's Shout", "Damage x1.3 for 5 minutes.", "damage 1.3 300s"),
              ("Unstoppable", "A ward soaking up 50 damage.", "ward 50"),
              ("Bone Breaker", "The next 5 swings freeze for 2 s.", "freeze 2 x5 melee"),
              ("Rage Fuel", "35% of the melee damage back as health for a minute.", "lifesteal 0.35 60s melee")],
        shop=[("Whetted Edge", "Melee damage x1.15 for 10 minutes.", "melee_damage 1.15 600s"),
              ("Mead of Might", "30 health at once.", "heal 30"),
              ("Brawler's Wrap", "Damage taken x0.9 for 10 minutes.", "defense 0.9 600s"),
              ("Axe Grinder", "No wedges, studs or whetstones used for 5 minutes.", "ammo_free 300s"),
              ("Iron Gut", "0.8 health a second for 5 minutes.", "regen_health 0.8 300s"),
              ("Trophy Hunter", "Experience x1.5 for 5 minutes.", "xp 1.5 300s"),
              ("Sundering Swing", "Two dice more on swings for a minute.", "extra_dice 2 60s melee"),
              ("Thunder Stomp", "The next 2 foes hit stand frozen 2 s.", "freeze 2 x2"),
              ("Wolf's Pace", "Speed x1.2 for 5 minutes.", "speed 1.2 300s")],
        passive=[("Brute Force", "For good: melee damage x1.12.", "melee_damage 1.12"),
                 ("Thick Skull", "For good: damage taken x0.93.", "defense 0.93"),
                 ("Battle-Hardened", "For good: half a stamina a second.", "regen_stamina 0.5")]),
    "warlock": dict(
        start=[("Hex", "Spell damage x1.3 for 5 minutes.", "spell_damage 1.3 300s"),
               ("Soul Drain", "A quarter of the spell damage back as health, 3 minutes.",
                "lifesteal 0.25 180s spell"),
               ("Blight", "Spells poison for half again, 3 minutes.", "poison 0.5 180s spell"),
               ("Dark Pact", "30 health traded for mana.", "health_to_mana 30")],
        perk=[("Curse of Frailty", "Damage x1.4 for 2 minutes.", "damage 1.4 120s"),
              ("Shadow Bolt", "Two dice more on spells for 2 minutes.", "extra_dice 2 120s spell"),
              ("Withering Touch", "Every blow poisons for 80% more, a minute.", "poison 0.8 60s"),
              ("Eldritch Ward", "A ward soaking up 45 damage.", "ward 45"),
              ("Soul Siphon", "40% of the damage back as health for a minute.", "lifesteal 0.4 60s"),
              ("Void Gaze", "The next 3 spells freeze for 4 s.", "freeze 4 x3 spell"),
              ("Pact of Plenty", "3 mana a second for a minute.", "regen_mana 3 60s"),
              ("Abyssal Grasp", "The next 2 foes hit become animals for a minute.", "transfigure 60 x2"),
              ("Demon's Bargain", "Spell damage doubled for 30 s.", "spell_damage 2 30s")],
        shop=[("Grim Candle", "Spell damage x1.15 for 10 minutes.", "spell_damage 1.15 600s"),
              ("Bone Charm", "A ward soaking up 20 damage.", "ward 20"),
              ("Ink of Night", "Noticed x0.7 for 5 minutes.", "stealth 0.7 300s"),
              ("Blood Rune", "50 mana at once.", "mana 50"),
              ("Whispering Skull", "Experience x1.4 for 10 minutes.", "xp 1.4 600s"),
              ("Witchfire", "Every blow poisons for 30% more, 5 minutes.", "poison 0.3 300s"),
              ("Black Mirror", "A quarter of every blow you take dealt back, 5 minutes.", "thorns 0.25 300s"),
              ("Coven's Gift", "Spells cost a fifth less for 10 minutes.", "mana_cost 0.8 600s"),
              ("Ashen Veil", "Damage taken x0.85 for 5 minutes.", "defense 0.85 300s")],
        passive=[("Dark Knowledge", "For good: spell damage x1.1.", "spell_damage 1.1"),
                 ("Leech", "For good: 5% of spell damage back as health.", "lifesteal 0.05 spell"),
                 ("Mana Font", "For good: 0.3 mana a second.", "regen_mana 0.3")]),
    "druid": dict(
        start=[("Regrowth", "1.5 health a second for 2 minutes.", "regen_health 1.5 120s"),
               ("Thornhide", "30% of every blow you take dealt back, 5 minutes.", "thorns 0.3 300s"),
               ("Entangling Roots", "The next 5 foes hit stand frozen 3 s.", "freeze 3 x5"),
               ("Wild Shape", "The next 2 foes hit become animals for 2 minutes.", "transfigure 120 x2")],
        perk=[("Barkskin", "Damage taken x0.6 for 2 minutes.", "defense 0.6 120s"),
              ("Moonwell", "2 mana a second for 2 minutes.", "regen_mana 2 120s"),
              ("Swarm", "Every blow poisons for half again, 2 minutes.", "poison 0.5 120s"),
              ("Stag's Bound", "Jumps x1.6 and speed x1.2 for 2 minutes.", "jump 1.6 120s; speed 1.2 120s"),
              ("Verdant Bloom", "50 health at once.", "heal 50"),
              ("Bramble Wall", "A 40 ward, and 40% of blows dealt back for a minute.",
               "ward 40; thorns 0.4 60s"),
              ("Feral Strength", "Melee damage x1.4 for 3 minutes.", "melee_damage 1.4 180s"),
              ("Oakheart", "2.5 health a second for a minute.", "regen_health 2.5 60s"),
              ("Gift of the Grove", "Draw two cards.", "draw 2")],
        shop=[("Herbal Poultice", "25 health at once.", "heal 25"),
              ("Mossy Cloak", "Noticed x0.7 for 5 minutes.", "stealth 0.7 300s"),
              ("Fern Tea", "50 stamina at once.", "stamina 50"),
              ("Spore Pouch", "Every blow poisons for a quarter more, 5 minutes.", "poison 0.25 300s"),
              ("Acorn Charm", "Experience x1.3 for 10 minutes.", "xp 1.3 600s"),
              ("River Spirit", "Breath lasts x3 for 5 minutes.", "breath 3 300s"),
              ("Sunleaf Tonic", "0.6 health a second for 10 minutes.", "regen_health 0.6 600s"),
              ("Owl's Wisdom", "30 mana at once.", "mana 30"),
              ("Seedling Ward", "A ward soaking up 15 damage.", "ward 15")],
        passive=[("Nature's Ward", "For good: 0.2 health a second.", "regen_health 0.2"),
                 ("Wild Heart", "For good: a tenth less stamina spent.", "stamina_cost 0.9"),
                 ("Briarskin", "For good: a tenth of every blow dealt back.", "thorns 0.1")]),
    "rogue": dict(
        start=[("Shadowstep", "Noticed x0.6 for 5 minutes.", "stealth 0.6 300s"),
               ("Backstab", "The next 2 blows land as sneak attacks, wherever you are.", "sneak 1 x2"),
               ("Envenom", "Swings poison for 40% more, 3 minutes.", "poison 0.4 180s melee"),
               ("Pickpocket", "Coins x1.5 for 10 minutes.", "coins 1.5 600s")],
        perk=[("Vanish", "Noticed x0.3 for 30 s.", "stealth 0.3 30s"),
              ("Assassinate", "The next blow a sneak attack, melee damage doubled for 30 s.",
               "sneak 1 x1; melee_damage 2 30s"),
              ("Smoke Bomb", "The next 3 foes hit stand frozen 3 s.", "freeze 3 x3"),
              ("Nimble Fingers", "Coins x2 for 5 minutes.", "coins 2 300s"),
              ("Deadly Toxins", "Swings poison for 80% more, a minute.", "poison 0.8 60s melee"),
              ("Fleet Foot", "Speed x1.35 for 3 minutes.", "speed 1.35 180s"),
              ("Twin Daggers", "Two dice more on swings for 2 minutes.", "extra_dice 2 120s melee"),
              ("Shadow Cloak", "Noticed x0.5 for 10 minutes.", "stealth 0.5 600s"),
              ("Cutthroat", "The next 4 blows land as sneak attacks.", "sneak 1 x4")],
        shop=[("Lockpick Set", "30 coins at once.", "coins_now 30"),
              ("Black Hood", "Noticed x0.8 for 10 minutes.", "stealth 0.8 600s"),
              ("Viper Oil", "Every blow poisons for a fifth more, 10 minutes.", "poison 0.2 600s"),
              ("Gambler's Coin", "Experience and coins x1.3 for 5 minutes.", "xp 1.3 300s; coins 1.3 300s"),
              ("Quiet Boots", "Noticed x0.75 for 5 minutes.", "stealth 0.75 300s"),
              ("Flash Powder", "The next 2 foes hit stand frozen 2 s.", "freeze 2 x2"),
              ("Thieves' Cant", "Draw a card.", "draw 1"),
              ("Silk Rope", "Jumps x1.3 higher for 5 minutes.", "jump 1.3 300s"),
              ("Rooftop Run", "Stamina spent x0.7 for 5 minutes.", "stamina_cost 0.7 300s")],
        passive=[("Light Fingers", "For good: coins x1.15.", "coins 1.15"),
                 ("Soft Steps", "For good: noticed x0.9.", "stealth 0.9"),
                 ("Quick Hands", "For good: melee damage x1.08.", "melee_damage 1.08")]),
    "ranger": dict(
        start=[("Steady Aim", "Arrow and bolt damage x1.3 for 5 minutes.", "ranged_damage 1.3 300s"),
               ("Quiver of Plenty", "No arrows or bolts used for 2 minutes.", "ammo_free 120s"),
               ("Volley", "Two dice more on shots for 2 minutes.", "extra_dice 2 120s ranged"),
               ("Hunter's Camouflage", "Noticed x0.5 for 3 minutes.", "stealth 0.5 180s")],
        perk=[("Eagle Eye", "Shot damage x1.6 for a minute.", "ranged_damage 1.6 60s"),
              ("Pinning Shot", "The next 3 shots freeze for 3 s.", "freeze 3 x3 ranged"),
              ("Barbed Arrows", "Shots poison for 40% more, 3 minutes.", "poison 0.4 180s ranged"),
              ("Trailblazer", "Speed x1.3 for 5 minutes.", "speed 1.3 300s"),
              ("Rain of Arrows", "Four dice more on shots for 30 s.", "extra_dice 4 30s ranged"),
              ("Fletcher's Bounty", "No arrows or bolts used for 10 minutes.", "ammo_free 600s"),
              ("Falcon's Call", "Damage x1.3 for 3 minutes.", "damage 1.3 180s"),
              ("Ghillie", "Noticed x0.4 for 2 minutes.", "stealth 0.4 120s"),
              ("Beast Bond", "1 health a second for 5 minutes.", "regen_health 1 300s")],
        shop=[("Waxed String", "Shot damage x1.15 for 10 minutes.", "ranged_damage 1.15 600s"),
              ("Trail Rations", "20 health and 20 stamina at once.", "heal 20; stamina 20"),
              ("Hawk Feather", "Jumps x1.2 higher for 5 minutes.", "jump 1.2 300s"),
              ("Tracker's Map", "Experience x1.4 for 10 minutes.", "xp 1.4 600s"),
              ("Bodkin Points", "A die more on shots for 5 minutes.", "extra_dice 1 300s ranged"),
              ("Forest Paint", "Noticed x0.75 for 5 minutes.", "stealth 0.75 300s"),
              ("Snare Trap", "The next 2 foes hit stand frozen 2 s.", "freeze 2 x2"),
              ("Spare Quiver", "No arrows or bolts used for a minute.", "ammo_free 60s"),
              ("Windreader", "Shot damage x1.4 for a minute.", "ranged_damage 1.4 60s")],
        passive=[("Keen Eye", "For good: shot damage x1.1.", "ranged_damage 1.1"),
                 ("Pathfinder", "For good: speed x1.06.", "speed 1.06"),
                 ("Woodland Stamina", "For good: 0.4 stamina a second.", "regen_stamina 0.4")]),
    "cleric": dict(
        start=[("Blessing", "1 health a second for 5 minutes.", "regen_health 1 300s"),
               ("Sanctuary", "A ward soaking up 30 damage.", "ward 30"),
               ("Divine Light", "60 health at once.", "heal 60"),
               ("Holy Smite", "Damage x1.4 for 2 minutes.", "damage 1.4 120s")],
        perk=[("Prayer of Mending", "2 health a second for 2 minutes.", "regen_health 2 120s"),
              ("Halo", "A ward soaking up 60 damage.", "ward 60"),
              ("Radiance", "Spell damage x1.4 and melee x1.2 for 3 minutes.",
               "spell_damage 1.4 180s; melee_damage 1.2 180s"),
              ("Resurgence", "100 health at once.", "heal 100"),
              ("Consecrate", "Every blow poisons for 40% more, 2 minutes.", "poison 0.4 120s"),
              ("Purity", "Damage taken x0.6 for 3 minutes.", "defense 0.6 180s"),
              ("Holy Bind", "The next 3 foes hit stand frozen 4 s.", "freeze 4 x3"),
              ("Miracle", "50 health, 50 mana and 50 stamina at once.", "heal 50; mana 50; stamina 50"),
              ("Martyr's Grace", "Half of every blow you take dealt back, 3 minutes.", "thorns 0.5 180s")],
        shop=[("Prayer Beads", "1 mana a second for 10 minutes.", "regen_mana 1 600s"),
              ("Votive Candle", "A ward soaking up 15 damage.", "ward 15"),
              ("Holy Water", "30 health at once.", "heal 30"),
              ("Pilgrim's Staff", "Stamina spent x0.8 for 10 minutes.", "stamina_cost 0.8 600s"),
              ("Tithe", "20 coins at once.", "coins_now 20"),
              ("Psalm of Light", "Experience x1.5 for 5 minutes.", "xp 1.5 300s"),
              ("Censer Smoke", "Noticed x0.8 for 5 minutes.", "stealth 0.8 300s"),
              ("Sacred Oil", "Melee damage x1.15 for 10 minutes.", "melee_damage 1.15 600s"),
              ("Angel's Feather", "Jumps x1.4 higher for 2 minutes.", "jump 1.4 120s")],
        passive=[("Faith", "For good: 0.25 health a second.", "regen_health 0.25"),
                 ("Devotion", "For good: 0.25 mana a second.", "regen_mana 0.25"),
                 ("Shielded Soul", "For good: damage taken x0.92.", "defense 0.92")]),
}

# any class's, bought at a shop
NEUTRAL = [("Fortune's Favor", "Coins x2 and experience x1.2 for 10 minutes.", "coins 2 600s; xp 1.2 600s"),
           ("Joker's Gambit", "Draw two cards.", "draw 2")]

# one to a class and one for anyone: the rarest cards there are, sold only by
# the best-stocked merchants (and found, a rare time, in a mystery box)
MYTHIC = {
    "mage": ("Clockwork Singularity", "A minute of spells at a quarter the mana, two dice more, freezing for 3 s.",
             "mana_cost 0.25 60s; extra_dice 2 60s spell; freeze 3 60s spell"),
    "bard": ("Symphony of the Spheres", "Two minutes at speed x1.5, a quarter the stamina, jumping twice as high; "
             "draw two cards.", "speed 1.5 120s; stamina_cost 0.25 120s; jump 2 120s; draw 2"),
    "knight": ("The Unbroken Oath", "Two minutes taking a third of the damage, melee x1.5, and a ward of 120.",
               "defense 0.35 120s; melee_damage 1.5 120s; ward 120"),
    "warrior": ("Wrath of the Forge-Titan", "45 s of melee x2.2 with three dice more, 30% of it back as health.",
                "melee_damage 2.2 45s; extra_dice 3 45s melee; lifesteal 0.3 45s melee"),
    "warlock": ("Pact of the Brass Devil", "A minute of spells x2 that poison for 60% more and drain 40% back.",
                "spell_damage 2 60s; poison 0.6 60s spell; lifesteal 0.4 60s spell"),
    "druid": ("Heart of the Elder Grove", "Two minutes mending 4 a second behind thorns of 80%; the next 6 foes "
              "your spells hit become animals.", "regen_health 4 120s; thorns 0.8 120s; transfigure 180 x6 spell"),
    "rogue": ("The Perfect Heist", "Two minutes all but unseen, the next 8 blows sneak attacks, coins x3.",
              "stealth 0.15 120s; sneak 1 x8; coins 3 120s"),
    "ranger": ("Skyfall Volley", "A minute of shots x1.8 with three dice more, and none of them used up.",
               "ranged_damage 1.8 60s; extra_dice 3 60s ranged; ammo_free 60s"),
    "cleric": ("Dawn of the Seventh Bell", "200 health at once, a ward of 100, and 3 health a second for 3 minutes.",
               "heal 200; ward 100; regen_health 3 180s"),
    None: ("The Grand Orrery", "Draw three cards; all damage x1.5 for 2 minutes; experience doubled for 10.",
           "draw 3; damage 1.5 120s; xp 2 600s"),
}

START_COPIES = [4, 2, 1, 1]
PERK_RARITY = ["uncommon", "uncommon", "rare", "rare", "epic", "epic", "legendary", "legendary", "legendary"]
SHOP_RARITY = ["common", "common", "common", "uncommon", "uncommon", "uncommon", "rare", "rare", "rare"]
PRICE = {"common": 30, "uncommon": 50, "rare": 80, "epic": 120, "legendary": 200, "mythic": 320}


def slug(name):
    out = "".join(c.lower() if c.isalnum() else "_" for c in name)
    while "__" in out:
        out = out.replace("__", "_")
    return out.strip("_")


def effects(text):
    out = []
    for part in text.split(";"):
        words = part.split()
        if not words:
            continue
        e = {"kind": words[0]}
        for w in words[1:]:
            if w.endswith("s") and w[:-1].replace(".", "").isdigit():
                e["seconds"] = float(w[:-1])
            elif w.startswith("x") and w[1:].isdigit():
                e["charges"] = int(w[1:])
            elif w in ("melee", "ranged", "spell"):
                e["only"] = w
            else:
                e["value"] = float(w)
        out.append(e)
    return out


def card(name, text, fx, cls, rarity, source):
    return {"id": slug(name), "name": name, "class": cls, "rarity": rarity, "text": text,
            "effects": effects(fx), "price": PRICE[rarity], "source": source}


def main(path):
    cards, decks, perks = [], {}, {}
    for cls, c in CARDS.items():
        for i, (n, t, fx) in enumerate(c["start"]):
            cards.append(card(n, t, fx, cls, "common" if i < 3 else "uncommon", "start"))
        decks[cls] = [{"card": slug(n), "copies": k} for (n, _, _), k in zip(c["start"], START_COPIES)]
        for i, (n, t, fx) in enumerate(c["perk"]):
            cards.append(card(n, t, fx, cls, PERK_RARITY[i], "perk"))
        for i, (n, t, fx) in enumerate(c["shop"]):
            cards.append(card(n, t, fx, cls, SHOP_RARITY[i], "shop"))
        # the tree: four rows of three; rows 1 - 3 two cards and a passive, row 4 three cards
        nodes, k = [], 0
        for row in range(4):
            for col in range(3):
                if row < 3 and col == 2:
                    n, t, fx = c["passive"][row]
                    nodes.append({"id": cls + "_" + slug(n), "name": n, "text": t, "row": row, "cost": [1, 1, 2, 3][row],
                                  "passive": effects(fx)})
                else:
                    n = c["perk"][k][0]
                    k += 1
                    nodes.append({"id": cls + "_perk_" + slug(n), "name": n, "row": row, "cost": [1, 1, 2, 3][row],
                                  "card": slug(n)})
        perks[cls] = nodes
        cards.append(card(*MYTHIC[cls], cls, "mythic", "shop"))
    for n, t, fx in NEUTRAL:
        cards.append(card(n, t, fx, None, "rare", "shop"))
    cards.append(card(*MYTHIC[None], None, "mythic", "shop"))
    ids = [c["id"] for c in cards]
    assert len(cards) == 210, len(cards)
    assert len(set(ids)) == len(ids), "card names must be unique"
    book = {"format": 1, "drawAtStart": 4, "handMost": 8, "drawEverySeconds": 300,
            "classes": {k: dict(v, deck=decks[k]) for k, v in CLASSES.items()},
            "cards": cards, "perks": perks}
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(book, f, indent=2)
        f.write("\n")
    print(f"{len(cards)} cards, {len(CLASSES)} classes -> {path}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "assets", "cards", "cards.json"))
