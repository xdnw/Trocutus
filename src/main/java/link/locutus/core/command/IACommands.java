package link.locutus.core.command;

import com.google.gson.JsonObject;
import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomList;
import link.locutus.core.db.entities.kingdom.KingdomMeta;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.core.db.guild.interview.IACategory;
import link.locutus.core.db.guild.interview.IACheckup;
import link.locutus.util.MarkupUtil;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class IACommands {
    @Command(desc = "Generate an audit report of a list of nations")
    @RolePermission(Roles.MEMBER)
    public String checkCities(@Me GuildDB db, @Me IMessageIO channel, @Me Guild guild, @Me User author, @Me Map<DBRealm, DBKingdom> me,
                              @Arg("Kingdoms to audit")
                              KingdomList nationList,
                              @Arg("Only perform these audits (default: all)")
                              @Default Set<IACheckup.AuditType> audits,
                              @Arg("Ping the user on discord with their audit")
                              @Switch("u") boolean pingUser,
                              @Arg("Mail the audit to each nation in-game")
                              @Switch("m") boolean mailResults,
                              @Arg("Post the audit in the interview channels (if exists)")
                              @Switch("c") boolean postInInterviewChannels,
                              @Arg("Skip updating nation info from the game")
                              @Switch("s") boolean skipUpdate) throws Exception {
        Collection<DBKingdom> nations = nationList.getKingdoms();
        Set<Integer> aaIds = nationList.getAllianceIds();

        if (nations.size() > 1) {
            IACategory category = db.getIACategory();
            if (category != null) {
                category.load();
                category.purgeUnusedChannels(channel);
                category.alertInvalidChannels(channel);
            }
        }

        for (DBKingdom nation : nations) {
            if (!db.isAllianceId(nation.getAlliance_id())) {
                return "Kingdom `" + nation.getName() + "` is in " + nation.getAlliance().getQualifiedName() + " but this server is registered to: "
                        + StringMan.getString(db.getAllianceIds()) + "\nSee: " + CM.settings.info.cmd.toSlashMention() + " with key `" + GuildKey.ALLIANCE_ID.name() + "`";
            }
        }

        if (nations.size() == 1) {
            nations.iterator().next().setMeta(KingdomMeta.INTERVIEW_CHECKUP, (byte) 1);
        }

        IACheckup checkup = new IACheckup(db, db.getAllianceList().subList(aaIds), false);

        Auth auth = mailResults ? db.getMailAuth() : null;
        if (mailResults && auth == null) throw new IllegalArgumentException("No auth set, please use " + CM.credentials.login.cmd.toSlashMention() + "");

        CompletableFuture<IMessageBuilder> msg = channel.send("Please wait...");

        Map<DBKingdom, Map<IACheckup.AuditType, Map.Entry<Object, String>>> auditResults = new HashMap<>();

        IACheckup.AuditType[] allowed = audits == null || audits.isEmpty() ? IACheckup.AuditType.values() : audits.toArray(new IACheckup.AuditType[0]);
        for (DBKingdom nation : nations) {
            StringBuilder output = new StringBuilder();
            int failed = 0;

            Map<IACheckup.AuditType, Map.Entry<Object, String>> auditResult = checkup.checkup(nation, allowed, nations.size() == 1, skipUpdate);
            auditResults.put(nation, auditResult);

            if (auditResult != null) {
                auditResult = IACheckup.simplify(auditResult);
            }

            if (!auditResult.isEmpty()) {
                for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : auditResult.entrySet()) {
                    IACheckup.AuditType type = entry.getKey();
                    Map.Entry<Object, String> info = entry.getValue();
                    if (info == null || info.getValue() == null) continue;
                    failed++;

                    output.append("**").append(type.toString()).append(":** ");
                    output.append(info.getValue()).append("\n\n");
                }
            }
            IMessageBuilder resultMsg = channel.create();
            if (failed > 0) {
                resultMsg.append("**").append(nation.getName()).append("** failed ").append(failed + "").append(" checks:");
                if (pingUser) {
                    User user = nation.getUser();
                    if (user != null) resultMsg.append(user.getAsMention());
                }
                resultMsg.append("\n");
                resultMsg.append(output.toString());
                if (mailResults) {
                    String title = nation.getAllianceName() + " automatic checkup";

                    String input = output.toString().replace("_", " ").replace(" * ", " STARPLACEHOLDER ");
                    String markdown = MarkupUtil.markdownToHTML(input);
                    markdown = MarkupUtil.transformURLIntoLinks(markdown);
                    markdown = MarkupUtil.htmlUrl(nation.getName(), nation.getUrl(null)) + "\n" + markdown;
                    markdown += ("\n\nPlease get in contact with us via discord for assistance");
                    markdown = markdown.replace("\n", "<br>").replace(" STARPLACEHOLDER ", " * ");

                    DBKingdom sender = auth.getKingdom(nation.getRealm_id());
                    if (sender == null) {
                        resultMsg.append("No sender for realm " + nation.getRealm_id());
                    } else {
                        String response = auth.sendMail(sender.getId(), nation, title, markdown);
                        String userStr = nation.getName() + "/" + nation.getId();
                        resultMsg.append("\n" + userStr + ": " + response);
                    }
                }
            } else {
                resultMsg.append("All checks passed for " + nation.getName());
            }
            resultMsg.send();
        }

        if (postInInterviewChannels) {
            if (db.getGuild().getCategoriesByName("interview", true).isEmpty()) {
                return "No `interview` category";
            }

            IACategory category = db.getIACategory();
            if (category.isValid()) {
                category.update(auditResults);
            }
        }

        return null;
    }
}
