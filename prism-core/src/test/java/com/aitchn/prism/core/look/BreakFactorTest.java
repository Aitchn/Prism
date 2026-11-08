package com.aitchn.prism.core.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.content.PrismComponents.Mining;
import com.aitchn.prism.core.block.DigMath;
import org.junit.jupiter.api.Test;

class BreakFactorTest {

    private static final ClientBreak NOTE = new ClientBreak(0.8, "axe", false);

    /** Ticks the client would need for {@code client} with the factor applied (vanilla: speed / seconds / 20 per tick). */
    private static double clientTicks(ClientBreak client, String held, double factor) {
        boolean tool = !"none".equals(client.tool()) && DigMath.toolType(held).equals(client.tool());
        double seconds = client.hardness() * (!client.requiresTool() || tool ? 1.5 : 5);
        double speed = (tool ? DigMath.tierSpeed(held) : 1) * factor;
        return seconds * 20 / speed;
    }

    @Test
    void clientPredictedTimeEqualsTheCustomTimeForEveryTool() {
        Mining custom = new Mining(2.5, "pickaxe");
        for (String held : new String[] {"AIR", "WOODEN_PICKAXE", "IRON_PICKAXE", "DIAMOND_AXE", "STONE_SHOVEL"}) {
            double factor = DigMath.breakSpeedFactor(custom, NOTE, held, 0);
            int serverTicks = DigMath.ticksToBreak(custom, held, 0, 0, 0, true, false);
            // the server rounds up to whole ticks
            assertEquals(serverTicks, clientTicks(NOTE, held, factor), 1.0, held);
        }
    }

    @Test
    void theSameToolAndEnchantmentOnBothSidesCancelOut() {
        Mining custom = new Mining(1.0, "axe");
        double factor = DigMath.breakSpeedFactor(custom, NOTE, "IRON_AXE", 3);
        // custom hardness 1.0 against 0.8 * 1.5 = 1.2 seconds by hand for the same tool and efficiency
        assertEquals(1.2 / 1.0, factor, 1e-9);
    }

    @Test
    void unbreakableBlocksNeverFinishOnTheClient() {
        assertEquals(0, DigMath.breakSpeedFactor(new Mining(-1, "none"), NOTE, "AIR", 0));
    }

    @Test
    void instantCustomBlocksAreCappedAtTheAttributeMaximum() {
        assertEquals(DigMath.MAX_BREAK_SPEED, DigMath.breakSpeedFactor(new Mining(0, "none"), NOTE, "AIR", 0));
    }

    @Test
    void anInstantClientStateCannotBeSlowed() {
        assertTrue(Double.isNaN(DigMath.breakSpeedFactor(new Mining(3, "none"), new ClientBreak(0, "none", false),
                "AIR", 0)));
    }

    @Test
    void aStateThatNeedsAToolIsSlowerByHandSoTheFactorIsLarger() {
        ClientBreak dispenser = new ClientBreak(3.5, "pickaxe", true);
        double byHand = DigMath.breakSpeedFactor(new Mining(1, "none"), dispenser, "AIR", 0);
        double pick = DigMath.breakSpeedFactor(new Mining(1, "none"), dispenser, "STONE_PICKAXE", 0);
        assertEquals(3.5 * 5, byHand, 1e-9);
        assertEquals(3.5 * 1.5 / 4, pick, 1e-9);
    }

    @Test
    void theFactorStaysInsideTheAttributeRange() {
        assertEquals(DigMath.MAX_BREAK_SPEED,
                DigMath.breakSpeedFactor(new Mining(0.001, "none"), new ClientBreak(3.5, "pickaxe", true), "AIR", 0));
    }
}
