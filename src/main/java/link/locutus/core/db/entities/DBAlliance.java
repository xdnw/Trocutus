package link.locutus.core.db.entities;

import link.locutus.Trocutus;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.settings.Settings;
import link.locutus.util.MathMan;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DBAlliance {
    public static DBAlliance getOrCreate(int aaId, int realm_id) {
        DBAlliance alliance = Trocutus.imp().getDB().getAlliance(aaId);
        if (alliance == null) {
            alliance = new DBAlliance(aaId, realm_id, null, null, null, 0, 0, 0, 0);
        }
        return alliance;
    }

    public static DBAlliance get(Integer aaId) {
        return Trocutus.imp().getDB().getAlliance(aaId);
    }

    private final int alliance_id;
    private final int realm_id;
    private String name;
    private String tag;
    private String description;
    private long last_fetched;
    private int level;
    private int experience;
    private int level_total;

    public DBAlliance(int alliance_id, int realm_id, String name, String tag, String description, int level, int experience, int level_total, long last_fetched) {
        this.alliance_id = alliance_id;
        this.realm_id = realm_id;
        this.name = name;
        this.tag = tag;
        this.description = description;
        this.last_fetched = last_fetched;
        this.level = level;
        this.experience = experience;
        this.level_total = level_total;
    }

    public static DBAlliance parse(String input) {
        if (MathMan.isInteger(input)) {
            DBAlliance aa = Trocutus.imp().getDB().getAlliance(Integer.parseInt(input));
            if (aa != null) return aa;
        }
        if (input.contains("/")) {
            String[] split = input.split("/", 2);
            DBRealm realm = DBRealm.parse(split[0]);
            if (realm == null) return null;
            Set<DBAlliance> matching = Trocutus.imp().getDB().getAllianceMatching(f -> f.realm_id == realm.getId() && f.getName().equalsIgnoreCase(split[1]));
            if (matching.size() >= 1) return matching.iterator().next();
        }
        Set<DBAlliance> matching = Trocutus.imp().getDB().getAllianceMatching(f -> f.getName().equalsIgnoreCase(input));
        return matching.isEmpty() ? null : matching.iterator().next();
    }

    public static Set<DBAlliance> parseList(Guild guild, String args) {
        Set<DBAlliance> result = new LinkedHashSet<>();
        for (String arg : StringMan.split(args, ',')) {
            if (arg.equalsIgnoreCase("*")) {
                result.addAll(Trocutus.imp().getDB().getAllianceMatching(f -> true));
                continue;
            }
            DBAlliance alliance = DBAlliance.parse(arg);
            if (alliance == null) {
                throw new IllegalArgumentException("Could not find alliance: `" + arg + "`");
            }
            result.add(alliance);
        }
        return result;
    }


    public DBRealm getRealm() {
        return DBRealm.getOrCreate(realm_id);
    }

    public String getName() {
        if (name == null || name.isEmpty()) return getRealm().getName() + "/" + alliance_id;
        return name;
    }

    public String getTag() {
        return tag;
    }

    private static final Pattern DISCORD_URL_REGEX = Pattern.compile("https://discord.gg/[a-zA-Z0-9]+");

    public String getDiscord_link() {
        // parse discord url from description
        if (description == null || description.isEmpty()) return null;
        String regexStr = "https://discord.gg/[a-zA-Z0-9]+";
        Matcher m = Pattern.compile(regexStr).matcher(description);
        if (m.find()) {
            return m.group(0);
        }
        return null;
    }

    public GuildDB getGuildDB() {
        return Trocutus.imp().getGuildDBByAA(alliance_id);
    }

    public String getUrl(String kingdomName) {
        return "https://trounced.net/kingdom/" + kingdomName + "/alliance/" + getName();
    }

    public int getId() {
        return alliance_id;
    }

    public String getQualifiedName() {
        return "aa:" + getRealm().getName() + "/" + getName();
    }
    public Set<DBKingdom> getKingdoms() {
        return Trocutus.imp().getDB().getKingdomsByAlliance(alliance_id);
    }
    public Set<DBKingdom> getKingdoms(Predicate<DBKingdom> filter) {
        Set<DBKingdom> results = new LinkedHashSet<>();
        for (DBKingdom kingdom : getKingdoms()) {
            if (filter.test(kingdom)) {
                results.add(kingdom);
            }
        }
        return results;
    }

    public int getRealm_id() {
        return realm_id;
    }

    public boolean setName(String name) {
        // if name changes
        if (name == null || name.isEmpty()) return false;
        if (this.name == null || !this.name.equals(name)) {
            this.name = name;
            return true;
        }
        return false;
    }

    public boolean setTag(String tag) {
        // if changed
        if (tag == null || tag.isEmpty()) return false;
        if (this.tag == null || !this.tag.equals(tag)) {
            this.tag = tag;
            return true;
        }
        return false;
    }

    public boolean setDescription(String description) {
        // if changed
        if (description == null || description.isEmpty()) return false;
        if (this.description == null || !this.description.equals(description)) {
            this.description = description;
            return true;
        }
        return false;
    }

    public boolean setLevel(int level) {
        // if changed
        if (this.level != level) {
            this.level = level;
            return true;
        }
        return false;
    }

    public boolean setExperience(int experience) {
        // if changed
        if (this.experience != experience) {
            this.experience = experience;
            return true;
        }
        return false;
    }

    public boolean setLevel_total(int next) {
        // if changed
        if (this.level_total != next) {
            this.level_total = next;
            return true;
        }
        return false;
    }

    public String getDescription() {
        return description;
    }

    public long getLastFetched() {
        return last_fetched;
    }

    public long getLevel() {
        return level;
    }

    public long getExperience() {
        return experience;
    }

    public long getLevelTotal() {
        return level_total;
    }

    public void setUpdated(long epoch) {
        this.last_fetched = epoch;
    }

    public DBAlliance copy() {
        return new DBAlliance(alliance_id, realm_id, name, tag, description, level, experience, level_total, last_fetched);
    }

    @Override
    public boolean equals(Object obj) {
        // id matches
        if (obj instanceof DBAlliance) {
            return ((DBAlliance) obj).alliance_id == alliance_id;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return alliance_id;
    }
}
