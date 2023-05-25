package link.locutus.core.db.guild.entities;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

public enum Coalition {
    DNR("Alliances to inclide members and applicants in the Do Not Raid list"){
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.hasAny(user, guild, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
        }
    },
    DNR_MEMBER("Alliances to include members of in the Do Not Raid list"){
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.hasAny(user, guild, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
        }
    },
    CAN_RAID("Alliances to not include in the Do Not Raid list") {
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.hasAny(user, guild, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
        }
    },
    FA_FIRST("Alliances to e.g. request peace before countering") {
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.hasAny(user, guild, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
        }
    },
    CAN_RAID_INACTIVE("Alliances to not include inactives in the Do Not Raid list") {
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.hasAny(user, guild, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
        }
    },
    COUNTER("Alliances to always counter") {
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.hasAny(user, guild, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
        }
    },
    IGNORE_FA("Alliances to not ping fa for") {
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.hasAny(user, guild, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
        }
    },
    ENEMIES("Enemies") {
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.hasAny(user, guild, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS);
        }
    },
    ALLIES("Allies"),
    MASKED_ALLIANCES("Additional alliances to mask with (if alliance masking is enabled)") {
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.hasAny(user, guild, Roles.ADMIN, Roles.INTERNAL_AFFAIRS);
        }
    },
    WHITELISTED("Is whitelisted to use locutus commands (root admin)") {
        @Override
        public boolean hasPermission(Guild guild, User user) {
            return Roles.ADMIN.hasOnRoot(user);
        }
    },

    ;

    private final String desc;

    Coalition() {
        this("");
    }

    Coalition(String desc) {
        this.desc = desc;
    }

    public String getDescription() {
        return desc;
    }

    public boolean hasPermission(Guild guild, User user) {
        return Roles.ADMIN.has(user, guild) ;
    }

    @Override
    public String toString() {
        return name() + ": `" + desc + "`";
    }

    public static Coalition getOrNull(String input) {
        try {
            return Coalition.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void checkPermission(String input, Guild guild, User user) {
        Coalition type = getOrNull(input);
        if (type != null && !type.hasPermission(guild, user)) {
            throw new IllegalArgumentException("You do not have permission to modify `" + type.name() + "`");
        }
    }
}
