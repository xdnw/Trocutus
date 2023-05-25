package link.locutus.core.db.guild;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class GuildHandler {
    private final GuildDB db;

    public GuildHandler(GuildDB db) {
        this.db = db;

    }

    public boolean onMessageReceived(MessageReceivedEvent event) {
        return true;
    }
}
