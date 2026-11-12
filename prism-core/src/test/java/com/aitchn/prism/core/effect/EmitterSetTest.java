package com.aitchn.prism.core.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ContentException;
import com.aitchn.prism.api.content.PrismComponents;
import com.aitchn.prism.api.effect.Emitter;
import com.aitchn.prism.api.effect.ParticleSpec;
import com.aitchn.prism.core.content.MapConfigSection;
import com.aitchn.prism.core.content.YamlFiles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmitterSetTest {

    private static final Emitter FLAME = new Emitter(ParticleSpec.of("minecraft:flame").withCount(2), 20,
            0, 0.5, -0.25, 8, List.of("active"));
    private static final Emitter SMOKE = new Emitter(ParticleSpec.of("minecraft:smoke"), 20, 0, 0, 0, 0, List.of());

    @Test
    void variantChoosesTheEmitters() {
        EmitterSet idle = new EmitterSet.Builder(5, 32).add(0, 64, 0, "NORTH", List.of(FLAME, SMOKE), "idle").build();
        EmitterSet active = new EmitterSet.Builder(5, 32).add(0, 64, 0, "NORTH", List.of(FLAME, SMOKE), "active")
                .build();
        assertEquals(1, idle.size());
        assertEquals("minecraft:smoke", idle.particle(0).type().toString());
        assertEquals(2, active.size());
        assertEquals(0.5, active.x(0), 1e-12);
        assertEquals(65.0, active.y(0), 1e-12);
        assertEquals(0.25, active.z(0), 1e-12);
        assertTrue(new EmitterSet.Builder(5, 32).add(0, 64, 0, "NORTH", List.of(FLAME), "idle").build().isEmpty());
    }

    @Test
    void offsetsTurnWithTheFacing() {
        EmitterSet east = new EmitterSet.Builder(5, 32).add(0, 0, 0, "EAST", List.of(FLAME), "active").build();
        assertEquals(0.75, east.x(0), 1e-12); // north offset -0.25 on z becomes +0.25 on x
        assertEquals(0.5, east.z(0), 1e-12);
        EmitterSet west = new EmitterSet.Builder(5, 32).add(0, 0, 0, "WEST", List.of(FLAME), "active").build();
        assertEquals(0.25, west.x(0), 1e-12);
    }

    @Test
    void aViewerInRangeIsScheduledAndNobodyFarIs() {
        EmitterSet set = new EmitterSet.Builder(5, 32).add(0, 64, 0, "NORTH", List.of(FLAME, SMOKE), "active").build();
        // 20 per second with a clock of 5 ticks: 5 emissions per cycle, flame 2 particles each, smoke 1
        List<EmitterSet.Pick> near = EmitterSet.select(7, 1, 64, 1, List.of(set), 1000);
        assertEquals(2, near.size());
        assertEquals(5, near.getFirst().emissions());
        // 20 blocks away: out of the flame's range of 8, inside the default 32 of the smoke
        List<EmitterSet.Pick> mid = EmitterSet.select(7, 20, 64, 0, List.of(set), 1000);
        assertEquals(1, mid.size());
        assertEquals("minecraft:smoke", mid.getFirst().set().particle(mid.getFirst().index()).type().toString());
        assertTrue(EmitterSet.select(7, 200, 64, 0, List.of(set), 1000).isEmpty());
        assertTrue(EmitterSet.select(7, 1, 64, 1, List.of(), 1000).isEmpty());
    }

    @Test
    void thousandsOfBlocksStayWithinTheBudgetNearestFirst() {
        EmitterSet.Builder builder = new EmitterSet.Builder(5, 32);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 60; y < 70; y++) {
                    builder.add(x, y, z, "NORTH", List.of(SMOKE), "");
                }
            }
        }
        EmitterSet set = builder.build();
        assertEquals(2560, set.size());
        List<EmitterSet.Pick> picks = EmitterSet.select(3, 0.5, 64.5, 0.5, List.of(set), 100);
        int sent = picks.stream().mapToInt(EmitterSet.Pick::emissions).sum();
        assertTrue(sent <= 100, "sent " + sent);
        assertEquals(20, picks.size()); // 5 emissions of 1 particle each
        EmitterSet.Pick first = picks.getFirst();
        assertEquals(0.5, first.set().x(first.index()), 1e-12);
        assertEquals(64.5, first.set().y(first.index()), 1e-12);
    }

    @Test
    void componentsDecode() throws ContentException {
        Object yaml = YamlFiles.parse("t", """
                value:
                  - particle: flame
                    rate: 0.25
                    count: 3
                    offset: [0, 0.6, 0]
                    spread: 0.2
                    range: 24
                    variants: [active]
                  - particle: minecraft:dust
                    color: "#ff4040"
                    size: 1.5
                """);
        PrismComponents.Particles particles = PrismComponents.PARTICLES.decoder()
                .decode(MapConfigSection.of("components.prism:particles", yaml));
        assertEquals(2, particles.emitters().size());
        Emitter flame = particles.emitters().getFirst();
        assertEquals(PrismKey.parse("minecraft:flame"), flame.particle().type());
        assertEquals(0.25, flame.rate());
        assertEquals(0.6, flame.offsetY());
        assertEquals(0.2, flame.particle().spreadZ());
        assertEquals(1, particles.in("idle").size());
        assertEquals(2, particles.in("active").size());
        assertEquals(0xff4040, particles.emitters().get(1).particle().color().orElseThrow());

        PrismComponents.Tint flat = PrismComponents.TINT.decoder().decode(
                new MapConfigSection("t", Map.of("value", List.of("#3ab5ff"))));
        assertEquals(List.of(0x3ab5ff), flat.colorsFor("anything"));
        PrismComponents.Tint perVariant = PrismComponents.TINT.decoder().decode(MapConfigSection.of("t",
                YamlFiles.parse("t", "colors: ['#ffffff']\nvariants:\n  active: ['#3ab5ff', '#000000']\n")));
        assertEquals(List.of(0xffffff), perVariant.colorsFor("idle"));
        assertEquals(List.of(0x3ab5ff, 0), perVariant.colorsFor("active"));
    }

    @Test
    void badComponentsAreRejected() {
        assertThrows(ContentException.class, () -> PrismComponents.PARTICLES.decoder().decode(MapConfigSection.of("t",
                YamlFiles.parse("t", "particle: flame\nrate: 0\n"))));
        assertThrows(ContentException.class, () -> PrismComponents.PARTICLES.decoder().decode(MapConfigSection.of("t",
                YamlFiles.parse("t", "particle: flame\nspeeed: 1\n"))));
        assertThrows(ContentException.class, () -> PrismComponents.PARTICLES.decoder().decode(MapConfigSection.of("t",
                YamlFiles.parse("t", "particle: flame\noffset: [1, 2]\n"))));
        assertThrows(ContentException.class, () -> PrismComponents.TINT.decoder().decode(
                new MapConfigSection("t", Map.of("value", List.of("blue")))));
    }
}
