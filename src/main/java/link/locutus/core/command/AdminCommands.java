package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.alliance.AllianceMetric;
import link.locutus.core.api.game.MilitaryUnit;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.KingdomLootType;
import link.locutus.core.db.entities.spells.DBSpy;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.util.PagePriority;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.StringMan;
import link.locutus.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AdminCommands {

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncMetrics() {
        AllianceMetric.update();
        return "Done!";
    }
    @Command(aliases = {"setloot"})
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setUnits(@Me IMessageIO channel, @Me Map<DBRealm, DBKingdom> me, DBKingdom nation, Map<MilitaryUnit, Long> units, int attack, int defense) {
        // int id, int attackerId, int attackerAa, int protectedGold, int gold, int attack, int defense, int soldiers, int cavalry, int archers, int elites, int defenderId, int defenderAa, long date
        DBSpy op = new DBSpy(
                0,
                0,
                0,
                0,
                units.getOrDefault(MilitaryUnit.GOLD, 0L).intValue(),
                attack,
                defense,
                units.getOrDefault(MilitaryUnit.SOLDIER, 0L).intValue(),
                units.getOrDefault(MilitaryUnit.CAVALRY, 0L).intValue(),
                units.getOrDefault(MilitaryUnit.ARCHER, 0L).intValue(),
                units.getOrDefault(MilitaryUnit.ELITE, 0L).intValue(),
                nation.getId(),
                nation.getAlliance_id(),
                System.currentTimeMillis()
        );
        Trocutus.imp().getDB().saveSpyOps(List.of(op), false);
        return "Set " + nation.getName() + " to " + StringMan.getString(units);
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String deleteAllInaccessibleChannels(@Switch("f") boolean force) {
        Map<GuildDB, List<GuildSetting>> toUnset = new LinkedHashMap<>();

        for (GuildDB db : Trocutus.imp().getGuildDatabases().values()) {
            if (force) {
                List<GuildSetting> keys = db.listInaccessibleChannelKeys();
                if (!keys.isEmpty()) {
                    toUnset.put(db, keys);
                }
            } else {
                db.unsetInaccessibleChannels();
            }
        }

        if (toUnset.isEmpty()) {
            return "No keys to unset";
        }
        StringBuilder response = new StringBuilder();
        for (Map.Entry<GuildDB, List<GuildSetting>> entry : toUnset.entrySet()) {
            response.append(entry.getKey().getGuild().toString() + ":\n");
            List<String> keys = entry.getValue().stream().map(f -> f.name()).collect(Collectors.toList());
            response.append("- " + StringMan.join(keys, "\n- "));
            response.append("\n");
        }
        String footer = "Rerun the command with `-f` to confirm";
        return response + footer;
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public void stop(boolean save) {
        Trocutus.imp().stop();
    }

    @RolePermission(value = Roles.ADMIN, root = true)
    @Command
    public String sync(int realm, String name) throws IOException {
        long start = System.currentTimeMillis();
        Trocutus.imp().getScraper().updateKingdom(PagePriority.FETCH_SELF, realm, name);
        return "Updated kingdom in " + (System.currentTimeMillis() - start) + "ms";
    }
    @RolePermission(value = Roles.ADMIN, root = true)
    @Command
    public String syncInteractions() throws IOException {
        long start = System.currentTimeMillis();
        for (DBRealm realm : Trocutus.imp().getDB().getRealms().values()) {
            Trocutus.imp().getScraper().updateAllianceInteractions(realm.getId());
        }
        return "Updated interactions in " + (System.currentTimeMillis() - start) + "ms";
    }

    @RolePermission(value = Roles.ADMIN, root = true)
    @Command
    public String syncAlliances() throws IOException {
        long start = System.currentTimeMillis();
        for (DBRealm realm : Trocutus.imp().getDB().getRealms().values()) {
            Trocutus.imp().getScraper().updateAlliances(realm.getId());
        }
        return "Updated alliances in " + (System.currentTimeMillis() - start) + "ms";
    }

    @RolePermission(value = Roles.ADMIN, root = true)
    @Command
    public String syncAllianceKingdoms(boolean updateMissing) throws IOException {
        long start = System.currentTimeMillis();
        for (DBRealm realm : Trocutus.imp().getDB().getRealms().values()) {
            for (DBKingdom kingdom : Trocutus.imp().getScraper().updateAllianceKingdoms(realm.getId())) {
                if  (updateMissing) {
                    Trocutus.imp().getScraper().updateKingdom(PagePriority.FETCH_SELF, kingdom.getRealm_id(), kingdom.getSlug());
                }
            }

        }
        return "Updated kingdoms in " + (System.currentTimeMillis() - start) + "ms";
    }

    @RolePermission(value = Roles.ADMIN, root = true)
    @Command
    public String syncAllKingdoms(boolean fetchNew, boolean fetchChanged, boolean fetchDeleted, boolean fetchMissingSlow) throws IOException {
        long start = System.currentTimeMillis();
        Trocutus.imp().getScraper().updateAllKingdoms(fetchNew, fetchChanged, fetchDeleted, fetchMissingSlow);

        return "Updated kingdoms in " + (System.currentTimeMillis() - start) + "ms";
    }


    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String dm(@Me User author, DBKingdom nation, String message) {
        User user = nation.getUser();
        if (user == null) return "No user found for " + nation.getName();

        user.openPrivateChannel().queue(new Consumer<PrivateChannel>() {
            @Override
            public void accept(PrivateChannel channel) {
                RateLimitUtil.queue(channel.sendMessage(author.getAsMention() + " said: " + message + "\n\n(no reply)"));
            }
        });
        return "Done!";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String leaveServer(long guildId) {
        GuildDB db = Trocutus.imp().getGuildDB(guildId);
        if (db == null) return "Server not found " + guildId;
        Guild guild = db.getGuild();
        RateLimitUtil.queue(guild.leave());
        return "Leaving " + guild.getName();
    }


    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listGuildOwners() {
        ArrayList<GuildDB> guilds = new ArrayList<>(Trocutus.imp().getGuildDatabases().values());
        guilds.sort(new Comparator<GuildDB>() {
            @Override
            public int compare(GuildDB o1, GuildDB o2) {
                return Long.compare(o1.getGuild().getIdLong(), o2.getGuild().getIdLong());
            }
        });
        StringBuilder result = new StringBuilder();
        for (GuildDB value : guilds) {
            Guild guild = value.getGuild();
            User owner = Trocutus.imp().getDiscordApi().getUserById(guild.getOwnerIdLong());
            result.append(guild.getIdLong() + " | " + guild.getName() + " | " + owner.getName()).append("\n");
        }
        return result.toString();
    }
}
