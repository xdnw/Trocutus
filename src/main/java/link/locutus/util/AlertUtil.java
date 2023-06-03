package link.locutus.util;

import link.locutus.Trocutus;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.core.settings.Settings;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class AlertUtil {
    public static void forEachChannel(Function<GuildDB, Boolean> hasPerm, GuildSetting<MessageChannel> key, BiConsumer<MessageChannel, GuildDB> channelConsumer) {
        for (GuildDB guildDB : Trocutus.imp().getGuildDatabases().values()) {
            try {
                if (!hasPerm.apply(guildDB)) continue;
                MessageChannel channel = guildDB.getOrNull(key, false);
                if (channel == null) {
                    continue;
                }
                channelConsumer.accept(channel, guildDB);
            } catch (InsufficientPermissionException e) {
                guildDB.deleteInfo(key);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public static void error(String s) {
        long channelId = Settings.INSTANCE.DISCORD.ERROR_CHANNEL_ID;
        System.err.println(s);
        if (channelId <= 0) return;
        MessageChannel channel = Trocutus.imp().getDiscordApi().getGuildChannelById(channelId);
        if (channel != null) {
            DiscordUtil.sendMessage(channel, s);
        }
    }
}
