package scripting;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import client.MapleClient;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import javax.script.ScriptException;
import server.MaplePortal;
import tools.FileoutputUtil;
import tools.StringUtil;

public class PortalScriptManager {

    private static final PortalScriptManager instance = new PortalScriptManager();
    private final Map<String, PortalScript> scripts = new HashMap();
    private final static ScriptEngineFactory sef = new ScriptEngineManager().getEngineByName("nashorn").getFactory();

    public final static PortalScriptManager getInstance() {
        return instance;
    }

    private final PortalScript getPortalScript(final String scriptName) {
        if (scripts.containsKey(scriptName)) {
            return scripts.get(scriptName);
        }

        final File scriptFile = new File("scripts/portal/" + scriptName + ".js");
        if (!scriptFile.exists()) {
            scripts.put(scriptName, null);
            return null;
        }

        InputStreamReader fr = null;
        final ScriptEngine portal = sef.getScriptEngine();
        try {
            fr = new InputStreamReader(new FileInputStream(scriptFile), StringUtil.codeString(scriptFile));
            CompiledScript compiled = ((Compilable) portal).compile(fr);
            compiled.eval();
        } catch (final FileNotFoundException | UnsupportedEncodingException | ScriptException e) {
            System.err.println("Error executing Portalscript: " + scriptName + ":" + e);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Error executing Portal script. (" + scriptName + ") " + e);
        } finally {
            try {
                if (fr != null) {
                    fr.close();
                }
            } catch (IOException ignore) {
            }
        }
        final PortalScript script = ((Invocable) portal).getInterface(PortalScript.class);
        scripts.put(scriptName, script);
        return script;
    }

    public final void executePortalScript(final MaplePortal portal, final MapleClient c) {
        final PortalScript script = getPortalScript(portal.getScriptName());
        if (c != null && c.getPlayer() != null && c.getPlayer().hasGmLevel(2)) {
            c.getPlayer().dropMessage(5, "傳送門腳本 " + portal.getScriptName());
        }
        if (script != null) {
            try {
                script.enter(new PortalPlayerInteraction(c, portal));
            } catch (Exception e) {
                System.err.println("進入傳送腳本失敗: " + portal.getScriptName() + ":" + e);
            }
        } else {
            System.err.println("未處理的傳送腳本 " + portal.getScriptName() + " 所在地圖 " + c.getPlayer().getMapId());
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Unhandled portal script " + portal.getScriptName() + " on map " + c.getPlayer().getMapId());
        }
        clearScripts();
    }

    public final void clearScripts() {
        scripts.clear();
    }
}
