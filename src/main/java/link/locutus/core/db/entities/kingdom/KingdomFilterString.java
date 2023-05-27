package link.locutus.core.db.entities.kingdom;

import net.dv8tion.jda.api.entities.Guild;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class KingdomFilterString implements KingdomFilter {
    private final String filter;
    private final Guild guild;
    private Set<Integer> cachedKingdoms;
    private long dateCached;

    public KingdomFilterString(String filter, Guild guild) {
        this.filter = filter;
        this.guild = guild;
    }

    @Override
    public String getFilter() {
        return filter;
    }

    @Override
    public boolean test(DBKingdom nation) {
        String localFilter = "#kingdom_id=" + nation.getId() + "," + filter;
        Set<DBKingdom> nations = DBKingdom.parseList(guild, localFilter, false);
        return nations != null && nations.contains(nation);
    }

    @Override
    public Predicate<DBKingdom> toCached(long expireAfter) {
        return nation -> {
            if (cachedKingdoms == null || System.currentTimeMillis() - dateCached > expireAfter) {
                Set<DBKingdom> nations = DBKingdom.parseList(guild, filter, false);
                cachedKingdoms = nations.stream().map(DBKingdom::getId).collect(Collectors.toSet());
                dateCached = System.currentTimeMillis();
            }
            return cachedKingdoms.contains(nation.getId());
        };
    }
}

