package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.util.StringMan;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminCommands {
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
        Trocutus.imp().getScraper().updateKingdom(realm, name);
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
                    Trocutus.imp().getScraper().updateKingdom(kingdom.getRealm_id(), kingdom.getSlug());
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
}
