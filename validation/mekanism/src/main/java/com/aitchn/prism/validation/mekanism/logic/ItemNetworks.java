package com.aitchn.prism.validation.mekanism.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Logistical transporters: inside each network, every face that offers items hands them to the other blocks that will
 * take them. Each source splits what it offers between the destinations that still accept it; {@code round} rotates who
 * is asked first so the same machine does not always win. The budget limits how many items a network moves per tick.
 */
public final class ItemNetworks {

    private ItemNetworks() {}

    public static long distribute(List<Networks.Network> networks, Function<Networks.Endpoint, ItemPort> ports,
            ToLongFunction<Networks.Network> budget, long round) {
        long moved = 0;
        for (Networks.Network network : networks) {
            long left = budget.applyAsLong(network);
            List<Networks.Endpoint> ends = new ArrayList<>(network.touches());
            for (int s = 0; s < ends.size() && left > 0; s++) {
                Networks.Endpoint source = ends.get(s);
                ItemPort out = ports.apply(source);
                Stack offer = out == null ? null : out.offer();
                if (offer == null || offer.isEmpty()) {
                    continue;
                }
                int remaining = (int) Math.min(offer.count(), left);
                int total = remaining;
                int count = ends.size();
                for (int k = 0; k < count && remaining > 0; k++) {
                    Networks.Endpoint target = ends.get((int) Math.floorMod(k + round + s, (long) count));
                    if (target.pos().equals(source.pos())) {
                        continue;
                    }
                    ItemPort in = ports.apply(target);
                    if (in == null) {
                        continue;
                    }
                    Stack rest = in.insert(new Stack(offer.id(), remaining));
                    remaining = rest.count();
                }
                int sent = total - remaining;
                if (sent > 0) {
                    out.taken(sent);
                    left -= sent;
                    moved += sent;
                }
            }
        }
        return moved;
    }
}
