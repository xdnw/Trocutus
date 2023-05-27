package link.locutus.core.db.entities.kingdom;

import link.locutus.Trocutus;
import link.locutus.core.db.entities.alliance.AllianceList;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface KingdomOrAllianceOrGuild {

    static KingdomOrAllianceOrGuild get(long id, int type) {
        switch (type) {
            case 1:
                return DBKingdom.get((int) id);
            case 2:
                return DBAlliance.get((int) id);
            case 3:
                return (KingdomOrAllianceOrGuild) Trocutus.imp().getDiscordApi().getGuildById(id);
            default:
                throw new IllegalArgumentException("Invalid type: " + type);
        }
    }

    default int getId() {
        return (int) getIdLong();
    }

    default long getIdLong() {
        return getId();
    }

    default int getReceiverType() {
        if (isKingdom()) return 1;
        if (isAlliance()) return 2;
        if (isGuild()) return 3;
        throw new IllegalArgumentException("Invalid state: " + this);
    }

    default String getQualifiedName() {
        return getTypePrefix() + ":" + getIdLong();
    }

    default String getTypePrefix() {
        if (isKingdom()) return "kingdom";
        if (isAlliance()) return "aa";
        if (isGuild()) return "guild";
        throw new IllegalArgumentException("Invalid state: " + this);
    }

    default boolean isAlliance() {
        return this instanceof DBAlliance;
    }

    String getName();

    default Map.Entry<Long, Integer> getTransferIdAndType() {
        long sender_id;
        int sender_type;
        if (isGuild()) {
            GuildDB db = asGuild();
            if (db.hasAlliance()) {
                throw new IllegalStateException("Alliance Guilds cannot be used as sender. Specify the alliance instead: " + db.getGuild());
            }
            sender_id = db.getGuild().getIdLong();
            sender_type = 3;
        } else if (isAlliance()) {
            sender_id = getIdLong();
            sender_type = 2;
        } else if (isKingdom()) {
            sender_id = getId();
            sender_type = 1;
        } else throw new IllegalArgumentException("Invalid receiver: " + this);
        return new AbstractMap.SimpleEntry<>(sender_id, sender_type);
    }

    default boolean isGuild() {
        return this instanceof GuildKey;
    }

    default DBAlliance asAlliance() {
        return (DBAlliance) this;
    }

    default boolean isKingdom() {
        return this instanceof DBKingdom;
    }

    default DBKingdom asKingdom() {
        return (DBKingdom) this;
    }

    default GuildDB asGuild() {
        return (GuildDB) this;
    }

    String getUrl(String slug);
}
