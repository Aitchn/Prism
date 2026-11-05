package com.example.prism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.content.ComponentType;
import com.aitchn.prism.api.mod.ContentView;
import com.aitchn.prism.api.mod.ModContext;
import com.aitchn.prism.api.mod.PrismMod;
import com.aitchn.prism.api.recipe.RecipeType;
import org.bukkit.persistence.PersistentDataType;
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
    public static RecipeType<Polishing> polishing;

    @Override
    public void onInitialize(@NotNull ModContext context) {
        tier = context.registerComponent("tier", Tier.class, section -> new Tier(section.requireInt("value")));
        context.registerBehavior("heal", HealBehavior.class, HealBehavior::fromYaml);
        context.registerBehavior("counter", CounterBehavior.class, CounterBehavior::fromYaml);
        context.registerBehavior("use_counter", UseCounterBehavior.class, UseCounterBehavior::fromYaml);
        polishing = context.registerRecipeType("polishing", Polishing.class, Polishing::decode);
    }

    @Override
    public void onContentLoaded(@NotNull ContentView view) {
        // Proves item creation works on the real server; the smoke test looks for this line.
        Prism.items().create(new PrismKey("examplemod", "ruby"), 1).ifPresentOrElse(
                stack -> getLogger().info("EXAMPLE_ITEM_OK examplemod:ruby x" + stack.getAmount()),
                () -> getLogger().severe("EXAMPLE_ITEM_FAIL examplemod:ruby is not registered"));
        // Recipes of the mod's own type are listed by Prism; the smoke test looks for EXAMPLE_RECIPES_OK.
        var polish = Prism.recipes().all(polishing);
        getLogger().info((polish.size() == 1 ? "EXAMPLE_RECIPES_OK" : "EXAMPLE_RECIPES_FAIL") + " polishing="
                + polish.size() + polish.stream().map(r -> " " + r.id() + ":" + r.value().input()).reduce("", String::concat));
        // Per-stack data and dynamic lore: the smoke test looks for EXAMPLE_LORE_OK.
        Prism.items().create(new PrismKey("examplemod", "ruby"), 1).ifPresent(stack -> {
            Prism.items().editData(stack, data -> data.set(UseCounterBehavior.USES, PersistentDataType.INTEGER, 3));
            java.util.List<net.kyori.adventure.text.Component> lore = stack.lore();
            String text = lore == null ? "" : lore.stream()
                    .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize)
                    .reduce("", (a, b) -> a + "|" + b);
            boolean ok = text.equals("|Right click to heal|Used 3 times");
            getLogger().info((ok ? "EXAMPLE_LORE_OK " : "EXAMPLE_LORE_FAIL ") + text);
        });
    }
}
