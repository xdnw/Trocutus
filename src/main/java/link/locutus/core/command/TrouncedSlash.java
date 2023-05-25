package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.impl.discord.SlashCommandManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;

public class TrouncedSlash extends SlashCommandManager {
    private final Trocutus trocutus;

    public TrouncedSlash(Trocutus trocutus) {
        this.trocutus = trocutus;
    }

    public void setupCommands() {

    }
}
