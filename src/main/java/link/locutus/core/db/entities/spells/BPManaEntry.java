package link.locutus.core.db.entities.spells;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BPManaEntry {
    private static final Map<Integer, BPManaEntry> bpManaCache = new ConcurrentHashMap<>();
    public final int bp;
    public final int mana;
    public final long date;

    public BPManaEntry(int bp, int mana, long date) {
        this.bp = bp;
        this.mana = mana;
        this.date = date;
    }

    public static int getMana(int nationId) {
        BPManaEntry cached = bpManaCache.get(nationId);
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        return cached == null || cached.date < cutoff ? 0 : cached.mana;
    }

    public static int getBp(int nationId) {
        BPManaEntry cached = bpManaCache.get(nationId);
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        return cached == null || cached.date < cutoff ? 0 : cached.bp;
    }

    public static void setBpMana(int nationId, int bp, int mana) {
        bpManaCache.put(nationId, new BPManaEntry(bp, mana, System.currentTimeMillis()));
    }

    public static BPManaEntry getEntry(int kingd) {
        return bpManaCache.get(kingd);
    }
}
