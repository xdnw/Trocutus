package link.locutus.util;

import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import javax.script.ScriptEngine;

public class ScriptUtil {
    private static final NashornScriptEngineFactory manager = new NashornScriptEngineFactory();
    private static final ScriptEngine engine = manager.getScriptEngine();

    public static ScriptEngine getEngine() {
        return engine;
    }
}
