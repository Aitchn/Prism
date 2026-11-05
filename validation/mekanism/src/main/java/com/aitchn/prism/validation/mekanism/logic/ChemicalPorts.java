package com.aitchn.prism.validation.mekanism.logic;

import java.util.List;

/** What a block offers other blocks for chemicals: tanks pipes may fill, and tanks pipes may drain. */
public record ChemicalPorts(List<ChemicalTank> inputs, List<ChemicalTank> outputs) {

    public ChemicalPorts {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }
}
