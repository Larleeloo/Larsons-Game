package com.larsons.game.world;

import com.larsons.game.cards.Card;
import com.larsons.game.cards.CardBook;
import com.larsons.game.math.Vec3;
import com.larsons.game.sprite.Wardrobe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Shops: their stock written as a line, buying items and cards with coins, selling for half. */
class ShopTest {

    @Test
    void theStockLineReadsCountsPricesAndCards() {
        Shop shop = new Shop(1, "Stall", Vec3.ZERO, 0,
                "whetstone x20 @3, arrow bundle, longbow rare @90, card:ember_script @55, cards x2, not a thing", true);
        List<Shop.Offer> stock = shop.stock("mage");
        Shop.Offer whet = stock.get(0);
        assertEquals(Supplies.WHETSTONE, whet.item().def());
        assertEquals(20, whet.left());
        assertEquals(3, whet.price());
        Shop.Offer arrows = stock.get(1);
        assertEquals(-1, arrows.left(), "nothing written: it never runs out");
        assertEquals(Shop.worth(arrows.item()), arrows.price(), "nothing written: what it is worth");
        Shop.Offer bow = stock.get(2);
        assertEquals(ItemDef.LONGBOW, bow.item().def());
        assertEquals(Rarity.RARE, bow.item().rarity());
        assertEquals(90, bow.price());
        long cards = stock.stream().filter(o -> o.card() != null).count();
        assertEquals(3, cards, "the named card and two of her class's shop cards");
        assertEquals(55, stock.get(3).price());
        assertEquals("ember_script", stock.get(3).card());
        for (Shop.Offer o : stock) {
            if (o.card() == null) continue;
            Card c = CardBook.get().card(o.card());
            assertNotNull(c);
            assertEquals(1, o.left(), "one of each card");
        }
        assertEquals(stock.size(), shop.stock("mage").size(), "the same cards each time she comes");
        assertNotEquals(-1, stock.indexOf(whet));
    }

    @Test
    void aBlankStockSellsTheSupplies() {
        Shop shop = new Shop(2, "", Vec3.ZERO, 0, "", true);
        assertEquals("Shop", shop.name());
        List<Shop.Offer> stock = shop.stock(null);
        for (ItemDef d : Supplies.AMMO) {
            assertTrue(stock.stream().anyMatch(o -> o.item() != null && o.item().def() == d), d.id() + " for sale");
        }
        assertTrue(stock.stream().noneMatch(o -> o.card() != null), "no class, no cards");
    }

    @Test
    void sheBuysWithCoinsAndSellsForHalf() {
        World w = new World(new Wardrobe());
        Player p = w.player();
        Shop shop = w.addShop(new Shop(3, "Smithy", new Vec3(0, 0, -1), 0, "upgrade ingot x1 @30", true));
        p.setPosition(Vec3.ZERO);
        assertSame(shop, w.reachableShop());
        Shop.Offer ingot = shop.stock(null).get(0);
        assertEquals(Shop.Deal.POOR, shop.buy(ingot, p));
        p.addCoins(45);
        assertEquals(Shop.Deal.DONE, shop.buy(ingot, p));
        assertEquals(15, p.coins());
        assertEquals(1, p.inventory().total(Item.of(Supplies.UPGRADE_INGOT)));
        assertEquals(Shop.Deal.SOLD_OUT, shop.buy(ingot, p));
        int slot = p.inventory().indexOf(Supplies.UPGRADE_INGOT);
        assertEquals(Shop.worth(Item.of(Supplies.UPGRADE_INGOT)) / 2, shop.offerFor(p.inventory().get(slot)));
        assertEquals(Shop.Deal.DONE, shop.sell(p, slot));
        assertEquals(15 + Shop.worth(Item.of(Supplies.UPGRADE_INGOT)) / 2, p.coins());
        assertFalse(p.inventory().contains(Supplies.UPGRADE_INGOT));
        p.setPosition(new Vec3(0, 0, 5));
        assertNull(w.reachableShop(), "out of reach");
    }

    @Test
    void aCardBoughtGoesOntoTheDiscardPile() {
        World w = new World(new Wardrobe());
        Player p = w.player();
        p.hero().choose("mage", p);
        Shop shop = new Shop(4, "Cards", Vec3.ZERO, 0, "cards x3", false);
        Shop.Offer card = shop.stock("mage").get(0);
        p.addCoins(card.price());
        int before = p.hero().deck().discardPile().size();
        assertEquals(Shop.Deal.DONE, shop.buy(card, p));
        assertEquals(0, p.coins());
        assertEquals(before + 1, p.hero().deck().discardPile().size());
        assertTrue(p.hero().deck().discardPile().contains(card.card()));
        assertEquals(0, shop.offerFor(Item.of(ItemDef.SWORD)), "it buys nothing");
    }
}
