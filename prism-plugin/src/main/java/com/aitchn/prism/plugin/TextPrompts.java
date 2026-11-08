package com.aitchn.prism.plugin;

import com.aitchn.prism.platform.spi.PlatformScheduler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;

/**
 * Text input through an anvil: the left slot holds a paper named with the initial text, the player edits the name,
 * and taking the result confirms. Every click in the view is cancelled, and the anvil is emptied before it closes,
 * so no item is ever given or taken.
 */
final class TextPrompts implements Listener {

    private static final int RESULT_SLOT = 2;

    private record Prompt(InventoryView view, Consumer<String> onSubmit, Runnable onCancel) {}

    private final PlatformScheduler scheduler;
    private final Logger log;
    private final Map<UUID, Prompt> open = new ConcurrentHashMap<>();

    TextPrompts(PlatformScheduler scheduler, Logger log) {
        this.scheduler = scheduler;
        this.log = log;
    }

    void open(Player player, String title, String initial, Consumer<String> onSubmit, Runnable onCancel) {
        scheduler.runOn(player, () -> {
            Component name = MiniMessage.miniMessage().deserialize(title);
            InventoryView view = MenuType.ANVIL.create(player, name);
            ItemStack paper = new ItemStack(Material.PAPER);
            // A single space keeps the name field editable when the initial text is empty.
            paper.editMeta(meta -> meta.displayName(Component.text(initial.isEmpty() ? " " : initial)));
            view.getTopInventory().setItem(0, paper);
            open.put(player.getUniqueId(), new Prompt(view, onSubmit, onCancel));
            player.openInventory(view);
        }, null);
    }

    private Prompt promptOf(InventoryView view) {
        Prompt prompt = open.get(view.getPlayer().getUniqueId());
        return prompt != null && prompt.view() == view ? prompt : null;
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        if (promptOf(event.getView()) == null) {
            return;
        }
        AnvilView view = event.getView();
        String text = view.getRenameText();
        ItemStack result = new ItemStack(Material.PAPER);
        result.editMeta(meta -> meta.displayName(Component.text(text == null ? "" : text)));
        event.setResult(result);
        view.setRepairCost(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Prompt prompt = promptOf(event.getView());
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory() || event.getRawSlot() != RESULT_SLOT) {
            return;
        }
        String text = event.getView() instanceof AnvilView anvil && anvil.getRenameText() != null
                ? anvil.getRenameText() : "";
        Player player = (Player) event.getWhoClicked();
        open.remove(player.getUniqueId(), prompt);
        event.getView().getTopInventory().clear();
        scheduler.runOn(player, () -> {
            player.closeInventory();
            run(() -> prompt.onSubmit().accept(text.strip()));
        }, null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (promptOf(event.getView()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Prompt prompt = promptOf(event.getView());
        if (prompt == null) {
            return; // not a prompt, or already submitted (its anvil was emptied then)
        }
        // Close fires before the anvil hands its items back: emptying it here means nothing is returned.
        event.getView().getTopInventory().clear();
        if (open.remove(event.getPlayer().getUniqueId(), prompt) && prompt.onCancel() != null) {
            run(prompt.onCancel());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        open.remove(event.getPlayer().getUniqueId());
    }

    private void run(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Text prompt callback failed", e);
        }
    }
}
