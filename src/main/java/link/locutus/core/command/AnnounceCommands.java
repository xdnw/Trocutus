package link.locutus.core.command;

import link.locutus.command.binding.annotation.Arg;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.annotation.Switch;
import link.locutus.command.binding.annotation.TextArea;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.HasApi;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.api.ApiKeyPool;
import link.locutus.core.api.Auth;
import link.locutus.core.api.alliance.Rank;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.kingdom.KingdomList;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import link.locutus.util.DiscordUtil;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class AnnounceCommands {
    @Command(desc = "Set the archive status of the bot's announcement")
    @RolePermission(any = true, value = {Roles.INTERNAL_AFFAIRS, Roles.MILCOM, Roles.ADMIN, Roles.FOREIGN_AFFAIRS, Roles.ECON})
    public String archiveAnnouncement(@Me GuildDB db, int announcementId, @Default Boolean archive) {
        if (archive == null) archive = true;
        db.setAnnouncementActive(announcementId, !archive);
        return (archive ? "Archived" : "Unarchived") + " announcement with id: #" + announcementId;
    }

    @Command(desc = "Send an announcement to multiple nations, with random variations for each receiver\n")
    @RolePermission(Roles.ADMIN)
    @HasApi
    public String announce(@Me GuildDB db, @Me Guild guild, @Me JSONObject command, @Me IMessageIO currentChannel,
                           @Me User author,
                           KingdomList sendTo,
                           @Arg("The subject used for sending an in-game mail if a discord direct message fails") String subject,
                           @Arg("The message you want to send") @TextArea String announcement,
                           @Arg("Lines of replacement words or phrases, separated by `|` for each variation\n" +
                                   "Add multiple lines for each replacement you want") @TextArea String replacements,
                           @Arg("The required number of differences between each message") @Switch("v") @Default("0") Integer requiredVariation,
                           @Arg("The required depth of changes from the original message") @Switch("r") @Default("0") Integer requiredDepth,
                           @Arg("Variation seed. The same seed will produce the same variations, otherwise results are random") @Switch("s") Long seed,
                           @Arg("If messages are sent in-game") @Switch("m") boolean sendMail,
                           @Arg("If messages are sent via discord direct message") @Switch("d") boolean sendDM,
                           @Switch("f") boolean force) throws IOException {
        Auth auth = db.getMailAuth();

        if (auth == null) throw new IllegalArgumentException("No API_KEY set, please use " + "CM.credentials.addApiKey.cmd.toSlashMention()" + "");
        Set<Integer> aaIds = db.getAllianceIds();

        List<String> errors = new ArrayList<>();
        Collection<DBKingdom> nations = sendTo.getKingdoms();
        for (DBKingdom nation : nations) {
            User user = nation.getUser();
            if (user == null) {
                errors.add("Cannot find user for `" + nation.getName() + "`");
            } else if (guild.getMember(user) == null) {
                errors.add("Cannot find member in guild for `" + nation.getName() + "` | `" + user.getName() + "`");
            } else {
                continue;
            }
            if (!aaIds.isEmpty() && !aaIds.contains(nation.getAlliance_id())) {
                throw new IllegalArgumentException("Cannot send to nation not in alliance: " + nation.getName() + " | " + user);
            }
            if (!force) {
                if (nation.getActive_m() > 20000)
                    return "The " + nations.size() + " receivers includes inactive for >2 weeks. Use `" + sendTo.getFilter() + ",#active_m<20000` or set `force` to confirm";
                if (nation.isVacation())
                    return "The " + nations.size() + " receivers includes vacation mode nations. Use `" + sendTo.getFilter() + ",#vm_turns=0` or set `force` to confirm";
                if (nation.getPosition().ordinal() < Rank.APPLICANT.ordinal()) {
                    return "The " + nations.size() + " receivers includes applicants. Use `" + sendTo.getFilter() + ",#position>1` or set `force` to confirm";
                }
            }
        }

        List<String> replacementLines = Arrays.asList(replacements.split("\n"));

        Random random = seed == null ? new Random() : new Random(seed);

        Set<String> results = StringMan.enumerateReplacements(announcement, replacementLines, nations.size() + 1000, requiredVariation, requiredDepth);

        if (results.size() < nations.size()) return "Not enough entropy. Please provide more replacements";

        if (!force) {
            StringBuilder confirmBody = new StringBuilder();
            if (!sendDM && !sendMail) confirmBody.append("**Warning: No ingame or direct message option has been specified**\n");
            confirmBody.append("Send DM (`-d`): " + sendDM).append("\n");
            confirmBody.append("Send Ingame (`-m`): " + sendMail).append("\n");
            if (!errors.isEmpty()) {
                confirmBody.append("\n**Errors**:\n- " + StringMan.join(errors, "\n- ")).append("\n");
            }
//            DiscordUtil.createEmbedCommand(currentChannel, "Send to " + nations.size() + " nations", confirmBody + "\nPress to confirm", );
            DiscordUtil.pending(currentChannel, command, "Send to " + nations.size() + " nations", confirmBody + "\nPress to confirm");
            return null;
        }

        currentChannel.send("Please wait...");

        List<String> resultsArray = new ArrayList<>(results);
        Collections.shuffle(resultsArray, random);

        resultsArray = resultsArray.subList(0, nations.size());

        List<Integer> failedToDM = new ArrayList<>();
        Map<Integer, String> failedToMail = new LinkedHashMap<>();

        StringBuilder output = new StringBuilder();

        Map<DBKingdom, String> sentMessages = new HashMap<>();

        int i = 0;
        for (DBKingdom nation : nations) {
            String replaced = resultsArray.get(i++);
            String personal = replaced + "\n\n- " + author.getAsMention() + " " + guild.getName();

            boolean result = sendDM && nation.sendDM(personal);
            if (!result && sendDM) {
                failedToDM.add(nation.getId());
            }
            if (!result || sendMail) {
                try {
                    DBKingdom sender = auth.getKingdom(nation.getRealm_id());
                    if (sender == null) {
                        failedToMail.put(nation.getRealm_id(), "Cannot find sender for auth " + auth.getUserId());
                    } else {
                        auth.sendMail(sender.getId(), nation, subject, personal);
                    }
                } catch (IllegalArgumentException e) {
                    failedToMail.put(nation.getId(), e.getMessage());
                }
            }

            sentMessages.put(nation, replaced);

            output.append("\n\n```" + replaced + "```" + "^ " + nation.getName());
        }

        output.append("\n\n------\n");
        if (errors.size() > 0) {
            output.append("Errors:\n- " + StringMan.join(errors, "\n- "));
        }
        if (failedToDM.size() > 0) {
            output.append("\nFailed DM (sent ingame): " + StringMan.getString(failedToDM));
        }
        if (failedToMail.size() > 0) {
            output.append("\nFailed Mail: " + StringMan.getString(failedToMail));
        }

        int annId = db.addAnnouncement(author, subject, announcement, replacements, sendTo.getFilter());
        for (Map.Entry<DBKingdom, String> entry : sentMessages.entrySet()) {
            byte[] diff = StringMan.getDiffBytes(announcement, entry.getValue());
            db.addPlayerAnnouncement(entry.getKey(), annId, diff);
        }

        return output.toString().trim();
    }
}
