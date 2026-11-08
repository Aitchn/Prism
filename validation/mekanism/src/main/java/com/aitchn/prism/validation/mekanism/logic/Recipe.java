package com.aitchn.prism.validation.mekanism.logic;

/**
 * One machine recipe. Every part is optional except the duration: item in/out, chemical in, up to two chemical
 * outs. Chemical input is consumed once per operation when the operation starts.
 */
public record Recipe(
        Stack itemIn,
        Stack itemOut,
        Chemical chemicalIn,
        long chemicalInAmount,
        Chemical chemicalOut,
        long chemicalOutAmount,
        Chemical chemicalOut2,
        long chemicalOut2Amount,
        int ticks,
        long energyPerTick,
        Stack itemIn2,
        Chemical chemicalIn2,
        long chemicalIn2Amount) {

    /** Form with a second input item but no second chemical. */
    public Recipe(Stack itemIn, Stack itemOut, Chemical chemicalIn, long chemicalInAmount, Chemical chemicalOut,
            long chemicalOutAmount, Chemical chemicalOut2, long chemicalOut2Amount, int ticks, long energyPerTick,
            Stack itemIn2) {
        this(itemIn, itemOut, chemicalIn, chemicalInAmount, chemicalOut, chemicalOutAmount, chemicalOut2,
                chemicalOut2Amount, ticks, energyPerTick, itemIn2, null, 0);
    }

    /** Canonical form without a second input item. */
    public Recipe(Stack itemIn, Stack itemOut, Chemical chemicalIn, long chemicalInAmount, Chemical chemicalOut,
            long chemicalOutAmount, Chemical chemicalOut2, long chemicalOut2Amount, int ticks, long energyPerTick) {
        this(itemIn, itemOut, chemicalIn, chemicalInAmount, chemicalOut, chemicalOutAmount, chemicalOut2,
                chemicalOut2Amount, ticks, energyPerTick, null);
    }

    /** Two input items (combiner, metallurgic infuser): both are consumed when the operation starts. */
    public static Recipe items2(String in, int inCount, String in2, int in2Count, String out, int outCount, int ticks,
            long energyPerTick) {
        return new Recipe(new Stack(in, inCount), new Stack(out, outCount), null, 0, null, 0, null, 0, ticks,
                energyPerTick, new Stack(in2, in2Count));
    }

    /** Two chemicals in (washer, chemical infuser, reaction chamber), any item in or out, one or two chemicals out. */
    public static Recipe chemicals(Stack itemIn, Stack itemOut, Chemical in1, long in1Amount, Chemical in2,
            long in2Amount, Chemical out1, long out1Amount, int ticks, long energyPerTick) {
        return new Recipe(itemIn, itemOut, in1, in1Amount, out1, out1Amount, null, 0, ticks, energyPerTick, null, in2,
                in2Amount);
    }

    public static Recipe item(String in, int inCount, String out, int outCount, int ticks, long energyPerTick) {
        return new Recipe(new Stack(in, inCount), new Stack(out, outCount), null, 0, null, 0, null, 0, ticks,
                energyPerTick);
    }

    /** Item + chemical to item: purification (oxygen), injection (hydrogen chloride), dissolution style steps. */
    public static Recipe itemWithChemical(String in, int inCount, Chemical chem, long chemAmount, String out,
            int outCount, int ticks, long energyPerTick) {
        return new Recipe(new Stack(in, inCount), new Stack(out, outCount), chem, chemAmount, null, 0, null, 0, ticks,
                energyPerTick);
    }

    /** Chemical to chemicals: electrolytic separator. */
    public static Recipe split(Chemical in, long inAmount, Chemical out1, long out1Amount, Chemical out2,
            long out2Amount, int ticks, long energyPerTick) {
        return new Recipe(null, null, in, inAmount, out1, out1Amount, out2, out2Amount, ticks, energyPerTick);
    }
}
