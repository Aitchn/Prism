package com.aitchn.validation.botania;

import com.aitchn.prism.api.mod.ModContext;
import java.util.function.Supplier;

/** Registers what the YAML in {@code prism/} refers to. Shared by the plugin and by the content test. */
final class BotaniaContent {

    private BotaniaContent() {}

    static void register(ModContext context, Supplier<BotaniaRuntime> runtime) {
        context.registerBehavior("block_entity", BlockEntityBehavior.class,
                section -> BlockEntityBehavior.fromYaml(runtime, section));
    }
}
