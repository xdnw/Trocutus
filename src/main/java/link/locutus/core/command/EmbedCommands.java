package link.locutus.core.command;

import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Default;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.command.CommandBehavior;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.permission.RolePermission;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.entities.Roles;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class EmbedCommands {
//    @Command(desc="Raid finder discord embed template")
//    @RolePermission(Roles.ADMIN)
//    public void raid(@Me User user, @Me GuildDB db, @Me IMessageIO io, @Default MessageChannel outputChannel) {
//        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
//        String title = "Find Raid Targets";
//        String body = """
//                        Press `7d_app` for inactive nones/apps
//                        Press `7d_members` for inactives in alliances
//                        Press `7d_beige` for nations on beige
//                        Press `ground` for actives with low ground
//                        Press `2d_ground` for 2d inactives with low ground
//                        Press `losing` for actives losing their current wars
//                        Press `unprotected` for with weak or no available counters
//                        """;
//
//        if (channelId != null) {
//            body += "\n\n> Results in <#" + channelId + ">";
//        }
//
//        CM.war.find.raid app = CM.war.find.raid.cmd.create(
//                null, "10", null, null, null, null, null, null, null, null, null);
//        CM.war.find.raid members = CM.war.find.raid.cmd.create(
//                "*", "25", null, null, null, null, null, null, null, null, null);
//        CM.war.find.raid beige = CM.war.find.raid.cmd.create(
//                "*", "25", null, null, "24", null, null, null, null, null, null);
//        CM.war.find.raid ground = CM.war.find.raid.cmd.create(
//                "#tank%<20,#soldier%<40,*", "25", "0d", "true", null, null, null, null, null, null, null);
//        CM.war.find.raid ground_2d = CM.war.find.raid.cmd.create(
//                "#tank%<20,#soldier%<40,*", "25", "2d",  "true", null,null, null, null, null, null, null);
//        CM.war.find.raid losing = CM.war.find.raid.cmd.create(
//                "#def>0,#strength<1,*", "25", "0d", "true", null, null, null, null, null, null, null);
//        CM.war.find.unprotected unprotected = CM.war.find.unprotected.cmd.create(
//                "*", "25", null, "true", null,  null, "90", null, null);
//
//        CommandBehavior behavior = CommandBehavior.UNDO_REACTION;
//        io.create().embed(title, body)
//                .commandButton(behavior, channelId, app, "7d_app")
//                .commandButton(behavior, channelId, members, "7d_members")
//                .commandButton(behavior, channelId, beige, "7d_beige")
//                .commandButton(behavior, channelId, ground, "ground")
//                .commandButton(behavior, channelId, ground_2d, "2d_ground")
//                .commandButton(behavior, channelId, losing, "losing")
//                .commandButton(behavior, channelId, unprotected, "unprotected")
//                .send();
//    }
}
