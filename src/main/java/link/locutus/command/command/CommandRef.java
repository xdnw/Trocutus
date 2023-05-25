package link.locutus.command.command;

import link.locutus.Trocutus;
import link.locutus.core.command.CommandManager;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class CommandRef {
    private final Map<String, String> arguments = new LinkedHashMap<>();
    private final String path;

    public CommandRef() {
        String[] split = getClass().getName().split("\\$");
        split = Arrays.copyOfRange(split, 1, split.length);
        path = String.join(" ", split);
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public <T extends CommandRef> T createArgs(String... args) {
        try {
            CommandRef instance = getClass().newInstance();
            for (int i = 0; i < args.length; i += 2) {
                instance.arguments.put(args[i], args[i + 1]);
            }
            return (T) instance;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public ParametricCallable getCommand() {
        CommandManager manager = Trocutus.imp().getCommandManager();
        CommandGroup root = manager.getCommands();
        CommandCallable cmd = root.get(Arrays.asList(path.split(" ")));
        if (cmd == null) throw new IllegalArgumentException("No command found for " + path);
        if (cmd instanceof ParametricCallable) return (ParametricCallable) cmd;
        throw new IllegalArgumentException("Command " + path + " is not parametric. " + cmd.getClass().getSimpleName());
    }

    public String toSlashMention() {
        return getCommand().getSlashMention();
    }

    public String toSlashCommand() {
        return getCommand().getSlashCommand(Collections.emptyMap());
    }

    public String toSlashCommand(boolean backTicks) {
        return getCommand().getSlashCommand(Collections.emptyMap(), backTicks);
    }


    public JSONObject toJson() {
        return new JSONObject(toCommandArgs());
    }

    public String toCommandArgs() {
        Map<String, String> data = new HashMap<>(arguments);
        data.put("", path);
        return new JSONObject(data).toString();
    }

    @Override
    public String toString() {
        return toSlashCommand();
    }
}