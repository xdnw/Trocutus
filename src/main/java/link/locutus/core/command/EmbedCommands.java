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
    @Command(desc="Raid finder discord embed template")
    @RolePermission(Roles.ADMIN)
    public void targets(@Me User user, @Me GuildDB db, @Me IMessageIO io, int realm_id, @Default MessageChannel outputChannel) {
        Long channelId = outputChannel == null ? null : outputChannel.getIdLong();
        String title = "Find Raid Targets";
        String body = """
                        Press `fey_top` for top fey
                        Press `fey_optimal` for optimal fey
                        Press `spy` for spy targets
                        Press `raid` for raid targets
                        Press `enemies_attack` for enemies w/ no attack alert
                        Press `enemies_spell` for enemies w/ no spell alert
                        Press `enemies_unaware` for enemies w/ no alert
                        Press `enemies_all` for all enemies
                        """;

        if (channelId != null) {
            body += "\n\n> Results in <#" + channelId + ">";
        }

        CM.fey.top fey_top = CM.fey.top.cmd.create("{getid}", null, null);
        CM.fey.optimal fey_optimal = CM.fey.optimal.cmd.create("{getid}", null, null, null);
        CM.war.find.intel spy = CM.war.find.intel.cmd.create(realm_id + "", null, null, null, null, null, null);
        CM.war.find.raid raid = CM.war.find.raid.cmd.create(realm_id + "", null, null, null, "true", null, null, null, "true");
        CM.war.find.enemy enemies_attack = CM.war.find.enemy.cmd.create("0", "9", null, null, null, "true", null);
        CM.war.find.enemy enemies_spell = CM.war.find.enemy.cmd.create("9", "0", null, null, null, "true", null);
        CM.war.find.enemy enemies_unaware = CM.war.find.enemy.cmd.create("0", "0", null, null, null, "true", null);
        CM.war.find.enemy enemies_all = CM.war.find.enemy.cmd.create("9", "9", null, null, null, "true", null);

        CommandBehavior behavior = CommandBehavior.UNDO_REACTION;
        io.create().embed(title, body)
                .commandButton(behavior, channelId, fey_top, "fey_top")
                .commandButton(behavior, channelId, fey_optimal, "fey_optimal")
                .commandButton(behavior, channelId, spy, "spy")
                .commandButton(behavior, channelId, raid, "raid")
                .commandButton(behavior, channelId, enemies_attack, "enemies_attack")
                .commandButton(behavior, channelId, enemies_spell, "enemies_spell")
                .commandButton(behavior, channelId, enemies_unaware, "enemies_unaware")
                .commandButton(behavior, channelId, enemies_all, "enemies_all")
                .send();
    }
}
