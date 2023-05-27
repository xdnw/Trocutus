package link.locutus.core.command;

import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import net.dv8tion.jda.api.entities.User;

import java.util.LinkedHashMap;
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

    @Command(desc = "Get an alert on discord when a target logs in within the next 5 days\n" +
            "Useful if you want to know when they might defeat you in war or perform an attack")
    public String loginNotifier(@Me User user, DBKingdom target, @Switch("w") boolean doNotRequireWar) {
        synchronized (target) {
            Map<Long, Long> existingMap = target.getLoginNotifyMap();
            if (existingMap == null) existingMap = new LinkedHashMap<>();
            existingMap.put(user.getIdLong(), System.currentTimeMillis());
            target.setLoginNotifyMap(existingMap);
        }
        return "You will be notified when " + target.getName() + " logs in (within the next 5d).";
    }

}
