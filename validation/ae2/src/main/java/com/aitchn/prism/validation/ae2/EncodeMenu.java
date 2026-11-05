package com.aitchn.prism.validation.ae2;

import com.aitchn.prism.api.Prism;
import com.aitchn.prism.api.PrismKey;
import com.aitchn.prism.api.menu.ClickKind;
import com.aitchn.prism.api.menu.ContainerMenu;
import com.aitchn.prism.api.menu.MenuView;
import com.aitchn.prism.api.menu.SlotRole;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** Pattern encoding: a blank pattern and a sample of the item to make go in; an encoded pattern comes out. */
final class EncodeMenu implements ContainerMenu {

    private static final int BLANK = 11;
    private static final int SAMPLE = 13;
    private static final int BUTTON = 15;

    private final Ae2Services services;

    EncodeMenu(Ae2Services services) {
        this.services = services;
    }

    @Override
    public @NotNull String title() {
        return "<dark_aqua>Pattern encoder";
    }

    @Override
    public int rows() {
        return 3;
    }

    @Override
    public @NotNull SlotRole role(int slot) {
        return slot == BLANK || slot == SAMPLE ? SlotRole.INPUT : SlotRole.BUTTON;
    }

    @Override
    public boolean accepts(int slot, @NotNull ItemStack stack) {
        return slot != BLANK || Prism.items().identify(stack).map(k -> k.value().equals("blank_pattern")).orElse(false);
    }

    @Override
    public void onOpen(@NotNull MenuView view) {
        view.setIcon(BUTTON, new PrismKey("minecraft", "anvil"), "<green>Encode",
                List.of("<gray>Left: a blank pattern", "<gray>Middle: the item to make"));
    }

    @Override
    public void onButton(@NotNull MenuView view, int slot, @NotNull ClickKind click) {
        if (slot != BUTTON) {
            return;
        }
        ItemStack blank = view.item(BLANK);
        ItemStack sample = view.item(SAMPLE);
        if (blank == null || sample == null) {
            view.player().sendMessage(net.kyori.adventure.text.Component.text("Put a blank pattern and a sample in."));
            return;
        }
        Encoder.Result result = Encoder.encode(sample);
        if (result.pattern().isEmpty()) {
            view.player().sendMessage(net.kyori.adventure.text.Component.text(result.error()));
            return;
        }
        services.ui.patternItem(result.pattern().get()).ifPresent(encoded -> {
            ItemStack rest = blank.clone();
            rest.setAmount(blank.getAmount() - 1);
            view.setItem(BLANK, rest.getAmount() > 0 ? rest : null);
            view.player().getInventory().addItem(encoded).values()
                    .forEach(left -> view.player().getWorld().dropItem(view.player().getLocation(), left));
        });
    }

    @Override
    public void onClose(@NotNull MenuView view) {
        for (int slot : new int[] {BLANK, SAMPLE}) {
            ItemStack left = view.item(slot);
            if (left != null) {
                view.setItem(slot, null);
                view.player().getInventory().addItem(left).values()
                        .forEach(rest -> view.player().getWorld().dropItem(view.player().getLocation(), rest));
            }
        }
    }
}
