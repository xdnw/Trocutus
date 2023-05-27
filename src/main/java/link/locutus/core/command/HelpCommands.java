package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.command.CommandCallable;
import link.locutus.command.command.IMessageIO;

public class HelpCommands {
    public HelpCommands() {
    }

    @Command
    public String command(@Me IMessageIO io, ValueStore store, CommandCallable command) {
        return command.desc(store);
    }
}
