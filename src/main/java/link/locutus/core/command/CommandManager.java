package link.locutus.core.command;

import com.google.gson.reflect.TypeToken;
import link.locutus.Trocutus;
import link.locutus.command.binding.Key;
import link.locutus.command.binding.LocalValueStore;
import link.locutus.command.binding.SimpleValueStore;
import link.locutus.command.binding.ValueStore;
import link.locutus.command.binding.annotation.Command;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.binding.bindings.PrimitiveBindings;
import link.locutus.command.binding.bindings.PrimitiveValidators;
import link.locutus.command.binding.validator.ValidatorStore;
import link.locutus.command.command.ArgumentStack;
import link.locutus.command.command.CommandCallable;
import link.locutus.command.command.CommandGroup;
import link.locutus.command.command.CommandUsageException;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.command.ParameterData;
import link.locutus.command.command.ParametricCallable;
import link.locutus.command.impl.discord.DiscordChannelIO;
import link.locutus.command.impl.discord.binding.DiscordBindings;
import link.locutus.command.impl.discord.binding.PermissionBinding;
import link.locutus.command.impl.discord.binding.SheetBindings;
import link.locutus.command.perm.PermissionHandler;
import link.locutus.core.command.binding.TrouncedBindings;
import link.locutus.core.db.entities.alliance.DBAlliance;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.entities.alliance.DBRealm;
import link.locutus.core.db.entities.kingdom.KingdomPlaceholders;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.db.guild.key.GuildSetting;
import link.locutus.core.settings.Settings;
import link.locutus.util.DiscordUtil;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandManager extends ListenerAdapter {
    private final CommandGroup commands;
    private final ValueStore<Object> store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final KingdomPlaceholders kingdomPlaceholders;

    public CommandManager(Trocutus trocutus) {
        this.store = new SimpleValueStore<>();
        new PrimitiveBindings().register(store);
        new DiscordBindings().register(store);
        new SheetBindings().register(store);
        new TrouncedBindings().register(store);

        this.validators = new ValidatorStore();
        new PrimitiveValidators().register(validators);

        this.permisser = new PermissionHandler();
        new PermissionBinding().register(permisser);

        this.commands = CommandGroup.createRoot(store, validators);

        this.kingdomPlaceholders = new KingdomPlaceholders(store, validators, permisser);
    }

    public CommandManager registerCommands() {
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "sync", "kingdom");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncInteractions", "interactions");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncAlliances", "alliances");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncAllianceKingdoms", "kingdoms_in_aa");
        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncAllKingdoms", "kingdoms");

        this.commands.registerMethod(new BasicCommands(), List.of(), "raid", "raid");
        this.commands.registerMethod(new BasicCommands(), List.of(), "spyop", "spyop");
        this.commands.registerMethod(new BasicCommands(), List.of(), "alliance", "alliance");
        this.commands.registerMethod(new BasicCommands(), List.of(), "who", "who");
        this.commands.registerMethod(new BasicCommands(), List.of(), "me", "me");
        this.commands.registerMethod(new BasicCommands(), List.of(), "register", "register");
        this.commands.registerMethod(new BasicCommands(), List.of("mail"), "mail", "send");
        registerSettings();
        return this;
    }

    public KingdomPlaceholders getKingdomPlaceholders() {
        return kingdomPlaceholders;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        try {
            Guild guild = event.isFromGuild() ? event.getGuild() : null;
            if (guild != null) {
                GuildDB db = Trocutus.imp().getGuildDB(guild);
                if (db != null && !db.getHandler().onMessageReceived(event)) {
                    return;
                }
            }

            long start = System.currentTimeMillis();
            User msgUser = event.getAuthor();
            if (msgUser.isSystem() || msgUser.isBot()) {
                return;
            }

            Message message = event.getMessage();
            String content = DiscordUtil.trimContent(message.getContentRaw());
            if (content.length() == 0) {
                return;
            }

            if (content.equalsIgnoreCase(Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "threads")) {
                threadDump();
                return ;
            }

            run(event, true);
            long diff = System.currentTimeMillis() - start;
            if (diff > 1000) {
                StringBuilder response = new StringBuilder("## Long action: " + event.getAuthor().getIdLong() + " | " + event.getAuthor().getName() + ": " + DiscordUtil.trimContent(event.getMessage().getContentRaw()));
                if (event.isFromGuild()) {
                    response.append("\n\n- " + event.getGuild().getName() + " | " + event.getGuild().getId());
                }
                new RuntimeException(response.toString()).printStackTrace();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void run(MessageReceivedEvent event, boolean async) {
        Guild guild = event.isFromGuild() ? event.getGuild() : event.getMessage().isFromGuild() ? event.getMessage().getGuild() : null;
        DiscordChannelIO io = new DiscordChannelIO(event.getChannel(), event::getMessage);
        User user = event.getAuthor();
        String fullCmdStr = DiscordUtil.trimContent(event.getMessage().getContentRaw()).trim();
        if (fullCmdStr.startsWith(Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX)) {
            fullCmdStr = fullCmdStr.substring(Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX.length());
        }
        run(guild, event.getChannel(), user, event.getMessage(), io, fullCmdStr, async);
    }

    private void threadDump() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread thread : threadSet) {
            System.err.println(Arrays.toString(thread.getStackTrace()));
        }
        System.err.print("\n\nQueue: " + Trocutus.imp().getScheduler().getQueue().size() + " | Active: " + Trocutus.imp().getScheduler().getActiveCount() + " | task count: " + Trocutus.imp().getScheduler().getTaskCount());
        Trocutus.imp().getScheduler().submit(() -> System.err.println("- COMMAND EXECUTOR RAN SUCCESSFULLY!!!"));
    }

    public static Map<String, String> parseArguments(Set<String> params, String input, boolean checkUnbound) {
        Map<String, String> lowerCase = new HashMap<>();
        for (String param : params) {
            lowerCase.put(param.toLowerCase(Locale.ROOT), param);
        }


        Map<String, String> result = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("(?i)(^| )(" + StringMan.join(lowerCase.keySet(), "|") + "): [^ ]");
        Matcher matcher = pattern.matcher(input);

        Pattern fuzzyArg = !checkUnbound ? null : Pattern.compile("(?i) ([a-zA-Z]+): [^ ]");

        Map<String, Integer> argStart = new LinkedHashMap<>();
        Map<String, Integer> argEnd = new LinkedHashMap<>();
        String lastArg = null;
        while (matcher.find()) {
            String argName = matcher.group(2).toLowerCase(Locale.ROOT);
            int index = matcher.end(2) + 2;
            Integer existing = argStart.put(argName, index);
            if (existing != null)
                throw new IllegalArgumentException("Duplicate argument `" + argName + "` in `" + input + "`");

            if (lastArg != null) {
                argEnd.put(lastArg, matcher.start(2) - 1);
            }
            lastArg = argName;
        }
        if (lastArg != null) {
            argEnd.put(lastArg, input.length());
        }

        for (Map.Entry<String, Integer> entry : argStart.entrySet()) {
            String id = entry.getKey();
            int start = entry.getValue();
            int end = argEnd.get(id);
            String value = input.substring(start, end);

            if (fuzzyArg != null) {
                Matcher valueMatcher = fuzzyArg.matcher(value);
                if (valueMatcher.find()) {
                    String fuzzyArgName = valueMatcher.group(1);
                    throw new IllegalArgumentException("Invalid argument: `" + fuzzyArgName + "` for `" + input + "` options: " + StringMan.getString(params));
                }
            }
            result.put(lowerCase.get(id), value);
        }

        if (argStart.isEmpty()) {
            throw new IllegalArgumentException("No arguments found` for `" + input + "` options: " + StringMan.getString(params));
        }

        return result;
    }


    public CommandCallable getCallable(List<String> args) {
        return commands.getCallable(args);
    }

    public Map.Entry<CommandCallable, String> getCallableAndPath(List<String> args) {
        CommandCallable root = commands;
        List<String> path = new ArrayList<>();

        Queue<String> stack = new ArrayDeque<>(args);
        while (!stack.isEmpty()) {
            String arg = stack.poll();
            path.add(arg);
            if (root instanceof CommandGroup) {
                root = ((CommandGroup) root).get(arg);
            } else {
                throw new IllegalArgumentException("Command: " + root.getPrimaryCommandId() + " of type " + root.getClass().getSimpleName() + " has no subcommand: " + arg);
            }
        }
        return new AbstractMap.SimpleEntry<>(root, StringMan.join(path, " "));
    }


    private LocalValueStore createLocals(@Nullable LocalValueStore<Object> existingLocals, @Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, @Nullable Map<String, String> fullCmdStr) {
        if (guild != null && Settings.INSTANCE.MODERATION.BANNED_GUILDS.contains(guild.getIdLong()))
            throw new IllegalArgumentException("Unsupported");

        LocalValueStore<Object> locals = existingLocals == null ? new LocalValueStore<>(store) : existingLocals;

        locals.addProvider(Key.of(PermissionHandler.class), permisser);
        locals.addProvider(Key.of(ValidatorStore.class), validators);

        if (user != null) {
            if (Settings.INSTANCE.MODERATION.BANNED_USERS.contains(user.getIdLong()))
                throw new IllegalArgumentException("Unsupported");
            Map<DBRealm, DBKingdom> kingdoms = DBKingdom.getFromUser(user);
            if (!kingdoms.isEmpty()) {
                for (DBKingdom kingdom : kingdoms.values()) {
                    if (Settings.INSTANCE.MODERATION.BANNED_NATIONS.contains(kingdom.getId())) {
                        throw new IllegalArgumentException("MODERATION.BANNED_NATIONS: " + kingdom.getId() + " | " + kingdom.getName());
                    }
                    if (Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.contains(kingdom.getAlliance_id())) {
                        throw new IllegalArgumentException("MODERATION.BANNED_ALLIANCES: " + kingdom.getAlliance_id() + " | " + kingdom.getAllianceName());
                    }
                }
                locals.addProvider(Key.of(TypeToken.getParameterized(Map.class, DBRealm.class, DBKingdom.class).getType(), Me.class), kingdoms);
            }
            locals.addProvider(Key.of(User.class, Me.class), user);
        }

        locals.addProvider(Key.of(IMessageIO.class, Me.class), io);
        if (fullCmdStr != null) {
            locals.addProvider(Key.of(JSONObject.class, Me.class), new JSONObject(fullCmdStr));
        }
        if (channel != null) locals.addProvider(Key.of(MessageChannel.class, Me.class), channel);
        if (message != null) locals.addProvider(Key.of(Message.class, Me.class), message);
        if (guild != null) {
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null) locals.addProvider(Key.of(Member.class, Me.class), member);
            }
            locals.addProvider(Key.of(Guild.class, Me.class), guild);
            GuildDB db = GuildDB.get(guild);
            if (db != null) {
                for (DBAlliance alliance : db.getAlliances()) {
                    if (Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.contains(alliance.getId()))
                        throw new IllegalArgumentException("Unsupported");
                }
            }
            locals.addProvider(Key.of(GuildDB.class, Me.class), db);
        }
        return locals;
    }

    public void run(@Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, String fullCmdStr, boolean async) {
        LocalValueStore existingLocals = createLocals(null, guild, channel, user, message, io, null);
        run(existingLocals, io, fullCmdStr, async);
    }

    public void run(LocalValueStore<Object> existingLocals, IMessageIO io, String fullCmdStr, boolean async) {
        Runnable task = () -> {
            if (fullCmdStr.startsWith("{")) {
                JSONObject json = new JSONObject(fullCmdStr);
                Map<String, Object> arguments = json.toMap();
                Map<String, String> stringArguments = new HashMap<>();
                for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                    stringArguments.put(entry.getKey(), entry.getValue().toString());
                }

                String pathStr = arguments.remove("").toString();
                run(existingLocals, io, pathStr, stringArguments, async);
                return;
            }
            List<String> args = StringMan.split(fullCmdStr, ' ');
            List<String> original = new ArrayList<>(args);
            CommandCallable callable = commands.getCallable(args, true);
            if (callable == null) {
                System.out.println("No cmd found for " + StringMan.getString(original));
                return;
            }

            LocalValueStore locals = createLocals(existingLocals, null, null, null, null, io, null);

            if (callable instanceof ParametricCallable parametric) {
                try {
                    ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
                    handleCall(io, () -> {
                        Map<ParameterData, Map.Entry<String, Object>> map = parametric.parseArgumentsToMap(stack);
                        Object[] parsed = parametric.argumentMapToArray(map);
                        return parametric.call(null, locals, parsed);
                    });
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            } else if (callable instanceof CommandGroup group) {
                handleCall(io, group, locals);
            } else throw new IllegalArgumentException("Invalid command class " + callable.getClass());
        };
        if (async) Trocutus.imp().getExecutor().submit(task);
        else task.run();
    }

    public void run(@Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, String path, Map<String, String> arguments, boolean async) {
        LocalValueStore existingLocals = createLocals(null, guild, channel, user, message, io, null);
        run(existingLocals, io, path, arguments, async);
    }

    public void run(LocalValueStore<Object> existingLocals, IMessageIO io, String path, Map<String, String> arguments, boolean async) {
        Runnable task = () -> {
            try {
                CommandCallable callable = commands.get(Arrays.asList(path.split(" ")));
                if (callable == null) {
                    System.out.println("No cmd found for " + path);
                    io.create().append("No command found for " + path).send();
                    return;
                }

                Map<String, String> argsAndCmd = new HashMap<>(arguments);
                argsAndCmd.put("", path);
                Map<String, String> finalArguments = new LinkedHashMap<>(arguments);
                finalArguments.remove("");

                LocalValueStore<Object> finalLocals = createLocals(existingLocals, null, null, null, null, io, argsAndCmd);
                if (callable instanceof ParametricCallable parametric) {
                    handleCall(io, () -> {
                        Object[] parsed = parametric.parseArgumentMap(finalArguments, finalLocals, validators, permisser);
                        return parametric.call(null, finalLocals, parsed);
                    });
                } else if (callable instanceof CommandGroup group) {
                    handleCall(io, group, finalLocals);
                } else {
                    System.out.println("Invalid command class " + callable.getClass());
                    throw new IllegalArgumentException("Invalid command class " + callable.getClass());
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        };
        if (async) Trocutus.imp().getExecutor().submit(task);
        else task.run();
    }

    private void handleCall(IMessageIO io, Supplier<Object> call) {
        try {
            Object result = call.get();
            if (result != null) {
                io.create().append(result.toString()).send();
            }
        } catch (CommandUsageException e) {
            e.printStackTrace();
            String title = e.getMessage();
            StringBuilder body = new StringBuilder();
            if (e.getHelp() != null) {
                body.append("`/").append(e.getHelp()).append("`");
            }
            if (e.getDescription() != null && !e.getDescription().isEmpty()) {
                body.append("\n").append(e.getDescription());
            }
            if (title == null || title.isEmpty()) title = e.getClass().getSimpleName();

            io.create().embed(title, body.toString()).send();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            io.create().append(e.getMessage()).send();
        } catch (Throwable e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();

            root.printStackTrace();
            io.create().append("Error: " + root.getMessage()).send();
        }
    }

    private void handleCall(IMessageIO io, CommandGroup group, ValueStore store) {
        handleCall(io, () -> group.call(new ArgumentStack(new ArrayList<>(), store, validators, permisser)));
    }

    public Map<String, String> validateSlashCommand(String input, boolean strict) {
        String original = input;
        CommandGroup root = commands;
        while (true) {
            int index = input.indexOf(' ');
            if (index < 0)
                throw new IllegalArgumentException("No parametric command found for " + original + " only found root: " + root.getFullPath());
            String arg0 = input.substring(0, index);
            input = input.substring(index + 1).trim();

            CommandCallable next = root.get(arg0);
            if (next instanceof ParametricCallable parametric) {
                if (!input.isEmpty()) {
                    return parseArguments(parametric.getUserParameterMap().keySet(), input, strict);
                }
                return new HashMap<>();
            } else if (next instanceof CommandGroup group) {
                root = group;
            } else if (next == null) {
                throw new IllegalArgumentException("No parametric command found for " + original + " (" + arg0 + ")");
            } else {
                throw new UnsupportedOperationException("Invalid command class " + next.getClass());
            }
        }
    }

    private void registerSettings() {
        this.commands.registerMethod(new SettingCommands(), List.of("settings"), "delete", "delete");
        this.commands.registerMethod(new SettingCommands(), List.of("settings"), "sheets", "sheets");
        this.commands.registerMethod(new SettingCommands(), List.of("settings"), "info", "info");

        for (GuildSetting setting : GuildKey.values()) {
            List<String> path = List.of("settings_" + setting.getCategory().name().toLowerCase(Locale.ROOT));

            Method[] methods = setting.getClass().getDeclaredMethods();
            Map<String, String> methodNameToCommandName = new HashMap<>();
            for (Method method : methods) {
                if (method.getAnnotation(Command.class) != null) {
                    Command command = method.getAnnotation(Command.class);

                    String[] aliases = command.aliases();
                    String commandName = aliases.length == 0 ? method.getName() : aliases[0];
                    methodNameToCommandName.put(method.getName(), commandName);
                }
            }

            for (Map.Entry<String, String> entry : methodNameToCommandName.entrySet()) {
                String methodName = entry.getKey();
                String commandName = entry.getValue();
                this.commands.registerMethod(setting, path, methodName, commandName);
            }
        }
    }

    public CommandGroup getCommands() {
        return commands;
    }

    public ValueStore<Object> getStore() {
        return store;
    }

    public ValidatorStore getValidators() {
        return validators;
    }

    public PermissionHandler getPermisser() {
        return permisser;
    }
}
