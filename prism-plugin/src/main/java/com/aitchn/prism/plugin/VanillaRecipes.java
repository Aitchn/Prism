package com.aitchn.prism.plugin;

import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.item.ItemService;
import com.aitchn.prism.api.recipe.Ingredient;
import com.aitchn.prism.api.recipe.ItemResult;
import com.aitchn.prism.api.recipe.PrismRecipe;
import com.aitchn.prism.api.recipe.PrismRecipes;
import com.aitchn.prism.api.recipe.RecipeService;
import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;

/**
 * Turns recipes of {@link PrismRecipes}' types into real vanilla recipes, and keeps custom items from being used as
 * their carrier material (a custom item on paper must not craft like paper). Custom ingredients use exact choices so
 * the recipe book shows the right item on Java and Bedrock.
 */
final class VanillaRecipes implements Listener {

    private final RecipeService recipes;
    private final ItemService items;
    private final PlatformScheduler scheduler;
    private final Logger log;
    /** Crafting recipes Prism added, with how many grid cells hold a custom item. */
    private final Map<NamespacedKey, Integer> added = new ConcurrentHashMap<>();
    /** Cooking recipes Prism added, and whether their input is a custom item. */
    private final Map<NamespacedKey, Boolean> cooking = new ConcurrentHashMap<>();

    VanillaRecipes(RecipeService recipes, ItemService items, PlatformScheduler scheduler, Logger log) {
        this.recipes = recipes;
        this.items = items;
        this.scheduler = scheduler;
        this.log = log;
    }

    static Optional<Tag<Material>> tag(PrismKey key) {
        NamespacedKey bukkit = NamespacedKey.fromString(key.toString());
        return bukkit == null ? Optional.empty()
                : Optional.ofNullable(Bukkit.getTag(Tag.REGISTRY_ITEMS, bukkit, Material.class));
    }

    /** Re-registers every Prism recipe; call after content changed. Runs on the global thread. */
    void sync() {
        scheduler.runGlobal(this::syncNow);
    }

    private void syncNow() {
        for (NamespacedKey key : List.copyOf(added.keySet())) {
            Bukkit.removeRecipe(key, false);
        }
        for (NamespacedKey key : List.copyOf(cooking.keySet())) {
            Bukkit.removeRecipe(key, false);
        }
        added.clear();
        cooking.clear();
        List<String> skipped = new ArrayList<>();
        for (PrismRecipe<PrismRecipes.Shaped> recipe : recipes.all(PrismRecipes.SHAPED)) {
            add(recipe.id(), () -> shaped(recipe), skipped);
        }
        for (PrismRecipe<PrismRecipes.Shapeless> recipe : recipes.all(PrismRecipes.SHAPELESS)) {
            add(recipe.id(), () -> shapeless(recipe), skipped);
        }
        for (var type : List.of(PrismRecipes.SMELTING, PrismRecipes.BLASTING, PrismRecipes.SMOKING,
                PrismRecipes.CAMPFIRE_COOKING)) {
            for (PrismRecipe<PrismRecipes.Cooking> recipe : recipes.all(type)) {
                add(recipe.id(), () -> cooking(recipe), skipped);
            }
        }
        Bukkit.updateRecipes();
        // Count what the server really holds now, with a custom result where one was asked for.
        long live = java.util.stream.Stream.concat(added.keySet().stream(), cooking.keySet().stream())
                .map(Bukkit::getRecipe).filter(java.util.Objects::nonNull).count();
        log.info("PRISM_RECIPES_READY added=" + live + " skipped=" + skipped.size());
        skipped.forEach(reason -> log.warning("PRISM_RECIPE_SKIPPED " + reason));
    }

    private interface Builder {
        Recipe build() throws IllegalArgumentException;
    }

    private void add(PrismKey id, Builder builder, List<String> skipped) {
        try {
            Recipe recipe = builder.build();
            if (!Bukkit.addRecipe(recipe, false)) {
                skipped.add(id + ": a recipe with this id already exists");
            }
        } catch (IllegalArgumentException e) {
            skipped.add(id + ": " + e.getMessage());
        }
    }

    private ShapedRecipe shaped(PrismRecipe<PrismRecipes.Shaped> recipe) {
        NamespacedKey key = key(recipe.id());
        ShapedRecipe shaped = new ShapedRecipe(key, result(recipe.value().result()));
        shaped.shape(recipe.value().pattern().toArray(String[]::new));
        int custom = 0;
        for (var entry : recipe.value().key().entrySet()) {
            shaped.setIngredient(entry.getKey(), choice(entry.getValue()));
            if (isCustom(entry.getValue())) {
                char symbol = entry.getKey();
                for (String row : recipe.value().pattern()) {
                    custom += (int) row.chars().filter(c -> c == symbol).count();
                }
            }
        }
        added.put(key, custom);
        return shaped;
    }

    private ShapelessRecipe shapeless(PrismRecipe<PrismRecipes.Shapeless> recipe) {
        NamespacedKey key = key(recipe.id());
        ShapelessRecipe shapeless = new ShapelessRecipe(key, result(recipe.value().result()));
        int custom = 0;
        for (Ingredient ingredient : recipe.value().ingredients()) {
            shapeless.addIngredient(choice(ingredient));
            custom += isCustom(ingredient) ? 1 : 0;
        }
        added.put(key, custom);
        return shapeless;
    }

    private CookingRecipe<?> cooking(PrismRecipe<PrismRecipes.Cooking> recipe) {
        NamespacedKey key = key(recipe.id());
        PrismRecipes.Cooking value = recipe.value();
        ItemStack result = result(value.result());
        RecipeChoice input = choice(value.input());
        CookingRecipe<?> made = switch (value.cooker()) {
            case FURNACE -> new FurnaceRecipe(key, result, input, value.experience(), value.ticks());
            case BLAST_FURNACE -> new BlastingRecipe(key, result, input, value.experience(), value.ticks());
            case SMOKER -> new SmokingRecipe(key, result, input, value.experience(), value.ticks());
            case CAMPFIRE -> new CampfireRecipe(key, result, input, value.experience(), value.ticks());
        };
        cooking.put(key, isCustom(value.input()));
        return made;
    }

    private boolean isCustom(Ingredient ingredient) {
        return !ingredient.isTag() && items.definition(ingredient.item()).isPresent();
    }

    private RecipeChoice choice(Ingredient ingredient) {
        if (ingredient.isTag()) {
            return new RecipeChoice.MaterialChoice(tag(ingredient.tag())
                    .orElseThrow(() -> new IllegalArgumentException("unknown item tag #" + ingredient.tag())));
        }
        Optional<ItemStack> custom = items.create(ingredient.item(), 1);
        if (custom.isPresent()) {
            return RecipeChoice.exactChoice(custom.get());
        }
        return new RecipeChoice.MaterialChoice(VanillaItems.material(ingredient.item())
                .orElseThrow(() -> new IllegalArgumentException("unknown item " + ingredient.item())));
    }

    private ItemStack result(ItemResult result) {
        return items.create(result).orElseThrow(() -> new IllegalArgumentException("unknown result " + result.item()));
    }

    private static NamespacedKey key(PrismKey id) {
        return new NamespacedKey(id.namespace(), id.value());
    }

    /** No result when the grid holds custom items in slots that expect vanilla items (or any vanilla recipe). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            return;
        }
        int expected = recipe instanceof Keyed keyed ? added.getOrDefault(keyed.getKey(), 0) : 0;
        int actual = 0;
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (stack != null && !stack.isEmpty() && items.identify(stack).isPresent()) {
                actual++;
            }
        }
        if (actual != expected) {
            event.getInventory().setResult(null);
        }
    }

    /** Custom items never cook as their carrier. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        if (items.identify(event.getSource()).isEmpty()) {
            return;
        }
        CookingRecipe<?> recipe = event.getRecipe();
        if (recipe == null || !cooking.getOrDefault(recipe.getKey(), false)) {
            event.setCancelled(true);
        }
    }
}
