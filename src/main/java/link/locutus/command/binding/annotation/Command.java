package link.locutus.command.binding.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Command {
    String[] aliases() default {};

    String help() default "";

    String desc() default "";
    String descMethod() default "";
}