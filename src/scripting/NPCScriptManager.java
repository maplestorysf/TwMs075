package scripting;

import java.util.Map;
import javax.script.Invocable;
import javax.script.ScriptEngine;

import client.MapleClient;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import javax.script.ScriptException;
import server.life.MapleLifeFactory;
import server.life.MapleNPC;
import server.quest.MapleQuest;
import tools.FileoutputUtil;

public class NPCScriptManager extends AbstractScriptManager {

    private final Map<MapleClient, NPCConversationManager> cms = new WeakHashMap<>();
    private static final NPCScriptManager instance = new NPCScriptManager();

    public static final NPCScriptManager getInstance() {
        return instance;
    }

    public final void start(final MapleClient c, final int npc) {
        start(c, npc, null);
    }

    public final void start(final MapleClient c, final int npc, String script) {
        start(c, npc, 0, script);
    }

    public final void start(final MapleClient c, final int npc, final int mode, String script) {
        final Lock lock = c.getNPCLock();
        lock.lock();
        try {
            MapleNPC CheckNpc = MapleLifeFactory.getNPC(npc);
            if (CheckNpc == null || CheckNpc.getName().equalsIgnoreCase("MISSINGNO")) {
                if (c.getPlayer().isGM()) {
                    c.getPlayer().dropMessage(5, "編號 " + npc + " 不存在");
                }
                dispose(c);
                return;
            }
            if (c.getPlayer().isGM()) {
                c.getPlayer().dropMessage(5, "編號 " + npc + (script == null ? "" : ("(" + script + ")")) + (mode == 0 ? "" : "型號: " + mode));
            }
            if (!cms.containsKey(c) && c.canClickNPC()) {
                Invocable iv;
                if (script == null) {
                    if (mode != 0) {
                        iv = getInvocable("npc/" + npc + "_" + mode + ".js", c, true); //safe disposal
                    } else {
                        iv = getInvocable("npc/" + npc + ".js", c, true); //safe disposal
                    }
                } else {
                    iv = getInvocable("special/" + script + ".js", c, true); //safe disposal
                }

                if (iv == null) {
                    iv = getInvocable("special/notcoded.js", c, true); //safe disposal
                    if (iv == null) {
                        dispose(c);
                        return;
                    }
                }
                final ScriptEngine scriptengine = (ScriptEngine) iv;
                final NPCConversationManager cm = new NPCConversationManager(c, npc, -1, mode, script, (byte) -1, iv);
                if (getInstance() == null) {
                    dispose(c);
                    return;
                }

                cms.put(c, cm);
                scriptengine.put("cm", cm);
                if (c.getPlayer() != null) {
                    c.getPlayer().setConversation(1);
                }
                c.setClickedNPC();
                try {
                    iv.invokeFunction("start"); // Temporary until I've removed all of start
                } catch (NoSuchMethodException nsme) {
                    iv.invokeFunction("action", (byte) 1, (byte) 0, 0);
                }
            } else if (c.getPlayer() != null) {
                //c.getPlayer().dropMessage(5, "你現在不能攻擊或不能跟npc對話,請在對話框打 @解卡/@ea 來解除異常狀態");
            }

        } catch (final ScriptException | NoSuchMethodException e) {
            System.err.println("NPC 腳本錯誤, 它ID為 : " + npc + "." + e);
            if (c.getPlayer() != null) {
                if (c.getPlayer().isGM()) {
                    c.getPlayer().dropMessage(5, "[系統提示] NPC " + npc + "腳本錯誤 " + e + "");
                }
            }
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Error executing NPC script, NPC ID : " + npc + "." + e);
            dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void action(final MapleClient c, final byte mode, final byte type, final int selection) {
        if (mode != -1) {
            final NPCConversationManager cm = cms.get(c);
            if (cm == null || cm.getLastMsg() > -1) {
                return;
            }
            final Lock lock = c.getNPCLock();
            lock.lock();
            try {

                if (cm.pendingDisposal) {
                    dispose(c);
                } else {
                    c.setClickedNPC();
                    cm.getIv().invokeFunction("action", mode, type, selection);
                }
            } catch (Exception e) {
                if (c.getPlayer() != null) {
                    if (c.getPlayer().isGM()) {
                        c.getPlayer().dropMessage(5, "[系統提示] NPC " + cm.getNpc() + "腳本錯誤 " + e + "");
                    }
                }
                System.err.println("NPC 腳本錯誤. 它ID為 : " + cm.getNpc() + ":" + e);
                FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Error executing NPC script, NPC ID : " + cm.getNpc() + "." + e);
                dispose(c);
            } finally {
                lock.unlock();
            }
        }
    }

    public final void startQuest(final MapleClient c, final int npc, final int quest) {
        if (!MapleQuest.getInstance(quest).canStart(c.getPlayer(), null)) {
            return;
        }
        final Lock lock = c.getNPCLock();
        lock.lock();
        try {
            if (!cms.containsKey(c) && c.canClickNPC()) {
                final Invocable iv = getInvocable("quest/" + quest + ".js", c, true);
                if (iv == null) {
                    c.getPlayer().dropMessage(1, "此任務尚未建置，請通知管理員。\r\n任務編號: " + quest);
                    dispose(c);
                    return;
                }
                final ScriptEngine scriptengine = (ScriptEngine) iv;
                final NPCConversationManager cm = new NPCConversationManager(c, npc, quest, 0, null, (byte) 0, iv);
                cms.put(c, cm);
                scriptengine.put("qm", cm);

                c.getPlayer().setConversation(1);
                c.setClickedNPC();
                if (c.getPlayer().isGM()) {
                    c.getPlayer().dropMessage(5, "任務編號 " + quest);
                }
                //System.out.println("NPCID started: " + npc + " startquest " + quest);
                iv.invokeFunction("start", (byte) 1, (byte) 0, 0); // start it off as something
            } else {
                // c.getPlayer().dropMessage(-1, "You already are talking to an NPC. Use @ea if this is not intended.");
            }
        } catch (final ScriptException | NoSuchMethodException e) {
            System.err.println("Error executing Quest script. (" + quest + ")..NPCID: " + npc + ":" + e);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Error executing Quest script. (" + quest + ")..NPCID: " + npc + ":" + e);
            dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void startQuest(final MapleClient c, final byte mode, final byte type, final int selection) {
        final Lock lock = c.getNPCLock();
        final NPCConversationManager cm = cms.get(c);
        if (cm == null || cm.getLastMsg() > -1) {
            return;
        }
        lock.lock();
        try {
            if (cm.pendingDisposal) {
                dispose(c);
            } else {
                c.setClickedNPC();
                cm.getIv().invokeFunction("start", mode, type, selection);
            }
        } catch (ScriptException | NoSuchMethodException e) {
            if (c.getPlayer().isGM()) {
                c.getPlayer().dropMessage(5, "[系統提示]任務腳本:" + cm.getQuest() + "錯誤...NPC: " + cm.getNpc() + ":" + e);
            }
            System.err.println("Error executing Quest script. (" + cm.getQuest() + ")...NPC: " + cm.getNpc() + ":" + e);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Error executing Quest script. (" + cm.getQuest() + ")..NPCID: " + cm.getNpc() + ":" + e);
            dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void endQuest(final MapleClient c, final int npc, final int quest, final boolean customEnd) {
        if (!customEnd && !MapleQuest.getInstance(quest).canComplete(c.getPlayer(), null)) {
            return;
        }
        final Lock lock = c.getNPCLock();
        //final NPCConversationManager cm = cms.get(c);
        lock.lock();
        try {
            if (!cms.containsKey(c) && c.canClickNPC()) {
                final Invocable iv = getInvocable("quest/" + quest + ".js", c, true);
                if (iv == null) {
                    dispose(c);
                    return;
                }
                final ScriptEngine scriptengine = (ScriptEngine) iv;
                final NPCConversationManager cm = new NPCConversationManager(c, npc, quest, 0, null, (byte) 1, iv);
                cms.put(c, cm);
                scriptengine.put("qm", cm);

                c.getPlayer().setConversation(1);
                c.setClickedNPC();
                //System.out.println("NPCID started: " + npc + " endquest " + quest);
                iv.invokeFunction("end", (byte) 1, (byte) 0, 0); // start it off as something
            } else {
                // c.getPlayer().dropMessage(-1, "You already are talking to an NPC. Use @ea if this is not intended.");
            }
        } catch (ScriptException | NoSuchMethodException e) {
            if (c.getPlayer().isGM()) {
                c.getPlayer().dropMessage(5, "[系統提示]任務腳本:" + quest + "錯誤...NPC: " + quest + ":" + e);
            }
            System.err.println("Error executing Quest script. (" + quest + ")..NPCID: " + npc + ":" + e);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Error executing Quest script. (" + quest + ")..NPCID: " + npc + ":" + e);
            dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void endQuest(final MapleClient c, final byte mode, final byte type, final int selection) {
        final Lock lock = c.getNPCLock();
        final NPCConversationManager cm = cms.get(c);
        if (cm == null || cm.getLastMsg() > -1) {
            return;
        }
        lock.lock();
        try {
            if (cm.pendingDisposal) {
                dispose(c);
            } else {
                c.setClickedNPC();
                cm.getIv().invokeFunction("end", mode, type, selection);
            }
        } catch (ScriptException | NoSuchMethodException e) {
            if (c.getPlayer().isGM()) {
                c.getPlayer().dropMessage(5, "[系統提示]任務腳本:" + cm.getQuest() + "錯誤...NPC: " + cm.getNpc() + ":" + e);
            }
            System.err.println("Error executing Quest script. (" + cm.getQuest() + ")...NPC: " + cm.getNpc() + ":" + e);
            FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Error executing Quest script. (" + cm.getQuest() + ")..NPCID: " + cm.getNpc() + ":" + e);
            dispose(c);
        } finally {
            lock.unlock();
        }
    }

    public final void dispose(final MapleClient c) {
        final NPCConversationManager npccm = cms.get(c);
        if (npccm != null) {
            cms.remove(c);
            if (npccm.getType() == -1) {
                c.removeScriptEngine("scripts/npc/" + npccm.getNpc() + ".js");
                if (npccm.getMode() != 0) {
                    c.removeScriptEngine("scripts/npc/" + npccm.getNpc() + "_" + npccm.getMode() + ".js");
                }
                c.removeScriptEngine("scripts/special/" + npccm.getScript() + ".js");
                c.removeScriptEngine("scripts/special/notcoded.js");
            } else {
                c.removeScriptEngine("scripts/quest/" + npccm.getQuest() + ".js");
            }
        }
        if (c.getPlayer() != null && c.getPlayer().getConversation() == 1) {
            c.getPlayer().setConversation(0);
        }
    }

    public final NPCConversationManager getCM(final MapleClient c) {
        return cms.get(c);
    }
}
