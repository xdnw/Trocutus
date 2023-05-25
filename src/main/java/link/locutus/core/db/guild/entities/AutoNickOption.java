package link.locutus.core.db.guild.entities;

public enum AutoNickOption {
    FALSE("No nickname given"),
    KINGDOM("Set to kingomd name"),
    DISCORD("Set to discord name")
            ;

    private final String description;

    AutoNickOption(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return name() + ": `" + description + "`";
    }
}
