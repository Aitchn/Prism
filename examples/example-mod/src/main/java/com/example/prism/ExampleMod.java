package com.example.prism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ComponentType;
import com.aitchn.prism.api.mod.ContentView;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * A mod is a plain Bukkit plugin that depends on Prism. Implementing {@link PrismMod} on the main class is all the
 * wiring needed: Prism finds it, reads {@code prism/items/*.yml} from this jar and registers the content.
 */
public final class ExampleMod extends JavaPlugin implements PrismMod {

    /** Data component: how valuable the item is. Declared in YAML as {@code examplemod:tier: 2}. */
    public record Tier(int level) {}

    public static ComponentType<Tier> tier;

    @Override
    public void onInitialize(@NotNull ModContext context) {
        tier = context.registerComponent("tier", Tier.class, section -> new Tier(section.requireInt("value")));
        context.registerBehavior("heal", HealBehavior.class, HealBehavior::fromYaml);
    }

    @Override
    public void onContentLoaded(@NotNull ContentView view) {
        // Proves item creation works on the real server; the smoke test looks for this line.
        Prism.items().create(new PrismKey("examplemod", "ruby"), 1).ifPresentOrElse(
                stack -> getLogger().info("EXAMPLE_ITEM_OK examplemod:ruby x" + stack.getAmount()),
                () -> getLogger().severe("EXAMPLE_ITEM_FAIL examplemod:ruby is not registered"));
    }
}
