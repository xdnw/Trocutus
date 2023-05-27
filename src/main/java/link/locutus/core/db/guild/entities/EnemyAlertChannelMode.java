package link.locutus.core.db.guild.entities;

public enum EnemyAlertChannelMode {
    PING_STRONGER_USERS_IN_RANGE(false),
    PING_USERS_IN_RANGE(true),
    PING_ROLE_IN_RANGE(true),

    PING_ROLE_ALL(false),
    ;

    private final boolean requireInRange;

    EnemyAlertChannelMode(boolean requireInRange) {
        this.requireInRange = requireInRange;
    }

    public boolean requireInRange() {
        return requireInRange;
    }

    public boolean pingUsers() {
        return switch (this) {
            case PING_STRONGER_USERS_IN_RANGE -> true;
            case PING_USERS_IN_RANGE -> true;
            case PING_ROLE_IN_RANGE -> false;
            case PING_ROLE_ALL -> false;
        };
    }

    public boolean pingUsersWeaker() {
        return switch (this) {
            case PING_STRONGER_USERS_IN_RANGE -> true;
            case PING_USERS_IN_RANGE -> false;
            case PING_ROLE_IN_RANGE -> false;
            case PING_ROLE_ALL -> false;
        };
    }

    public boolean pingRole() {
            return switch (this) {
            case PING_STRONGER_USERS_IN_RANGE -> false;
            case PING_USERS_IN_RANGE -> false;
            case PING_ROLE_IN_RANGE -> true;
            case PING_ROLE_ALL -> true;
        };
    }
}
