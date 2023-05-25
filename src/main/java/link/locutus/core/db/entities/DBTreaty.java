package link.locutus.core.db.entities;

import link.locutus.core.api.game.TreatyType;

public class DBTreaty {

    private final int realm_id;
    private final TreatyType type;
    private final int from;
    private final int to;

    public DBTreaty(TreatyType type, int from, int to, int realm_id) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.realm_id = realm_id;
    }

    public int getRealm_id() {
        return realm_id;
    }

    public int getFrom_id() {
        return from;
    }

    public int getTo_id() {
        return to;
    }

    public DBAlliance getFrom() {
        return DBAlliance.getOrCreate(from, realm_id);
    }

    public DBAlliance getTo() {
        return DBAlliance.getOrCreate(to, realm_id);
    }

    public DBRealm getRealm() {
        return DBRealm.getOrCreate(realm_id);
    }

    public TreatyType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "DBTreaty{" +
                "realm_id=" + realm_id +
                ", type=" + type +
                ", from=" + from +
                ", to=" + to +
                '}';
    }

    @Override
    public int hashCode() {
        return realm_id + type.hashCode() + from + to;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DBTreaty) {
            DBTreaty other = (DBTreaty) obj;
            return other.realm_id == realm_id && other.type == type && other.from == from && other.to == to;
        }
        return false;
    }
}
