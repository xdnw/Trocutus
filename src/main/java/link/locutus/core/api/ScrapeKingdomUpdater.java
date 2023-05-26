package link.locutus.core.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.Trocutus;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.HeroType;
import link.locutus.core.api.game.TreatyType;
import link.locutus.core.api.pojo.pages.AllianceDirectory;
import link.locutus.core.api.pojo.pages.AllianceMembers;
import link.locutus.core.api.pojo.pages.AlliancePage;
import link.locutus.core.api.pojo.pages.AttackInteraction;
import link.locutus.core.api.pojo.pages.Dashboard;
import link.locutus.core.api.pojo.pages.SpyInteraction;
import link.locutus.core.db.TrouncedDB;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.war.DBAttack;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.core.db.entities.alliance.DBTreaty;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ScrapeKingdomUpdater {
    private final Auth auth;
    private final ObjectMapper mapper;
    private final TrouncedDB db;

    private final Map<Integer, AllianceMembers.AllianceMember> allianceMembers = new ConcurrentHashMap<>();

    public ScrapeKingdomUpdater(Auth auth, TrouncedDB db) {
        this.auth = auth;
        this.mapper = new ObjectMapper();
        this.db = db;

        // update alliances
        Trocutus.imp().getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (DBRealm realm : db.getRealms().values()) {
                        updateAlliances(realm.getId());
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, 60000, 1500000, TimeUnit.MILLISECONDS);

        // update nations
        Trocutus.imp().getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (DBRealm realm : db.getRealms().values()) {
                        updateAllianceKingdoms(realm.getId());
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, 60000, 300000, TimeUnit.MILLISECONDS);

        // update attacks

        Trocutus.imp().getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    updateAllKingdoms(true, true, true, true);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, 60000, 3300000, TimeUnit.MILLISECONDS);

        Trocutus.imp().getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (DBRealm realm : db.getRealms().values()) {
                        updateAllianceInteractions(realm.getId());
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, 60000, 60000, TimeUnit.MILLISECONDS);

        Trocutus.imp().getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (DBRealm realm : db.getRealms().values()) {
                        fetchAllianceMemberStrength(realm.getId());
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, 80000, 65000, TimeUnit.MILLISECONDS);
    }

    public AllianceMembers.AllianceMember getAllianceMemberStrength(int realm_id, int kingdom_id) throws IOException {
        if (allianceMembers.isEmpty()) {
            fetchAllianceMemberStrength(realm_id);
        }
        return allianceMembers.get(kingdom_id);
    }

    private JSONObject getJson(String url) throws IOException {
        String html = auth.readStringFromURL(url, Collections.emptyMap(), false);
        Document doc = Jsoup.parse(html);
        Element elem = doc.getElementById("app");
        if (elem == null) return null;
        String data = elem.attr("data-page");
        System.out.println("Data " + data);
        data = StringEscapeUtils.unescapeHtml4(data);
        data = data.replaceAll("\\/", "/");
        // todo paginate

        // parse to json
        return new JSONObject(data);
    }

    public void updateAlliances(int realmId) throws IOException {
        // todo support for realms
        // todo support for pagination

        String url = "https://trounced.net/kingdom/" + auth.getKingdom(realmId).getSlug() + "/alliance/directory";
        JSONObject json = getJson(url);

        JSONObject props = json.getJSONObject("props");
        // self
        JSONObject self = props.getJSONObject("kingdom");
        updateSelf(self);

        int realm_id = self.getInt("realm_id");


        JSONArray alliances = props.getJSONObject("alliances").getJSONArray("data");

        List<AllianceDirectory.Pact> pacts = new ArrayList<>();
        Map<String, Integer> allianceIdByTag = new HashMap<>();

        Map<Integer, DBAlliance> original = new HashMap<>();
        List<DBAlliance> toSave = new ArrayList<>();
        Set<Integer> allIds = new HashSet<>();
        for (int i = 0; i < alliances.length(); i++) {
            JSONObject alliance = alliances.getJSONObject(i);
            // map to pojo
            AllianceDirectory.Alliance pojo = mapper.readValue(alliance.toString(), AllianceDirectory.Alliance.class);

            if (pojo.pacts != null && !pojo.pacts.isEmpty()) {
                pacts.addAll(pojo.pacts);
            }
            allianceIdByTag.put(pojo.tag, pojo.id);
            allIds.add(pojo.id);

            DBAlliance existing = db.getAlliance(pojo.id);
            boolean modified = false;
            if (existing == null) {
                existing = new DBAlliance(pojo.id, realm_id, null, null, "", 0, 0, 0, 0);
                modified = true;
            } else {
                original.put(existing.getId(), existing.copy());
            }
            {
                modified |= existing.setName(pojo.name);
                modified |= existing.setTag(pojo.tag);
                modified |= existing.setDescription(pojo.description);
                modified |= existing.setLevel(pojo.breakdown.level);
                modified |= existing.setExperience(pojo.experience);
                modified |= existing.setLevel_total(pojo.breakdown.next);
                existing.setUpdated(System.currentTimeMillis());
            }

            System.out.println("Modified " + modified + " | " + existing.getId() + " | " + existing.getName());
            if (modified) {
                toSave.add(existing);
            }
        }
        if (!toSave.isEmpty()) {
            db.saveAlliances(realm_id, allIds, original, toSave, true);
        }

        updatePacts(realm_id, pacts, allianceIdByTag);
    }

    private DBKingdom toKingdom(int realm, JSONObject kingdom, BiConsumer<Integer, DBKingdom> makeCopy, AtomicBoolean modifiedFlag) throws JsonProcessingException {
        AlliancePage.Kingdom pojo = mapper.readValue(kingdom.toString(), AlliancePage.Kingdom.class);
        if (pojo.realm_id == 0 && realm != -1) {
            pojo.realm_id = realm;
        }

        DBKingdom existing = db.getKingdom(pojo.id);

        boolean modified = false;
        if (existing == null) {
            existing = new DBKingdom(pojo.id, pojo.realm_id, pojo.alliance_id, Rank.NONE, null, null, 0, 0, 0, 0, 0, 0, null, 0, 0);
            modified = true;
        } else {
            makeCopy.accept(existing.getId(), existing.copy());
        }
        {
            System.out.println("updating kingdom " + pojo.name + " | " + kingdom);
            if (existing.getRealm_id() != pojo.realm_id && pojo.realm_id != 0) {
                existing.setRealm_id(pojo.realm_id);
                db.saveKingdoms(List.of(existing));
            }
            modified |= existing.setName(pojo.name);
            modified |= existing.setSlug(pojo.slug);
            modified |= existing.setAlliance_id(pojo.alliance_id);
            if (pojo.alliance_permissions != null) {
                Rank rank = Rank.parse(pojo.alliance_permissions);
                modified |= existing.setAlliance_permissions(rank);
            }
            if (pojo.total_land != null && !pojo.total_land.isEmpty()) {
                modified |= existing.setTotal_land(Integer.parseInt(pojo.total_land));
            }
            modified |= existing.setAlert_level(pojo.alert_level);
            modified |= existing.setResource_level(pojo.resource_level);
            modified |= existing.setSpell_alert(pojo.spell_alert);
            modified |= existing.setLast_active(pojo.last_active);
            modified |= existing.setVacation_start(pojo.vacation_start);
            if (pojo.hero != null) {
                modified |= existing.setHeroType(HeroType.valueOf(pojo.hero.name.toUpperCase()));
                modified |= existing.setHeroLevel(pojo.hero.level);
            }
            existing.setUpdated(System.currentTimeMillis());
        }
        if (modified) {
            modifiedFlag.set(true);
        }
        return existing;
    }

    /**
     * Update the self kingdom
     * @return a list of kingdoms that are in alliances but were not updated
     * @throws IOException
     */
    public Set<DBKingdom> updateAllianceKingdoms(int realmId) throws IOException {
        Set<DBKingdom> leftAlliancePossible = db.getKingdomsMatching(f -> f.getAlliance_id() != 0 && f.getRealm_id() == realmId);

        for (DBAlliance alliance : db.getAlliances()) {
            if (alliance.getRealm_id() != realmId) continue;
            String url = "https://trounced.net/kingdom/" + auth.getKingdom(realmId).getSlug() + "/alliance/" + alliance.getTag();
            JSONObject json = getJson(url);

            JSONObject props = json.getJSONObject("props");
            // self
            JSONObject self = props.getJSONObject("kingdom");
            updateSelf(self);

            // kingdoms
            JSONArray kingdoms = props.getJSONArray("kingdoms");

            Map<Integer, DBKingdom> original = new HashMap<>();

            List<DBKingdom> toSave = new ArrayList<>();
            for (int i = 0; i < kingdoms.length(); i++) {
                JSONObject kingdom = kingdoms.getJSONObject(i);
                AtomicBoolean updateFlag = new AtomicBoolean();
                DBKingdom existing = toKingdom(realmId, kingdom, (id, copy) -> original.put(id, copy), updateFlag);
                leftAlliancePossible.remove(existing);
                if (updateFlag.get()) {
                    toSave.add(existing);
                }
            }

            if (!toSave.isEmpty()) {
                db.saveKingdoms(original, toSave, false);
            }
        }

        return leftAlliancePossible;
    }

    public void updateKingdom(int realm_id, String name) throws IOException {
        String url = "https://trounced.net/kingdom/" + auth.getKingdom(realm_id).getSlug() + "/search/" + name;
        JSONObject json = getJson(url);
        if (json == null) {
            Set<DBKingdom> kingdoms = db.getKingdomsMatching(f -> f.getRealm_id() == realm_id && f.getSlug().equalsIgnoreCase(name));
            if (kingdoms.size() > 0) {
                db.deleteKingdoms(kingdoms);
            }
        } else {
            JSONObject props = json.getJSONObject("props");
            JSONObject self = props.getJSONObject("kingdom");
            updateSelf(self);

            JSONObject target = props.getJSONObject("target");
            AtomicBoolean updateFlag = new AtomicBoolean();
            Map<Integer, DBKingdom> copyMap = new HashMap<>();
            DBKingdom existing = toKingdom(realm_id, target, (id, copy) -> copyMap.put(id, copy), updateFlag);

            if (updateFlag.get()) {
                db.saveKingdoms(copyMap, List.of(existing), false);
            }

            try {
                // interactions
                JSONArray interactions = props.getJSONArray("interactions");
                Map<Integer, JSONObject> interactionMap = new LinkedHashMap<>();
                for (int i = 0; i < interactions.length(); i++) {
                    JSONObject interaction = interactions.getJSONObject(i);
                    int id = interaction.getInt("id");
                    interactionMap.put(id, interaction);
                }
                updateInteractions(interactionMap);
            } catch (JSONException ignore) {
                ignore.printStackTrace();
            };
        }
    }

    public void updateAllianceInteractions(int realmId) throws IOException {
        DBKingdom kingdom = auth.getKingdom(realmId);
        String url = "https://trounced.net/kingdom/" + kingdom.getSlug() + "/alliance?page=";
        int page = 1;

        Integer latestId = db.getLatestInteractionAlliance(kingdom.getAlliance_id());
        if (latestId == null) latestId = 0;

        Map<Integer, JSONObject> interactionsById = new LinkedHashMap<>();
        int maxId = 0;

        outer:
        while (true) {
            System.out.println("Page " + page);
            JSONObject json = getJson(url + page);
            JSONObject props = json.getJSONObject("props");
            JSONObject interactionsInfo = props.getJSONObject("interactions");
            JSONArray interactions = interactionsInfo.getJSONArray("data");
            for (int i = 0; i < interactions.length(); i++) {
                JSONObject interaction = interactions.getJSONObject(i);
                int id = interaction.getInt("id");
                maxId = Math.max(id, maxId);
                if (id <= latestId) break outer;
                interactionsById.put(id, interaction);
            }
            int last_page = interactionsInfo.getInt("last_page");
            if (last_page <= page) {
                break;
            }
            page++;
        }

        // set latest
        if (maxId != 0 && maxId > latestId) {
            db.setLatestInteractionAlliance(kingdom.getAlliance_id(), maxId);
        }

        updateInteractions(interactionsById);
    }

    public void updateInteractions(Map<Integer, JSONObject> interactionMap) throws JsonProcessingException {
        Map<Integer, String> raw = new LinkedHashMap<>();
        List<DBAttack> attacks = new ArrayList<>();
        List<DBSpy> spies = new ArrayList<>();
        for (Map.Entry<Integer, JSONObject> entry : interactionMap.entrySet()) {
            int id = entry.getKey();
            JSONObject interaction = entry.getValue();
            String type = interaction.getString("type").toLowerCase();
            switch (type) {
                case "attack" -> {
                    AttackInteraction.Attack pojo = mapper.readValue(interaction.toString(), AttackInteraction.Attack.class);
                    DBAttack attack = new DBAttack(pojo);
                    attacks.add(attack);
                }
                case "spy" -> {
                    // mapper to
                    SpyInteraction.Spy pojo = mapper.readValue(interaction.toString(), SpyInteraction.Spy.class);
                    DBSpy spy = new DBSpy(pojo);
                    spies.add(spy);
                }
                default -> {
                    raw.put(id, interaction.toString());
                }
            }
        }
        // sort by id
        Collections.sort(attacks, Comparator.comparingInt(f -> f.id));
        Collections.sort(spies, Comparator.comparingInt(f -> f.id));

        db.saveSpyOps(new LinkedHashSet<>(spies));
        db.saveAttacks(new LinkedHashSet<>(attacks));
        db.saveUnknownInteractions(raw);
    }

    public String updateAllKingdoms(boolean fetchNew, boolean fetchChanged, boolean fetchDeleted, boolean fetchMissingSlow) throws IOException {
        Map<Integer, String> fetchNewMap = new HashMap<>();
        Map<DBKingdom, String> fetchChangedMap = new HashMap<>();
        Set<DBKingdom> fetchDeletedSet = new HashSet<>();
        Set<DBKingdom> fetchMissingSlowSet = new HashSet<>();

        long start = System.currentTimeMillis();
        for (DBRealm realm : Trocutus.imp().getDB().getRealms().values()) {
            updateLeaderboardKingdoms(realm.getId(), new BiConsumer<Integer, String>() {
                @Override
                public void accept(Integer id, String name) {
                    if (fetchNew) {
                        fetchNewMap.put(id, name);
                    }
                }
            }, new BiConsumer<DBKingdom, String>() {
                @Override
                public void accept(DBKingdom kingdom, String name) {
                    if (fetchChanged) {
                        fetchChangedMap.put(kingdom, name);
                    }
                }
            }, new Consumer<DBKingdom>() {
                @Override
                public void accept(DBKingdom kingdom) {
                    if (fetchDeleted) {
                        fetchDeletedSet.add(kingdom);
                    }
                }
            }, new Consumer<DBKingdom>() {
                @Override
                public void accept(DBKingdom kingdom) {
                    if (fetchMissingSlow) {
                        fetchMissingSlowSet.add(kingdom);
                    }
                }
            });

            for (Map.Entry<Integer, String> entry : fetchNewMap.entrySet()) {
                updateKingdom(realm.getId(), entry.getValue());
            }

            for (Map.Entry<DBKingdom, String> entry : fetchChangedMap.entrySet()) {
                updateKingdom(realm.getId(), entry.getValue());
            }

            for (DBKingdom kingdom : fetchDeletedSet) {
                updateKingdom(kingdom.getRealm_id(), kingdom.getSlug());
            }

            for (DBKingdom kingdom : fetchMissingSlowSet) {
                updateKingdom(kingdom.getRealm_id(), kingdom.getSlug());
            }
        }

        return "Updated kingdoms in " + (System.currentTimeMillis() - start) + "ms\n" +
                "New: " + fetchNewMap.size() + "\n" +
                "Changed: " + fetchChangedMap.size() + "\n" +
                "Deleted: " + fetchDeletedSet.size() + "\n" +
                "Missing (from leaderboard): " + fetchMissingSlowSet.size();
    }

    public void updateLeaderboardKingdoms(int realmId, BiConsumer<Integer, String> createdFlag, BiConsumer<DBKingdom, String> updatedFlag, Consumer<DBKingdom> checkDeleteFlag, Consumer<DBKingdom> checkUnlistedFlag) throws IOException {
        // only update nations that have had their acreage change

        // https://trounced.net/leaderboard/7?primary=Kingdom&secondary=Acreage&tertiary=HERO_NAME

        Set<Integer> allKingdoms = new HashSet<>();

        Map<HeroType, Integer> lowestAcreageByType = new HashMap<>();
        Set<DBKingdom> toSave = new HashSet<>();

        Map<Integer, DBKingdom> original = new HashMap<>();

        for (HeroType heroType : HeroType.values) {
            // first letter uppercase, rest lower
            String heroName = heroType.name().substring(0, 1).toUpperCase() + heroType.name().substring(1).toLowerCase();
            String url = "https://trounced.net/leaderboard/" + realmId + "?primary=Kingdom&secondary=Acreage&tertiary=" + heroName;
            JSONObject json = getJson(url);
            JSONObject props = json.getJSONObject("props");

//            String realmName = props.getJSONObject("realm").getString("name");

            // props.rankings
            JSONArray rankings = props.getJSONArray("rankings");
            // iterate
            for (int i = 0; i < rankings.length(); i++) {
                JSONObject ranking = rankings.getJSONObject(i);

                int id = ranking.getInt("id");
                String name = ranking.getString("name");
                int value = ranking.getInt("value");

                String allianceTag = null;
                if (name.contains("[")) {
                    allianceTag = name.substring(name.indexOf("[") + 1, name.indexOf("]"));
                    name = name.substring(name.indexOf("]") + 1).trim();
                }

                allKingdoms.add(id);
                lowestAcreageByType.put(heroType, Math.min(value, lowestAcreageByType.getOrDefault(heroType, Integer.MAX_VALUE)));

                DBKingdom kingdom = db.getKingdom(id);
                if (kingdom == null) {
                    // new kingdom
                    createdFlag.accept(id, name);
                } else {
                    if (kingdom.getTotal_land() != value) {
                        original.put(id, kingdom.copy());

                        kingdom.setTotal_land(value);
                        toSave.add(kingdom);
                        updatedFlag.accept(kingdom, name);
                    }
                    if (kingdom.getHero() != heroType) {
                        if (!original.containsKey(id)) original.put(id, kingdom.copy());

                        kingdom.setHeroType(heroType);
                        toSave.add(kingdom);
                        updatedFlag.accept(kingdom, name);
                    }

                    if (allianceTag != null) {
                        DBAlliance aa = kingdom.getAlliance();
                        if (aa != null && aa.getTag() != null && !aa.getTag().isEmpty() && !aa.getTag().equals(allianceTag)) {
                            String allianceTagFinal = allianceTag;
                            Set<DBAlliance> aaByTag = db.getAllianceMatching(f -> f.getRealm_id() == realmId && allianceTagFinal.equalsIgnoreCase(f.getTag()));
                            if (aaByTag != null && aaByTag.size() == 1) {
                                DBAlliance aaOther = aaByTag.iterator().next();
                                if (aaOther.getId() != kingdom.getAlliance_id()) {
                                    kingdom.setAlliance_id(aaOther.getId());
                                    toSave.add(kingdom);
                                    updatedFlag.accept(kingdom, name);
                                }
                            }
                        }
                    } else if (allianceTag == null && kingdom.getAlliance_id() != 0) {
                        // left alliance
                        if (!original.containsKey(id)) original.put(id, kingdom.copy());

                        kingdom.setAlliance_id(0);
                        toSave.add(kingdom);
                        updatedFlag.accept(kingdom, name);
                    }
                }
            }
        }

        db.saveKingdoms(original, new ArrayList<>(toSave), false);


        for (DBKingdom kingdom : db.getKingdomsMatching(f -> f.getRealm_id() == realmId)) {
            if (allKingdoms.contains(kingdom.getId())) {
                continue;
            }
            Integer acreage = lowestAcreageByType.get(kingdom.getHero());
            if (acreage == null) continue;
            if (acreage < kingdom.getTotal_land()) {
                checkDeleteFlag.accept(kingdom);
            } else {
                checkUnlistedFlag.accept(kingdom);
            }
        }
    }

    private void updatePacts(int realmId, List<AllianceDirectory.Pact> pacts, Map<String, Integer> allianceIdByTag) {
        Map<Integer, Map<Integer, DBTreaty>> treaties = new ConcurrentHashMap<>();
        for (AllianceDirectory.Pact pact : pacts) {
            int from = allianceIdByTag.getOrDefault(pact.other, -1);
            int to = allianceIdByTag.getOrDefault(pact.other, -1);
            if (from == -1 || to == -1) {
                continue;
            }
            TreatyType type = TreatyType.valueOf(pact.type.toUpperCase());
            DBTreaty treaty = new DBTreaty(type, from, to, realmId);
            treaties.computeIfAbsent(from, k -> new ConcurrentHashMap<>()).put(to, treaty);
            treaties.computeIfAbsent(to, k -> new ConcurrentHashMap<>()).put(from, treaty);
        }
        db.updateTreaties(realmId, treaties);
    }

    public Set<DBKingdom> updateSelf() throws IOException {
        Set<DBKingdom> result = new HashSet<>();

        String url = "https://trounced.net/dashboard";
        JSONObject json = getJson(url);

        JSONObject props = json.getJSONObject("props");

        JSONArray realms = props.getJSONArray("realms");
        Map<Integer, DBRealm> allRealms = new HashMap<>(db.getRealms());
        // iterate
        for (int i = 0; i < realms.length(); i++) {
            JSONObject realmJson = realms.getJSONObject(i);
            int id = realmJson.getInt("id");
            String name = realmJson.getString("name");
            DBRealm realm = new DBRealm(id, name);
            db.saveRealm(realm);
            allRealms.remove(id);
        }

        if (!allRealms.isEmpty() && realms.length() > 0) {
            // deleted realms
            for (DBRealm realm : allRealms.values()) {
                db.deleteRealm(realm);
            }
        }

        JSONArray kingdoms = props.getJSONArray("kingdoms");
        // iterate
        for (int i = 0; i < kingdoms.length(); i++) {
            JSONObject kingdomJson = kingdoms.getJSONObject(i);
            DBKingdom kingdom = updateSelf(kingdomJson);
            if (kingdom != null) {
                result.add(kingdom);
            }
        }
        return result;
    }

    public DBKingdom updateSelf(JSONObject self) throws JsonProcessingException {
        AtomicBoolean updateFlag = new AtomicBoolean();
        Map<Integer, DBKingdom> copyMap = new HashMap<>();
        DBKingdom existing = toKingdom(-1, self, (id, copy) -> copyMap.put(id, copy), updateFlag);

        if (updateFlag.get()) {
            db.saveKingdoms(copyMap, List.of(existing), false);
        }

        // map to Dashboard.Kingdom with mapper
        Dashboard.Kingdom kingdom = mapper.readValue(self.toString(), Dashboard.Kingdom.class);
        // todo extra?

        return existing;
    }

    public Map<Integer, AllianceMembers.AllianceMember> fetchAllianceMemberStrength(int realmId) throws IOException {
        String url = "https://trounced.net/kingdom/" + auth.getKingdom(realmId).getSlug() + "/alliance/members";
        JSONObject json = getJson(url);

        // props
        JSONObject props = json.getJSONObject("props");
        // kingdoms

        JSONArray membersJson = props.getJSONArray("kingdoms");
        for (int i = 0; i < membersJson.length(); i++) {
            JSONObject memberJson = membersJson.getJSONObject(i);
            {
                AtomicBoolean updateFlag = new AtomicBoolean();
                Map<Integer, DBKingdom> copyMap = new HashMap<>();
                DBKingdom existing = toKingdom(-1, memberJson, (id, copy) -> copyMap.put(id, copy), updateFlag);

                if (updateFlag.get()) {
                    db.saveKingdoms(copyMap, List.of(existing), false);
                }
            }

            AllianceMembers.AllianceMember member = mapper.readValue(memberJson.toString(), AllianceMembers.AllianceMember.class);
            allianceMembers.put(member.id, member);

        }
        return allianceMembers;
    }
}
