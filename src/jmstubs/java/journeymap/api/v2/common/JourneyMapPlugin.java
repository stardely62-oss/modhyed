// Compile-only stand-in for JourneyMap API 2.0 (journeymap-api 1.20.1_2.0): the real classes come with JourneyMap.
// Only the members Ender Obelisks uses, with the exact signatures; never packaged.
package journeymap.api.v2.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JourneyMapPlugin {
	String apiVersion();

	String[] dependencies() default {};

	boolean require() default true;
}
