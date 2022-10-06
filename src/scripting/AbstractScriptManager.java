package scripting;

import java.io.File;

import java.io.IOException;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import client.MapleClient;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.script.ScriptException;

/**
 *
 * @author Matze
 */
public abstract class AbstractScriptManager {
    
    private static final ScriptEngineManager sem = new ScriptEngineManager();
    
    protected Invocable getInvocable(String path, MapleClient c) {
        return getInvocable(path, c, false);
    }
    
    protected Invocable getInvocable(String path, MapleClient c, boolean npc) {
        path = "scripts/" + path;
        ScriptEngine engine = null;
        
        if (c != null) {
            engine = c.getScriptEngine(path);
        }
        if (engine == null) {
            File scriptFile = new File(path);
            if (!scriptFile.exists()) {
                return null;
            }
            engine = sem.getEngineByName("nashorn");
            if (c != null) {
                c.setScriptEngine(path, engine);
            }
            try (Stream<String> stream = Files.lines(scriptFile.toPath())) {
                String lines = "load('nashorn:mozilla_compat.js');";
                lines += stream.collect(Collectors.joining(System.lineSeparator()));
                engine.eval(lines);
            } catch (final ScriptException | IOException t) {
                System.out.println(t);
                return null;
            }
        } else if (c != null && npc) {
            c.getPlayer().dropMessage(5, "請輸入 @ea 解卡指令。");
        }
        return (Invocable) engine;
    }
}
