package link.locutus.core.db.entities.kingdom;

import java.util.Collection;

public class SimpleKingdomList implements KingdomList {
    private final Collection<DBKingdom> nations;
    public String filter;

    public SimpleKingdomList(Collection<DBKingdom> nations) {
        this.nations = nations;
    }

    public SimpleKingdomList setFilter(String filter) {
        this.filter = filter;
        return this;
    }

    @Override
    public String getFilter() {
        return filter;
    }

    @Override
    public Collection<DBKingdom> getKingdoms() {
        return nations;
    }
}
