package com.aitchn.prism.validation.mekanism;

import com.aitchn.prism.api.block.BlockBreakBehavior;
import com.aitchn.prism.api.block.BlockContext;
import com.aitchn.prism.api.block.BlockInteractBehavior;
import com.aitchn.prism.api.content.ConfigSection;
import com.aitchn.prism.api.content.ContentException;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * One behavior type for every Mekanism block; {@code kind} says what the block is. Prism dispatches by capability,
 * so the machine id lives in YAML data, not in a hard-coded id check.
 */
public record MekanismBlock(Kind kind, String machine) implements BlockInteractBehavior, BlockBreakBehavior {

    public enum Kind { MACHINE, SOLAR, CABLE, PIPE, CASING }

    static MekanismBlock fromYaml(ConfigSection section) throws ContentException {
        section.rejectUnknownKeys(Set.of("kind", "machine"));
        Kind kind;
        try {
            kind = Kind.valueOf(section.requireString("kind").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw section.error("unknown kind: " + e.getMessage());
        }
        String machine = section.getString("machine").orElse(null);
        if (kind == Kind.MACHINE && machine == null) {
            throw section.error("kind machine needs a machine id");
        }
        return new MekanismBlock(kind, machine);
    }

    @Override
    public void onInteract(@NotNull BlockContext context) {
        MekanismMod.instance().world().interact(this, context);
    }

    @Override
    public void onBreak(@NotNull BlockContext context) {
        MekanismMod.instance().world().broken(this, context.block());
    }
}
