package link.locutus.core.db.entities.kingdom;

import link.locutus.core.api.alliance.Rank;
import link.locutus.core.db.entities.alliance.AllianceList;
import link.locutus.core.db.entities.alliance.DBAlliance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface KingdomList extends KingdomFilter {
    default AllianceList toAllianceList() {
        return new AllianceList(getAlliances());
    }
    @Override
    default String getFilter() {
        return null;
    }

    @Override
    default boolean test(DBKingdom dbKingdom){
        return getKingdoms().contains(dbKingdom);
    }

    Collection<DBKingdom> getKingdoms();

    default Set<Integer> getAllianceIds() {
        return getKingdoms().stream().map(DBKingdom::getAlliance_id).collect(Collectors.toSet());
    }

    default Set<DBAlliance> getAlliances() {
        return getAllianceIds().stream().map(DBAlliance::get).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    default Set<Integer> getKingdomIds() {
        return getKingdoms().stream().map(DBKingdom::getId).collect(Collectors.toSet());
    }

    default  <T> Map<T, KingdomList> groupBy(Function<DBKingdom, T> groupBy) {
        Map<T, List<DBKingdom>> mapList = new HashMap<>();
        for (DBKingdom nation : getKingdoms()) {
            T group = groupBy.apply(nation);
            mapList.computeIfAbsent(group, f -> new ArrayList<>()).add(nation);
        }
        Map<T, KingdomList> result = new HashMap<>();
        for (Map.Entry<T, List<DBKingdom>> entry : mapList.entrySet()) {
            result.put(entry.getKey(), new SimpleKingdomList(entry.getValue()));
        }
        return result;
    }

    default <T> Stream<T> stream(Function<? super DBKingdom, ? extends T> map) {
        return getKingdoms().stream().map(map);
    }

    default Stream<DBKingdom> stream() {
        return getKingdoms().stream();
    }

    default double getScore() {
        Collection<DBKingdom> nations = new ArrayList<>(getKingdoms());
        nations.removeIf(f -> f.getPosition().ordinal() <= 0 || f.isVacation());
        double total = 0;
        for (DBKingdom nation : nations) {
            total += nation.getScore();
        }
        return total;
    }

    default boolean contains(DBKingdom f) {
        return getKingdoms().contains(f);
    }
}
