package link.locutus.core.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.api.game.HeroType;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.api.game.TreatyType;
import link.locutus.core.api.pojo.pages.AllianceDirectory;
import link.locutus.core.api.pojo.pages.AllianceMembers;
import link.locutus.core.api.pojo.pages.AlliancePage;
import link.locutus.core.api.pojo.pages.AttackInteraction;
import link.locutus.core.api.pojo.pages.Dashboard;
import link.locutus.core.api.pojo.pages.FireballInteraction;
import link.locutus.core.api.pojo.pages.SpyInteraction;
import link.locutus.core.db.TrouncedDB;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.spells.DBFireball;
import link.locutus.core.db.entities.war.DBAttack;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.core.db.entities.alliance.DBTreaty;
import link.locutus.util.AlertUtil;
import link.locutus.util.FileUtil;
import link.locutus.util.PagePriority;
import link.locutus.util.StringMan;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.tuple.Triple;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ScrapeKingdomUpdater {
    private final Auth rootAuth;
    private final ObjectMapper mapper;
    private final TrouncedDB db;

    private final Map<Integer, AllianceMembers.AllianceMember> allianceMembers = new ConcurrentHashMap<>();

    public ScrapeKingdomUpdater(Auth rootAuth, TrouncedDB db) {
        this.rootAuth = rootAuth;
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
        }, 60000, TimeUnit.MINUTES.toMillis(25), TimeUnit.MILLISECONDS);

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
        }, 60000, TimeUnit.MINUTES.toMillis(5), TimeUnit.MILLISECONDS);

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
        }, 60000, TimeUnit.MINUTES.toMillis(55), TimeUnit.MILLISECONDS);

        Trocutus.imp().getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (DBRealm realm : db.getRealms().values()) {
                        boolean noAuth = true;
                        for (DBAlliance alliance : realm.getAlliances()) {
                            Auth auth = alliance.getAuth(Rank.ADMIN);
                            if (auth == null) continue;
                            updateAllianceInteractions(auth, realm.getId());
                            noAuth = false;
                        }
                        if (noAuth) {
                            AlertUtil.error("No auth for realm " + realm.getId());
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, 60000, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS);

        Trocutus.imp().getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (DBRealm realm : db.getRealms().values()) {
                        boolean noAuth = true;
                        for (DBAlliance alliance : realm.getAlliances()) {
                            Auth auth = alliance.getAuth(Rank.ADMIN);
                            if (auth == null) continue;
                            fetchAllianceMemberStrength(auth, realm.getId());
                            noAuth = false;
                        }
                        if (noAuth) {
                            AlertUtil.error("No auth for realm " + realm.getId());
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, 80000, TimeUnit.MINUTES.toMillis(1), TimeUnit.MILLISECONDS);

        Trocutus.imp().getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (DBRealm realm : db.getRealms().values()) {
                        fetchAid(realm.getId(), MilitaryUnit.SOLDIERS);
                        fetchAid(realm.getId(), MilitaryUnit.GOLD);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, 60_000, TimeUnit.MINUTES.toMillis(5), TimeUnit.MILLISECONDS);

        Trocutus.imp().getScheduler().scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    for (DBRealm realm : db.getRealms().values()) {
                        int minScore = Integer.MAX_VALUE;
                        int maxScore = 0;
                        for (DBKingdom kingdom : Trocutus.imp().getDB().getKingdomsMatching(f -> f.getRealm_id() == realm.getId())) {
                            minScore = Math.min(minScore, kingdom.getScore());
                            maxScore = Math.max(maxScore, kingdom.getScore());
                        }
                        double multiplier = 1.1;
                        for (int land = minScore + 1; land <= maxScore; land = (int) (land * multiplier + 1)) {
                            fetchFey(PagePriority.FETCH_FEY_BG, realm.getId(), land);
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }, 60_000 * 60, TimeUnit.MINUTES.toMillis(120), TimeUnit.MILLISECONDS);
    }

    public AllianceMembers.AllianceMember getAllianceMemberStrength(int realm_id, int kingdom_id) {
        if (allianceMembers.isEmpty()) {
            try {
                fetchAllianceMemberStrength(rootAuth, realm_id);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return allianceMembers.get(kingdom_id);
    }

    private CompletableFuture<JSONObject> getJson(PagePriority priority, Auth auth, String url) throws IOException {
        return (auth.readStringFromURL(priority.ordinal(), url, Collections.emptyMap(), false)).thenApply(html -> {
            Document doc = Jsoup.parse(html);
            Element elem = doc.getElementById("app");
            if (elem == null) {
                System.out.println("DATA\n\n------------\n\n" + html + "\n\n---------------\n\n");
                return null;
            }
            String data = elem.attr("data-page");
            data = StringEscapeUtils.unescapeHtml4(data);
            data = data.replaceAll("\\/", "/");
            // todo paginate

            // parse to json
            return new JSONObject(data);
        });
    }

    public void updateAlliances(int realmId) throws IOException {
        // todo support for realms
        // todo support for pagination
        String url = "https://trounced.net/kingdom/" + rootAuth.getKingdom(realmId).getSlug() + "/alliance/directory";
        JSONObject json = FileUtil.get(getJson(PagePriority.UPDATE_ALLIANCES, rootAuth, url));

        JSONObject props = json.getJSONObject("props");
        // self
        JSONObject self = props.getJSONObject("kingdom");
        updateSelf(self);

        int realm_id = self.getInt("realm_id");


        JSONArray alliances = props.getJSONObject("alliances").getJSONArray("data");

        Map<Integer, List<AllianceDirectory.Pact>> pacts = new HashMap<>();
        Map<String, Integer> allianceIdByTag = new HashMap<>();

        Map<Integer, DBAlliance> original = new HashMap<>();
        List<DBAlliance> toSave = new ArrayList<>();
        Set<Integer> allIds = new HashSet<>();
        for (int i = 0; i < alliances.length(); i++) {
            JSONObject alliance = alliances.getJSONObject(i);
            // map to pojo
            AllianceDirectory.Alliance pojo = mapper.readValue(alliance.toString(), AllianceDirectory.Alliance.class);

            if (pojo.pacts != null && !pojo.pacts.isEmpty()) {
                pacts.put(pojo.id, pojo.pacts);
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

    public List<DBKingdom> fetchFey(PagePriority priority, int realm_id, int land) throws IOException {
        String url = "https://trounced.net/kingdom/" + rootAuth.getKingdom(realm_id).getSlug() + "/search?acreage=" + land + "&only_fey=true";
        JSONObject json = FileUtil.get(getJson(priority, rootAuth, url));

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
            DBKingdom existing = toKingdom(realm_id, kingdom, (id, copy) -> original.put(id, copy), updateFlag);
            if (updateFlag.get()) {
                if (existing.getHero() == null) {
                    existing.setHeroType(HeroType.FEY);
                }
                toSave.add(existing);
            }
        }

        if (!toSave.isEmpty()) {
            db.saveKingdoms(original, toSave, false);
        }
        return toSave;
    }

    private DBKingdom toKingdom(int realm, JSONObject kingdom, BiConsumer<Integer, DBKingdom> makeCopy, AtomicBoolean modifiedFlag) throws JsonProcessingException {
        AlliancePage.Kingdom pojo = mapper.readValue(kingdom.toString(), AlliancePage.Kingdom.class);
        if (pojo.realm_id == 0 && realm != -1) {
            pojo.realm_id = realm;
        }

        DBKingdom existing = db.getKingdom(pojo.id);

        boolean modified = false;
        if (existing == null) {
            existing = new DBKingdom(pojo.id, pojo.realm_id, pojo.alliance_id, Rank.NONE, null, null, 0, 0, 0, 0, 0, 0, null, 0, 0, pojo.is_fey == Boolean.TRUE);
            modified = true;
        } else {
            makeCopy.accept(existing.getId(), existing.copy());
        }
        {
            if (existing.getName().equalsIgnoreCase("vargstad")) {
                new Exception().printStackTrace();
            }
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
            String url = "https://trounced.net/kingdom/" + rootAuth.getKingdom(realmId).getSlug() + "/alliance/" + alliance.getTag();
            JSONObject json = FileUtil.get(getJson(PagePriority.UPDATE_AA_KINGDOMS, rootAuth, url));

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
                    if (existing.getName().equalsIgnoreCase("vargstad")) {
                        new Exception().printStackTrace();
                    }
                    toSave.add(existing);
                }
            }

            if (!toSave.isEmpty()) {
                db.saveKingdoms(original, toSave, false);
            }
        }

        return leftAlliancePossible;
    }

    public CompletableFuture<DBKingdom> updateKingdom(PagePriority priority, int realm_id, String name) throws IOException {
        System.out.println("Updating kingdom " + name);
        if (name.equalsIgnoreCase("vargstad")) {
            new Exception().printStackTrace();
        }
        String slug = name.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_ -]", "").trim().replaceAll(" ", "-");

        // remove non alphanumeric underscore
        String url = "https://trounced.net/kingdom/" + rootAuth.getKingdom(realm_id).getSlug() + "/search/" + slug;
        CompletableFuture<JSONObject> future = getJson(priority, rootAuth, url);
        return future.thenApply(new Function<JSONObject, DBKingdom>() {
            @Override
            public DBKingdom apply(JSONObject json) {
                if (json == null) {
                    Set<DBKingdom> kingdoms = db.getKingdomsMatching(f -> f.getRealm_id() == realm_id && f.getSlug().equalsIgnoreCase(slug));
                    if (kingdoms.size() > 0) {
                        System.out.println("Deleting " + slug);
                        db.deleteKingdoms(kingdoms);
                    } else {
                        System.out.println("Coud not find kingdom in db " + name + " | " + slug);
                    }
                    return null;
                } else {
                    System.out.println("Found " + name + " | " + slug);
                    JSONObject props = json.getJSONObject("props");
                    JSONObject self = props.getJSONObject("kingdom");
                    DBKingdom existing = null;
                    try {
                        updateSelf(self);

                        JSONObject target = props.getJSONObject("target");
                        AtomicBoolean updateFlag = new AtomicBoolean();
                        Map<Integer, DBKingdom> copyMap = new HashMap<>();
                        existing = toKingdom(realm_id, target, (id, copy) -> copyMap.put(id, copy), updateFlag);

                        if (updateFlag.get()) {
                            db.saveKingdoms(copyMap, List.of(existing), false);
                        }

                        if (name.equalsIgnoreCase("Natashja")) {
                            System.out.println("Target " + target + " | " + updateFlag.get());
                        }
                        // interactions
                        long minId = Long.MAX_VALUE;
                        JSONObject interactionsInfo = props.getJSONObject("interactions");
                        JSONArray interactions = interactionsInfo.getJSONArray("data");
                        Map<Integer, JSONObject> interactionMap = new LinkedHashMap<>();
                        for (int i = 0; i < interactions.length(); i++) {
                            JSONObject interaction = interactions.getJSONObject(i);
                            int id = interaction.getInt("id");
                            interactionMap.put(id, interaction);
                            minId = Math.min(minId, id);
                        }
                        updateInteractions(interactionMap);

                        Integer latest = db.getLatestInteractionKingdom(existing.getId());
                        if (latest == null || latest < minId) {
                            int last_page = interactionsInfo.getInt("last_page");
                            System.out.println("Update kingdom interactions " + existing.getName() + " | " + last_page);
                            if (last_page > 1) {
                                updateKingdomInteractions(existing, 2, latest);
                            }
                        }
                    } catch (JSONException | JsonProcessingException ignore) {
                        ignore.printStackTrace();
                    };
                    return existing;
                }
            }
        });

    }

    private Object updateKingdomInteractionsLock = new Object();

    public void updateKingdomInteractions(DBKingdom kingdom, int minPage, Integer latestIdOriginal) {
        DBAlliance alliance = kingdom.getAlliance();
        Auth auth = alliance == null ? rootAuth : alliance.getAuth(Rank.MEMBER);
        if (auth == null) auth = rootAuth;
        DBKingdom authKingdom = auth.getKingdom(kingdom.getRealm_id());;
        Auth finalAuth = auth;
        Trocutus.imp().getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                synchronized (updateKingdomInteractionsLock) {
                    try {
                        String url = kingdom.getUrl(authKingdom.getSlug()) + "?page=";
                        int page = minPage;

                        Integer latestId = db.getLatestInteractionKingdom(kingdom.getId());
                        if (latestIdOriginal != null && !Objects.equals(latestIdOriginal, latestId)) return;
                        if (latestId == null) latestId = 0;

                        Map<Integer, JSONObject> interactionsById = new LinkedHashMap<>();
                        int maxId = 0;

                        outer:
                        while (true) {
                            JSONObject json = FileUtil.get(getJson(PagePriority.KINGDOM_INTERACTIONS, finalAuth, url + page));
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
                            System.out.println("Page " + page + " | " + last_page);
                            if (last_page <= page) {
                                break;
                            }
                            page++;
                        }

                        // set latest
                        if (maxId != 0 && maxId > latestId) {
                            db.setLatestInteractionKingdom(kingdom.getId(), maxId);
                        }

                        updateInteractions(interactionsById);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void updateAllianceInteractions(Auth auth, int realmId) throws IOException {
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
            JSONObject json = FileUtil.get(getJson(PagePriority.ALLIANCE_INTERACTIONS, auth, url + page));
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
        List<DBFireball> fireballs = new ArrayList<>();
        for (Map.Entry<Integer, JSONObject> entry : interactionMap.entrySet()) {
            int id = entry.getKey();
            JSONObject interaction = entry.getValue();
            String type = interaction.getString("type").toLowerCase();
            switch (type) {
                case "attack" -> {
                    AttackInteraction.Attack pojo = mapper.readValue(interaction.toString(), AttackInteraction.Attack.class);
                    handleFey(pojo.defender.id, pojo.defender.name, pojo.defender.slug);
                    DBAttack attack = new DBAttack(pojo);
                    attacks.add(attack);
                }
                case "fireball" -> {
                    // mapper to
                    FireballInteraction.Fireball pojo = mapper.readValue(interaction.toString(), FireballInteraction.Fireball.class);
                    DBFireball spell = new DBFireball(pojo);
                    fireballs.add(spell);
                }
                case "spy" -> {
                    // mapper to
                    SpyInteraction.Spy pojo = mapper.readValue(interaction.toString(), SpyInteraction.Spy.class);
                    handleFey(pojo.defender.id, pojo.defender.name, pojo.defender.slug);
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
        Collections.sort(fireballs, Comparator.comparingInt(f -> f.id));
        Collections.sort(spies, Comparator.comparingInt(f -> f.id));

        db.saveSpyOps(new LinkedHashSet<>(spies));
        db.saveAttacks(new LinkedHashSet<>(attacks));
        db.saveUnknownInteractions(raw);
    }

    private void handleFey(int id, String name, String slug) {
        DBKingdom existing = DBKingdom.get(id);
        if (existing != null) return;
        if (!name.contains("(") || !name.contains(")")) return;
        String[] split = slug.split("-");
        // name-land-realm
        int realm = Integer.parseInt(split[split.length - 1]);
        int land = Integer.parseInt(split[split.length - 2]);
        DBKingdom fey = new DBKingdom(id, realm, 0, Rank.NONE, name, slug, land, 0, 4, 0, 0, 0, HeroType.FEY, 0, 0, true);
        Trocutus.imp().getDB().saveKingdoms(Collections.emptyMap(), List.of(fey), false);
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
                updateKingdom(PagePriority.FETCH_NEW, realm.getId(), entry.getValue());
            }

            for (Map.Entry<DBKingdom, String> entry : fetchChangedMap.entrySet()) {
                updateKingdom(PagePriority.FETCH_CHANGED, realm.getId(), entry.getValue());
            }

            for (DBKingdom kingdom : fetchDeletedSet) {
                updateKingdom(PagePriority.FETCH_DELETED, kingdom.getRealm_id(), kingdom.getSlug());
            }

            for (DBKingdom kingdom : fetchMissingSlowSet) {
                updateKingdom(PagePriority.FETCH_MISSING, kingdom.getRealm_id(), kingdom.getSlug());
            }
        }

        String result = "Updated kingdoms in " + (System.currentTimeMillis() - start) + "ms\n" +
                "New: " + fetchNewMap.size() + "\n" +
                "Changed: " + fetchChangedMap.size() + "\n" +
                "Deleted: " + fetchDeletedSet.size() + "\n" +
                "Missing (from leaderboard): " + fetchMissingSlowSet.size();
        return result;
    }

    public void fetchAid(int realmId, MilitaryUnit type) throws IOException {
        Map<Integer, Long> aid = new HashMap<>();

        String url = "https://trounced.net/leaderboard/" + realmId + "?primary=Kingdom&secondary=Support&tertiary=" + type;

        for (Triple<Integer, String, Long> entry : fetchLeaderboardValues(url)) {
            int id = entry.getLeft();
            String name = entry.getMiddle();
            long value = entry.getRight();
            aid.put(id, value);
        }
        Trocutus.imp().getDB().saveAidMap(type, aid, realmId);
    }

    public List<Triple<Integer, String, Long>> fetchLeaderboardValues(String url) throws IOException {
        JSONObject json = FileUtil.get(getJson(PagePriority.UPDATE_LEADERBOARD_KINGDOMS, rootAuth, url));
        JSONObject props = json.getJSONObject("props");
        JSONArray rankings = props.getJSONArray("rankings");
        // iterate

        List<Triple<Integer, String, Long>> result = new ArrayList<>();

        for (int i = 0; i < rankings.length(); i++) {
            JSONObject ranking = rankings.getJSONObject(i);

            int id = ranking.getInt("id");
            String name = ranking.getString("name");
            long value = ranking.getLong("value");

            result.add(Triple.of(id, name, (long) value));
        }
        return result;

    }

    public void updateLeaderboardKingdoms(int realmId, BiConsumer<Integer, String> createdFlag, BiConsumer<DBKingdom, String> updatedFlag, Consumer<DBKingdom> checkDeleteFlag, Consumer<DBKingdom> checkUnlistedFlag) throws IOException {
        // only update nations that have had their acreage change


        Set<Integer> allKingdoms = new HashSet<>();

        Map<HeroType, Long> lowestAcreageByType = new HashMap<>();
        Set<DBKingdom> toSave = new HashSet<>();

        Map<Integer, DBKingdom> original = new HashMap<>();

        for (HeroType heroType : HeroType.values) {
            // first letter uppercase, rest lower
            String heroName = heroType.name().substring(0, 1).toUpperCase() + heroType.name().substring(1).toLowerCase();
            String url = "https://trounced.net/leaderboard/" + realmId + "?primary=Kingdom&secondary=Acreage&tertiary=" + heroName;

            List<Triple<Integer, String, Long>> values = fetchLeaderboardValues(url);
            for (Triple<Integer, String, Long> entry : values) {
                int id = entry.getLeft();
                String name = entry.getMiddle();
                long value = entry.getRight();

                String allianceTag = null;
                if (name.contains("[")) {
                    allianceTag = name.substring(name.indexOf("[") + 1, name.indexOf("]"));
                    name = name.substring(name.indexOf("]") + 1).trim();
                }

                allKingdoms.add(id);
                lowestAcreageByType.put(heroType, Math.min(value, lowestAcreageByType.getOrDefault(heroType, Long.MAX_VALUE)));

                DBKingdom kingdom = db.getKingdom(id);
                if (kingdom == null) {
                    // new kingdom
                    System.out.println("New kingdom " + id + " | " + name);
                    createdFlag.accept(id, name);
                } else {
                    if (kingdom.getTotal_land() != value) {
                        original.put(id, kingdom.copy());

                        kingdom.setTotal_land((int) value);
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
            Long acreage = lowestAcreageByType.get(kingdom.getHero());
            if (acreage == null) continue;
            if (acreage < kingdom.getTotal_land()) {
                checkDeleteFlag.accept(kingdom);
            } else {
                checkUnlistedFlag.accept(kingdom);
            }
        }
    }

    private void updatePacts(int realmId, Map<Integer, List<AllianceDirectory.Pact>> pacts, Map<String, Integer> allianceIdByTag) {
        Map<Integer, Map<Integer, DBTreaty>> treaties = new ConcurrentHashMap<>();
        for (Map.Entry<Integer, List<AllianceDirectory.Pact>> entry : pacts.entrySet()) {
            int alliance_id = entry.getKey();
            for (AllianceDirectory.Pact pact : entry.getValue()) {
                int to = allianceIdByTag.getOrDefault(pact.other, -1);
                if (alliance_id == -1 || to == -1) {
                    continue;
                }
                TreatyType type = TreatyType.valueOf(pact.type.toUpperCase());
                DBTreaty treaty = new DBTreaty(type, alliance_id, to, realmId);
                treaties.computeIfAbsent(alliance_id, k -> new ConcurrentHashMap<>()).put(to, treaty);
                treaties.computeIfAbsent(to, k -> new ConcurrentHashMap<>()).put(alliance_id, treaty);
            }
        }
        db.updateTreaties(realmId, treaties);
    }

    public Set<DBKingdom> updateSelf(Auth auth) throws IOException {
        Set<DBKingdom> result = new HashSet<>();

        String url = "https://trounced.net/dashboard";
        JSONObject json = FileUtil.get(getJson(PagePriority.FETCH_SELF, auth, url));

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

    public Map<Integer, AllianceMembers.AllianceMember> fetchAllianceMemberStrength(Auth auth, int realmId) throws IOException {
        return fetchAllianceMemberStrength(PagePriority.ALLIANCE_STRENGTH, auth, realmId);
    }

    public Map<Integer, AllianceMembers.AllianceMember> fetchAllianceMemberStrength(PagePriority priority, Auth auth, int realmId) throws IOException {
        String url = "https://trounced.net/kingdom/" + auth.getKingdom(realmId).getSlug() + "/alliance/members";
        JSONObject json = FileUtil.get(getJson(priority, auth, url));

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
                if (existing.getName().equalsIgnoreCase("vargstad")) {
                    new Exception().printStackTrace();
                }

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
