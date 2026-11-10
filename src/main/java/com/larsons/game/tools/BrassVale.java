package com.larsons.game.tools;

import com.larsons.game.level.LevelObject;
import com.larsons.game.world.Atmosphere;
import com.larsons.game.world.Goods;
import com.larsons.game.world.GroundMap;
import com.larsons.game.world.Sky;
import com.larsons.game.world.WaterMap;
import com.larsons.game.world.Weather;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Lays out the first of the main lands - the Brass Vale, as the overworld map
 * {@code assets/overworld/maps/brass_vale.json} draws it - a level for each
 * of its destinations:
 *
 * <pre>
 *   ./gradlew makeBrassVale          # → assets/levels/&lt;id&gt;/
 * </pre>
 *
 * <p>Each is a level as Creative Mode saves one, and opens there (Dev tab,
 * Levels) to be changed: this only gives them a start - the ground, the
 * water, the houses, who lives there, what they ask and what they sell.
 * Run again, it writes them over; a level changed by hand is kept by not
 * running it (or by taking its line out of {@link #main}).
 *
 * <p>The story it starts: home is <b>Oakhollow</b>. Bortzal hold the mill at
 * <b>Miller's Crossing</b>, on the road to <b>Brasswick</b>; the miller's
 * iron key opens the gate in the <b>Copperpit Mine</b>, where the dead have
 * put the lamps out. Brasswick's Reeve sends her to the <b>Drurin
 * Observatory</b>; its Captain wants the warlord of the <b>Old
 * Watchtower</b>, whose gate takes the silver key the warden of the
 * <b>Sallow Marsh</b> pays with. Past the tower is the <b>Crystal
 * Hollow</b>, the sky serpents, and the road east.
 */
public final class BrassVale {

    private static final String HUMAN = "human", DRURINEAN = "drurinean", HALF = "half_drurinean",
            KHORIGALE = "khorigale";
    private static final String KILL = "kill", CLEAR = "clear", TALK = "talk", FETCH = "fetch", REACH = "reach";
    private static final Atmosphere AIR = Atmosphere.DEFAULT;

    private BrassVale() {}

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : "assets/levels");
        if (Goods.EGGS.isEmpty()) throw new IllegalStateException("no goods");   // (the goods are items by now)
        for (Land l : List.of(oakhollow(), millersCrossing(), brasswick(), whisperingWood(), fishersRest(),
                copperpitMine(), drurinObservatory(), khorigaleCamp(), sallowMarsh(), oldWatchtower(),
                crystalHollow())) {
            l.save(root);
        }
    }

    // --- Oakhollow: home ---------------------------------------------------------------------

    static Land oakhollow() {
        Land l = new Land("oakhollow", "Oakhollow", 192, 11);
        l.sky(0x5C78A3, 0xBDC9DB, 130, AIR.withStartHour(9));
        l.wild("rabbit", "squirrel", "butterfly", "bee", "robin", "blue_jay", "cardinal", "pheasant");
        l.roll(1.5, 48).hill(58, -58, 34, 5).flat(0, 0, 44, 0.4);
        l.paint(GroundMap.FOREST, -84, -30, 40).paint(GroundMap.FOREST, -86, 44, 32);
        l.paint(GroundMap.COBBLES, 0, 0, 7).patches(GroundMap.DIRT, 9, 0, 0, 30, 1.5, 4);
        // the roads: in from the south, round the well, out to the mill, the wood, the elder's and the fields
        l.road(GroundMap.PACKED, 2.2, 0, 94, 0, 14).ringRoad(GroundMap.PACKED, 1.8, 0, 0, 14);
        l.road(GroundMap.PACKED, 1.6, 14, 0, 30, -2, 46, -30, 56, -50);
        l.road(GroundMap.PACKED, 1.6, -14, 0, -48, 4, -78, 8);
        l.road(GroundMap.PACKED, 1.6, 0, -14, 0, -38);
        l.road(GroundMap.PACKED, 1.6, 0, 44, 30, 42, 60, 40);
        l.keepOpen(0, 0, 15);

        l.prop("wishing_well", 0, 0, 0);
        l.home("house", 0, -47, 0, 0);
        for (double[] c : new double[][]{{-27, -25}, {-35, 16}, {-21, 34}, {24, 28}, {28, -27}}) {
            l.home("cottage", c[0], c[1], 0, 0);
        }
        l.home("workshop", 40, 4, 0, 4);
        l.prop("weapon_rack", 31, 12, 90);
        l.put("workbench", 31, -4, 90, "name", "Garrick's bench");
        l.home("windmill", 58, -58, 40, -30);
        l.house("stable", 62, 18, 90, 8);
        l.herd("horse", 2, 54, 18, 4).set("saddled", true).set("temperament", "tame");
        l.prop("notice_board", 7, 11, 0);
        l.prop("market_stall", -11, 13, Land.facing(-11, 13, 0, 0));
        l.put("cauldron", -17, 17, 0, "name", "Tilda's cauldron");
        l.prop("barrel", -7, 16, 0);
        l.prop("crate", -14.5, 9.5, 20);
        for (double[] p : new double[][]{{9, 9}, {-9, -9}, {3.4, 36}, {-3.4, 64}, {36, -8}}) l.lamp(p[0], p[1]);

        // Odo's farm, south-east: barley, cabbages, a pen of pigs - and boars out of the hedge
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) l.prop("wheat_field", 40 + i * 7, 50 + j * 7, 0);
        }
        l.prop("cabbage_patch", 62, 50, 0);
        l.prop("cabbage_patch", 62, 57, 0);
        l.prop("pumpkin_patch", 62, 64, 0);
        l.prop("scarecrow", 47, 45.5, 180);
        l.prop("haystack", 32, 62, 0);
        l.prop("hay_bale", 34.5, 58, 30);
        l.prop("farm_tools", 30, 50, 0);
        l.house("animal_pen", 18, 60, 0, 5);
        l.herd("pig", 3, 18, 60, 2.2).set("temperament", "tame");
        l.herd("boar", 4, 66, 72, 9);
        l.herd("cow", 4, -52, 66, 10);
        l.herd("sheep", 5, 30, -66, 10);
        l.animal("dog", 8, 22);
        l.animal("cat", -22, 26);

        l.pool(WaterMap.Kind.CLEAR, -52, 38, 8, 1.4);
        l.herd("duck", 4, -52, 38, 6);
        l.scatter(16, -52, 38, 13, 0.8, 1.2, "reeds");
        l.critters("dragonfly", 5, -52, 38, 9);
        l.critters("firefly", 10, -52, 38, 14);

        l.person("Elder Maren", HUMAN, "elegant", 3, -9, 180,
                        "Welcome home, child. Oakhollow is small, but the Vale is wide.|The roads are not what they "
                                + "were. Bortzal at the mill, and worse in the marsh, they say.")
                .set("body", "feminine").set("topics",
                        "The Vale=Brasswick lies east, past the mill. The Khorigale camp beyond it trades in everything."
                                + "; The wood=Old pines and older stones. Bram the hunter keeps a cabin there."
                                + "; Work=Garrick wants a word with you, and Odo has boars in his barley.");
        l.person("Garrick", HUMAN, "workshop", 31, 4, 90,
                        "Mind the sparks.|A blade is only as good as its edge. Keep whetstones on you.")
                .set("body", "masculine").set("weapon", "mace").set("buys", true).set("stock",
                        "sword, sword sharper, dagger, mace, battle_axe, longbow, whetstone x20, iron wedges x20, "
                                + "mace studs x20, arrow bundle x10, iron bar x10, upgrade ingot x3")
                .set("topics", "The forge=My bench is yours to use. Bring iron and I will show you keys and hooks."
                        + "; The mine=The Copperpit gate is locked. Hobb the miller had the key, last I heard.");
        l.person("Tilda", HUMAN, "common", -14.5, 14.5, Land.facing(-14.5, 14.5, 0, 0),
                        "Herbs, tonics, hooks and twine. What ails you?")
                .set("body", "feminine").set("buys", true).set("stock",
                        "health potion x5, stamina potion x4, mana potion x3, empty bottle x12, sage x6, "
                                + "wild berries x10, twine x10, fish hook x10, willow rod x1, parchment x4")
                .set("topics", "Brewing=A bottle, berries and sage in my cauldron makes a health potion. Try it."
                        + "; Fishing=Fen down at Fisher's Rest sells better rods than mine.");
        l.person("Farmer Odo", HUMAN, "common", 33, 44, 270, "If it is not the crows, it is the boars.")
                .set("body", "masculine");
        l.person("Wenna", HUMAN, "common", -8, -20, 150, "Lovely morning for it.").set("roam", 7.0);
        l.person("Old Tam", HUMAN, "ragged", 4, 5, 300,
                "I saw the planet rise red once. Nothing good came of that year.");
        l.person("Pip", HUMAN, "common", 12, 20, 0, "Are you going to fight the Bortzal? Can I come?")
                .set("size", 0.72).set("roam", 10.0);

        l.quest(31, 4, "A word with the smith", "Elder Maren", TALK, "Garrick", 1, 0,
                "Garrick at the forge has been asking after you. Hear him out, then come and tell me what he wants.",
                "Garrick's forge is east of the well.",
                "So he means to arm you. Good. You will need it on the road.", "coins x20, health potion x2", 30)
                .set("id", "oakhollow_smith");
        l.quest(52, 58, "Boars in the barley", "Farmer Odo", KILL, "boar", 3, 40,
                "Boars have been at my barley every night this week. Fell three of them and I will make it worth "
                        + "your while.", "They root about the south-east corner, past the cabbages.",
                "That is three fewer. Here - and some leather, for the trouble.",
                "coins x60, leather x4, roast meat x2", 80).set("id", "oakhollow_boars");
        l.quest(-14.5, 14.5, "Sage for the pot", "Tilda", FETCH, "sage", 3, 0,
                "I am out of sage, and my knees will not take the walk to the wood. Bring me three bunches?",
                "Sage grows grey-green at the edge of the wood, west of here.",
                "That will do nicely. Take these - you will want them out there.",
                "health potion x3, empty bottle x4, coins x15", 40).set("id", "oakhollow_sage");

        l.chest(-82, -66, 135, "coins x35, scroll of return, bilberries x4");
        l.scatter(12, -76, -12, 22, 0.9, 1.2, "sage_bush");
        l.scatter(8, -78, 40, 22, 0.9, 1.2, "berry_bush", "bilberry_bush");
        l.scatter(110, -92, 0, 52, 0.9, 1.5, "oak_tree", "pine_tree", "great_oak", "tall_pine", "birch_tree");
        l.everywhere(60, 0.9, 1.4, "oak_tree", "great_oak", "birch_tree");
        l.everywhere(60, 0.8, 1.2, "wildflowers", "grass_tuft_a", "grass_tuft_b", "tall_grass_a", "fern");
        l.everywhere(10, 0.8, 1.3, "boulder", "mossy_rock");
        l.border(110, 12, 1.0, 1.6, "oak_tree", "great_oak", "pine_tree");
        l.spawn(0, 84, 0);
        l.wayOut("The road out of Oakhollow", 5, 90, 0, false);
        return l;
    }

    // --- Miller's Crossing: the mill the Bortzal took -----------------------------------------------

    static Land millersCrossing() {
        Land l = new Land("millers_crossing", "Miller's Crossing", 192, 23);
        l.sky(0x5A7AA8, 0xC4CFDC, 120, AIR.withStartHour(14).withClouds(0.5));
        l.wild("rabbit", "squirrel", "fox", "butterfly", "robin", "crow", "pheasant");
        l.roll(1.8, 44);
        // the Brassrun, north to south, and the ford the road crosses it by
        l.river(WaterMap.Kind.CLEAR, -0.5, 1.5, 5, 14, -96, 6, -60, 16, -30, 11, 2, 18, 34, 8, 66, 14, 96);
        l.flat(11, 2, 9, -0.72);
        l.paint(GroundMap.GRAVEL, 11, 2, 9);
        l.road(GroundMap.PACKED, 2.2, -94, 8, -40, 3, 2, 2);
        l.road(GroundMap.PACKED, 2.2, 20, 2, 44, -4, 70, -8, 94, -10);
        l.road(GroundMap.PACKED, 1.5, -40, 3, -22, -14, -6, -14);

        l.house("watermill", 1.5, -21, 180, 6);
        l.home("cottage", -24, -28, -22, -14);
        l.prop("grain_sacks", -5, -13, 20);
        l.prop("hand_cart", -9, -10, 60);
        l.prop("barrel", -3, -27, 0);
        for (int i = 0; i < 4; i++) l.prop("wheat_field", -46 + (i % 2) * 7, -36 - (i / 2) * 7, 0);
        l.home("cabin", -72, -8, -72, 6);
        l.fire(-66, 2);

        l.person("Miller Hobb", HUMAN, "common", -69, 0, 120,
                        "Keep your voice down. They have ears like bats.|My mill. Forty years, and now there are "
                                + "Bortzal sleeping on my flour.")
                .set("body", "masculine").set("topics",
                        "The Bortzal=A dozen came over the ford at dawn. The big one they call Grukk sits across the "
                                + "river, by their fire.; The mine=The Copperpit gate key came down with the last "
                                + "cart. I have it still.");
        l.quest(10, -6, "Take back the mill", "Miller Hobb", CLEAR, "", 1, 80,
                "Drive them off my mill, every last one, and the key to the old mine gate is yours. I have no use "
                        + "for it now.", "They are still there. I can hear them.",
                "Quiet. Listen to that - just the wheel. Here is the key, and something for the road.",
                "iron key x1, coins x80, grilled fish x3", 150).set("id", "millers_mill");

        l.band("bortzal", 4, -5, -13, 7, 5);
        l.band("bortzal", 2, 0, 8, 4, 4);
        l.band("bortzal", 3, 42, -12, 6, 5);
        l.foe("bortzal", 45, -17, 250).set("name", "Grukk the Wide").set("size", 1.3).set("health", 2.5)
                .set("weapon", "battle_axe").set("roam", 3.0);
        l.fire(40, -11);
        l.prop("crate", 37, -15, 10);
        l.prop("barrel", 43, -7, 0);
        l.prop("hand_cart", 47, -10, 200);
        l.chest(48, -21, 300, "coins x45, scroll of haste, bolt bundle x2, health potion x2");

        l.scatter(6, 11, 2, 9, 0.8, 1.1, "river_stones");
        for (double[] w : new double[][]{{2, -70}, {22, -50}, {4, -42}, {24, -14}, {2, 20}, {26, 30}, {0, 50},
                {20, 62}, {2, 82}, {24, 84}}) {
            l.scenery("willow_tree", w[0], w[1], 1.1 + l.rnd.nextDouble() * 0.4);
        }
        l.scatter(40, 12, 0, 96, 0.8, 1.2, "reeds");
        l.everywhere(120, 0.9, 1.5, "oak_tree", "birch_tree", "great_oak", "tall_birch");
        l.everywhere(70, 0.8, 1.2, "wildflowers", "grass_tuft_a", "grass_tuft_c", "tall_grass_b", "fern");
        l.everywhere(14, 0.8, 1.4, "boulder", "mossy_rock", "mossy_log");
        l.scatter(6, -60, 50, 20, 0.9, 1.2, "berry_bush");
        l.border(110, 12, 1.0, 1.6, "oak_tree", "great_oak", "birch_tree");
        l.herd("deer", 3, -50, 60, 12);
        l.herd("duck", 3, 16, 40, 5);
        l.critters("dragonfly", 6, 12, 10, 10);
        l.spawn(-86, 8, 270);
        l.wayOut("The road back", -91, 13, 270, false);
        l.wayOut("The road on to Brasswick", 90, -6, 90, false);
        return l;
    }

    // --- Brasswick: the market town ---------------------------------------------------------------

    static Land brasswick() {
        Land l = new Land("brasswick", "Brasswick", 256, 37);
        l.sky(0x5C78A3, 0xC8CCD4, 140, AIR.withStartHour(11).withClouds(0.45));
        l.wild("robin", "crow", "butterfly");
        l.roll(1.2, 60).flat(0, 0, 150, 0.3);
        l.paint(GroundMap.FLAGSTONES, 0, 0, 22);
        l.road(GroundMap.COBBLES, 3, 0, 126, 0, 20).road(GroundMap.COBBLES, 3, 0, -20, 0, -90);
        l.road(GroundMap.COBBLES, 3, -20, 0, -96, 0).road(GroundMap.COBBLES, 3, 20, 0, 96, 0);
        l.road(GroundMap.COBBLES, 2, 0, -52, 60, -52).road(GroundMap.PACKED, 2, -40, 0, -40, 96, 0, 104);
        l.level.ground().stroke(GroundMap.FLAGSTONES, 0, 30, 0, 98, 5.6);          // the terraces' doorsteps
        l.level.ground().stroke(GroundMap.COBBLES, 0, 30, 0, 126, 3);
        l.paint(GroundMap.PACKED, 58, -42, 30).paint(GroundMap.GRAVEL, 62, -34, 12);   // the works' yard
        l.keepOpen(0, 0, 24);

        l.prop("fountain", 0, 0, 0);
        l.house("clocktower", -18, -18, 135, 4);
        l.prop("market_cross", 17, -17, 0);
        l.prop("statue", -17, 17, 180);
        l.prop("notice_board", 16, 18, 315);
        // the market: six stalls round the fountain, each with its own wares (all to be changed in the editor)
        String[][] stalls = {
                {"The Brass Blade", "sword, sword sharper, sword crystal, sword perfect, sword mythic, "
                        + "sword sharper venom, sword crystal mystic, sword perfect bonded, "
                        + "dagger sharper, mace sharper, battle_axe sharper, longbow crafted, crossbow, "
                        + "whetstone x30, iron wedges x30, mace studs x30, upgrade ingot x6"},
                {"Phials and Fumes", "health potion x8, greater health potion x3, mana potion x6, stamina potion x6, "
                        + "swiftness potion x3, might potion x3, stoneskin potion x2, shadow potion x2, "
                        + "gills potion x2, spring potion x2, thorn potion x2, fortune potion x1, "
                        + "crystal elixir x1, empty bottle x30"},
                {"The Inked Quill", "scroll of fury x2, scroll of warding x2, scroll of frost x1, scroll of haste x3, "
                        + "scroll of renewal x1, scroll of venom x2, scroll of insight x2, scroll of return x6, "
                        + "reshuffle scroll x4, parchment x20, ink x10"},
                {"Pack and Strap", "satchel x1, backpack x1, explorers pack x1, willow rod x2, reed rod x1, "
                        + "brass rod x1, twine x30, fish hook x30, leather x16, cloth x16, drinking horn x1, "
                        + "rusty key x2, iron key x1, brass key x1"},
                {"Shells and Hatchlings", "egg cow x3, egg sheep x3, egg pig x3, egg rabbit x4, egg duck x4, "
                        + "egg goose x2, egg dog x2, egg cat x2, egg horse x1, egg fox x1, egg owl x1, egg deer x1"},
                {"The Painted Deck", "cards x8, reshuffle scroll x6, small crystal x20, large crystal x10, "
                        + "arrow bundle x20, bolt bundle x20, crystal ball x1"}};
        for (int i = 0; i < stalls.length; i++) {
            // (clear of the four streets that meet at the fountain)
            double a = Math.toRadians(new double[]{30, 60, 120, 150, 240, 300}[i]), x = Math.cos(a) * 14.5,
                    z = Math.sin(a) * 14.5;
            l.stall(stalls[i][0], x, z, Land.facing(x, z, 0, 0), stalls[i][1]);
        }

        // the streets: terraces down the south street, shops up the north, the works to the north-east
        for (int i = 0; i < 9; i++) {
            l.house("row_house", -9.5, 34 + i * 7.6, 270, 5);
            l.house("row_house", 9.5, 34 + i * 7.6, 90, 5);
        }
        for (int i = 0; i < 4; i++) {
            l.house("shop_house", -11, -32 - i * 11, 270, 6);
            if (i != 2) l.house("shop_house", 11, -32 - i * 11, 90, 6);
        }
        for (int i = 0; i < 5; i++) {
            l.house(i % 2 == 0 ? "house" : "cottage", -34 - i * 13, -11, 180, 7);
            l.house(i % 2 == 0 ? "cottage" : "house", -34 - i * 13, 11, 0, 7);
            l.house(i % 2 == 0 ? "house" : "row_house", 34 + i * 13, 11, 0, 7);
        }
        l.house("workshop", 36, -13, 180, 7);
        l.house("foundry", 62, -30, 0, 10);
        l.house("workshop", 40, -66, 0, 7);
        l.prop("boiler", 52, -40, 0);
        l.prop("boiler", 72, -42, 90);
        l.prop("tesla_coil", 78, -30, 0);
        l.prop("tesla_coil", 46, -30, 0);
        l.prop("distiller", 30, -58, 0);
        l.put("workbench", 50, -56, 180, "name", "The works' bench");
        l.put("cauldron", -24, -26, 0, "name", "The dyers' vat", "model", "large_pot");
        l.prop("crate", 56, -44, 15);
        l.prop("barrel", 58, -46, 0);
        l.prop("hand_cart", 8, 26, 30);
        l.prop("wishing_well", -60, 34, 0);
        l.house("stable", -40, 108, 90, 8);
        l.herd("horse", 3, -32, 106, 4).set("saddled", true).set("temperament", "tame");
        // the south wall and its gate
        l.house("gatehouse", 0, 112, 180, 5);
        l.rampart(-66, 112, -3.75, 112).rampart(3.75, 112, 66, 112);
        l.house("round_tower", -70, 112, 180, 4);
        l.house("round_tower", 70, 112, 180, 4);
        for (int i = 0; i < 5; i++) {
            l.lamp(4.5, 30 + i * 18);
            l.lamp(-4.5, -30 - i * 14);
            l.lamp(-30 - i * 15, 4.5);
            l.lamp(30 + i * 15, -4.5);
        }

        l.person("Reeve Aldous", HUMAN, "elegant", 12, -11, 200,
                        "Brasswick keeps its books straight and its streets lit. Lately, only one of those.")
                .set("body", "masculine").set("topics",
                        "The town=Everything the Vale makes is sold in this square. Mind the prices at the Blade."
                                + "; The mine=The lamps in the Copperpit went out a month ago. Nobody will tell me why.");
        l.person("Captain Rhosyn", HUMAN, "guard", 5, 102, 180,
                        "State your business. No - never mind. You have the look of someone useful.")
                .set("body", "feminine").set("weapon", "sword").set("topics",
                        "The watchtower=The Bortzal burnt the hill bare and shut the gate. It takes a silver key - "
                                + "the marsh warden carries one.; The Bortzal=Yellow, quick and never alone.");
        l.person("Dyer Maud", HUMAN, "workshop", -21, -23, 120,
                "Blue. Everyone wants blue this year, and there is not a bilberry in the town.")
                .set("body", "feminine");
        l.person("Guard", HUMAN, "guard", -5, 106, 180, "Move along.").set("weapon", "mace");
        l.person("Guard", HUMAN, "armoured", 20, 6, 270, "Keep your blade sheathed in the square.")
                .set("weapon", "sword").set("roam", 8.0);
        String[][] folk = {{"Marta", "The Quill has a scroll that will take you straight home. Worth every coin."},
                {"Jory", "They say a dragon's egg came through the Khorigale camp. Nothing hatches from it."},
                {"Tinker Bell-Anne", "Brass gears, brass keys, brass everything. That is Brasswick."},
                {"Old Hesk", "I fished the Brassrun before there was a bridge. Trout as long as your arm."},
                {"Lysa", "The clock has not been right since the lamps went out in the mine."},
                {"Corwin", "A backpack. That is what you want. You can never carry enough."}};
        for (int i = 0; i < folk.length; i++) {
            double a = Math.toRadians(i * 60), d = 26 + (i % 3) * 14;
            l.person(folk[i][0], i == 2 ? HALF : HUMAN, i % 2 == 0 ? "town" : "common", Math.cos(a) * d,
                    Math.sin(a) * d * 0.5, l.rnd.nextInt(360), folk[i][1]).set("roam", 9.0);
        }
        l.animal("dog", -8, 24);
        l.animal("cat", 14, -30);

        l.quest(12, -11, "Word for the Magister", "Reeve Aldous", TALK, "Magister Ilvaren", 1, 0, MAGISTER[0],
                MAGISTER[1], MAGISTER[2], MAGISTER[3], 120).set("id", "brasswick_magister");
        l.quest(5, 102, "The warlord's head", "Captain Rhosyn", KILL, "Warlord Thrakk", 1, 0, WARLORD[0],
                WARLORD[1], WARLORD[2], WARLORD[3], 300).set("id", "brasswick_warlord");
        l.quest(-21, -23, "Blue for the vats", "Dyer Maud", FETCH, "bilberries", 5, 0,
                "Bring me five handfuls of bilberries and I will pay better than the market does.",
                "Bilberries grow in the Whispering Wood, and by Oakhollow.",
                "Look at that colour. Here - a little luck, bottled.", "fortune potion x1, coins x40", 60)
                .set("id", "brasswick_dye");

        l.keepOpen(0, 0, 100);
        l.everywhere(90, 0.9, 1.5, "oak_tree", "birch_tree", "great_oak");
        l.everywhere(50, 0.8, 1.2, "wildflowers", "grass_tuft_a", "grass_tuft_b", "tall_grass_a");
        l.border(140, 12, 1.0, 1.6, "oak_tree", "great_oak", "birch_tree");
        l.spawn(0, 121, 0);
        l.wayOut("The south road", 6, 124, 0, false);
        return l;
    }

    /** Quests two levels share (the same words where they are given and where they are done). */
    private static final String[] MAGISTER = {
            "The lamps in the Copperpit went out a month ago. Magister Ilvaren at the Drurin Observatory reads such "
                    + "things in the sky. Ask what the Magister has seen, and bring me the answer.",
            "The observatory stands on its hill, north-east of the town.",
            "The dead, in my mine. Then it is blades we need, not lamplighters. You have earned this.",
            "coins x90, scroll of insight x1"};
    private static final String[] WARLORD = {
            "The Bortzal in the old watchtower answer to one they call Thrakk. Bring him down and the warband "
                    + "breaks. The town will pay, and so will I.",
            "Thrakk keeps to the tower, behind the gate. It takes a silver key.",
            "Then the north road is open again. Take this - it was my mother's, and it has not been drawn in "
                    + "anger for years.", "coins x300, sword crystal x1, greater health potion x2"};

    // --- The Whispering Wood: the blue bear ---------------------------------------------------------

    static Land whisperingWood() {
        Land l = new Land("whispering_wood", "The Whispering Wood", 256, 41);
        l.sky(0x3F5F78, 0x9FB4B8, 60, AIR.withStartHour(16).withMist(0.6).withMistHeight(4).withRays(1)
                .withClouds(0.5));
        l.wild("rabbit", "squirrel", "fox", "deer", "owl", "robin", "blue_jay", "crow", "pheasant", "butterfly");
        l.roll(3, 40);
        l.paint(GroundMap.FOREST, 0, 0, 200);
        l.road(GroundMap.DIRT, 1.4, 0, 124, -10, 84, -30, 44, -46, 4, -56, -38, -60, -58);
        l.road(GroundMap.DIRT, 1.2, -30, 44, 20, 36, 62, 30);
        // the old stones, and what has made its den among them
        l.flat(-60, -74, 26);
        l.paint(GroundMap.MEADOW, -60, -74, 17);
        l.keepOpen(-60, -74, 11);
        for (int i = 0; i < 9; i++) {
            double a = Math.toRadians(i * 40 + 10);
            l.scenery("standing_stone", -60 + Math.cos(a) * 13, -74 + Math.sin(a) * 13, 1.5 + l.rnd.nextDouble() * 0.6);
        }
        l.scatter(4, -60, -74, 16, 0.9, 1.2, "crystal_bloom");
        l.animal("bear", -60, -74).set("size", 1.6).set("colours", List.of("#3A6AC8"))
                .set("temperament", "hostile").set("radius", 9.0);
        l.glow("moonlight", -60, -74, 16, 5);

        l.home("cabin", 14, 96, 2, 104);
        l.fire(6, 104);
        l.prop("firewood", 20, 103, 40);
        l.person("Hunter Bram", HUMAN, "common", 3, 108, 200,
                        "Quiet in the wood today. Too quiet.|I have hunted these trees thirty years. I never saw a "
                                + "bear that colour, nor that size.")
                .set("body", "masculine").set("weapon", "longbow").set("buys", true)
                .set("stock", "arrow bundle x20, longbow, roast meat x4, leather x6, twine x8")
                .set("topics", "The stones=Follow the path north and west. They stand in a ring, older than the "
                        + "wood.; The wood=Bandits camp east of the fork. Give them a wide berth, or do not.");
        l.quest(-60, -74, "The blue bear", "Hunter Bram", KILL, "bear", 1, 30,
                "There is a bear among the old stones: blue as a kingfisher and half again the size of any I have "
                        + "seen. It has had two of my dogs. Put it down, and my best bow is yours.",
                "The stone ring is north-west, where the path ends.",
                "Blue. I would not have believed it. A bargain is a bargain - she shoots true.",
                "coins x120, longbow crafted x1, bear pelt x1", 200).set("id", "wood_blue_bear");

        l.keepOpen(64, 28, 7);
        l.fire(64, 28);
        l.band("bandit", 3, 64, 28, 5, 5);
        l.prop("crate", 68, 24, 30);
        l.chest(70, 32, 250, "coins x60, dagger sharper, stamina potion x2");
        l.herd("boar", 3, 70, -70, 10);
        l.herd("deer", 4, -84, 44, 14);
        l.herd("fox", 2, 30, -30, 14);
        l.herd("rabbit", 5, -10, 60, 12);
        l.herd("squirrel", 4, 40, 80, 12);
        for (double[] f : new double[][]{{-30, 44}, {-56, -38}, {20, 0}, {60, 70}}) {
            l.critters("firefly", 12, f[0], f[1], 14);
        }
        l.scatter(14, -80, 0, 40, 0.9, 1.2, "bilberry_bush");
        l.scatter(12, 30, 60, 40, 0.9, 1.2, "berry_bush");
        l.scatter(10, 0, -40, 40, 0.9, 1.2, "sage_bush");
        l.everywhere(520, 0.9, 1.7, "tall_pine", "pine_tree", "tall_pine", "great_oak", "oak_tree");
        l.everywhere(170, 0.8, 1.3, "fern", "fern", "grass_tuft_b", "tall_grass_a");
        l.everywhere(70, 0.8, 1.5, "mossy_rock", "mossy_log", "boulder");
        l.border(160, 12, 1.1, 1.8, "tall_pine", "pine_tree");
        l.spawn(0, 116, 0);
        l.wayOut("The path home", -5, 121, 0, false);
        return l;
    }

    // --- Fisher's Rest: the south shore ----------------------------------------------------------

    static Land fishersRest() {
        Land l = new Land("fishers_rest", "Fisher's Rest", 192, 53);
        l.sky(0x4F7FB0, 0xC9DCE6, 150, AIR.withStartHour(7).withClouds(0.3).withMist(0.15));
        l.wild("rabbit", "butterfly", "robin", "crow", "eagle");
        l.roll(1.0, 40);
        // the land runs down to the sea in the south
        l.shape((x, z, was) -> z < 6 ? 1.0 + was : 1.0 - (z - 6) * 0.075 + was * Math.max(0, 1 - (z - 6) / 22.0));
        l.level.water().setSeaLevel(-0.4);
        l.level.ground().stroke(GroundMap.SAND, -96, 30, 96, 30, 14);
        l.level.ground().stroke(GroundMap.SAND, -96, 62, 96, 62, 24);
        l.level.ground().stroke(GroundMap.SAND, -96, 86, 96, 86, 16);
        l.road(GroundMap.PACKED, 2, 0, -94, 0, -30, -4, 14);
        l.road(GroundMap.PACKED, 1.4, 0, -50, 30, -58, 44, -62);

        l.house("cabin", -30, -6, 180, 7);
        l.house("cabin", -10, -14, 180, 7);
        l.house("cabin", 18, -8, 180, 7);
        l.house("cottage", 42, -22, 225, 8);
        l.house("cabin", -52, -18, 135, 7);
        l.prop("bread_oven", -44, -10, 180);
        l.put("cauldron", -40, -6, 0, "name", "The smokehouse kettle");
        for (double[] c : new double[][]{{-12, 16}, {-14, 18}, {10, 17}, {24, 14}}) l.prop("crate", c[0], c[1], 20);
        for (double[] c : new double[][]{{-10, 19}, {12, 19}, {-34, 12}}) l.prop("barrel", c[0], c[1], 0);
        l.prop("hand_cart", 4, 6, 100);
        l.fire(-20, 10);
        l.lamp(3, -30);
        l.lamp(3, 0);
        l.lamp(-6, 20);

        l.pool(WaterMap.Kind.CLEAR, 52, -64, 9, 1.4);
        l.herd("swan", 2, 52, -64, 6);
        l.herd("duck", 3, 52, -64, 7);
        l.scatter(14, 52, -64, 14, 0.8, 1.2, "reeds");
        l.herd("goose", 4, 30, 20, 8);
        l.animal("cat", -8, 4);

        l.person("Old Fen", HUMAN, "common", -2, 16, 180,
                        "Tide is right. It is always right, if you know where to stand.|Cast with the rod in your "
                                + "hand, wait for the float to dip, then pull. Nothing to it.")
                .set("body", "masculine").set("weapon", "willow_rod").set("buys", true).set("stock",
                        "willow rod x2, reed rod x1, brass rod x1, clockwork rod x1, twine x20, fish hook x20, "
                                + "sea bass x4, gills potion x2")
                .set("topics", "The fish=Bass off the beach, trout in the pond up the path. The better the rod, the "
                        + "better the fish.; The rods=Willow to learn on. The clockwork one winds itself - "
                        + "Brasswick work.");
        l.person("Smoker Hilde", HUMAN, "workshop", -42, -3, 160, "Fresh off the racks. Best you will eat this side "
                + "of Brasswick.").set("body", "feminine").set("buys", true)
                .set("stock", "grilled fish x8, hunters stew x3, roast meat x4, berry tart x2");
        l.person("Net-mender Ysa", HUMAN, "common", 16, 12, 200, "There is a rod lying by the rocks nobody has "
                + "claimed. Yours, if you want to try your hand.").set("body", "feminine");
        l.quest(-2, 16, "Three for the smokehouse", "Old Fen", FETCH, "sea_bass", 3, 0,
                "Hilde's racks are empty and my back is not what it was. Bring me three sea bass off the beach and "
                        + "you can have my reedcane rod.",
                "Stand at the water's edge with a rod in hand and cast.",
                "Fine fish. The rod is yours - she takes a little longer over the bait, and that is all to the good.",
                "reed rod x1, coins x40, grilled fish x2", 90).set("id", "fishers_bass");
        l.item("willow_rod", 1, 9, 24);

        l.scatter(60, 0, 24, 90, 0.8, 1.2, "beach_grass");
        l.scatter(18, 0, 30, 90, 0.8, 1.5, "beach_rock");
        l.scatter(10, 0, 14, 80, 0.9, 1.2, "sea_buckthorn");
        l.keepOpen(0, 60, 0);
        l.scatter(130, 0, -60, 100, 0.9, 1.5, "oak_tree", "birch_tree", "pine_tree", "great_oak");
        l.scatter(60, 0, -50, 90, 0.8, 1.2, "wildflowers", "grass_tuft_a", "tall_grass_b");
        l.border(90, 12, 1.0, 1.6, "oak_tree", "pine_tree");
        l.spawn(0, -86, 180);
        l.wayOut("The road inland", -5, -91, 180, false);
        return l;
    }

    // --- Copperpit Mine: where the lamps went out ---------------------------------------------------

    /** The mine's galleries: {x0, z0, x1, z1, half-width} - a chamber is a short wide one. */
    private static final double[][] GALLERIES = {
            {0, 54, 0, 46, 8}, {0, 46, 0, 22, 3}, {0, 16, 0, 16, 11}, {0, 16, -32, 10, 2.6}, {-38, 8, -38, 8, 9},
            {0, 16, 10, -8, 2.8}, {10, -8, 30, -14, 2.8}, {34, -16, 34, -16, 9}, {34, -16, 34, -36, 1.9},
            {34, -36, 20, -46, 2.6}, {6, -46, 6, -46, 12}};

    static Land copperpitMine() {
        Land l = new Land("copperpit_mine", "Copperpit Mine", 128, 67);
        l.level.setCover(Sky.Cover.CAVE);
        l.level.setWeather(Weather.NONE);
        l.sky(0x0A0C12, 0x14161C, 60, Atmosphere.STILL);
        l.wild().noCritters();
        // rock, ten metres of it, but for the galleries
        l.shape((x, z, was) -> {
            double near = 1e9;
            for (double[] g : GALLERIES) near = Math.min(near, toSegment(x, z, g[0], g[1], g[2], g[3]) - g[4]);
            return 10 * Land.ease(near / 2.5) + (near > 2.5 ? Land.noise(x / 7, z / 7, 5) * 1.5 : 0);
        });
        l.onlyBelow = 0.4;
        l.paint(GroundMap.STONE, 0, 0, 120);
        for (double[] g : GALLERIES) l.level.ground().stroke(GroundMap.GRAVEL, g[0], g[1], g[2], g[3], g[4] * 0.8);
        l.level.ground().disc(GroundMap.CRYSTAL, -38, 8, 6);

        l.put("lock", 34, -27, 0, "name", "The mine gate", "key", "iron_key", "consumes", true, "model", "iron_gate");
        for (double[] p : new double[][]{{-5, 50}, {5, 44}, {0, 30}, {-7, 20}, {8, 12}, {22, -10}, {30, -12}}) {
            l.lamp(p[0], p[1]);
        }
        for (double[] p : new double[][]{{-38, 8}, {-42, 4}, {12, -50}, {0, -42}, {38, -20}}) {
            l.glow("crystal", p[0], p[1], 11, 1.4);
        }
        l.scatter(14, -38, 8, 8, 0.7, 1.3, "crystal_cluster", "crystal_spire");
        l.scatter(8, 6, -46, 11, 0.7, 1.2, "crystal_cluster");
        l.scatter(16, 0, 0, 60, 0.6, 1.1, "boulder", "stones", "flat_rock");
        l.prop("hand_cart", 2, 38, 10);
        l.prop("crate", -6, 14, 40);
        l.prop("barrel", 6, 22, 0);
        l.prop("crate", 30, -20, 0);
        l.prop("boiler", -4, 52, 0);
        l.prop("hand_cart", 16, -11, 80);

        l.band("risen_walker", 4, 0, 14, 8, 4);
        l.band("risen_crawler", 2, -38, 8, 6, 4);
        l.foe("risen_walker", -34, 12, 90);
        l.band("risen_walker", 3, 34, -16, 6, 4);
        l.foe("risen_spitter", 38, -12, 200);
        l.foe("risen_brute", 6, -48, 90).set("name", "The Foreman").set("health", 1.5).set("roam", 5.0);
        l.band("risen_walker", 2, 8, -42, 6, 4);
        l.chest(-44, 10, 90, "crystal dust x3, coins x50, small crystal x6");
        l.chest(-2, -54, 0, "coins x120, brass gear x4, mace crystal");

        l.person("Overseer Brann", HUMAN, "workshop", 4, 50, 200,
                        "Not a step further without a blade in your hand.|The lamps went out gallery by gallery. "
                                + "Then the night shift came back up - and they were not the night shift any more.")
                .set("body", "masculine").set("topics",
                        "The gate=The lower gate is locked, and a good thing. The key went down to the mill with the "
                                + "last ore cart - ask Hobb.; The dead=Slow, most of them. The ones that crawl are "
                                + "not. And something big walks the lowest gallery.");
        l.quest(0, 16, "Lamps in the dark", "Overseer Brann", KILL, "risen", 8, 0,
                "Eight of them, at the least, between here and the lower gallery. Lay them down and I can get my "
                        + "lamplighters back in.", "They are still down there. I can hear them on the gravel.",
                "Eight. I counted the quiet. Take this - and the brass key, it opens more doors than mine.",
                "coins x150, brass key x1, crystal dust x4", 220).set("id", "mine_risen");
        l.spawn(0, 53, 0);
        l.wayOut("The way up", -5, 57, 0, false);
        l.wayOut("The old adit", -3, -55, 180, true);
        return l;
    }

    private static double toSegment(double x, double z, double x0, double z0, double x1, double z1) {
        double dx = x1 - x0, dz = z1 - z0, len2 = dx * dx + dz * dz;
        double t = len2 < 1e-9 ? 0 : Math.max(0, Math.min(1, ((x - x0) * dx + (z - z0) * dz) / len2));
        return Math.hypot(x - (x0 + dx * t), z - (z0 + dz * t));
    }

    // --- The Drurin Observatory: the scholars on the hill ------------------------------------------

    static Land drurinObservatory() {
        Land l = new Land("drurin_observatory", "The Drurin Observatory", 192, 71);
        l.sky(0x1F2A52, 0x6A5A8A, 150, AIR.withStartHour(20).withStars(1).withAurora(0.6).withPlanetAt(170, 34)
                .withPlanetSize(40).withRings(true).withClouds(0.15));
        l.wild("rabbit", "fox", "deer", "owl", "eagle");
        l.roll(1.5, 40);
        l.shape((x, z, was) -> was + 16 * Land.ease(1 - Math.hypot(x, z + 10) / 82));
        l.flat(0, -12, 40, 16.5);
        l.hill(70, -66, 18, 4);
        l.paint(GroundMap.STONE, 0, -12, 27).paint(GroundMap.FLAGSTONES, 0, -8, 12);
        l.road(GroundMap.PACKED, 1.8, 0, 92, 20, 62, -16, 36, 10, 14, 0, 4);
        l.road(GroundMap.DIRT, 1.2, 14, -14, 44, -40, 70, -64);
        l.keepOpen(0, -12, 26);

        l.house("spire", 0, -28, 180, 4);
        l.house("round_tower", -17, -14, 135, 4);
        l.house("house", 18, -16, 225, 6);
        l.prop("tesla_coil", -7, -2, 0);
        l.prop("tesla_coil", 7, -2, 0);
        l.prop("distiller", 11, 3, 0);
        l.prop("statue", 0, 8, 180);
        l.put("workbench", -12, 2, 90, "name", "The instrument bench", "model", "desk");
        for (double[] p : new double[][]{{-4, -18}, {4, -18}, {-14, -4}, {14, -4}}) l.glow("crystal", p[0], p[1], 9, 2);
        for (double[] p : new double[][]{{3, 84}, {21, 58}, {-14, 38}, {11, 16}}) l.lamp(p[0], p[1]);

        l.person("Magister Ilvaren", DRURINEAN, "elegant", 0, -17, 180,
                        "You have climbed a long way to be told to look up.|The Watcher fills a tenth of our sky "
                                + "and has never once blinked. We are its moon. Remember that when your troubles "
                                + "seem large.")
                .set("topics",
                        "The mine=The lamps did not fail. Something put them out. The dead walk the lower galleries - "
                                + "tell your Reeve to send blades, not lamplighters."
                                + "; The serpents=The Vérnees come through where the sky is thin, over the Crystal "
                                + "Hollow.; The planet=We call it the Watcher. Its rings are ice, and older than "
                                + "this world.");
        l.person("Scribe Thessaly", DRURINEAN, "elegant", 12, -6, 220, "Every scroll here is copied fair, and "
                + "burns only once.").set("body", "feminine").set("buys", true).set("stock",
                "scroll of insight x2, scroll of frost x2, scroll of renewal x2, scroll of the storm x1, "
                        + "scroll of return x4, parchment x12, ink x8, crystal ball x1, crystal dust x6");
        l.person("Apprentice Niv", HALF, "workshop", -8, 4, 140,
                "The Magister says I may use the great glass when I can find the far stone without a map.");
        l.quest(0, -17, "Word for the Magister", "Reeve Aldous", TALK, "Magister Ilvaren", 1, 0, MAGISTER[0],
                MAGISTER[1], MAGISTER[2], MAGISTER[3], 120).set("id", "brasswick_magister");
        l.quest(70, -66, "The far stone", "Apprentice Niv", REACH, "", 1, 7,
                "There is a sighting stone on the knoll north-east of the hill. Stand at it for me and tell me it "
                        + "is still upright? I have a chart to finish.",
                "The far stone: down the east path, on its own knoll.",
                "Still standing. Then my chart is right and the Magister is wrong. Do not tell the Magister.",
                "scroll of haste x1, coins x30, crystal dust x2", 70).set("id", "observatory_stone");
        l.scenery("standing_stone", 70, -66, 2.2);
        l.glow("moonlight", 70, -66, 8, 3);

        l.herd("deer", 3, -60, 50, 12);
        l.herd("fox", 1, 50, 40, 10);
        l.everywhere(190, 0.9, 1.6, "pine_tree", "tall_pine", "pine_tree", "birch_tree");
        l.everywhere(40, 0.8, 1.6, "boulder", "mossy_rock", "stones");
        l.everywhere(60, 0.8, 1.2, "fern", "grass_tuft_a", "wildflowers");
        l.border(100, 12, 1.1, 1.7, "tall_pine", "pine_tree");
        l.spawn(0, 87, 0);
        l.wayOut("The road down", 5, 92, 0, false);
        return l;
    }

    // --- Khorigale Camp: the traders on the dry meadows -----------------------------------------------

    static Land khorigaleCamp() {
        Land l = new Land("khorigale_camp", "Khorigale Camp", 192, 83);
        l.sky(0x6A86B0, 0xE2CFA8, 150, AIR.withStartHour(17).withClouds(0.2).withMist(0.05));
        l.wild("rabbit", "iguana", "eagle", "crow", "butterfly");
        l.roll(1.2, 55).flat(0, 0, 40);
        l.paint(GroundMap.DRY_GRASS, 0, 0, 200).patches(GroundMap.DIRT, 14, 0, 0, 24, 2, 5);
        l.road(GroundMap.PACKED, 2, 0, 94, 0, 18);
        l.keepOpen(0, 0, 20);

        l.fire(0, 0);
        l.prop("firewood", 1.5, 1, 60);
        l.put("cauldron", 4, 5, 0, "name", "The cook-pot", "model", "large_pot");
        String[][] stalls = {
                {"Ashka's Hatchery", "egg cow, egg sheep, egg pig, egg horse, egg dog, egg cat, egg fox, egg deer, "
                        + "egg eagle, egg peacock, egg boar, egg buffalo, egg bear, egg bortzal, egg khorigale, "
                        + "egg drurinean, egg risen walker, egg vernees, egg dragon"},
                {"Tovrin's Packs", "satchel x1, backpack x1, explorers pack x1, leather x12, cloth x12, "
                        + "drinking horn x2, gilded horn x1, twine x20"},
                {"Mirel's Keys and Curios", "rusty key x2, iron key x1, brass key x1, silver key x1, gold key x1, "
                        + "bone key x1, skeleton key x1, crystal ball x1, crystal shield x1, scroll of return x4"},
                {"Dhal's Edge", "dagger sharper venom, mace sharper mystic, battle_axe sharper bonded, "
                        + "sword crystal venom, sword perfect mystic, longbow crafted bonded, "
                        + "crossbow crafted venom, wand honed mystic, whetstone x20, iron wedges x20"}};
        String[] keepers = {"Ashka of the Long Road", "Tovrin Packmaker", "Old Mirel", "Dhal"};
        String[] says = {"Every shell here is warm. Mind the green one - it has been warm for a hundred years.",
                "A Khorigale carries her house on her back. I can sell you the back.",
                "A key for every door in the Vale, and one for doors that are not there yet.",
                "Steel is steel. It is what the smith quenches it in that makes it sing."};
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(200 + i * 47), x = Math.cos(a) * 13, z = Math.sin(a) * 13;
            double heading = Land.facing(x, z, 0, 0);
            l.stall(stalls[i][0], x, z, heading, stalls[i][1]);
            double side = a + Math.toRadians(17);
            l.person(keepers[i], KHORIGALE, i == 3 ? "armoured" : "common", Math.cos(side) * 13.5,
                    Math.sin(side) * 13.5, heading, says[i]).set("body", i % 2 == 0 ? "feminine" : "masculine");
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(20 + i * 28), x = Math.cos(a) * 17, z = Math.sin(a) * 17;
            l.prop(i % 3 == 2 ? "crate" : "hand_cart", x, z, Land.facing(x, z, 0, 0) + 90);
        }
        l.prop("haystack", -24, 12, 0);
        l.prop("hay_bale", -22, 16, 30);
        l.prop("trough", -30, 18, 0);
        l.prop("barrel", 14, 12, 0);
        l.prop("small_keg", 15.5, 13, 0);
        l.herd("horse", 4, -32, 22, 7).set("saddled", true).set("temperament", "tame");
        l.animal("dog", 8, -6);
        for (double[] p : new double[][]{{-10, 20}, {12, 18}, {-18, -8}, {18, -6}}) l.lamp(p[0], p[1]);

        l.person("Herdmaster Dhal", KHORIGALE, "common", -18, 20, 120,
                        "The herd goes where the grass goes. We go where the herd goes.")
                .set("body", "masculine").set("topics",
                        "The Khorigale=Horns for pride, a tail for balance, and a wagon for everything else."
                                + "; The east=The road east runs out of the Vale at the gate. We came in by it. "
                                + "It was quieter then.");
        l.person("Sef the Tinker", HALF, "workshop", 16, -12, 240,
                "If it ticks, I can mend it. If it does not tick, I can make it.");
        l.put("workbench", 20, -14, 240, "name", "Sef's bench");
        for (int i = 0; i < 3; i++) {
            l.person("Khorigale drover", KHORIGALE, "common", -6 + i * 9, 26 + i * 3, l.rnd.nextInt(360),
                    i == 0 ? "Good grass this year." : i == 1 ? "Mind the bulls. They mind you." : "Long road. "
                            + "Good road.").set("roam", 8.0);
        }
        l.quest(58, -44, "Thunder on the plain", "Herdmaster Dhal", KILL, "buffalo", 2, 50,
                "Two old bulls have turned on the herd, north-east of the camp. They must come down before they "
                        + "gore another calf. I will not ask my drovers - will you?",
                "The bulls keep to the north-east, where the grass is tall.",
                "It had to be done. Drink to them with this - they were fine beasts once.",
                "coins x90, drinking horn x1, leather x6", 140).set("id", "camp_buffalo");
        l.herd("buffalo", 5, 58, -44, 14);
        l.herd("deer", 3, -60, -50, 14);

        l.everywhere(46, 0.9, 1.5, "acacia_tree");
        l.everywhere(330, 0.8, 1.3, "tall_dry_grass", "dry_grass_tuft", "dry_grass_tuft");
        l.everywhere(30, 0.8, 1.4, "sandstone_rock", "termite_mound", "thorn_bush");
        l.scatter(8, 40, 40, 30, 0.9, 1.2, "sunfruit_bush");
        l.scatter(6, -50, 30, 26, 0.9, 1.2, "thornberry_bush");
        l.border(70, 12, 1.0, 1.6, "acacia_tree");
        l.spawn(0, 86, 0);
        l.wayOut("The road west", 5, 91, 0, false);
        return l;
    }

    // --- The Sallow Marsh: the dead who do not lie still --------------------------------------------

    static Land sallowMarsh() {
        Land l = new Land("sallow_marsh", "The Sallow Marsh", 256, 97);
        l.sky(0x4A5560, 0x8C9488, 58, AIR.withStartHour(16.5).withMist(0.75).withMistHeight(3.5).withClouds(0.8)
                .withRays(0.2).withAurora(0));
        l.wild("crow", "owl");
        l.roll(0.7, 30);
        l.paint(GroundMap.MUD, 0, 0, 260);
        // the causeway, the warden's camp at its foot and the old tower at its end
        l.road(GroundMap.GRAVEL, 1.7, 0, 124, 0, 92, -30, 52, -10, 12, 20, -20, 0, -56);
        l.flat(0, -66, 22, 0.8);
        l.paint(GroundMap.STONE, 0, -66, 13);
        l.keepOpen(0, -66, 12).keepOpen(4, 110, 9);
        for (int i = 0; i < 34; i++) {
            double x = (l.rnd.nextDouble() * 2 - 1) * 112, z = (l.rnd.nextDouble() * 2 - 1) * 112;
            double r = 5 + l.rnd.nextDouble() * 7;
            if (l.clearOf(x, z, r * 1.8)) l.pool(WaterMap.Kind.SWAMP, x, z, r, 0.5 + l.rnd.nextDouble() * 0.8);
        }

        l.house("square_tower", 0, -70, 180, 5);
        for (int i = 0; i < 7; i++) {
            double a = Math.toRadians(i * 51);
            l.scenery("standing_stone", Math.cos(a) * 11, -66 + Math.sin(a) * 11, 1.2 + l.rnd.nextDouble() * 0.5);
        }
        l.glow("magic", 0, -62, 12, 2.5);
        l.chest(5, -76, 200, "coins x80, bone key, shadow potion x2, scroll of venom");
        l.foe("risen_brute", 0, -60, 180).set("name", "The Drowned Reeve").set("health", 1.6).set("roam", 6.0);
        l.band("risen_walker", 2, 0, -58, 7, 5);
        l.band("risen_walker", 3, -30, 52, 9, 6);
        l.band("risen_crawler", 3, -10, 12, 9, 6);
        l.band("risen_runner", 3, 20, -20, 9, 8);
        l.band("risen_spitter", 2, 34, -44, 7, 5);
        l.band("risen_walker", 4, -56, -30, 12, 6);
        l.band("risen_walker", 2, 54, 40, 8, 6);

        l.fire(8, 112);
        l.prop("crate", 11, 109, 20);
        l.prop("barrel", 12, 113, 0);
        l.lamp(-3, 104);
        l.person("Warden Ysolde", HUMAN, "armoured", 4, 108, 190,
                        "Stay on the causeway. What is off it was a village once.|They do not rest while one of "
                                + "them still walks. All of them, or none.")
                .set("body", "feminine").set("weapon", "mace").set("topics",
                        "The marsh=It took Sallowford in a single winter. Then the dead got up out of it."
                                + "; The key=Silver. It opens the gate of the old watchtower - I held that gate "
                                + "once. Earn it.");
        l.quest(0, 0, "Lay them down", "Warden Ysolde", CLEAR, "", 1, 0,
                "Every one of the dead in this marsh, laid down for good. Do that, and the silver key I carry is "
                        + "yours, and welcome.", "I can still hear them out there.",
                "Listen. Frogs. I had forgotten the marsh had frogs. Here is the key - and my thanks.",
                "silver key x1, coins x120, scroll of warding x1", 260).set("id", "marsh_clear");

        l.everywhere(300, 0.8, 1.3, "reeds");
        l.everywhere(60, 1.0, 1.6, "willow_tree");
        l.everywhere(90, 0.9, 1.5, "dead_tree");
        l.everywhere(70, 0.8, 1.3, "mossy_log", "mossy_rock", "fern");
        l.border(130, 12, 1.0, 1.6, "dead_tree", "willow_tree");
        for (double[] f : new double[][]{{-40, 80}, {30, 60}, {-60, 0}, {50, -10}, {-20, -40}}) {
            l.critters("frog", 6, f[0], f[1], 12);
            l.critters("firefly", 10, f[0] + 10, f[1] - 8, 14);
        }
        l.spawn(0, 118, 0);
        l.wayOut("The causeway back", -5, 122, 0, false);
        return l;
    }

    // --- The Old Watchtower: the warband's hold ------------------------------------------------------

    static Land oldWatchtower() {
        Land l = new Land("old_watchtower", "The Old Watchtower", 256, 101);
        l.sky(0x5A4A48, 0xC08A5A, 95, AIR.withStartHour(17.5).withClouds(0.7).withMist(0.3).withAurora(0));
        l.wild("crow", "eagle");
        l.roll(1.5, 40);
        l.shape((x, z, was) -> was + 14 * Land.ease(1 - Math.hypot(x, z + 20) / 104));
        l.flat(0, -20, 76, 14.2);
        l.paint(GroundMap.DIRT, 0, 0, 260).paint(GroundMap.ASH, 0, -20, 96);
        l.road(GroundMap.PACKED, 2, 0, 124, 10, 92, -12, 62, 0, 34, 0, 16);
        l.keepOpen(0, -20, 44);

        // the wall: eight sides of thirty metres, a turret at each corner, the gatehouse in the south one
        double r = 39.2, cx = 0, cz = -20;
        double[][] corner = new double[8][];
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(22.5 + i * 45);
            corner[i] = new double[]{cx + Math.cos(a) * r, cz + Math.sin(a) * r};
            l.prop("turret", corner[i][0], corner[i][1], Land.facing(corner[i][0], corner[i][1], cx, cz) + 180);
        }
        for (int i = 0; i < 8; i++) {
            double[] a = corner[i], b = corner[(i + 1) % 8];
            if (i == 1) {               // (the south side: 67.5° to 112.5°)
                l.rampart(a[0], a[1], 3.75, a[1]).rampart(-3.75, b[1], b[0], b[1]);
                l.prop("gatehouse", 0, a[1], 180);
                l.put("lock", 0, a[1], 180, "name", "The watchtower gate", "key", "silver_key", "consumes", true,
                        "model", "iron_gate");
            } else {
                l.rampart(a[0], a[1], b[0], b[1]);
            }
        }
        l.prop("square_tower", 0, -30, 180);
        l.fire(-10, -8);
        l.fire(12, -4);
        l.prop("crate", -14, -12, 20);
        l.prop("crate", 16, -8, 60);
        l.prop("barrel", 14, -10, 0);
        l.prop("weapon_rack", -8, -24, 90);
        l.prop("stocks", 8, 4, 180);
        l.chest(12, -40, 315, "coins x200, battle_axe crystal, scroll of fury x2, gold key");
        l.foe("bortzal", 0, -16, 180).set("name", "Warlord Thrakk").set("size", 1.35).set("health", 3.5)
                .set("weapon", "battle_axe").set("roam", 4.0);
        l.band("bortzal", 6, 0, -12, 18, 6);
        for (double x : new double[]{-9, 9}) l.foe("bortzal", x, 22, 180).set("weapon", "longbow").set("roam", 2.0);
        l.band("bortzal", 3, 34, 66, 8, 7);
        l.band("bortzal", 3, -44, 56, 8, 7);

        l.fire(9, 112);
        l.person("Scout Perrin", HUMAN, "guard", 5, 110, 200,
                        "Keep low. They watch the road from the wall.|The gate takes a silver key. The marsh warden "
                                + "has one - she held this tower before they burnt the hill.")
                .set("weapon", "longbow").set("topics",
                        "The warband=A score of them, and Thrakk. He is half again the size of the rest."
                                + "; Beyond=Past the tower the road runs on to the Crystal Hollow. Nobody has "
                                + "come back down it since spring.");
        l.quest(0, -16, "The warlord's head", "Captain Rhosyn", KILL, "Warlord Thrakk", 1, 0, WARLORD[0],
                WARLORD[1], WARLORD[2], WARLORD[3], 300).set("id", "brasswick_warlord");

        l.everywhere(120, 0.9, 1.5, "dead_tree");
        l.everywhere(70, 0.8, 1.5, "ash_spire", "boulder", "stones");
        l.everywhere(40, 0.8, 1.2, "dry_grass_tuft", "thorn_bush");
        l.border(120, 12, 1.0, 1.6, "dead_tree", "pine_tree");
        l.spawn(0, 118, 0);
        l.wayOut("The road back", -5, 122, 0, false);
        l.wayOut("The north postern", -14, -50, 0, true);
        return l;
    }

    // --- Crystal Hollow: where the sky serpents come through ----------------------------------------------

    static Land crystalHollow() {
        Land l = new Land("crystal_hollow", "Crystal Hollow", 192, 113);
        l.sky(0x1A1440, 0x5A3A8A, 110, AIR.withStartHour(22).withStars(1).withAurora(1).withClouds(0.1)
                .withMist(0.18).withPlanetAt(120, 40).withPlanetSize(34));
        l.wild("owl", "butterfly");
        l.roll(1.6, 36);
        // a hollow, its rim nine metres over its floor (the mist lies in the bottom of it)
        l.shape((x, z, was) -> was + 9 * (1 - Land.ease(1 - Math.hypot(x, z) / 74)));
        l.paint(GroundMap.STONE, 0, 0, 200).paint(GroundMap.CRYSTAL, 0, 0, 72);
        l.road(GroundMap.GRAVEL, 1.6, -94, 0, -50, 6, -18, 0);
        l.road(GroundMap.GRAVEL, 1.6, 18, 0, 52, -6, 94, 0);
        l.pool(WaterMap.Kind.CRYSTAL, 0, 0, 12, 1.6);

        l.put("portal", -26, -14, 0, "count", 3, "every", 7.0, "height", 18.0, "range", 36.0);
        l.put("portal", 28, 16, 0, "count", 3, "every", 7.0, "height", 20.0, "range", 34.0, "colour", "any");
        l.band("drurinean", 3, 62, -4, 7, 5);
        for (double[] p : new double[][]{{-40, 20}, {-20, -30}, {24, -26}, {40, 18}, {0, 30}, {-60, -8}, {66, 6}}) {
            l.glow("crystal", p[0], p[1], 12, 2);
        }
        l.chest(2, -34, 0, "crystal key, crystal elixir, coins x150");

        l.fire(-84, 6);
        l.person("Seer Oriel", DRURINEAN, "ragged", -82, 3, 250,
                        "Hush. They hear through the thin places.|The blooms drink what comes down through the "
                                + "sky. I would know what it is they are drinking.")
                .set("body", "feminine").set("topics",
                        "The serpents=Vérnees. They fall out of the holes in the sky and climb back before dawn. "
                                + "Strike when they dive.; The road=East, past the pool, the Vale ends. Beyond the "
                                + "gate the main lands go on.");
        l.quest(0, 0, "Blooms for the seer", "Seer Oriel", FETCH, "crystal_bloom", 4, 0,
                "Four crystal blooms from the hollow - picked, not cut. The serpents will not thank you for it.",
                "The blooms grow low, among the crystal grass round the pool.",
                "Still humming. Yes. Take these: the dust is theirs, the storm is mine.",
                "crystal dust x6, scroll of the storm x1, coins x100", 240).set("id", "hollow_blooms");

        l.scatter(18, 0, 0, 60, 0.9, 1.2, "crystal_bloom");
        l.scatter(60, 0, 0, 74, 0.9, 1.7, "crystal_tree");
        l.scatter(90, 0, 0, 78, 0.7, 1.5, "crystal_spire", "crystal_cluster", "crystal_cluster");
        l.scatter(150, 0, 0, 76, 0.8, 1.3, "crystal_grass");
        l.everywhere(50, 0.8, 1.5, "iridescent_rock", "boulder");
        l.everywhere(80, 0.9, 1.6, "pine_tree", "tall_pine", "dead_tree");
        l.border(90, 12, 1.0, 1.6, "tall_pine", "pine_tree");
        l.spawn(-88, 0, 270);
        l.wayOut("The road back", -92, -5, 270, false);
        l.wayOut("The east road", 90, 3, 90, true);
        return l;
    }
}
