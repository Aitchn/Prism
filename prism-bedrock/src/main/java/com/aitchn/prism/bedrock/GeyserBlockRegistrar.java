package com.aitchn.prism.bedrock;

import com.aitchn.prism.core.bedrock.BedrockShapes;
import com.aitchn.prism.core.bedrock.SyntheticBlock;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.component.BoxComponent;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.component.TransformationComponent;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBlockState;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBoundingBox;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomBlocksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;

/**
 * Registers every synthetic block with a Geyser that runs inside this server (ADR-0024): one Bedrock custom block per
 * look, reached through a non-vanilla Java block state id above Geyser's vanilla range. Nothing in here is reachable
 * unless Geyser is installed; callers load this class only after checking that, and treat a {@link LinkageError} as
 * "Geyser is not usable".
 *
 * <p>Geyser asks for custom blocks once while it starts, and again on {@code /geyser reload}. Whatever is registered
 * then is what Bedrock players can be shown until the next start, so the {@link Source} decides what that is.
 */
public final class GeyserBlockRegistrar {

    /**
     * What to register.
     *
     * @param blocks the synthetic blocks, one per look
     * @param pack   the Bedrock resource pack ({@code .mcpack}) that holds their textures and geometry, or null if it is
     *               installed some other way
     */
    public record Content(List<SyntheticBlock> blocks, Path pack) {}

    /** Supplies the content when Geyser asks, and hears how it went. Called on Geyser's start-up thread. */
    public interface Source {

        /** The blocks to register now; null or empty to register none. May be asked twice per start (blocks, pack). */
        Content content();

        /** Every block of {@code content} was registered. */
        void registered(Content content);

        /** Registration failed; Bedrock players keep the legacy look until the cause is fixed. */
        void failed(Throwable cause);
    }

    private GeyserBlockRegistrar() {}

    /**
     * Subscribes to Geyser's custom block and resource pack events.
     *
     * @param owner any object that identifies the subscriber, normally the plugin
     */
    public static void install(Object owner, Source source) {
        EventRegistrar registrar = EventRegistrar.of(owner);
        EventBus<EventRegistrar> bus = GeyserApi.api().eventBus();
        bus.subscribe(registrar, GeyserDefineCustomBlocksEvent.class, event -> defineBlocks(event, source));
        bus.subscribe(registrar, GeyserDefineResourcePacksEvent.class, event -> definePack(event, source));
    }

    private static void defineBlocks(GeyserDefineCustomBlocksEvent event, Source source) {
        try {
            Content content = source.content();
            if (content == null || content.blocks().isEmpty()) {
                return;
            }
            // Build everything first: a block that cannot be built must not leave the others half registered.
            List<CustomBlockData> data = new ArrayList<>();
            List<JavaBlockState> states = new ArrayList<>();
            for (SyntheticBlock block : content.blocks()) {
                data.add(customBlock(block));
                states.add(javaState(block));
            }
            for (int i = 0; i < data.size(); i++) {
                event.register(data.get(i));
                event.registerOverride(states.get(i), data.get(i).defaultBlockState());
            }
            source.registered(content);
        } catch (RuntimeException | LinkageError e) {
            source.failed(e);
        }
    }

    private static void definePack(GeyserDefineResourcePacksEvent event, Source source) {
        try {
            Content content = source.content();
            if (content == null || content.pack() == null) {
                return;
            }
            ResourcePack pack = ResourcePack.create(PackCodec.path(content.pack()));
            // Geyser loads its own packs folder before this event. A copy of this pack left there (written for the
            // legacy route, or by an earlier version) has the same UUID and would make the registration fail on
            // every start, so the stale copy gives way to the one built for this boot.
            java.util.UUID id = pack.manifest().header().uuid();
            if (event.resourcePacks().stream().anyMatch(known -> known.manifest().header().uuid().equals(id))) {
                event.unregister(id);
            }
            event.register(pack);
        } catch (RuntimeException | LinkageError e) {
            source.failed(e);
        }
    }

    /** The Bedrock block of a look: its geometry, materials, collision, outline and rotation, no properties. */
    static CustomBlockData customBlock(SyntheticBlock block) {
        CustomBlockComponents.Builder components = CustomBlockComponents.builder()
                .displayName(block.displayName())
                .selectionBox(box(BedrockShapes.selection(block.selection())))
                .collisionBoxes(collisionBoxes(block))
                // Geyser keeps the break time itself (from the hardness of the Java state below); like its JSON mappings
                // this tells the Bedrock client not to finish a break on its own.
                .destructibleByMining(Float.MAX_VALUE)
                .placeAir(true);
        if (block.geometry() != null) {
            components.geometry(GeometryComponent.builder().identifier(block.geometry()).build());
        }
        for (Map.Entry<String, SyntheticBlock.Material> entry : block.materials().entrySet()) {
            components.materialInstance(entry.getKey(), MaterialInstance.builder()
                    .texture(entry.getValue().texture())
                    .renderMethod(entry.getValue().renderMethod())
                    .faceDimming(true)
                    .ambientOcclusionExponent(1.0f)
                    .build());
        }
        if (block.rotationX() != 0 || block.rotationY() != 0) {
            components.transformation(new TransformationComponent(block.rotationX(), block.rotationY(), 0));
        }
        return CustomBlockData.builder()
                .name(block.name())
                .includedInCreativeInventory(false)
                .components(components.build())
                .build();
    }

    /** The non-vanilla Java state: unique id and group, the hardness, no tool needed, the collision the server enforces. */
    static JavaBlockState javaState(SyntheticBlock block) {
        List<JavaBoundingBox> collision = new ArrayList<>();
        for (BedrockShapes.Box box : block.collision()) {
            collision.add(new JavaBoundingBox(box.middleX(), box.middleY(), box.middleZ(), box.sizeX(), box.sizeY(),
                    box.sizeZ()));
        }
        return JavaBlockState.builder()
                .identifier(block.javaIdentifier())
                .javaId(block.javaId())
                // one group per state: the group id only has to be unique and above Geyser's vanilla block ids
                .stateGroupId(block.javaId())
                .blockHardness(block.destroyTime())
                .waterlogged(false)
                .collision(collision.toArray(new JavaBoundingBox[0]))
                .canBreakWithHand(block.canBreakWithHand())
                .pistonBehavior(SyntheticBlock.PISTON_BEHAVIOR)
                .build();
    }

    private static List<BoxComponent> collisionBoxes(SyntheticBlock block) {
        List<BoxComponent> boxes = new ArrayList<>();
        for (BedrockShapes.BedrockBox box : BedrockShapes.collisionForBedrock(block.collision())) {
            boxes.add(box(box));
        }
        return boxes;
    }

    private static BoxComponent box(BedrockShapes.BedrockBox box) {
        return new BoxComponent(box.originX(), box.originY(), box.originZ(), box.sizeX(), box.sizeY(), box.sizeZ());
    }
}
