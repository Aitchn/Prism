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
        long energyPerTick) {

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
