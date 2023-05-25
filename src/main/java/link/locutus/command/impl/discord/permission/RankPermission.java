package link.locutus.command.impl.discord.permission;


import link.locutus.core.api.alliance.Rank;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface RankPermission {
    Rank value() default Rank.MEMBER;
}
