package link.locutus.core.command;

import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;

import java.util.Map;

public class PlayerSettingCommands {
    @Command(desc = "Mark an announcement by the bot as read/unread")
    @RolePermission(Roles.MEMBER)
    public String readAnnouncement(@Me GuildDB db, @Me Map<DBRealm, DBKingdom> kingdoms, DBRealm realm, int ann_id, @Default Boolean markRead) {
        DBKingdom kingdom = kingdoms.get(realm);
        if (kingdom == null) return "You have no kingdom in " + realm.getName();
        if (markRead == null) markRead = true;
        db.setAnnouncementActive(ann_id, kingdom.getId(), !markRead);
        return "Marked announcement #" + ann_id + " as " + (markRead ? "" : "un") + " read";
    }

}
