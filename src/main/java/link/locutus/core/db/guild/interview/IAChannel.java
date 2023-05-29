package link.locutus.core.db.guild.interview;

import link.locutus.core.command.CM;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.util.DiscordUtil;
import link.locutus.util.MathMan;
import link.locutus.util.RateLimitUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class IAChannel {
    private final DBKingdom nation;
    private final GuildDB db;
    private final Category category;
    private final TextChannel channel;

    public IAChannel(DBKingdom nation, GuildDB db, Category category, TextChannel channel) {
        this.nation = nation;
        this.db = db;
        this.category = category;
        this.channel = channel;
    }

    public DBKingdom getKingdom() {
        if (nation == null) {
            String[] split = channel.getName().split("-");
            if (MathMan.isInteger(split[split.length - 1])) {
                return DBKingdom.get(Integer.parseInt(split[split.length - 1]));
            }
        }
        return nation;
    }

    public void updatePerms() {
        User user = nation.getUser();
        if (user == null) return;
        Member member = db.getGuild().getMember(user);
        if (member == null) return;
        RateLimitUtil.complete(channel.upsertPermissionOverride(member).grant(Permission.VIEW_CHANNEL));

        String expected = DiscordUtil.toDiscordChannelString(nation.getSlug()) + "-" + nation.getId();
        String name = channel.getName();
        if (!name.equalsIgnoreCase(expected)) {
            RateLimitUtil.queue(channel.getManager().setName(expected));
        }
    }

    public TextChannel getChannel() {
        return channel;
    }

    public void update(Map<IACheckup.AuditType, Map.Entry<Object, String>> audits) {
        Set<String> emojis = new LinkedHashSet<>();
        if (audits == null) {
            if (nation.isVacation() == true) {
                emojis.add("\uD83C\uDFD6\ufe0f");
            }
            if (nation.getActive_m() > 2440) {
                emojis.add("\uD83E\uDEA6");
            }
            User user = nation.getUser();
            Guild guild = db.getGuild();
            if (user == null || guild.getMember(user) == null) {
                emojis.add("\uD83D\uDCDB");
            }
        } else {
            for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : audits.entrySet()) {
                IACheckup.AuditType type = entry.getKey();
                Map.Entry<Object, String> result = entry.getValue();
                emojis.add(type.emoji);
            }
            String cmd = CM.audit.run.cmd.create(nation.getId() + "", null, null, null, null, null).toCommandArgs();
            IACheckup.createEmbed(channel, null, cmd, nation, audits, 0);
        }
//        emojis.remove("");
//        if (!emojis.isEmpty()) {
//            expected = DiscordUtil.toDiscordChannelString(nation.getKingdom()) + "-" + StringMan.join(emojis, "") + "-" + nation.getKingdom_id();
//        }
    }
}
