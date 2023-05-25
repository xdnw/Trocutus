package link.locutus.core.db.guild.entities;

public enum AutoRoleOption {
    FALSE("No roles given"),
    ALL("Roles for the alliance"),
    ALLIES("Roles for allies (e.g. if a coalition server)"),
            ;

    private final String description;

    AutoRoleOption(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return name() + ": `" + description + "`";
    }
}
