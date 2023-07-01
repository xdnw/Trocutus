package link.locutus.core.command;

import link.locutus.Trocutus;
import link.locutus.command.binding.Key;
import link.locutus.command.binding.LocalValueStore;
import link.locutus.command.binding.Parser;
import link.locutus.command.binding.annotation.Autocomplete;
import link.locutus.command.binding.annotation.Autoparse;
import link.locutus.command.binding.annotation.Me;
import link.locutus.command.command.ArgumentStack;
import link.locutus.command.command.CommandCallable;
import link.locutus.command.command.ParameterData;
import link.locutus.command.command.ParametricCallable;
import link.locutus.command.impl.SlashCommandManager;
import link.locutus.command.impl.discord.DiscordHookIO;
import link.locutus.core.settings.Settings;
import link.locutus.util.MathMan;
import link.locutus.util.RateLimitUtil;
import link.locutus.util.StringMan;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class TrouncedSlash extends SlashCommandManager {
    private final Trocutus trocutus;
    private final CommandManager manager;

    private Map<ParametricCallable, String> commandNames = new HashMap<>();

    public TrouncedSlash(Trocutus trocutus) {
        this.trocutus = trocutus;
        this.manager = trocutus.getCommandManager();
    }

    public void setupCommands() {
        List<SlashCommandData> toRegister = this.toSlashCommandData(manager.getCommands(), manager.getStore(), commandNames);
        if (!toRegister.isEmpty()) {

            Guild guild = Trocutus.imp().getServer();
            JDA api = Trocutus.imp().getDiscordApi().getApiByGuildId(guild.getIdLong());


            List<Command> commands = RateLimitUtil.complete(api.updateCommands().addCommands(toRegister));
            for (net.dv8tion.jda.api.interactions.commands.Command command : commands) {
                String path = command.getName();
                commandIds.put(path, command.getIdLong());
            }
        }
    }


    private static String getCommandPath(ParametricCallable callable) {
        TrouncedSlash slash = Trocutus.imp().getSlashCommands();
        return slash == null ? null : slash.commandNames.get(callable);
    }

    public static String getSlashMention(ParametricCallable callable) {
        String path = getCommandPath(callable);
        if (path == null) {
            return "/" + callable.getFullPath();
        }
        return getSlashMention(path);
    }

    public static String getSlashCommand(ParametricCallable callable, Map<String, String> arguments, boolean backTicks) {
        String path = getCommandPath(callable);
        if (path == null) {
            path = callable.getFullPath();
        }
        return getSlashCommand(path, arguments, backTicks);
    }

    public static String getSlashCommand(String path, Map<String, String> arguments, boolean backTicks) {
        StringBuilder builder = new StringBuilder();
        builder.append("/").append(path.toLowerCase(Locale.ROOT));
        if (!arguments.isEmpty()) {
            // join on " "
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                if (entry.getValue() == null) continue;
                builder.append(" ").append(entry.getKey().toLowerCase(Locale.ROOT)).append(": ").append(entry.getValue());
            }
        }
        if (backTicks) return "`" + builder + "`";
        return builder.toString();
    }

    private final Map<String, Long> commandIds = new HashMap<>();

    public static String getSlashMention(String path) {
        TrouncedSlash slash = Trocutus.imp().getSlashCommands();
        if (slash != null) {
            Long id = slash.getCommandId(path);
            if (id != null && id > 0) {
                return "</" + path.toLowerCase(Locale.ROOT) + ":" + id + ">";
            }
        }
        return getSlashCommand(path, Collections.emptyMap(), true);
    }

    private Long getCommandId(String path) {
        Long id = commandIds.get(path);
        if (id == null) id = commandIds.get(path.split(" ")[0]);
        return id;
    }


    private Map<Long, Long> userIdToAutoCompleteTimeNs = new ConcurrentHashMap<>();

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        long startNanos = System.nanoTime();
        User user = event.getUser();
        userIdToAutoCompleteTimeNs.put(user.getIdLong(), startNanos);

        String path = event.getFullCommandName().replaceAll("/", " ");
        AutoCompleteQuery option = event.getFocusedOption();
        String optionName = option.getName();

        List<String> pathArgs = StringMan.split(path, ' ');
        CommandManager commands = Trocutus.imp().getCommandManager();
        Map.Entry<CommandCallable, String> cmdAndPath = commands.getCallableAndPath(pathArgs);
        CommandCallable cmd = cmdAndPath.getKey();

        if (cmd == null) {
            // No command found
            System.out.println("remove:||No command found: " + path);
            return;
        }

        if (!(cmd instanceof ParametricCallable parametric)) {
            System.out.println("remove:||Not parametric: " + path);
            return;
        }

        Map<String, ParameterData> map = parametric.getUserParameterMap();
        ParameterData param = map.get(optionName);
        if (param == null) {
            System.out.println("remove:||No parameter found for " + optionName + " | " + map.keySet());
            return;
        }

        if (event.getUser().getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) {
            System.out.println("remove:||Admin runs complete " + path + " | " + option.getValue());
        }

        /*
        Dont post if they have run the command
        Dont post if they have typed something else since then
         */
        ExecutorService executor = Trocutus.imp().getExecutor();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                boolean autoParse = true;
                Parser binding = param.getBinding();
                Key key = binding.getKey();
                Key parserKey = key.append(Autoparse.class);
                Parser parser = commands.getStore().get(parserKey);

                if (parser == null) {
                    autoParse = false;
                    Key completerKey = key.append(Autocomplete.class);
                    parser = commands.getStore().get(completerKey);
                }

                if (parser == null) {
                    System.out.println("remove:||No completer found for " + key);
                    return;
                }

                LocalValueStore<Object> locals = new LocalValueStore<>(commands.getStore());
                locals.addProvider(Key.of(User.class, Me.class), event.getUser());
                if (event.isFromGuild()) {
                    locals.addProvider(Key.of(Guild.class, Me.class), event.getGuild());
                }
                if (event.getMessageChannel() != null) {
                    locals.addProvider(Key.of(MessageChannel.class, Me.class), event.getMessageChannel());
                }

                // Option with current value
                List<String> args = new ArrayList<>(List.of(option.getValue()));
                ArgumentStack stack = new ArgumentStack(args, locals, commands.getValidators(), commands.getPermisser());
                locals.addProvider(stack);

                List<Command.Choice> choices = new ArrayList<>();
                if (autoParse) {
                    binding.apply(stack);
                } else {
                    Object result = parser.apply(stack);
                    if (!(result instanceof List) || ((List) result).isEmpty()) {
                        long diff = System.currentTimeMillis() - (startNanos / 1_000_000);
                        System.out.println("remove:||No results for " + option.getValue() + " | " + diff);
                        return;
                    }
                    List<Object> resultList = (List<Object>) result;
                    if (resultList.size() > OptionData.MAX_CHOICES) {
                        resultList = resultList.subList(0, OptionData.MAX_CHOICES);
                    }
                    for (Object o : resultList) {
                        String name;
                        String value;
                        if (o instanceof Map.Entry<?, ?> entry) {
                            name = entry.getKey().toString();
                            value = entry.getKey().toString();
                        } else {
                            name = o.toString();
                            value = o.toString();
                        }
                        choices.add(new Command.Choice(name, value));
                    }
                }
                if (!choices.isEmpty()) {
                    double diff = (System.nanoTime() - startNanos) / 1_000_000d;
                    if (diff > 15) {
                        System.out.println("remove:||results for " + option.getValue() + " | " + key + " | " + MathMan.format(diff));
                    }
                    long newCompleteTime = userIdToAutoCompleteTimeNs.get(user.getIdLong());
                    if (newCompleteTime != startNanos) {
                        return;
                    }
                    RateLimitUtil.queue(event.replyChoices(choices));
                }
            }
        });
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        try {
            long start = System.currentTimeMillis();
            userIdToAutoCompleteTimeNs.put(event.getUser().getIdLong(), System.nanoTime());

            RateLimitUtil.queue(event.deferReply(false));

            MessageChannel channel = event.getChannel();
            InteractionHook hook = event.getHook();

            String path = event.getFullCommandName();

            Map<String, String> combined = new HashMap<>();
            List<OptionMapping> options = event.getOptions();
            for (OptionMapping option : options) {
                combined.put(option.getName(), option.getAsString());
            }

            DiscordHookIO io = new DiscordHookIO(hook);
            Guild guild = event.isFromGuild() ? event.getGuild() : null;
            Trocutus.imp().getCommandManager().run(guild, channel, event.getUser(), null, io, path.replace("/", " "), combined, true);
            long end = System.currentTimeMillis();
            if (end - start > 15) {
                System.out.println("remove:||Slash command took " + (end - start) + "ms");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


}
