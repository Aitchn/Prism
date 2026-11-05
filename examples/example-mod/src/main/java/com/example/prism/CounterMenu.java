package com.example.prism;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.block.CustomBlock;
import com.aitchn.prism.api.menu.ClickKind;
import com.aitchn.prism.api.menu.ContainerMenu;
import com.aitchn.prism.api.menu.MenuView;
import com.aitchn.prism.api.menu.SlotRole;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * The counter's GUI: a live count, a button that switches the block's look, and one slot that stores an item in the
 * block's own data. Shows how a menu keeps its items in the block (not in the menu) and touches the block only on the
 * block's thread.
 */
final class CounterMenu implements ContainerMenu {

    static final NamespacedKey STORED = NamespacedKey.fromString("examplemod:stored");

    private static final int COUNT_SLOT = 11;
    private static final int SWITCH_SLOT = 13;
    private static final int STORE_SLOT = 15;

    /** One viewer per block: two players editing the same stored slot could duplicate the item. */
    private static final Map<Location, CounterMenu> OPEN = new ConcurrentHashMap<>();

    private final Location at;
    private final ItemStack stored;
    private final AtomicInteger count;
    private volatile String variant;
    /** Set when the block is removed while open: the item in the slot then goes to the viewer on close. */
    private volatile boolean removed;

    private CounterMenu(Location at, ItemStack stored, int count, String variant) {
        this.at = at;
        this.stored = stored;
        this.count = new AtomicInteger(count);
        this.variant = variant;
    }

    /** Builds the menu from the block; call on the block's thread. Null if someone else has it open. */
    static CounterMenu of(CustomBlock block) {
        Location at = block.block().getLocation();
        byte[] bytes = block.data().get(STORED, PersistentDataType.BYTE_ARRAY);
        CounterMenu menu = new CounterMenu(at, bytes == null ? null : ItemStack.deserializeBytes(bytes),
                block.data().getOrDefault(CounterBehavior.TICKS, PersistentDataType.INTEGER, 0), block.variant());
        return OPEN.putIfAbsent(at, menu) == null ? menu : null;
    }

    /**
     * Called on the block's thread when the block goes. Returns true if a menu is open on it: the viewer then keeps
     * the stored item (it is handed over on close), so the block must not drop it as well.
     */
    static boolean blockRemoved(Location at) {
        CounterMenu menu = OPEN.get(at);
        if (menu == null) {
            return false;
        }
        menu.removed = true;
        return true;
    }

    @Override
    public @NotNull String title() {
        return "<dark_red>Ruby counter";
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public @NotNull SlotRole role(int slot) {
        return slot == STORE_SLOT ? SlotRole.INPUT : SlotRole.BUTTON;
    }

    @Override
    public void onOpen(@NotNull MenuView view) {
        drawCount(view);
        drawSwitch(view);
        view.setItem(STORE_SLOT, stored);
    }

    @Override
    public void onButton(@NotNull MenuView view, int slot, @NotNull ClickKind click) {
        if (slot != SWITCH_SLOT) {
            return;
        }
        String next = "active".equals(variant) ? "idle" : "active";
        variant = next;
        drawSwitch(view);
        Prism.scheduler().runAt(at, () -> Prism.blocks().setVariant(at.getBlock(), next));
    }

    /** Once the block is gone the slot is frozen: whatever is in it goes back to the viewer on close. */
    @Override
    public boolean allowChange(@NotNull MenuView view, int slot) {
        return !removed;
    }

    @Override
    public void onSlotChanged(@NotNull MenuView view, int slot) {
        ItemStack item = view.item(STORE_SLOT);
        byte[] bytes = item == null ? null : item.serializeAsBytes();
        Prism.scheduler().runAt(at, () -> Prism.blocks().get(at.getBlock()).ifPresent(block -> {
            if (bytes == null) {
                block.data().remove(STORED);
            } else {
                block.data().set(STORED, PersistentDataType.BYTE_ARRAY, bytes);
            }
        }));
    }

    @Override
    public int refreshTicks() {
        return 10;
    }

    @Override
    public void onRefresh(@NotNull MenuView view) {
        Prism.scheduler().runAt(at, () -> {
            var block = Prism.blocks().get(at.getBlock());
            if (block.isEmpty()) {
                view.close(); // broken while open; onClose hands the stored item to the viewer
                return;
            }
            count.set(block.get().data().getOrDefault(CounterBehavior.TICKS, PersistentDataType.INTEGER, 0));
        });
        drawCount(view);
    }

    @Override
    public void onClose(@NotNull MenuView view) {
        OPEN.remove(at, this);
        ItemStack left = view.item(STORE_SLOT);
        if (removed && left != null) {
            view.setItem(STORE_SLOT, null);
            view.player().getInventory().addItem(left).values()
                    .forEach(rest -> view.player().getWorld().dropItem(view.player().getLocation(), rest));
        }
    }

    private void drawCount(MenuView view) {
        view.setIcon(COUNT_SLOT, PrismKey.parse("minecraft:clock"), "<gold>Count: " + count.get(),
                List.of("<gray>Counts once a second"));
    }

    private void drawSwitch(MenuView view) {
        boolean active = "active".equals(variant);
        view.setIcon(SWITCH_SLOT, PrismKey.parse(active ? "minecraft:lime_dye" : "minecraft:gray_dye"),
                active ? "<green>Active" : "<gray>Idle", List.of("<gray>Click to switch the block's look"));
    }
}
