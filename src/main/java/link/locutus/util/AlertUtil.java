package link.locutus.util;

import link.locutus.Trocutus;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.key.GuildSetting;
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
}
