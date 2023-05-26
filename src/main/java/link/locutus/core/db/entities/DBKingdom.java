package link.locutus.core.db.entities;

import link.locutus.Trocutus;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.HeroType;
import link.locutus.core.api.game.ResourceLevel;
import link.locutus.util.MathMan;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.math.BigInteger;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DBKingdom {
    public static DBKingdom getFromUser(User user, DBRealm realm) {
        Map<DBRealm, DBKingdom> map = getFromUser(user);
        return map == null ? null : map.get(realm);
    }
    public static DBKingdom getFromUser(User user, int realm_id) {
        return getFromUser(user, DBRealm.getOrCreate(realm_id));
    }

    public static Map<DBRealm, DBKingdom> getFromUser(User user) {
        Set<DBKingdom> kingdoms = Trocutus.imp().getDB().getKingdomFromUser(user.getIdLong());
        Map<DBRealm, DBKingdom> byRealm = new LinkedHashMap<>();
        for (DBKingdom kingdom : kingdoms) {
            byRealm.put(kingdom.getRealm(), kingdom);
        }
        return byRealm;
    }

    public static DBKingdom parse(String input) {
        if (MathMan.isInteger(input)) {
            DBKingdom aa = Trocutus.imp().getDB().getKingdom(Integer.parseInt(input));
            if (aa != null) return aa;
        }
        if (input.contains("/")) {
            String[] split = input.split("/", 2);
            DBRealm realm = DBRealm.parse(split[0]);
            if (realm == null) return null;
            Set<DBKingdom> matching = Trocutus.imp().getDB().getKingdomsMatching(f -> f.realm_id == realm.getId() && f.getName().equalsIgnoreCase(split[1]));
            if (matching.size() >= 1) return matching.iterator().next();
        }
        Set<DBKingdom> matching = Trocutus.imp().getDB().getKingdomsMatching(f -> f.getName().equalsIgnoreCase(input));
        return matching.isEmpty() ? null : matching.iterator().next();

    }
    public static DBKingdom get(int id) {
        return Trocutus.imp().getDB().getKingdom(id);
    }

    public static Set<DBKingdom> parseList(Guild guild, String args) {
        Set<DBKingdom> result = new LinkedHashSet<>();
        for (String arg : StringMan.split(args, ',')) {
            if (arg.equalsIgnoreCase("*")) {
                result.addAll(Trocutus.imp().getDB().getKingdomsMatching(f -> true));
                continue;
            }
            DBKingdom kingdom = DBKingdom.parse(arg);
            if (kingdom == null) {
                throw new IllegalArgumentException("Could not find kingdom: `" + arg + "`");
            }
            result.add(kingdom);
        }
        return result;
    }

    private final int id;
    private int realm_id;
    private int alliance_id;
    private Rank position;
    private String name;
    private String slug;
    private int total_land;
    private int alert_level;
    private int resource_level;
    private int spell_alert;
    private long last_active;
    private long vacation_start;
    private HeroType hero;
    private int hero_level;
    private long last_fetched;

    public DBKingdom(int id, int realmId, int allianceId, Rank permission, String name, String slug, int totalLand, int alertLevel, int resourceLevel, int spellAlert, long lastActive, long vacationStart, HeroType hero, int heroLevel, long last_fetched) {
        this.id = id;
        this.realm_id = realmId;
        this.alliance_id = allianceId;
        this.position = permission;
        this.name = name;
        this.slug = slug;
        this.total_land = totalLand;
        this.alert_level = alertLevel;
        this.resource_level = resourceLevel;
        this.spell_alert = spellAlert;
        this.last_active = lastActive;
        this.vacation_start = vacationStart;
        this.hero = hero;
        this.hero_level = heroLevel;
        this.last_fetched = last_fetched;
    }

    public String getApiKey() {
        return Trocutus.imp().getDB().getApiKeyFromKingdom(id);
    }

    public int getId() {
        return id;
    }

    public int getAlliance_id() {
        return alliance_id;
    }

    public int getRealm_id() {
        return realm_id;
    }

    public DBRealm getRealm() {
        return DBRealm.getOrCreate(realm_id);
    }

    public String getAllianceName() {
        DBAlliance aa = getAlliance();
        if (aa == null) return "None";
        return aa.getName();
    }

    public String getName() {
        if (name == null || name.isEmpty()) {
            return getRealm().getName() + "/" + id + "";
        }
        return name;
    }

    public DBAlliance getAlliance() {
        return DBAlliance.getOrCreate(alliance_id, realm_id);
    }

    public Rank getPosition() {
        return position;
    }

    public User getUser() {
        Long user = Trocutus.imp().getDB().getUserIdFromKingdomId(id);
        if (user != null) {
            return Trocutus.imp().getDiscordApi().getUserById(user);
        }
        return null;
    }

    public HeroType getHero() {
        return hero;
    }

    public int getAlert_level() {
        return alert_level;
    }

    public int getHero_level() {
        return hero_level;
    }

    public ResourceLevel getResource_level() {
        return ResourceLevel.values[resource_level];
    }

    public int getSpell_alert() {
        return spell_alert;
    }

    public int getTotal_land() {
        return total_land;
    }

    public long getLast_active() {
        return last_active;
    }

    public long getLast_fetched() {
        return last_fetched;
    }

    public long getVacation_start() {
        return vacation_start;
    }

    public String getSlug() {
        return slug;
    }

    public String getQualifiedName() {
        return "kingdom:" + getRealm().getName() + "/" + getName();
    }

    public long active_m() {
        return TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - getLast_active());
    }

    public DBKingdom copy() {
        return new DBKingdom(id, realm_id, alliance_id, position, name, slug, total_land, alert_level, resource_level, spell_alert, last_active, vacation_start, hero, hero_level, last_fetched);
    }

    public void setUpdated(long epoch) {
        this.last_fetched = epoch;
    }

    public boolean setName(String name) {
        // return if changed
        if (name == null) return false;
        if (name.equals(this.name)) return false;
        this.name = name;
        return true;
    }

    public boolean setSlug(String slug) {
        // return if changed
        if (slug == null) return false;
        if (slug.equals(this.slug)) return false;
        this.slug = slug;
        return true;
    }

    public boolean setAlliance_id(int allianceId) {
        // return if changed
        if (allianceId == this.alliance_id) return false;
        this.alliance_id = allianceId;
        return true;
    }

    public boolean setAlliance_permissions(Rank rank) {
        // return if changed
        if (rank == this.position) return false;
        this.position = rank;
        return true;
    }

    public boolean setTotal_land(int totalLand) {
        // return if changed
        if (totalLand == this.total_land) return false;
        this.total_land = totalLand;
        return true;
    }

    public boolean setAlert_level(int alertLevel) {
        // return if changed
        if (alertLevel == this.alert_level) return false;
        this.alert_level = alertLevel;
        return true;
    }

    public boolean setResource_level(int resourceLevel) {
        // return if changed
        if (resourceLevel == this.resource_level) return false;
        this.resource_level = resourceLevel;
        return true;
    }

    public boolean setSpell_alert(int spellAlert) {
        // return if changed
        if (spellAlert == this.spell_alert) return false;
        this.spell_alert = spellAlert;
        return true;
    }

    public boolean setLast_active(Date lastActive) {
        // return if changed
        if (lastActive == null) return false;
        long last = lastActive.getTime();
        if (last == this.last_active) return false;
        this.last_active = last;
        return true;
    }

    public boolean setVacation_start(Date vacationStart) {
        // return if changed
        if (vacationStart == null) return false;
        long vacation = vacationStart.getTime();
        if (vacation == this.vacation_start) return false;
        this.vacation_start = vacation;
        return true;
    }

    public boolean setHeroType(HeroType heroType) {
        // return if changed
        if (heroType == this.hero) return false;
        this.hero = heroType;
        return true;
    }

    public boolean setHeroLevel(int level) {
        // return if changed
        if (level == this.hero_level) return false;
        this.hero_level = level;
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        // is DBKingdom and id matches
        if (obj instanceof DBKingdom) {
            DBKingdom other = (DBKingdom) obj;
            return other.id == this.id;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public String getUrl(String myName) {
        return "https://trounced.net/kingdom/" + myName + "/search/" + slug;
    }

    public DBSpy getLatestSpyReport() {
        return Trocutus.imp().getDB().getLatestSpy(id);
    }

    public DBAttack getLatestOffensive() {
        return Trocutus.imp().getDB().getLatestOffensive(id);
    }

    public DBAttack getLatestDefensive() {
        return Trocutus.imp().getDB().getLatestDefensive(id);
    }

    public void setRealm_id(int realmId) {
        this.realm_id = realmId;
    }
}
