package client.messages.commands;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.MapleClient;
import client.SkillFactory;
import constants.ServerConstants;
import database.DatabaseConnection;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.world.World;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import server.maps.MapleMap;
import tools.FileoutputUtil;
import tools.StringUtil;
import tools.packet.CWvsContext;
/**
 *
 * @author Windyboy
 */
public class PracticerCommand {
    public static ServerConstants.PlayerGMRank getPlayerLevelRequired() {
        return ServerConstants.PlayerGMRank.實習生;
    }
    public static class WarpT extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            List<MapleCharacter> chrs = new LinkedList<>();
            String input = splitted[1].toLowerCase();
            MapleCharacter smart_victim = null;
            StringBuilder sb = new StringBuilder();
            for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                for (MapleCharacter chr : cserv.getPlayerStorage().getAllCharactersThreadSafe()) {
                    String name = chr.getName().toLowerCase();
                    if (name.contains(input)) {
                        if (smart_victim == null) {
                            smart_victim = chr;
                        }
                        chrs.add(chr);
                    }
                }
            }
            if (chrs.size() > 1) {
                sb.append("尋找到的玩家共").append(chrs.size()).append("位 名單如下 : ");
                c.getPlayer().dropMessage(5, sb.toString());
                for (MapleCharacter list : chrs) {
                    c.getPlayer().dropMessage(5, "頻道" + list.getClient().getChannel() + ": " + list.getName() + "(" + list.getId() + ") -- " + list.getMapId() + "(" + list.getMap().getMapName() + ")");
                }
                return true;
            } else if (chrs.isEmpty()) {
                c.getPlayer().dropMessage(6, "沒有搜尋到名稱含有 '" + input + "' 的角色");
            } else if (smart_victim != null) {
                c.getPlayer().changeMap(smart_victim.getMap(), smart_victim.getMap().findClosestSpawnpoint(smart_victim.getTruePosition()));
            } else {
                c.getPlayer().dropMessage(6, "角色不存在或是不在線上");
            }
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!WarpT [玩家名稱片段] - 移動到某個地圖或某個玩家所在的地方").toString();
        }
    }
    public static class Warp extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            String input = "";
            try {
                input = splitted[1];
            } catch (Exception ex) {
            }
            MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(input);
            if (victim != null) {
                if (splitted.length == 2) {
                    c.getPlayer().changeMap(victim.getMap(), victim.getMap().findClosestSpawnpoint(victim.getPosition()));
                } else {
                    MapleMap target = null;
                    try {
                        target = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(Integer.parseInt(splitted[2]));
                    } catch (Exception ex) {
                    }
                    if (target == null) {
                        victim = CashShopServer.getPlayerStorage().getCharacterByName(input);
                        if (victim == null) {
                            c.getPlayer().dropMessage(6, "地圖不存在");
                        } else {
                            c.getPlayer().dropMessage("玩家「" + input + "」目前位於商城");
                        }
                    } else {
                        c.getPlayer().changeMap(target, target.getPortal(0));
                    }
                }
            } else {
                int ch = World.Find.findChannel(input);
                if (ch < 0) {
                    Integer map = null;
                    MapleMap target = null;
                    try {
                        map = Integer.parseInt(input);
                        target = c.getChannelServer().getMapFactory().getMap(map);
                    } catch (Exception ex) {
                        if (map == null || target == null) {
                            c.getPlayer().dropMessage(6, "地圖不存在");
                            return true;
                        }
                    }
                    if (target == null) {
                        c.getPlayer().dropMessage(6, "地圖不存在");
                    } else {
                        c.getPlayer().changeMap(target, target.getPortal(0));
                    }
                } else {
                    victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(input);
                    if (victim != null) {
                        if (victim.getMapId() != c.getPlayer().getMapId()) {
                            final MapleMap mapp = c.getChannelServer().getMapFactory().getMap(victim.getMapId());
                            c.getPlayer().changeMap(mapp, mapp.getPortal(0));
                        }
                        c.getPlayer().dropMessage(6, "正在改變頻道請等待");
                        c.getPlayer().changeChannel(ch);
                    } else {
                        c.getPlayer().dropMessage(6, "角色不存在");
                    }
                }
            }
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!warp [玩家名稱] <地圖ID> - 移動到某個地圖或某個玩家所在的地方").toString();
        }
    }
    public static class WarpID extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            int input = 0;
            try {
                input = Integer.parseInt(splitted[1]);
            } catch (Exception ex) {
            }
            int ch = World.Find.findChannel(input);
            if (ch < 0) {
                MapleCharacter victim = CashShopServer.getPlayerStorage().getCharacterById(input);
                if (victim == null) {
                    c.getPlayer().dropMessage(6, "玩家編號[" + input + "] 不在線上");
                } else {
                    c.getPlayer().dropMessage("玩家編號「" + input + "」目前位於商城");
                }
                return true;
            }
            MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterById(input);
            if (victim != null) {
                if (splitted.length == 2) {
                    c.getPlayer().changeMap(victim.getMap(), victim.getMap().findClosestSpawnpoint(victim.getPosition()));
                } else {
                    MapleMap target = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(Integer.parseInt(splitted[2]));
                    if (target == null) {
                        c.getPlayer().dropMessage(6, "地圖不存在");
                    } else {
                        victim.changeMap(target, target.getPortal(0));
                    }
                }
            } else {
                try {
                    victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(Integer.parseInt(splitted[1]));
                    if (victim != null) {
                        if (victim.getMapId() != c.getPlayer().getMapId()) {
                            final MapleMap mapp = c.getChannelServer().getMapFactory().getMap(victim.getMapId());
                            c.getPlayer().changeMap(mapp, mapp.getPortal(0));
                        }
                        c.getPlayer().dropMessage(6, "正在改變頻道請等待");
                        c.getPlayer().changeChannel(ch);
                    } else {
                        c.getPlayer().dropMessage(6, "角色不存在");
                    }
                } catch (Exception e) {
                    c.getPlayer().dropMessage(6, "出問題了 " + e.getMessage());
                }
            }
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!warpID [玩家編號] - 移動到某個玩家所在的地方").toString();
        }
    }
    public static class Ban extends CommandExecute {
        protected boolean hellban = false;
        private String getCommand() {
            return "Ban";
        }
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            StringBuilder sb = new StringBuilder(c.getPlayer().getName());
            sb.append(" 封鎖 ").append(splitted[1]).append(": ").append(StringUtil.joinStringFrom(splitted, 2));
            boolean offline = false;
            MapleCharacter target = null;
            String name = "";
            String input = "null";
            try {
                name = splitted[1];
                input = splitted[2];
            } catch (Exception ex) {
            }
            int ch = World.Find.findChannel(name);
            if (ch >= 1) {
                target = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);
            } else {
                target = CashShopServer.getPlayerStorage().getCharacterByName(name);
            }
            if (target == null) {
                if (c.getPlayer().OfflineBanByName(name, sb.toString())) {
                    c.getPlayer().dropMessage(6, "[" + getCommand() + "] 成功離線封鎖 " + splitted[1] + ".");
                    offline = true;
                } else {
                    c.getPlayer().dropMessage(6, "[" + getCommand() + "] 封鎖失敗 " + splitted[1]);
                    return true;
                }
            } else {
                if (Ban(c, target, sb) != 1) {
                    return true;
                }
            }
            FileoutputUtil.logToFile("封鎖/指令封鎖名單.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " " + c.getPlayer().getName() + " 封鎖了 " + splitted[1] + " 原因: " + sb.toString() + " 是否離線封鎖: " + offline);
            String reason = "null".equals(input) ? "使用違法程式練功" : StringUtil.joinStringFrom(splitted, 2);
            World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, "[封鎖系統] " + splitted[1] + " 因為" + reason + "而被管理員永久停權。"));
            String msg = "[GM 密語] GM " + c.getPlayer().getName() + "  封鎖了 " + splitted[1] + " 是否離線封鎖 " + offline + " 原因：" + reason;
            World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, msg));
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!ban <玩家> <原因> - 封鎖玩家").toString();
        }
        public int Ban(MapleClient c, MapleCharacter target, StringBuilder sb) {
            if (c.getPlayer().getGMLevel() >= target.getGMLevel()) {
                sb.append(" (IP: ").append(target.getClient().getSessionIPAddress()).append(")");
                if (target.ban(sb.toString(), c.getPlayer().hasGmLevel(5), false, hellban)) {
                    c.getPlayer().dropMessage(6, "[" + getCommand() + "] 成功封鎖 " + target.getName() + ".");
                    target.getClient().getSession().close();
                    target.getClient().disconnect(true, false);
                    return 1;
                } else {
                    c.getPlayer().dropMessage(6, "[" + getCommand() + "] 封鎖失敗.");
                    return 0;
                }
            } else {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] 無法封鎖GMs...");
                return 0;
            }
        }
    }
    public static class BanID extends CommandExecute {
        protected boolean hellban = false;
        private String getCommand() {
            return "Ban";
        }
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            StringBuilder sb = new StringBuilder(c.getPlayer().getName());
            sb.append(" 封鎖 ").append(splitted[1]).append(": ").append(StringUtil.joinStringFrom(splitted, 2));
            boolean offline = false;
            boolean ban = false;
            MapleCharacter target;
            int id = 0;
            String input = "null";
            try {
                id = Integer.parseInt(splitted[1]);
                input = splitted[2];
            } catch (Exception ex) {
            }
            int ch = World.Find.findChannel(id);
            String name = c.getPlayer().getCharacterNameById(id);
            target = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(id);
            if (target == null) {
                target = CashShopServer.getPlayerStorage().getCharacterById(id);
                if (target == null) {
                    if (c.getPlayer().OfflineBanById(id, sb.toString())) {
                        c.getPlayer().dropMessage(6, "[" + getCommand() + "] 成功離線封鎖 " + name + ".");
                        offline = true;
                    } else {
                        c.getPlayer().dropMessage(6, "[" + getCommand() + "] 封鎖失敗 " + splitted[1]);
                        return true;
                    }
                } else {
                    if (Ban(c, target, sb) != 1) {
                        return true;
                    }
                }
            } else {
                if (Ban(c, target, sb) != 1) {
                    return true;
                }
                name = target.getName();
            }
            FileoutputUtil.logToFile("封鎖/指令封鎖名單.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " " + c.getPlayer().getName() + " 封鎖了 " + name + " 原因: " + sb.toString() + " 是否離線封鎖: " + offline);
            String reason = "null".equals(input) ? "使用違法程式練功" : StringUtil.joinStringFrom(splitted, 2);
            World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, "[封鎖系統] " + name + " 因為" + reason + "而被管理員永久停權。"));
            String msg = "[GM 密語] GM " + c.getPlayer().getName() + "  封鎖了 " + name + " 是否離線封鎖 " + offline + " 原因：" + reason;
            World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, msg));
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!BanID <玩家ID> <原因> - 封鎖玩家").toString();
        }
        public int Ban(MapleClient c, MapleCharacter target, StringBuilder sb) {
            if (c.getPlayer().getGMLevel() >= target.getGMLevel()) {
                sb.append(" (IP: ").append(target.getClient().getSessionIPAddress()).append(")");
                if (target.ban(sb.toString(), c.getPlayer().hasGmLevel(5), false, hellban)) {
                    c.getPlayer().dropMessage(6, "[" + getCommand() + "] 成功封鎖 " + target.getName() + ".");
                    target.getClient().getSession().close();
                    target.getClient().disconnect(true, false);
                    return 1;
                } else {
                    c.getPlayer().dropMessage(6, "[" + getCommand() + "] 封鎖失敗.");
                    return 0;
                }
            } else {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] 無法封鎖GMs...");
                return 0;
            }
        }
    }
    public static class CnGM extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(5, "<GM聊天視窗>" + "頻道" + c.getPlayer().getClient().getChannel() + " [" + c.getPlayer().getName() + "](" + c.getPlayer().getId() + ") : " + StringUtil.joinStringFrom(splitted, 1)));
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!cngm <訊息> - GM聊天").toString();
        }
    }
    public static class Hide extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            SkillFactory.getSkill(9001004).getEffect(1).applyTo(c.getPlayer());
            c.getPlayer().dropMessage(6, "管理員隱藏 = 開啟 \r\n 解除請輸入!unhide");
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!hide - 隱藏").toString();
        }
    }
    public static class UnHide extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            c.getPlayer().dispelBuff(9001004);
            c.getPlayer().dropMessage(6, "管理員隱藏 = 關閉 \r\n 開啟請輸入!hide");
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!unhide - 解除隱藏").toString();
        }
    }
//
//    public static class 精靈商人訊息 extends CommandExecute {
//
//        @Override
//        public boolean execute(MapleClient c, String[] splitted) {
//            MapleCharacter p = c.getPlayer();
//            boolean x = p.get_control_精靈商人();
//            if (x) {
//                p.control_精靈商人(false);
//            } else {
//                p.control_精靈商人(true);
//            }
//            x = p.get_control_精靈商人();
//            p.dropMessage("目前精靈商人購買訊息狀態: " + (x ? "開啟 " : " 關閉 ") + "");
//            return true;
//        }
//
//        @Override
//        public String getMessage() {
//            return new StringBuilder().append("!精靈商人訊息 - 開啟精靈商人購買訊息顯示").toString();
//        }
//    }
//
//    public static class 玩家私聊 extends CommandExecute {
//
//        @Override
//        public boolean execute(MapleClient c, String[] splitted) {
//            MapleCharacter p = c.getPlayer();
//            boolean x = p.get_control_玩家私聊();
//            if (x) {
//                p.control_玩家私聊(false);
//            } else {
//                p.control_玩家私聊(true);
//            }
//            x = p.get_control_玩家私聊();
//            p.dropMessage("目前玩家私聊狀態: " + (x ? "開啟 " : "關閉 ") + "");
//            return true;
//        }
//
//        @Override
//        public String getMessage() {
//            return new StringBuilder().append("!玩家私聊 - 開啟玩家訊息顯示").toString();
//        }
//    }
    public static class online extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            int total = 0;
            int curConnected = c.getChannelServer().getConnectedClients();
            c.getPlayer().dropMessage(6, "-------------------------------------------------------------------------------------");
            c.getPlayer().dropMessage(6, new StringBuilder().append("頻道: ").append(c.getChannelServer().getChannel()).append(" 線上人數: ").append(curConnected).toString());
            total += curConnected;
            for (MapleCharacter chr : c.getChannelServer().getPlayerStorage().getAllCharactersThreadSafe()) {
                if (chr != null && c.getPlayer().getGMLevel() >= chr.getGMLevel()) {
                    StringBuilder ret = new StringBuilder();
                    ret.append(" 角色暱稱 ");
                    ret.append(StringUtil.getRightPaddedStr(chr.getName(), ' ', 13));
                    ret.append(" ID: ");
                    ret.append(StringUtil.getRightPaddedStr(chr.getId() + "", ' ', 5));
                    ret.append(" 等級: ");
                    ret.append(StringUtil.getRightPaddedStr(String.valueOf(chr.getLevel()), ' ', 3));
                    ret.append(" 職業: ");
                    ret.append(StringUtil.getRightPaddedStr(String.valueOf(chr.getJob()), ' ', 4));
                    if (chr.getMap() != null) {
                        ret.append(" 地圖: ");
                        ret.append(chr.getMapId());
                        c.getPlayer().dropMessage(6, ret.toString());
                    }
                }
            }
            c.getPlayer().dropMessage(6, new StringBuilder().append("當前頻道總計線上人數: ").append(total).toString());
            c.getPlayer().dropMessage(6, "-------------------------------------------------------------------------------------");
            int channelOnline = c.getChannelServer().getConnectedClients();
            int totalOnline = 0;
            /*伺服器總人數*/
            for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                totalOnline += cserv.getConnectedClients();
            }
            c.getPlayer().dropMessage(6, new StringBuilder().append("當前伺服器總計線上人數: ").append(totalOnline).append("個").toString());
            c.getPlayer().dropMessage(6, "-------------------------------------------------------------------------------------");
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!online - 查看線上人數").toString();
        }
    }
    public static class onlineGM extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            int channelOnline = 0;
            int totalOnline = 0;
            int GmInChannel = 0;
            List<MapleCharacter> chrs = new LinkedList<>();
            /*當前頻道總GM數*/
            for (MapleCharacter chr : c.getChannelServer().getPlayerStorage().getAllCharactersThreadSafe()) {
                if (chr.getGMLevel() > 0) {
                    channelOnline++;
                }
            }
            /*伺服器總GM數*/
            for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                for (MapleCharacter chr : cserv.getPlayerStorage().getAllCharactersThreadSafe()) {
                    if (chr != null && chr.getGMLevel() > 0) {
                        totalOnline++;
                    }
                }
            }
            c.getPlayer().dropMessage(6, "-------------------------------------------------------------------------------------");
            for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                for (MapleCharacter chr : cserv.getPlayerStorage().getAllCharactersThreadSafe()) {
                    if (chr != null && chr.getGMLevel() > 0) {
                        chrs.add(chr);
                    }
                }
                GmInChannel = chrs.size();
                if (GmInChannel > 0) {
                    c.getPlayer().dropMessage(6, new StringBuilder().append("頻道: ").append(cserv.getChannel()).append(" 線上GM人數: ").append(GmInChannel).toString());
                    for (MapleCharacter chr : chrs) {
                        if (chr != null) {
                            StringBuilder ret = new StringBuilder();
                            ret.append(" GM暱稱 ");
                            ret.append(StringUtil.getRightPaddedStr(chr.getName(), ' ', 13));
                            ret.append(" ID: ");
                            ret.append(StringUtil.getRightPaddedStr(chr.getId() + "", ' ', 5));
                            ret.append(" 權限: ");
                            ret.append(StringUtil.getRightPaddedStr(String.valueOf(chr.getGMLevel()), ' ', 3));
                            c.getPlayer().dropMessage(6, ret.toString());
                        }
                    }
                }
                chrs = new LinkedList<>();
            }
            c.getPlayer().dropMessage(6, new StringBuilder().append("當前頻道總計GM線上人數: ").append(channelOnline).toString());
            c.getPlayer().dropMessage(6, "-------------------------------------------------------------------------------------");
            c.getPlayer().dropMessage(6, new StringBuilder().append("當前伺服器GM總計線上人數: ").append(totalOnline).append("個").toString());
            c.getPlayer().dropMessage(6, "-------------------------------------------------------------------------------------");
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!onlineGM - 查看線上人數GM").toString();
        }
    }
    public static class WarpHere extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]);
            if (victim != null) {
                victim.changeMap(c.getPlayer().getMap(), c.getPlayer().getMap().findClosestSpawnpoint(c.getPlayer().getPosition()));
            } else {
                int ch = World.Find.findChannel(splitted[1]);
                if (ch < 0) {
                    c.getPlayer().dropMessage(5, "找不到");
                } else {
                    victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(splitted[1]);
                    c.getPlayer().dropMessage(5, "正在把玩家傳到這來");
                    victim.dropMessage(5, "正在傳送到GM那邊");
                    if (victim.getMapId() != c.getPlayer().getMapId()) {
                        final MapleMap mapp = victim.getClient().getChannelServer().getMapFactory().getMap(c.getPlayer().getMapId());
                        victim.changeMap(mapp, mapp.getPortal(0));
                    }
                    victim.changeChannel(c.getChannel());
                }
            }
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!warphere 把玩家傳送到這裡").toString();
        }
    }
    public static class Whoshere extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            StringBuilder builder = new StringBuilder("在地圖上的玩家 : ").append(c.getPlayer().getMap().getCharactersThreadsafe().size()).append(", ");
            for (MapleCharacter chr : c.getPlayer().getMap().getCharactersThreadsafe()) {
                if (builder.length() > 150) { // wild guess :o
                    builder.setLength(builder.length() - 2);
                    c.getPlayer().dropMessage(6, builder.toString());
                    builder = new StringBuilder();
                }
                builder.append(MapleCharacterUtil.makeMapleReadable(chr.getName()));
                builder.append(", ");
            }
            builder.setLength(builder.length() - 2);
            c.getPlayer().dropMessage(6, builder.toString());
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!Whoshere - 查地圖上玩家").toString();
        }
    }
    public static class UnHellBan extends UnBan {
        public UnHellBan() {
            hellban = true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!UnHellBan <玩家> - 解鎖玩家").toString();
        }
    }
    public static class UnBan extends CommandExecute {
        protected boolean hellban = false;
        private String getCommand() {
            return "UnBan";
        }
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            byte ret;
            if (hellban) {
                ret = MapleClient.unHellban(splitted[1]);
            } else {
                ret = MapleClient.unban(splitted[1]);
            }
            if (ret == -2) {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] SQL 錯誤");
            } else if (ret == -1) {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] 目標玩家不存在");
            } else {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] 成功解除鎖定");
            }
            byte ret_ = MapleClient.unbanIPMacs(splitted[1]);
            if (ret_ == -2) {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] SQL 錯誤.");
            } else if (ret_ == -1) {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] 角色不存在.");
            } else if (ret_ == 0) {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] No IP or Mac with that character exists!");
            } else if (ret_ == 1) {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] IP或Mac已解鎖其中一個.");
            } else if (ret_ == 2) {
                c.getPlayer().dropMessage(6, "[" + getCommand() + "] IP以及Mac已成功解鎖.");
            }
            if (ret_ == 1 || ret_ == 2) {
                FileoutputUtil.logToFile("封鎖/解除封鎖名單.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress().split(":")[0] + " " + c.getPlayer().getName() + " 解鎖了 " + splitted[1]
                );
            }
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!unban <玩家> - 解鎖玩家").toString();
        }
    }
    public static class DCID extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            MapleCharacter victim;
            int id = Integer.parseInt(splitted[1]);
            int ch = World.Find.findChannel(id);
            if (ch <= 0) {
                c.getPlayer().dropMessage("該玩家不在線上");
                return true;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(id);
            if (victim != null) {
                victim.getClient().disconnect(true, false);
                victim.getClient().getSession().close();
            } else {
                c.getPlayer().dropMessage("該玩家不在線上");
            }
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!DCID <玩家ID> - 讓玩家斷線").toString();
        }
    }
    public static class DC extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            MapleCharacter victim;
            String name = splitted[1];
            int ch = World.Find.findChannel(name);
            if (ch <= 0) {
                c.getPlayer().dropMessage("該玩家不在線上");
                return true;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);
            if (victim != null) {
                victim.getClient().disconnect(true, false);
                victim.getClient().getSession().close();
            } else {
                c.getPlayer().dropMessage("該玩家不在線上");
            }
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!dc <玩家> - 讓玩家斷線").toString();
        }
    }
    public static class fixch extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            ChannelServer.forceRemovePlayerByCharName(c, splitted[1]);
            c.getPlayer().dropMessage("已經解卡玩家<" + splitted[1] + ">");
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!fixch <玩家名稱> - 解卡角").toString();
        }
    }
    public static class fixac extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            String input = splitted[1];
            int Accountid = 0;
            int times = 0;
            try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("SELECT accountid FROM characters WHERE name = ?")) {
                ps.setString(1, input);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        rs.close();
                        ps.close();
                        return true;
                    }
                    Accountid = rs.getInt(1);
                }
            } catch (Exception ex) {
                Logger.getLogger(PracticerCommand.class.getName()).log(Level.SEVERE, null, ex);
            }
            try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("SELECT name FROM characters WHERE accountid = ?")) {
                ps.setInt(1, Accountid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        rs.close();
                        ps.close();
                        return false;
                    }
                    times++;
                    try {
                        ChannelServer.forceRemovePlayerByCharName(c, rs.getString("name"));
                    } catch (Exception ex) {
                    }
                }
            } catch (Exception ex) {
                Logger.getLogger(PracticerCommand.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (c != null && c.getPlayer() != null) {
                c.getPlayer().dropMessage("已經解卡玩家<" + splitted[1] + ">帳號內的" + times + "個角色");
            }
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!fixac <玩家名稱> - 解帳號卡角").toString();
        }
    }
    public static class Job extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            int jobid = 0;
            try {
                jobid = Integer.parseInt(splitted[1]);
            } catch (Exception ex) {
                return false;
            }
            c.getPlayer().changeJob(jobid);
            c.getPlayer().dispelDebuffs();
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!job <職業代碼> - 更換職業").toString();
        }
    }
//    public static class 吸怪自動傳送 extends CommandExecute {
//
//        @Override
//        public boolean execute(MapleClient c, String splitted[]) {
//            c.getPlayer().setAuto吸怪(!c.getPlayer().getAuto吸怪());
//            c.getPlayer().dropMessage("自動吸怪傳送已經: " + (c.getPlayer().getAuto吸怪() ? "開啟" : "關閉") + "");
//            return true;
//        }
//
//        @Override
//        public String getMessage() {
//            return new StringBuilder().append("!吸怪自動傳送 - 吸怪自動傳送").toString();
//        }
//    }
    public static class WhereAmI extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().dropMessage(5, "目前地圖 " + c.getPlayer().getMapId() + "座標 (" + String.valueOf(c.getPlayer().getPosition().x) + " , " + String.valueOf(c.getPlayer().getPosition().y) + ")");
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!whereami - 目前地圖").toString();
        }
    }
    public static class BanStatus extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            String name = splitted[1];
            String mac = "";
            String ip = "";
            int acid = 0;
            boolean Systemban = false;
            boolean ACbanned = false;
            boolean IPbanned = false;
            boolean MACbanned = false;
            String reason = null;
            try {
                Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps;
                ps = con.prepareStatement("select accountid from characters where name = ?");
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        acid = rs.getInt("accountid");
                    }
                }
                ps = con.prepareStatement("select banned, banreason, macs, Sessionip from accounts where id = ?");
                ps.setInt(1, acid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Systemban = rs.getInt("banned") == 2;
                        ACbanned = rs.getInt("banned") == 1 || rs.getInt("banned") == 2;
                        reason = rs.getString("banreason");
                        mac = rs.getString("macs");
                        ip = rs.getString("Sessionip");
                    }
                }
                ps.close();
            } catch (Exception e) {
            }
            if (reason == null || reason.isEmpty()) {
                reason = "無";
            }
            if (c.isBannedIP(ip)) {
                IPbanned = true;
            }
            if (c.hasBannedMac()) {
                MACbanned = true;
            }
            c.getPlayer().dropMessage("玩家[" + name + "] 帳號ID[" + acid + "]是否被封鎖: " + (ACbanned ? "是" : "否") + (Systemban ? "(系統自動封鎖)" : "") + ", 原因: " + reason);
            c.getPlayer().dropMessage("最後登入IP: " + ip + " 是否在封鎖IP名單: " + (IPbanned ? "是" : "否"));
            //     c.getPlayer().dropMessage("目前MAC: " + c.getPlayer().getNowMacs() + " 是否在封鎖IP名單: " + (c.isBannedMac(c.getPlayer().getNowMacs()) ? "是" : "否"));
            for (String SingleMac : mac.split(", ")) {
                c.getPlayer().dropMessage("MAC: " + SingleMac + " 是否在封鎖MAC名單: " + (c.isBannedMac(SingleMac) ? "是" : "否"));
            }
            // c.getPlayer().dropMessage("MAC: " + mac + " 是否在封鎖MAC名單: " + (MACbanned ? "是" : "否"));
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!BanStatus <玩家名稱> - 查看玩家是否被封鎖及原因").toString();
        }
    }
    public static class banMac extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            String mac = splitted[1];
            if (mac.equalsIgnoreCase("00-00-00-00-00-00") || mac.length() != 17) {
                c.getPlayer().dropMessage("封鎖MAC失敗，可能為格式錯誤或是長度錯誤 Ex: 00-00-00-00-00-00 ");
                return true;
            }
            c.getPlayer().dropMessage("封鎖MAC [" + mac + "] 成功");
            try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("INSERT INTO macbans (mac) VALUES (?)")) {
                ps.setString(1, mac);
                ps.executeUpdate();
                ps.close();
            } catch (SQLException e) {
                System.err.println("Error banning MACs" + e);
                return true;
            }
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!BanMAC <MAC> - 封鎖MAC ").toString();
        }
    }
    public static class BanIP extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            boolean error = false;
            String IP = splitted[1];
            if (!IP.contains("/") || !IP.contains(".")) {
                c.getPlayer().dropMessage("輸入IP必須包含 '/' 以及 '.' 例如: !banIP /127.0.0.1");
                return true;
            }
            try {
                Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps;
                ps = con.prepareStatement("INSERT INTO ipbans (ip) VALUES (?)");
                ps.setString(1, IP);
                ps.executeUpdate();
                ps.close();
            } catch (Exception ex) {
                error = true;
            }
            try {
                for (ChannelServer cs : ChannelServer.getAllInstances()) {
                    for (MapleCharacter chr : cs.getPlayerStorage().getAllCharactersThreadSafe()) {
                        if (chr.getClient().getSessionIPAddress().equals(IP)) {
                            if (!chr.getClient().isGm()) {
                                chr.getClient().disconnect(true, false);
                                chr.getClient().getSession().close();
                            }
                        }
                    }
                }
            } catch (Exception ex) {
            }
            c.getPlayer().dropMessage("封鎖IP [" + IP + "] " + (error ? "成功 " : "失敗"));
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!BanIP <IP> - 封鎖IP ").toString();
        }
    }
    public static class MobSize extends CommandExecute {
        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            int size = c.getPlayer().getMap().mobCount();
            c.getPlayer().dropMessage(5, "當前地圖怪物數量總共有" + size + "隻");
            return true;
        }
        @Override
        public String getMessage() {
            return new StringBuilder().append("!MobSize 查看當前地圖總共的怪物數量").toString();
        }
    }
}