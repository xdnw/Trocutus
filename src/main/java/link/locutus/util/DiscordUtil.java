package link.locutus.util;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import com.google.common.base.Charsets;
import link.locutus.Trocutus;
import link.locutus.command.command.CommandBehavior;
import link.locutus.command.command.IMessageBuilder;
import link.locutus.command.command.IMessageIO;
import link.locutus.command.impl.discord.DiscordChannelIO;
import link.locutus.core.db.entities.kingdom.DBKingdom;
import link.locutus.core.db.guild.GuildDB;
import link.locutus.core.db.guild.GuildKey;
import link.locutus.core.settings.Settings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.json.JSONObject;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class DiscordUtil {
    public static Color BACKGROUND_COLOR = Color.decode("#36393E");

    public static String trimContent(String content) {
        if (content.isEmpty()) return content;
        if (content.charAt(0) == '<') {
            int end = content.indexOf('>');
            if (end != -1) {
                String idStr = content.substring(1, end);
                content = content.substring(end + 1).trim();
                if (content.length() > 0 && Character.isAlphabetic(content.charAt(0)) && MathMan.isInteger(idStr) && Long.parseLong(idStr) == Settings.INSTANCE.APPLICATION_ID) {
                    content = Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + content;
                }
            }
        }
        return content;
    }

    public static CompletableFuture<Message> upload(MessageChannel channel, String title, String body) {
        if (!title.contains(".")) title += ".txt";
        return RateLimitUtil.queue(channel.sendFiles(FileUpload.fromData(body.getBytes(StandardCharsets.ISO_8859_1), title)));
    }

    public static MessageChannel getChannel(Guild guild, String keyOrLong) {
        if (keyOrLong.charAt(0) == '<' && keyOrLong.charAt(keyOrLong.length() - 1) == '>') {
            keyOrLong = keyOrLong.substring(1, keyOrLong.length() - 1);
        }
        if (keyOrLong.charAt(0) == '#') {
            keyOrLong = keyOrLong.substring(1);
        }
        if (MathMan.isInteger(keyOrLong)) {
            long idLong = Long.parseLong(keyOrLong);
            GuildChannel channel = guild != null ? guild.getGuildChannelById(idLong) : null;
            if (channel == null) {
                channel = Trocutus.imp().getDiscordApi().getGuildChannelById(idLong);
            }
            if (channel == null) {
//                channel = Trocutus.imp().getDiscordApi().getPrivateChannelById(keyOrLong);
//                if (channel == null) {
//                    throw new IllegalArgumentException("Invalid channel " + keyOrLong);
//                }
            } else {
                if (!(channel instanceof MessageChannel)) {
                    throw new IllegalArgumentException("Invalid channel <#" + keyOrLong + "> (not a message channel)");
                }
                long channelGuildId = channel.getGuild().getIdLong();
                if (guild != null) {
                    GuildDB db = GuildDB.get(guild);
                    GuildDB faServer = db.getOrNull(GuildKey.FA_SERVER);
                    if (faServer != null && channelGuildId == faServer.getIdLong()) {
                        return (MessageChannel) channel;
                    }
                }
                if (guild != null && channelGuildId != guild.getIdLong()) {
                    GuildDB otherDB = GuildDB.get(channel.getGuild());
                    Map.Entry<Integer, Long> delegate = otherDB.getOrNull(GuildKey.DELEGATE_SERVER);
                    if (delegate == null || delegate.getValue() != guild.getIdLong()) {
                        throw new IllegalArgumentException("Channel: " + keyOrLong + " not in " + guild + " (" + guild + ")");
                    }
                }
            }
            return (MessageChannel) channel;
        }
        List<TextChannel> channel = guild.getTextChannelsByName(keyOrLong, true);
        if (channel.size() == 1) {
            return channel.get(0);
        }
        return null;
    }

    public static String parseArg(List<String> args, String prefix) {
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String next = iter.next();
            if (next.startsWith(prefix + ":")) {
                iter.remove();
                return next.split(":", 2)[1];
            }
            if (next.startsWith(prefix + "=")) {
                iter.remove();
                return next.split("=", 2)[1];
            }
        }
        return null;
    }

    public static Integer parseArgInt(List<String> args, String prefix) {
        String arg = parseArg(args, prefix);
        return arg == null ? null : MathMan.parseInt(arg);
    }

    public static Double parseArgDouble(List<String> args, String prefix) {
        String arg = parseArg(args, prefix);
        return arg == null ? null : MathMan.parseDouble(arg);
    }

    public static void paginate(MessageChannel channel, String title, String command, Integer page, int perPage, List<String> results) {
        paginate(channel, title, command, page, perPage, results, null);
    }

    public static void paginate(MessageChannel channel, String title, String command, Integer page, int perPage, List<String> results, String footer) {
        paginate(channel, null, title, command, page, perPage, results, footer, false);
    }

    public static void paginate(MessageChannel channel, Message message, String title, String command, Integer page, int perPage, List<String> results, String footer, boolean inline) {
        DiscordChannelIO io = new DiscordChannelIO(channel, () -> message);
        paginate(io, title, command, page, perPage, results, footer, inline);
    }

    public static String timestamp(long timestamp, String type) {
        if (type == null) type = "R";
        return "<t:" + (timestamp / 1000L) + ":" + type + ">";
    }

    public static void paginate(IMessageIO io, String title, String command, Integer page, int perPage, List<String> results, String footer, boolean inline) {
        if (results.isEmpty()) {
            System.out.println("Results are empty");
            return;
        }

        int numResults = results.size();
        int maxPage = (numResults - 1) / perPage;
        if (page == null) page = 0;

        int start = page * perPage;
        int end = Math.min(numResults, start + perPage);

        StringBuilder body = new StringBuilder();
        for (int i = start; i < end; i++) {
            body.append(results.get(i)).append('\n');
        }

        Map<String, String> reactions = new LinkedHashMap<>();
        boolean hasPrev = page > 0;
        boolean hasNext = page < maxPage;

        String previousPageCmd;
        String nextPageCmd;

        if (command.charAt(0) == Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX.charAt(0)) {
            String cmdCleared = command.replaceAll("page:" + "[0-9]+", "");
            previousPageCmd = cmdCleared + " page:" + (page - 1);
            nextPageCmd = cmdCleared + " page:" + (page + 1);
        } else if (command.charAt(0) == '{') {
            JSONObject json = new JSONObject(command);
            Map<String, Object> arguments = json.toMap();

            previousPageCmd = new JSONObject(arguments).put("page", page - 1).toString();
            nextPageCmd = new JSONObject(arguments).put("page", page + 1).toString();
        } else {
            String cmdCleared = command.replaceAll("-p " + "[0-9]+", "");
            if (!cmdCleared.startsWith(Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX)) {
                cmdCleared = Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + cmdCleared;
            }
            previousPageCmd = cmdCleared + " -p " + (page - 1);
            nextPageCmd = cmdCleared + " -p " + (page + 1);
        }

        String prefix = inline ? "~" : "";
        if (hasPrev) {
            reactions.put("\u2B05\uFE0F Previous", prefix + previousPageCmd);
        }
        if (hasNext) {
            reactions.put("Next \u27A1\uFE0F", prefix + nextPageCmd);
        }

        IMessageBuilder message = io.getMessage();
        if (message == null || message.getAuthor() == null || message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
            message = io.create();
        }

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle(title);
        builder.setDescription(body.toString());
        if (footer != null) builder.setFooter(footer);

        message.clearEmbeds();
        message.embed(builder.build());
        message.clearButtons();
        message.addCommands(reactions);

        message.send();
    }

    public static void createEmbedCommand(long chanelId, String title, String message, String... reactionArguments) {
        MessageChannel channel = Trocutus.imp().getDiscordApi().getGuildChannelById(chanelId);
        if (channel == null) {
            throw new IllegalArgumentException("Invalid channel " + chanelId);
        }
        createEmbedCommand(channel, title, message, reactionArguments);
    }

    public static void createEmbedCommand(MessageChannel channel, String title, String message, String... reactionArguments) {
        createEmbedCommandWithFooter(channel, title, message, null, reactionArguments);
    }

    public static void createEmbedCommandWithFooter(MessageChannel channel, String title, String message, String footer, String... reactionArguments) {
        if (message.length() > 2000 && reactionArguments.length == 0) {
            if (title.length() >= 1024) title = title.substring(0, 1020) + "...";
            DiscordUtil.upload(channel, title, message);
            return;
        }
        String finalTitle = title;
        createEmbedCommand(channel, new Consumer<EmbedBuilder>() {
            @Override
            public void accept(EmbedBuilder builder) {
                String titleFinal = finalTitle;
                if (titleFinal.length() >= 200) {
                    titleFinal = titleFinal.substring(0, 197) + "..";
                }
                builder.setTitle(titleFinal);
                builder.setDescription(message);
                if (footer != null) builder.setFooter(footer);
            }
        }, reactionArguments);
    }

    public static void createEmbedCommand(MessageChannel channel, Consumer<EmbedBuilder> builder, String... reactionArguments) {
        if (reactionArguments.length % 2 != 0) {
            throw new IllegalArgumentException("invalid pairs: " + StringMan.getString(reactionArguments));
        }
        HashMap<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < reactionArguments.length; i+=2) {
            map.put(reactionArguments[i], reactionArguments[i + 1]);
        }
        createEmbedCommand(channel, builder, map);
    }

    public static Message getMessage(String url) {
        String[] split = url.split("/");
        long messageId = Long.parseLong(split[split.length - 1]);
        long channelId = Long.parseLong(split[split.length - 2]);
        long guildId = Long.parseLong(split[split.length - 3]);

        return getMessage(guildId, channelId, messageId);
    }

    public static Message getMessage(long guildId, long channelId, long messageId) {
        Guild guild = Trocutus.imp().getDiscordApi().getGuildById(guildId);
        if (guild == null) return null;
        GuildMessageChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) return null;
        Message message = RateLimitUtil.complete(channel.retrieveMessageById(messageId));
        return message;
    }


    public static void createEmbedCommand(MessageChannel channel, Consumer<EmbedBuilder> consumer, Map<String, String> reactionArguments) {
        EmbedBuilder builder = new EmbedBuilder();
        consumer.accept(builder);
        MessageEmbed embed = builder.build();

        new DiscordChannelIO(channel, null).create().embed(embed).addCommands(reactionArguments).send();
    }

    public static Map<String, String> getReactions(MessageEmbed embed) {
        MessageEmbed.ImageInfo img = embed.getImage();
        MessageEmbed.Thumbnail thumb = embed.getThumbnail();

        if (thumb == null && img == null) {
            return null;
        }

        String url = thumb != null && thumb.getUrl() != null && !thumb.getUrl().isEmpty() ? thumb.getUrl() : img.getUrl();
        if (url == null) {
            return null;
        }

        String placeholder = "https://example.com?";
        if (!url.startsWith(placeholder)) {
            return null;
        }
        String query = url.substring(placeholder.length());
        List<NameValuePair> entries = URLEncodedUtils.parse(query, Charsets.UTF_8);
        Map<String, String> map = new LinkedHashMap<>();
        for (NameValuePair entry : entries) {
            map.put(entry.getName(), entry.getValue());
        }
        return map;
    }

    public static Map<String, Role> getAARoles(Collection<Role> roles) {
        Map<String, Role> allianceRoles = null;
        for (Role role : roles) {
            if (role.getName().startsWith("AA ")) {
                String[] split = role.getName().split(" ");
                String idStr = split[1];
                if (allianceRoles == null) {
                    allianceRoles = new HashMap<>();
                }
                allianceRoles.put(idStr, role);
            }
        }
        return allianceRoles == null ? Collections.emptyMap() : allianceRoles;
    }

    public static Role getRole(Guild server, String arg) {
        List<Role> discordRoleOpt = server.getRolesByName(arg, true);
        if (discordRoleOpt.size() == 1) {
            return discordRoleOpt.get(0);
        }
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '@') {
            arg = arg.substring(1);
            if (arg.charAt(0) == '&') arg = arg.substring(1);
        }
        if (MathMan.isInteger(arg)) {
            long discordId = Long.parseLong(arg);
            return server.getRoleById(discordId);
        }
        return null;
    }

    public static User getMention(String arg) {
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '@') {
            String idStr = arg.substring(1);
            if (idStr.charAt(0) == '!') idStr = idStr.substring(1);
            if (MathMan.isInteger(idStr)) {
                long discordId = Long.parseLong(idStr);
                return Trocutus.imp().getDiscordApi().getUserById(discordId);
            }
        }
        if (MathMan.isInteger(arg)) {
            User user = Trocutus.imp().getDiscordApi().getUserById(Long.parseLong(arg));
            if (user != null) return user;
        }
        String[] split = arg.split("#");
        if (split.length == 2) {
            return Trocutus.imp().getDiscordApi().getUserByTag(split[0], split[1]);
        }
        if (split[0].isEmpty()) return null;
        List<User> users = Trocutus.imp().getDiscordApi().getUsersByName(split[0], true);
        return users.size() == 1 ? users.get(0) : null;
    }

    public static String toDiscordChannelString(String name) {
        name = name.toLowerCase().replace("-", " ").replaceAll("[ ]+", " ").replaceAll("[^a-z0-9 ]", "");
        name = name.replaceAll(" +", " ");
        name = name.trim();
        name = name.replaceAll(" ", "-");
        return name;
    }

    public static User findUser(String arg) {
        if (arg.contains("#")) {
            String[] split = arg.split("#");
            if (split.length == 2) {
                return Trocutus.imp().getDiscordApi().getUserByTag(split[0], split[1]);
            }
        } else {
            List<User> users = Trocutus.imp().getDiscordApi().getUsersByName(arg, true);
            if (users.size() == 1) {
                return users.get(0);
            }
        }
        return getUser(arg);
    }

    public static User getUser(String arg) {
        if (arg.charAt(0) == '<' && arg.charAt(arg.length() - 1) == '>') {
            arg = arg.substring(1, arg.length() - 1);
        }
        if (arg.charAt(0) == '@') {
            arg = arg.substring(1);
        }
        if (arg.charAt(0) == '!') {
            arg = arg.substring(1);
        }
        if (MathMan.isInteger(arg)) {
            return Trocutus.imp().getDiscordApi().getUserById(Long.parseLong(arg));
        }
        DBKingdom nation = DBKingdom.parse(arg);
        if (nation != null) {
            return nation.getUser();
        }
        return null;
    }

    public static CompletableFuture<Message> sendMessage(InteractionHook hook, String message) {
        if (message.length() > 20000) {
            if (message.length() < 200000) {
                return RateLimitUtil.queue(hook.sendFiles(FileUpload.fromData(message.getBytes(StandardCharsets.ISO_8859_1), "message.txt")));
            }
            new Exception().printStackTrace();
            System.out.println(message);
            throw new IllegalArgumentException("Cannot send message of this length: " + message.length());
        }
        if (message.contains("@everyone")) {
            message = message.replace("@everyone", "");
        }
        if (message.contains("@here")) {
            message = message.replace("@here", "");
        }
        message = WordUtils.wrap(message, 2000, "\n", true, ",");
        if (message.length() > 2000) {
            String[] lines = message.split("\\r?\\n");
            StringBuilder buffer = new StringBuilder();
            for (String line : lines) {
                if (buffer.length() + 1 + line.length() > 2000) {
                    String str = buffer.toString().trim();
                    if (!str.isEmpty()) {
                        RateLimitUtil.complete(hook.sendMessage(str));
                    }
                    buffer.setLength(0);
                }
                buffer.append('\n').append(line);
            }
            String finalMsg = buffer.toString().trim();
            if (finalMsg.length() != 0) {
                return RateLimitUtil.queue(hook.sendMessage(finalMsg));
            }
        } else if (!message.isEmpty()) {
            return RateLimitUtil.queue(hook.sendMessage(message));
        }
        return null;
    }

    public static CompletableFuture<Message> sendMessage(MessageChannel channel, String message) {
        if (message.length() > 20000) {
            if (message.length() < 200000) {
                return DiscordUtil.upload(channel, "message.txt", message);
            }
            new Exception().printStackTrace();
            throw new IllegalArgumentException("Cannot send message of this length: " + message.length());
        }
        if (message.contains("@everyone")) {
            message = message.replace("@everyone", "");
        }
        if (message.contains("@here")) {
            message = message.replace("@here", "");
        }
        message = WordUtils.wrap(message, 2000, "\n", true, ",");
        if (message.length() > 2000) {
            String[] lines = message.split("\\r?\\n");
            StringBuilder buffer = new StringBuilder();
            for (String line : lines) {
                if (buffer.length() + 1 + line.length() > 2000) {
                    String str = buffer.toString().trim();
                    if (!str.isEmpty()) {
                        RateLimitUtil.complete(channel.sendMessage(str));
                    }
                    buffer.setLength(0);
                }
                buffer.append('\n').append(line);
            }
            if (buffer.length() != 0) {
                return RateLimitUtil.queue(channel.sendMessage(buffer));
            }
        } else if (!message.isEmpty()) {
            return RateLimitUtil.queue(channel.sendMessage(message));
        }
        return null;
    }

    public static void withWebhook(String name, String url, WebhookEmbed embed) {
        WebhookClientBuilder builder = new WebhookClientBuilder(url); // or id, token
        builder.setThreadFactory((job) -> {
            Thread thread = new Thread(job);
            thread.setName(name);
            thread.setDaemon(true);
            return thread;
        });
        builder.setWait(true);
        try (WebhookClient client = builder.build()) {
            client.send(embed);
        }
    }

    public static Category getCategory(Guild guild, String categoryStr) {
        if (categoryStr.startsWith("<") && categoryStr.endsWith(">")) {
            categoryStr = categoryStr.substring(1, categoryStr.length() - 1);
        }
        if (categoryStr.charAt(0) == '#') categoryStr = categoryStr.substring(1);
        if (MathMan.isInteger(categoryStr)) {
            Category category = guild.getCategoryById(Long.parseLong(categoryStr));
            if (category != null) return category;

            if (guild != null) {
                GuildDB db = GuildDB.get(guild);
                GuildDB faServer = db.getOrNull(GuildKey.FA_SERVER);
                Set<Guild> guilds = new HashSet<>();
                if (faServer != null) {
                    guilds.add(faServer.getGuild());
                }
                for (GuildDB otherDB : Trocutus.imp().getGuildDatabases().values()) {
                    Map.Entry<Integer, Long> delegate = otherDB.getOrNull(GuildKey.DELEGATE_SERVER);
                    if (delegate != null && delegate.getValue() == guild.getIdLong()) {
                        guilds.add(otherDB.getGuild());
                    }
                }
                for (Guild other : guilds) {
                    if (other == guild) continue;
                    category = other.getCategoryById(Long.parseLong(categoryStr));
                    if (category != null) return category;
                }
            }
        }
        List<Category> categories = guild.getCategoriesByName(categoryStr, true);
        if (categories.size() == 1) {
            return categories.get(0);
        }
        return null;
    }
    public static void pending(IMessageIO output, JSONObject command, String title, String desc) {
        pending(output, command, title, desc, "force");
    }
    public static void pending(IMessageIO output, JSONObject command, String title, String desc, String forceFlag) {
        String forceCmd = command.put(forceFlag, "true").toString();
        output.create()
                .embed("Confirm: " + title, desc)
                .commandButton(CommandBehavior.DELETE_MESSAGE, forceCmd, "Confirm")
                .send();
    }

    public static void pending(MessageChannel channel, Message message, String title, String desc, char f) {
        pending(channel, message, title, desc, f, "");
    }

    public static void pending(MessageChannel channel, Message message, String title, String desc, char f, String cmdAppend) {
        String cmd = DiscordUtil.trimContent(message.getContentRaw()) + " -" + f + cmdAppend;
        DiscordUtil.createEmbedCommand(channel, "Confirm: " + title, desc, "Confirm", cmd);
    }

    public static String getChannelUrl(GuildMessageChannel channel) {
        return "https://discord.com/channels/" + channel.getGuild().getIdLong() + "/" + channel.getIdLong();
    }

    public static String cityRangeToString(Map.Entry<Integer, Integer> range) {
        if (range.getValue() == Integer.MAX_VALUE) {
            return "c" + range.getKey() + "+";
        }
        return "c" + range.getKey() + "-" + range.getValue();
    }

    public static Category findFreeCategory(Collection<Category> categories) {
        for (Category cat : categories) {
            if (cat.getChannels().size() < 50) return cat;
        }
        return null;
    }

    public static User getUser(long userId) {
        return Trocutus.imp().getDiscordApi().getUserById(userId);
    }
}
