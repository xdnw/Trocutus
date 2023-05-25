package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.TextArea;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.db.entities.DBKingdom;
import link.locutus.core.db.entities.DBRealm;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.settings.Settings;
import link.locutus.util.MarkupUtil;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BasicCommands {
    @Command
    public String me(@Me Map<DBRealm, DBKingdom> me, @Me IMessageIO io) {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<DBRealm, DBKingdom> entry : me.entrySet()) {
            DBKingdom kingdom = entry.getValue();
            DBRealm realm = entry.getKey();
            result.append("# **" + realm.getName() + "**\n");
            result.append(kingdom.getName() + " | " + kingdom.getAllianceName() + "\n");

            //    private final int id;
            //    private final int realm_id;
            //    private int alliance_id;
            //    private Rank position;
            //    private String name;
            //    private String slug;
            //    private int total_land;
            //    private int alert_level;
            //    private int resource_level;
            //    private int spell_alert;
            //    private long last_active;
            //    private long vacation_start;
            //    private HeroType hero;
            //    private int hero_level;
            //    private long last_fetched;

            result.append("position: `" + kingdom.getPosition() + "`\n");
            result.append("total land: `" + kingdom.getTotal_land() + "`\n");
            result.append("alert level: `" + kingdom.getAlert_level() + "`\n");
            result.append("resource level: `" + kingdom.getResource_level() + "`\n");
            result.append("spell alert: `" + kingdom.getSpell_alert() + "`\n");
            result.append("last active: " + TimeUtil.getDiscordTimestamp(kingdom.getLast_active()) + "\n");
            result.append("vacation start: " + TimeUtil.getDiscordTimestamp(kingdom.getVacation_start()) + "\n");
            result.append("hero: `" + kingdom.getHero() + "`\n");
            result.append("hero level: `" + kingdom.getHero_level() + "`\n");
            result.append("last fetched: " + TimeUtil.getDiscordTimestamp(kingdom.getLast_fetched()) + "\n");
        }

        io.create().embed("Your Kingdoms (" + me.size() + ")", result.toString()).send();
        return null;
    }
    @Command(desc = "Send in-game mail to a list of nations")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String mail(@Me User author, @Me JSONObject command, @Me GuildDB db, @Me IMessageIO channel, Set<DBKingdom> kingdoms, String subject, @TextArea String message, @Switch("f") boolean confirm) throws IOException {
        Auth auth = Trocutus.imp().rootAuth();;

        message = MarkupUtil.transformURLIntoLinks(message);

        if (!confirm) {
            String title = "Send " + kingdoms.size() + " messages";

            Set<Integer> alliances = new LinkedHashSet<>();
            for (DBKingdom kingdom : kingdoms) alliances.add(kingdom.getAlliance_id());
            String embedTitle = title + " to ";
            if (kingdoms.size() == 1) {
                DBKingdom kingdom = kingdoms.iterator().next();
                embedTitle += kingdoms.size() == 1 ? kingdom.getName() + " | " + kingdom.getAllianceName() : "nations";
            } else {
                embedTitle += " nations";
            }
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances";

            StringBuilder body = new StringBuilder();
            body.append("subject: " + subject + "\n");
            body.append("body: ```" + message + "```");

            channel.create().confirmation(embedTitle, body.toString(), command, "confirm").send();
            return null;
        }

        if (!Roles.ADMIN.hasOnRoot(author)) {
            message += "\n\n<i>This message was sent by: " + author.getName() + "</i>";
        }

        CompletableFuture<IMessageBuilder> msg = null;
        if (kingdoms.size() > 1) {
            msg = channel.send("Sending to " + kingdoms.size() + " (please wait)");
        }
        List<String> full = new ArrayList<>();
        for (DBKingdom kingdom : kingdoms) {
            try {
//                String subjectF = DiscordUtil.format(db.getGuild(), null, nation.getUser(), nation, subject);
//                String messageF = DiscordUtil.format(db.getGuild(), null, nation.getUser(), nation, message);
                String result = auth.sendMail(Settings.INSTANCE.NATION_ID, kingdom, subject, message);
                full.add(result);
            } catch (Throwable e) {
                e.printStackTrace();
                full.add("Error sending mail to " + kingdom.getName() + " (" + kingdom.getId() + "): " + e.getMessage());
            }
        }

        return "Done sending mail:\n- " + String.join("\n- ", full);
    }
}
