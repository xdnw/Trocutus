package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.ScrapeKingdomUpdater;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.db.entities.DBRealm;
import link.locutus.core.db.guild.entities.Roles;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class AdminCommands {

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
