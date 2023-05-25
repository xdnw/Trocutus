package link.locutus.core.db.guild.key;

import link.locutus.command.binding.Key;
import link.locutus.core.db.guild.GuildDB;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public abstract class GuildChannelSetting extends GuildSetting<MessageChannel> {
    public GuildChannelSetting(GuildSettingCategory category) {
        super(category, Key.of(MessageChannel.class));
    }

    @Override
    public MessageChannel validate(GuildDB db, MessageChannel value) {
        return validateChannel(db, value);
    }

    @Override
    public final String toString(MessageChannel value) {
        return ((IMentionable) value).getAsMention();
    }
}
