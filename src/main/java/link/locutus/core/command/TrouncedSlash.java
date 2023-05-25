package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.impl.discord.SlashCommandManager;

public class TrouncedSlash extends SlashCommandManager {
    private final Trocutus trocutus;

    public TrouncedSlash(Trocutus trocutus) {
        this.trocutus = trocutus;
    }

    public void setupCommands() {

    }
}
