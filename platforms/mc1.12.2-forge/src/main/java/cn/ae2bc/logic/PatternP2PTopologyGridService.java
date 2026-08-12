package cn.ae2bc.logic;

import cn.ae2bc.part.PatternP2PUnitManagerPart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import cn.ae2bc.core.cache.TickCachedIndex;

/** Per-grid, per-tick manager index for the rv6 adapter. */
public final class PatternP2PTopologyGridService {
    private static final Map<IGrid, TickCachedIndex<UUID, PatternP2PUnitManagerPart>> CACHE =
            new WeakHashMap<IGrid, TickCachedIndex<UUID, PatternP2PUnitManagerPart>>();

    private PatternP2PTopologyGridService() { }

    public static PatternP2PUnitManagerPart find(IGridNode ownNode, UUID id, long tick) {
        if (ownNode == null || ownNode.getGrid() == null || id == null) return null;
        return managers(ownNode.getGrid(), tick).get(id);
    }

    private static Map<UUID, PatternP2PUnitManagerPart> managers(final IGrid grid, long tick) {
        TickCachedIndex<UUID, PatternP2PUnitManagerPart> index = CACHE.get(grid);
        if (index == null) { index = new TickCachedIndex<UUID, PatternP2PUnitManagerPart>(); CACHE.put(grid, index); }
        return index.values(tick, () -> {
            Map<UUID, PatternP2PUnitManagerPart> managers = new HashMap<UUID, PatternP2PUnitManagerPart>();
            for (IGridNode node : grid.getNodes()) {
                Object machine = node.getMachine();
                if (machine instanceof PatternP2PUnitManagerPart && node.isActive()) {
                    PatternP2PUnitManagerPart manager = (PatternP2PUnitManagerPart) machine;
                    if (!managers.containsKey(manager.getUnitId())) managers.put(manager.getUnitId(), manager);
                }
            }
            return managers;
        });
    }

    public static List<PatternP2PUnitManagerPart> findByFrequency(IGridNode ownNode, short frequency, long tick) {
        List<PatternP2PUnitManagerPart> result = new ArrayList<PatternP2PUnitManagerPart>();
        if (ownNode == null || ownNode.getGrid() == null) return result;
        for (PatternP2PUnitManagerPart manager : managers(ownNode.getGrid(), tick).values()) {
            if (manager.getFrequency() == frequency) result.add(manager);
        }
        Collections.sort(result, (left, right) -> left.getUnitId().compareTo(right.getUnitId()));
        return result;
    }

    public static int countByFrequency(IGridNode ownNode, short frequency, long tick) {
        if (ownNode == null || ownNode.getGrid() == null || frequency == 0) return 0;
        int count = 0;
        for (PatternP2PUnitManagerPart manager : managers(ownNode.getGrid(), tick).values()) {
            if (manager.getFrequency() == frequency) count++;
        }
        return count;
    }

}
