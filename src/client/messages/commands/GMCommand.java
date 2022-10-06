package client.messages.commands;

import client.MapleCharacter;
import constants.ServerConstants.PlayerGMRank;
import client.MapleClient;
import client.MapleStat;
import client.SkillFactory;
import client.inventory.Equip;
import client.inventory.Item;
import client.inventory.ItemFlag;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryIdentifier;
import client.inventory.MapleInventoryType;
import client.inventory.MapleRing;
import client.messages.CommandProcessorUtil;
import constants.GameConstants;
import constants.ServerConstants;
import database.DatabaseConnection;
import handling.RecvPacketOpcode;
import handling.SendPacketOpcode;
import handling.channel.ChannelServer;
import handling.login.LoginServer;
import handling.world.World;
import handling.world.guild.MapleGuild;
import java.awt.Point;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import scripting.NPCScriptManager;
import scripting.PortalScriptManager;
import scripting.ReactorScriptManager;
import server.CashItemFactory;
import server.FishingRewardFactory;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MaplePortal;
import server.MapleShopFactory;
import server.ShutdownServer;
import server.Timer.EventTimer;
import server.events.MapleOxQuizFactory;
import server.life.MapleLifeFactory;
import server.life.MapleMonsterInformationProvider;
import server.life.MapleNPC;
import server.maps.MapleMap;
import server.maps.MapleMapFactory;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import server.maps.MapleReactor;
import server.maps.MapleReactorFactory;
import server.maps.MapleReactorStats;
import server.quest.MapleQuest;
import tools.ArrayMap;
import tools.Pair;
import tools.StringUtil;
import tools.packet.CField;
import tools.packet.CField.NPCPacket;
import tools.packet.CWvsContext;
import tools.packet.CWvsContext.GuildPacket;

/**
 *
 * @author Emilyx3
 */
public class GMCommand {

    public static PlayerGMRank getPlayerLevelRequired() {
        return PlayerGMRank.管理員;
    }

    public static class SaveAll extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            int p = 0;
            for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                List<MapleCharacter> chrs = cserv.getPlayerStorage().getAllCharactersThreadSafe();
                for (MapleCharacter chr : chrs) {
                    p++;
                    chr.saveToDB(false, false);
                }
            }
            c.getPlayer().dropMessage("[保存] " + p + "個玩家數據保存到數據中.");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!saveall - 儲存所有角色資料").toString();
        }
    }

    public static class LowHP extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().getStat().setHp((short) 1);
            c.getPlayer().getStat().setMp((short) 1);
            c.getPlayer().updateSingleStat(MapleStat.HP, 1);
            c.getPlayer().updateSingleStat(MapleStat.MP, 1);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!lowhp - 血魔歸ㄧ").toString();
        }
    }

    public static class MyPos extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            Point pos = c.getPlayer().getPosition();
            c.getPlayer().dropMessage(6, "X: " + pos.x + " | Y: " + pos.y + " | RX0: " + (pos.x + 50) + " | RX1: " + (pos.x - 50) + " | FH: " + c.getPlayer().getFH() + "| CY:" + pos.y);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!mypos - 我的位置").toString();
        }
    }

    public static class Notice extends CommandExecute {

        private static int getNoticeType(String typestring) {
            switch (typestring) {
                case "n":
                    return 0;
                case "p":
                    return 1;
                case "l":
                    return 2;
                case "nv":
                    return 5;
                case "v":
                    return 5;
                case "b":
                    return 6;
            }
            return -1;
        }

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            int joinmod = 1;
            int range = -1;
            if (splitted.length < 2) {
                return false;
            }
            switch (splitted[1]) {
                case "m":
                    range = 0;
                    break;
                case "c":
                    range = 1;
                    break;
                case "w":
                    range = 2;
                    break;
            }

            int tfrom = 2;
            if (range == -1) {
                range = 2;
                tfrom = 1;
            }
            if (splitted.length < tfrom + 1) {
                return false;
            }
            int type = getNoticeType(splitted[tfrom]);
            if (type == -1) {
                type = 0;
                joinmod = 0;
            }
            StringBuilder sb = new StringBuilder();
            if (splitted[tfrom].equals("nv")) {
                sb.append("[Notice]");
            } else {
                sb.append("");
            }
            joinmod += tfrom;
            if (splitted.length < joinmod + 1) {
                return false;
            }
            sb.append(StringUtil.joinStringFrom(splitted, joinmod));

            byte[] packet = CWvsContext.serverNotice(type, sb.toString());
            if (range == 0) {
                c.getPlayer().getMap().broadcastMessage(packet);
            } else if (range == 1) {
                ChannelServer.getInstance(c.getChannel()).broadcastPacket(packet);
            } else if (range == 2) {
                World.Broadcast.broadcastMessage(packet);
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!notice <n|p|l|nv|v|b> <m|c|w> <message> - 公告").toString();
        }
    }

    public static class Yellow extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            int range = -1;
            switch (splitted[1]) {
                case "m":
                    range = 0;
                    break;
                case "c":
                    range = 1;
                    break;
                case "w":
                    range = 2;
                    break;
            }
            if (range == -1) {
                range = 2;
            }
            byte[] packet = CWvsContext.yellowChat((splitted[0].equals("!y") ? ("[" + c.getPlayer().getName() + "] ") : "") + StringUtil.joinStringFrom(splitted, 2));
            if (range == 0) {
                c.getPlayer().getMap().broadcastMessage(packet);
            } else if (range == 1) {
                ChannelServer.getInstance(c.getChannel()).broadcastPacket(packet);
            } else if (range == 2) {
                World.Broadcast.broadcastMessage(packet);
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!yellow <m|c|w> <message> - 黃色公告").toString();
        }
    }

    public static class Y extends Yellow {
    }

    public static class NpcNotice extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length <= 2) {
                return false;
            }
            int npcid = Integer.parseInt(splitted[1]);
            String msg = splitted[2];
            World.Broadcast.broadcastMessage(NPCPacket.getNPCTalk(npcid, (byte) 0, msg, "00 00", (byte) 0));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!NpcNotice <npcid> <message> - 用NPC發訊息").toString();
        }
    }

    public static class opennpc extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 2) {
                return false;
            }
            int npcid = 0;
            try {
                npcid = Integer.parseInt(splitted[1]);
            } catch (NumberFormatException asd) {
            }
            MapleNPC npc = MapleLifeFactory.getNPC(npcid);
            if (npc != null && !npc.getName().equalsIgnoreCase("MISSINGNO")) {
                NPCScriptManager.getInstance().start(c, npcid);
            } else {
                c.getPlayer().dropMessage(6, "未知NPC");
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!openNpc <NPC代碼> - 開啟NPC").toString();
        }
    }

    public static class 改名字 extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            String after = splitted[1];
            if (after.length() <= 12) {
                c.getPlayer().setName(splitted[1]);
                c.getPlayer().fakeRelog();
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!改名字 	新名字 - 改角色名字").toString();
        }
    }

    public static class 加入公會 extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length != 2) {
                return false;
            }
            com.mysql.jdbc.Connection dcon = (com.mysql.jdbc.Connection) DatabaseConnection.getConnection();
            try {
                com.mysql.jdbc.PreparedStatement ps = (com.mysql.jdbc.PreparedStatement) dcon.prepareStatement("SELECT guildid FROM guilds WHERE name = ?");
                ps.setString(1, splitted[1]);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    if (c.getPlayer().getGuildId() > 0) {
                        try {
                            World.Guild.leaveGuild(c.getPlayer().getMGC());
                        } catch (Exception e) {
                            c.sendPacket(CWvsContext.serverNotice(5, "無法連接到世界伺服器，請稍後再嘗試。"));
                            return false;
                        }
                        c.sendPacket(GuildPacket.showGuildInfo(null));

                        c.getPlayer().setGuildId(0);
                        c.getPlayer().saveGuildStatus();
                    }
                    c.getPlayer().setGuildId(rs.getInt("guildid"));
                    c.getPlayer().setGuildRank((byte) 2); // 副會長
                    try {
                        World.Guild.addGuildMember(c.getPlayer().getMGC(), false);
                    } catch (Exception e) {
                    }
                    c.sendPacket(GuildPacket.showGuildInfo(c.getPlayer()));
                    c.getPlayer().getMap().broadcastMessage(c.getPlayer(), CField.removePlayerFromMap(c.getPlayer().getId()), false);
                    c.getPlayer().getMap().broadcastMessage(c.getPlayer(), CField.spawnPlayerMapobject(c.getPlayer()), false);
                    c.getPlayer().saveGuildStatus();
                } else {
                    c.getPlayer().dropMessage(6, "公會名稱不存在。");
                }
                rs.close();
                ps.close();
            } catch (SQLException e) {
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!加入公會 	公會名字 - 強制加入公會").toString();
        }
    }

    public static class 離婚 extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleCharacter victim;
            String name = splitted[1];
            int ch = World.Find.findChannel(name);
            if (ch <= 0) {
                c.getPlayer().dropMessage(6, "玩家必須上線");
                return true;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);
            if (victim == null) {
                c.getPlayer().dropMessage(6, "玩家必須上線");
                return true;
            }
            victim.setMarriageId(0);
            victim.reloadC();
            victim.dropMessage(5, "離婚成功！");
            victim.saveToDB(false, false);
            c.getPlayer().dropMessage(6, victim.getName() + "離婚成功！");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!離婚 <玩家名稱> - 離婚").toString();
        }
    }

    public static class CancelBuffs extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            c.getPlayer().cancelAllBuffs();
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!CancelBuffs - 取消所有BUFF").toString();
        }
    }

    public static class RemoveNPCs extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().getMap().resetNPCs();
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!removenpcs - 刪除所有NPC").toString();
        }
    }

    public static class LookNPCs extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            for (MapleMapObject reactor1l : c.getPlayer().getMap().getAllNPCsThreadsafe()) {
                MapleNPC reactor2l = (MapleNPC) reactor1l;
                c.getPlayer().dropMessage(5, "NPC: oID: " + reactor2l.getObjectId() + " npcID: " + reactor2l.getId() + " Position: " + reactor2l.getPosition().toString() + " Name: " + reactor2l.getName());
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!looknpcs - 查看所有NPC").toString();
        }
    }

    public static class LookReactors extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            for (MapleMapObject reactor1l : c.getPlayer().getMap().getAllReactorsThreadsafe()) {
                MapleReactor reactor2l = (MapleReactor) reactor1l;
                c.getPlayer().dropMessage(5, "Reactor: oID: " + reactor2l.getObjectId() + " reactorID: " + reactor2l.getReactorId() + " Position: " + reactor2l.getPosition().toString() + " State: " + reactor2l.getState() + " Name: " + reactor2l.getName());
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!lookreactors - 查看所有反應堆").toString();
        }
    }

    public static class LookPortals extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            for (MaplePortal portal : c.getPlayer().getMap().getPortals()) {
                c.getPlayer().dropMessage(5, "Portal: ID: " + portal.getId() + " script: " + portal.getScriptName() + " name: " + portal.getName() + " pos: " + portal.getPosition().x + "," + portal.getPosition().y + " target: " + portal.getTargetMapId() + " / " + portal.getTarget());
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!lookportals - 查看所有反應堆").toString();
        }
    }

    public static class GoTo extends CommandExecute {

        private static final HashMap<String, Integer> gotomaps = new HashMap<>();

        static {
            gotomaps.put("gmmap", 180000000);
            gotomaps.put("southperry", 2000000);
            gotomaps.put("amherst", 1010000);
            gotomaps.put("henesys", 100000000);
            gotomaps.put("ellinia", 101000000);
            gotomaps.put("perion", 102000000);
            gotomaps.put("kerning", 103000000);
            gotomaps.put("lithharbour", 104000000);
            gotomaps.put("sleepywood", 105040300);
            gotomaps.put("florina", 110000000);
            gotomaps.put("orbis", 200000000);
            gotomaps.put("happyville", 209000000);
            gotomaps.put("elnath", 211000000);
            gotomaps.put("ludibrium", 220000000);
            gotomaps.put("aquaroad", 230000000);
            gotomaps.put("leafre", 240000000);
            gotomaps.put("mulung", 250000000);
            gotomaps.put("herbtown", 251000000);
            gotomaps.put("omegasector", 221000000);
            gotomaps.put("koreanfolktown", 222000000);
            gotomaps.put("newleafcity", 600000000);
            gotomaps.put("sharenian", 990000000);
            gotomaps.put("pianus", 230040420);
            gotomaps.put("horntail", 240060200);
            gotomaps.put("chorntail", 240060201);
            gotomaps.put("mushmom", 100000005);
            gotomaps.put("griffey", 240020101);
            gotomaps.put("manon", 240020401);
            gotomaps.put("zakum", 280030000);
            gotomaps.put("czakum", 280030001);
            gotomaps.put("papulatus", 220080001);
            gotomaps.put("showatown", 801000000);
            gotomaps.put("zipangu", 800000000);
            gotomaps.put("ariant", 260000100);
            gotomaps.put("nautilus", 120000000);
            gotomaps.put("boatquay", 541000000);
            gotomaps.put("malaysia", 550000000);
            gotomaps.put("taiwan", 740000000);
            gotomaps.put("thailand", 500000000);
            gotomaps.put("erev", 130000000);
            gotomaps.put("ellinforest", 300000000);
            gotomaps.put("kampung", 551000000);
            gotomaps.put("singapore", 540000000);
            gotomaps.put("amoria", 680000000);
            gotomaps.put("timetemple", 270000000);
            gotomaps.put("pinkbean", 270050100);
            gotomaps.put("peachblossom", 700000000);
            gotomaps.put("fm", 910000000);
            gotomaps.put("freemarket", 910000000);
            gotomaps.put("oxquiz", 109020001);
            gotomaps.put("ola", 109030101);
            gotomaps.put("fitness", 109040000);
            gotomaps.put("snowball", 109060000);
            gotomaps.put("cashmap", 741010200);
            gotomaps.put("golden", 950100000);
            gotomaps.put("phantom", 610010000);
            gotomaps.put("cwk", 610030000);
            gotomaps.put("rien", 140000000);
        }

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                c.getPlayer().dropMessage(6, "使用方法: !goto <地圖名稱>");
            } else if (gotomaps.containsKey(splitted[1])) {
                MapleMap target = c.getChannelServer().getMapFactory().getMap(gotomaps.get(splitted[1]));
                MaplePortal targetPortal = null;
                try {
                    targetPortal = target.getPortal(0);
                } catch (Exception ex) {

                }
                c.getPlayer().changeMap(target, targetPortal);
            } else if (splitted[1].equals("目的地")) {
                c.getPlayer().dropMessage(6, "使用 !goto <目的地>. 目的地地圖如下:");
                StringBuilder sb = new StringBuilder();
                for (String s : gotomaps.keySet()) {
                    sb.append(s).append(", ");
                }
                c.getPlayer().dropMessage(6, sb.substring(0, sb.length() - 2));
            } else {
                c.getPlayer().dropMessage(6, "錯誤的指令規則 - 使用 !goto <目的地>. 來看目的地地圖清單, 接著使用 !goto 目的地地圖名稱.");
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!goto <名稱> - 到某個地圖").toString();

        }
    }

    public static class cleardrops extends RemoveDrops {

    }

    public static class RemoveDrops extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().dropMessage(5, "清除了 " + c.getPlayer().getMap().getNumItems() + " 個掉落物");
            c.getPlayer().getMap().removeDrops();
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!removedrops - 移除地上的物品").toString();

        }
    }

    public static class NearestPortal extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MaplePortal portal = c.getPlayer().getMap().findClosestSpawnpoint(c.getPlayer().getPosition());
            c.getPlayer().dropMessage(6, portal.getName() + " id: " + portal.getId() + " script: " + portal.getScriptName());

            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!nearestportal - 不知道啥").toString();

        }
    }

    public static class SpawnDebug extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().dropMessage(6, c.getPlayer().getMap().spawnDebug());
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!spawndebug - debug怪物出生").toString();

        }
    }

    public static class Speak extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleCharacter victim;
            String name = splitted[1];
            int ch = World.Find.findChannel(name);
            if (ch <= 0) {
                c.getPlayer().dropMessage(6, "玩家必須上線");
                return true;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);

            if (victim == null) {
                c.getPlayer().dropMessage(5, "找不到 '" + splitted[1]);
                return false;
            } else {
                victim.getMap().broadcastMessage(CField.getChatText(victim.getId(), StringUtil.joinStringFrom(splitted, 2), victim.isGM(), 0));
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!speak <玩家名稱> <訊息> - 對某個玩家傳訊息").toString();
        }
    }

    public static class SpeakMap extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            for (MapleCharacter victim : c.getPlayer().getMap().getCharactersThreadsafe()) {
                if (victim.getId() != c.getPlayer().getId()) {
                    victim.getMap().broadcastMessage(CField.getChatText(victim.getId(), StringUtil.joinStringFrom(splitted, 1), victim.isGM(), 0));
                }
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!speakmap <訊息> - 對目前地圖進行傳送訊息").toString();
        }

    }

    public static class SpeakChannel extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            for (MapleCharacter victim : c.getChannelServer().getPlayerStorage().getAllCharactersThreadSafe()) {
                if (victim.getId() != c.getPlayer().getId()) {
                    victim.getMap().broadcastMessage(CField.getChatText(victim.getId(), StringUtil.joinStringFrom(splitted, 1), victim.isGM(), 0));
                }
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!speakchannel <訊息> - 對目前頻道進行傳送訊息").toString();
        }

    }

    public static class SpeakWorld extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                for (MapleCharacter victim : cserv.getPlayerStorage().getAllCharactersThreadSafe()) {
                    if (victim.getId() != c.getPlayer().getId()) {
                        victim.getMap().broadcastMessage(CField.getChatText(victim.getId(), StringUtil.joinStringFrom(splitted, 1), victim.isGM(), 0));
                    }
                }
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!speakchannel <訊息> - 對目前伺服器進行傳送訊息").toString();
        }
    }

    public static class SpeakMega extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleCharacter victim = null;
            if (splitted.length >= 2) {
                victim = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]);
            }
            try {
                World.Broadcast.broadcastSmega(CWvsContext.serverNotice(3, victim == null ? c.getChannel() : victim.getClient().getChannel(), victim == null ? splitted[1] : victim.getName() + " : " + StringUtil.joinStringFrom(splitted, 2), true));
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!speakmega [玩家名稱] <訊息> - 對某個玩家的頻道進行廣播").toString();
        }
    }

    public static class Say extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length > 1) {
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                sb.append(c.getPlayer().getName());
                sb.append("] ");
                sb.append(StringUtil.joinStringFrom(splitted, 1));
                World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, sb.toString()));
            } else {
                return false;
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!say 訊息 - 伺服器公告").toString();
        }
    }

    public static class Shutdown extends CommandExecute {

        private static Thread t = null;

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().dropMessage(6, "關閉伺服器中...");
            if (t == null || !t.isAlive()) {
                ShutdownServer.getInstance().run();
                t = new Thread(ShutdownServer.getInstance());
                t.start();
            } else {
                c.getPlayer().dropMessage(6, "已在執行中...");
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!shutdown - 關閉伺服器").toString();
        }
    }

    public static class ShutdownTime extends CommandExecute {

        private static ScheduledFuture<?> ts = null;
        private int minutesLeft = 0;
        private static Thread t = null;

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            minutesLeft = Integer.parseInt(splitted[1]);
            c.getPlayer().dropMessage(6, "伺服器將在 " + minutesLeft + "分鐘後關閉. 請盡速關閉精靈商人 並下線.");
            if (ts == null && (t == null || !t.isAlive())) {
                t = new Thread(ShutdownServer.getInstance());
                ts = EventTimer.getInstance().register(new Runnable() {

                    @Override
                    public void run() {
                        if (minutesLeft == 0) {
                            ShutdownServer.getInstance().run();
                            t.start();
                            ts.cancel(false);
                            return;
                        }
                        StringBuilder message = new StringBuilder();
                        message.append("[楓之谷公告] 伺服器將在 ");
                        message.append(minutesLeft);
                        message.append(" 分鐘後關閉，請關閉精靈商人並存檔並下線。");
                        World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, message.toString()));
                        World.Broadcast.broadcastMessage(CWvsContext.serverMessage(message.toString()));
                        for (ChannelServer cs : ChannelServer.getAllInstances()) {
                            cs.setServerMessage("伺服器將於 " + minutesLeft + " 分鐘後關機");
                        }
                        minutesLeft--;
                    }
                }, 60000);
            } else {
                c.getPlayer().dropMessage(6, new StringBuilder().append("伺服器關閉時間修改為 ").append(minutesLeft).append("分鐘後，請稍等伺服器關閉").toString());
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!shutdowntime <分鐘數> - 關閉伺服器").toString();
        }
    }

    public static class UnbanIP extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            byte ret_ = MapleClient.unbanIP(splitted[1]);
            if (ret_ == -2) {
                c.getPlayer().dropMessage(6, "[unbanip] SQL 錯誤.");
            } else if (ret_ == -1) {
                c.getPlayer().dropMessage(6, "[unbanip] 角色不存在.");
            } else if (ret_ == 0) {
                c.getPlayer().dropMessage(6, "[unbanip] No IP or Mac with that character exists!");
            } else if (ret_ == 1) {
                c.getPlayer().dropMessage(6, "[unbanip] IP或Mac已解鎖其中一個.");
            } else if (ret_ == 2) {
                c.getPlayer().dropMessage(6, "[unbanip] IP以及Mac已成功解鎖.");
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!unbanip <玩家名稱> - 解鎖玩家").toString();
        }
    }

    public static class TempBan extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleCharacter victim;
            String name = splitted[1];
            int ch = World.Find.findChannel(name);
            if (ch <= 0) {
                return false;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);
            final int reason = Integer.parseInt(splitted[2]);
            final int numDay = Integer.parseInt(splitted[3]);

            final Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, numDay);
            final DateFormat df = DateFormat.getInstance();

            if (victim == null) {
                c.getPlayer().dropMessage(6, "[tempban] 找不到目標角色");

            } else {
                victim.tempban("由" + c.getPlayer().getName() + "暫時鎖定了", cal, reason, true);
                c.getPlayer().dropMessage(6, "[tempban] " + splitted[1] + " 已成功被暫時鎖定至 " + df.format(cal.getTime()));
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!tempban <玩家名稱> - 暫時鎖定玩家").toString();
        }
    }

    public static class copyAll extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            MapleCharacter player = c.getPlayer();
            MapleCharacter victim;
            if (splitted.length < 2) {
                return false;
            }
            String name = splitted[1];
            int ch = World.Find.findChannel(name);
            if (ch <= 0) {
                c.getPlayer().dropMessage(6, "玩家必須上線");
                return true;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);
            if (victim == null) {
                player.dropMessage("找不到該玩家");
                return true;
            }
            MapleInventory equipped = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED);
            MapleInventory equip = c.getPlayer().getInventory(MapleInventoryType.EQUIP);
            List<Short> ids = new LinkedList<>();
            for (Item item : equipped.list()) {
                ids.add(item.getPosition());
            }
            for (short id : ids) {
                MapleInventoryManipulator.unequip(c, id, equip.getNextFreeSlot());
            }
            c.getPlayer().clearSkills();
            c.getPlayer().setStr(victim.getStr());
            c.getPlayer().setDex(victim.getDex());
            c.getPlayer().setInt(victim.getInt());
            c.getPlayer().setLuk(victim.getLuk());

            c.getPlayer().setMeso(victim.getMeso());
            c.getPlayer().setLevel((short) (victim.getLevel()));
            c.getPlayer().changeJob(victim.getJob());

            c.getPlayer().setHp(victim.getHp());
            c.getPlayer().setMp(victim.getMp());
            c.getPlayer().setMaxHp(victim.getMaxHp());
            c.getPlayer().setMaxMp(victim.getMaxMp());

            String normal = victim.getName();
            String after = (normal + "x2");
            if (after.length() <= 12) {
                c.getPlayer().setName(victim.getName() + "x2");
            }
            c.getPlayer().setRemainingAp(victim.getRemainingAp());
            c.getPlayer().setRemainingSp(victim.getRemainingSp());
            c.getPlayer().LearnSameSkill(victim);

            c.getPlayer().setFame(victim.getFame());
            c.getPlayer().setHair(victim.getHair());
            c.getPlayer().setFace(victim.getFace());

            c.getPlayer().setSkinColor(victim.getSkinColor() == 0 ? c.getPlayer().getSkinColor() : victim.getSkinColor());

            c.getPlayer().setGender(victim.getGender());

            for (Item ii : victim.getInventory(MapleInventoryType.EQUIPPED).list()) {
                Item eq = ii.copy();
                eq.setPosition(eq.getPosition());
                eq.setQuantity((short) 1);
                c.getPlayer().forceReAddItem_NoUpdate(eq, MapleInventoryType.EQUIPPED);
            }
            c.getPlayer().fakeRelog();
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!copyall 玩家名稱 - 複製玩家").toString();
        }
    }

    public static class copyInv extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            MapleCharacter player = c.getPlayer();
            MapleCharacter victim;
            int type = 1;
            if (splitted.length < 2) {
                return false;
            }

            String name = splitted[1];
            int ch = World.Find.findChannel(name);
            if (ch <= 0) {
                c.getPlayer().dropMessage(6, "玩家必須上線");
                return false;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);
            if (victim == null) {
                player.dropMessage("找不到該玩家");
                return true;
            }
            try {
                type = Integer.parseInt(splitted[2]);
            } catch (Exception ex) {
            }
            if (type == 0) {
                for (client.inventory.Item ii : victim.getInventory(MapleInventoryType.EQUIPPED).list()) {
                    client.inventory.Item n = ii.copy();
                    player.getInventory(MapleInventoryType.EQUIP).addItem(n);
                }
                player.fakeRelog();
            } else {
                MapleInventoryType types;
                if (type == 1) {
                    types = MapleInventoryType.EQUIP;
                } else if (type == 2) {
                    types = MapleInventoryType.USE;
                } else if (type == 3) {
                    types = MapleInventoryType.ETC;
                } else if (type == 4) {
                    types = MapleInventoryType.SETUP;
                } else if (type == 5) {
                    types = MapleInventoryType.CASH;
                } else {
                    types = null;
                }
                if (types == null) {
                    c.getPlayer().dropMessage("發生錯誤");
                    return true;
                }
                int[] equip = new int[97];
                for (int i = 1; i < 97; i++) {
                    if (victim.getInventory(types).getItem((short) i) != null) {
                        equip[i] = i;
                    }
                }
                for (int i = 0; i < equip.length; i++) {
                    if (equip[i] != 0) {
                        Item n = victim.getInventory(types).getItem((short) equip[i]).copy();
                        player.getInventory(types).addItem(n);
                        c.sendPacket(CWvsContext.InventoryPacket.addInventorySlot(types, n));
                    }
                }
            }
            return true;
        }

        public String getMessage() {
            return new StringBuilder().append("!copyinv 玩家名稱 裝備欄位(0 = 裝備中 1=裝備欄 2=消耗欄 3=其他欄 4=裝飾欄 5=點數欄)(預設裝備欄) - 複製玩家道具").toString();
        }
    }

    public static class Clock extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            c.getPlayer().getMap().broadcastMessage(CField.getClock(CommandProcessorUtil.getOptionalIntArg(splitted, 1, 60)));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!clock <time> 時鐘").toString();
        }
    }

    public static class Song extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            c.getPlayer().getMap().broadcastMessage(CField.musicChange(splitted[1]));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!song - 播放音樂").toString();
        }
    }

    public static class Kill extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleCharacter player = c.getPlayer();
            if (splitted.length < 2) {
                return false;
            }
            MapleCharacter victim;
            String name = splitted[1];
            int ch = World.Find.findChannel(name);
            if (ch <= 0) {
                c.getPlayer().dropMessage(6, "玩家必須上線");
                return true;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);
            if (victim == null) {
                c.getPlayer().dropMessage(6, "[kill] 玩家 " + name + " 不存在.");
            } else if (player.allowedToTarget(victim)) {
                victim.getStat().setHp((short) 0);
                victim.getStat().setMp((short) 0);
                victim.updateSingleStat(MapleStat.HP, 0);
                victim.updateSingleStat(MapleStat.MP, 0);
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!kill <玩家名稱> - 殺掉玩家").toString();
        }
    }

    public static class ReloadOps extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            SendPacketOpcode.reloadValues();
            RecvPacketOpcode.reloadValues();
            c.getPlayer().dropMessage(6, "服務端包頭已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!reloadops - 重新載入OpCode").toString();
        }
    }

    public static class ReloadDrops extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleMonsterInformationProvider.getInstance().clearDrops();
            ReactorScriptManager.getInstance().clearDrops();
            c.getPlayer().dropMessage(6, "掉落相關道具已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!reloaddrops - 重新載入掉寶").toString();
        }
    }

    public static class ReloadPortals extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            PortalScriptManager.getInstance().clearScripts();
            c.getPlayer().dropMessage(6, "傳送腳本已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!reloadportals - 重新載入進入點").toString();
        }
    }

    public static class ReloadShops extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleShopFactory.getInstance().clear();
            c.getPlayer().dropMessage(6, "NPC商城已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!reloadshops - 重新載入商店").toString();
        }
    }

    public static class ReloadCS extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            CashItemFactory.getInstance().clearItems();
            c.getPlayer().dropMessage(6, "購物商城已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!reloadCS - 重新載入購物商城").toString();
        }
    }

    public static class ReloadFishing extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            FishingRewardFactory.getInstance().reloadItems();
            c.getPlayer().dropMessage(6, "釣魚已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!reloadFishing - 重新載入釣魚獎勵").toString();
        }
    }

    public static class ReloadEvents extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            for (ChannelServer instance : ChannelServer.getAllInstances()) {
                instance.reloadEvents();
            }
            c.getPlayer().dropMessage(6, "副本已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!reloadevents - 重新載入活動腳本").toString();
        }
    }

    public static class ReloadQuests extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleQuest.clearQuests();
            c.getPlayer().dropMessage(6, "任務已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!reloadquests - 重新載入任務").toString();
        }
    }

    public static class ReloadOX extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleOxQuizFactory.getInstance().reloadOX();
            c.getPlayer().dropMessage(6, "OX任務已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!reloadox - 重新載入OX題目").toString();
        }
    }

    public static class Reloadall extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            for (ChannelServer instance : ChannelServer.getAllInstances()) {
                instance.reloadEvents();
            }
            MapleShopFactory.getInstance().clear();
            PortalScriptManager.getInstance().clearScripts();
            MapleItemInformationProvider.getInstance().runEtc();
            MapleItemInformationProvider.getInstance().runItems();
            CashItemFactory.getInstance().initialize();
            MapleMonsterInformationProvider.getInstance().clearDrops();
            MapleGuild.loadAll();
            MapleLifeFactory.loadQuestCounts();
            MapleQuest.initQuests();
            MapleOxQuizFactory.getInstance();
            ReactorScriptManager.getInstance().clearDrops();
            SendPacketOpcode.reloadValues();
            RecvPacketOpcode.reloadValues();
            c.getPlayer().dropMessage(6, "已重置完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!Reloadall - 重置全伺服器").toString();
        }
    }

    public static class Skill extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            client.Skill skill = SkillFactory.getSkill(Integer.parseInt(splitted[1]));
            byte level = (byte) CommandProcessorUtil.getOptionalIntArg(splitted, 2, 1);
            byte masterlevel = (byte) CommandProcessorUtil.getOptionalIntArg(splitted, 3, 1);
            if (level > skill.getMaxLevel()) {
                level = (byte) skill.getMaxLevel();
            }
            c.getPlayer().changeSingleSkillLevel(skill, level, masterlevel);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!skill <技能ID> [技能等級] [技能最大等級] ...  - 學習技能").toString();
        }
    }

    public static class GiveSkill extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 3) {
                return false;
            }
            MapleCharacter victim;
            String name = splitted[1];
            int ch = World.Find.findChannel(name);
            if (ch <= 0) {
                return false;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);

            client.Skill skill = SkillFactory.getSkill(Integer.parseInt(splitted[2]));
            byte level = (byte) CommandProcessorUtil.getOptionalIntArg(splitted, 3, 1);
            byte masterlevel = (byte) CommandProcessorUtil.getOptionalIntArg(splitted, 4, 1);

            if (level > skill.getMaxLevel()) {
                level = (byte) skill.getMaxLevel();
            }
            victim.changeSingleSkillLevel(skill, level, masterlevel);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!giveskill <玩家名稱> <技能ID> [技能等級] [技能最大等級] - 給予技能").toString();
        }
    }

    public static class MaxSkillsByJob extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().maxSkillsByJob();
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!MaxSkillsByJob - 職業技能全滿").toString();
        }
    }

    public static class MaxSkills extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().maxSkills();
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!MaxSkills - 技能全滿").toString();
        }
    }

    public static class ClearSkills extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().clearSkills();
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!ClearSkills - 技能全消").toString();
        }
    }

    public static class SP extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().setRemainingSp(CommandProcessorUtil.getOptionalIntArg(splitted, 1, 1));
            c.getPlayer().updateSingleStat(MapleStat.AVAILABLESP, 0);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!sp [數量] - 增加SP").toString();
        }
    }

    public static class AP extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().setRemainingAp((short) CommandProcessorUtil.getOptionalIntArg(splitted, 1, 1));
            c.getPlayer().updateSingleStat(MapleStat.AVAILABLEAP, c.getPlayer().getRemainingAp());
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!ap [數量] - 增加AP").toString();
        }
    }

    public static class Shop extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleShopFactory shop = MapleShopFactory.getInstance();
            int shopId = 0;
            try {
                shopId = Integer.parseInt(splitted[1]);
            } catch (Exception ex) {
            }
            if (shop.getShop(shopId) != null) {
                shop.getShop(shopId).sendShop(c);
            } else {
                c.getPlayer().dropMessage(5, "此商店ID[" + shopId + "]不存在");
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!shop - 開啟商店").toString();
        }
    }

    public static class 關鍵時刻 extends CommandExecute {

        protected static ScheduledFuture<?> ts = null;

        @Override
        public boolean execute(final MapleClient c, String splitted[]) {
            if (splitted.length < 1) {
                return false;
            }
            if (ts != null) {
                ts.cancel(false);
                c.getPlayer().dropMessage(0, "原定的關鍵時刻已取消");
            }
            int minutesLeft;
            try {
                minutesLeft = Integer.parseInt(splitted[1]);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (minutesLeft > 0) {
                ts = EventTimer.getInstance().schedule(new Runnable() {
                    @Override
                    public void run() {
                        for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                            for (MapleCharacter mch : cserv.getPlayerStorage().getAllCharactersThreadSafe()) {
                                if (mch.getLevel() >= 29 && !mch.isGM()) {
                                    NPCScriptManager.getInstance().start(mch.getClient(), 9010010, "CrucialTime");
                                }
                            }
                        }
                        World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, "關鍵時刻開放囉，沒有30等以上的玩家是得不到的。"));
                        ts.cancel(false);
                        ts = null;
                    }
                }, minutesLeft); // 六十秒
                c.getPlayer().dropMessage(0, "關鍵時刻預定已完成");
            } else {
                c.getPlayer().dropMessage(0, "設定的時間必須 > 0。");
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!關鍵時刻 <時間:分鐘> - 關鍵時刻").toString();
        }
    }

    public static class Letter extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 3) {
                c.getPlayer().dropMessage(6, "指令規則: ");
                return false;
            }
            int start, nstart;
            if (splitted[1].equalsIgnoreCase("green")) {
                start = 3991026;
                nstart = 3990019;
            } else if (splitted[1].equalsIgnoreCase("red")) {
                start = 3991000;
                nstart = 3990009;
            } else {
                c.getPlayer().dropMessage(6, "未知的顏色!");
                return true;
            }
            String splitString = StringUtil.joinStringFrom(splitted, 2);
            List<Integer> chars = new ArrayList<>();
            splitString = splitString.toUpperCase();
            // System.out.println(splitString);
            for (int i = 0; i < splitString.length(); i++) {
                char chr = splitString.charAt(i);
                if (chr == ' ') {
                    chars.add(-1);
                } else if ((int) (chr) >= (int) 'A' && (int) (chr) <= (int) 'Z') {
                    chars.add((int) (chr));
                } else if ((int) (chr) >= (int) '0' && (int) (chr) <= (int) ('9')) {
                    chars.add((int) (chr) + 200);
                }
            }
            final int w = 32;
            int dStart = c.getPlayer().getPosition().x - (splitString.length() / 2 * w);
            for (Integer i : chars) {
                if (i == -1) {
                    dStart += w;
                } else if (i < 200) {
                    int val = start + i - (int) ('A');
                    client.inventory.Item item = new client.inventory.Item(val, (byte) 0, (short) 1);
                    c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), item, new Point(dStart, c.getPlayer().getPosition().y), false, false);
                    dStart += w;
                } else if (i >= 200 && i <= 300) {
                    int val = nstart + i - (int) ('0') - 200;
                    client.inventory.Item item = new client.inventory.Item(val, (byte) 0, (short) 1);
                    c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), item, new Point(dStart, c.getPlayer().getPosition().y), false, false);
                    dStart += w;
                }
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append(" !letter <color (green/red)> <word> - 送信").toString();
        }

    }

    public static class Marry extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 3) {
                return false;
            }
            int itemId = Integer.parseInt(splitted[2]);
            if (!GameConstants.isEffectRing(itemId)) {
                c.getPlayer().dropMessage(6, "錯誤的戒指ID.");
            } else {
                MapleCharacter fff;
                String name = splitted[1];
                int ch = World.Find.findChannel(name);
                if (ch <= 0) {
                    c.getPlayer().dropMessage(6, "玩家必須上線");
                    return false;
                }
                fff = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);
                if (fff == null) {
                    c.getPlayer().dropMessage(6, "玩家必須上線");
                } else {
                    int[] ringID = {MapleInventoryIdentifier.getInstance(), MapleInventoryIdentifier.getInstance()};
                    try {
                        MapleCharacter[] chrz = {fff, c.getPlayer()};
                        for (int i = 0; i < chrz.length; i++) {
                            Equip eq = (Equip) MapleItemInformationProvider.getInstance().getEquipById(itemId);
                            if (eq == null) {
                                c.getPlayer().dropMessage(6, "錯誤的戒指ID.");
                                return true;
                            } else {
                                eq.setUniqueId(ringID[i]);
                                MapleInventoryManipulator.addbyItem(chrz[i].getClient(), eq.copy());
                                chrz[i].dropMessage(6, "成功與  " + chrz[i == 0 ? 1 : 0].getName() + " 結婚");
                            }
                        }
                        MapleRing.addToDB(itemId, c.getPlayer(), fff.getName(), fff.getId(), ringID);
                    } catch (SQLException e) {
                    }
                }
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!marry <玩家名稱> <戒指代碼> - 結婚").toString();
        }
    }

    public static class KillID extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleCharacter player = c.getPlayer();
            if (splitted.length < 2) {
                return false;
            }
            MapleCharacter victim;
            int id = 0;
            try {
                id = Integer.parseInt(splitted[1]);
            } catch (Exception ex) {

            }
            int ch = World.Find.findChannel(id);
            if (ch <= 0) {
                return false;
            }
            victim = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(id);
            if (victim == null) {
                c.getPlayer().dropMessage(6, "[kill] 玩家ID " + id + " 不存在.");
            } else if (player.allowedToTarget(victim)) {
                victim.getStat().setHp((short) 0);
                victim.getStat().setMp((short) 0);
                victim.updateSingleStat(MapleStat.HP, 0);
                victim.updateSingleStat(MapleStat.MP, 0);
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!KillID <玩家ID> - 殺掉玩家").toString();
        }
    }

    public static class autoreg extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            ServerConstants.AUTO_REGISTER = !ServerConstants.AUTO_REGISTER;
            c.getPlayer().dropMessage(0, "[自動註冊] " + (ServerConstants.AUTO_REGISTER ? "開啟" : "關閉"));
            System.out.println("[自動註冊] " + (ServerConstants.AUTO_REGISTER ? "開啟" : "關閉"));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!autoreg  - 自動註冊開關").toString();
        }
    }

    public static class LevelUp extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                c.getPlayer().levelUp();
            } else {
                int up = 0;
                try {
                    up = Integer.parseInt(splitted[1]);
                } catch (Exception ex) {

                }
                for (int i = 0; i < up; i++) {
                    c.getPlayer().levelUp();
                }
            }
            c.getPlayer().setExp(0);
            c.getPlayer().updateSingleStat(MapleStat.EXP, 0);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!levelup - 等級上升").toString();
        }
    }

    public static class FakeRelog extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleCharacter player = c.getPlayer();
            c.sendPacket(CField.getCharInfo(player));
            player.getMap().removePlayer(player);
            player.getMap().addPlayer(player);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!fakerelog - 假登出再登入").toString();

        }
    }

    public static class SpawnReactor extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleReactorStats reactorSt = MapleReactorFactory.getReactor(Integer.parseInt(splitted[1]));
            MapleReactor reactor = new MapleReactor(reactorSt, Integer.parseInt(splitted[1]));
            reactor.setDelay(-1);
            reactor.setPosition(c.getPlayer().getPosition());
            c.getPlayer().getMap().spawnReactor(reactor);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!spawnreactor - 設立Reactor").toString();

        }
    }

    public static class HReactor extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            c.getPlayer().getMap().getReactorByOid(Integer.parseInt(splitted[1])).hitReactor(c);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!hitreactor - 觸碰Reactor").toString();

        }
    }

    public static class DestroyReactor extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleMap map = c.getPlayer().getMap();
            List<MapleMapObject> reactors = map.getMapObjectsInRange(c.getPlayer().getPosition(), Double.POSITIVE_INFINITY, Arrays.asList(MapleMapObjectType.REACTOR));
            if (splitted[1].equals("all")) {
                for (MapleMapObject reactorL : reactors) {
                    MapleReactor reactor2l = (MapleReactor) reactorL;
                    c.getPlayer().getMap().destroyReactor(reactor2l.getObjectId());
                }
            } else {
                c.getPlayer().getMap().destroyReactor(Integer.parseInt(splitted[1]));
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!drstroyreactor - 移除Reactor").toString();

        }
    }

    public static class ResetReactors extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            c.getPlayer().getMap().resetReactors();
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!resetreactors - 重置此地圖所有的Reactor").toString();

        }
    }

    public static class SetReactor extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            c.getPlayer().getMap().setReactorState(Byte.parseByte(splitted[1]));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!hitreactor - 觸碰Reactor").toString();

        }
    }

    public static class ResetQuest extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleQuest.getInstance(Integer.parseInt(splitted[1])).forfeit(c.getPlayer());
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!resetquest <任務ID> - 重置任務").toString();

        }
    }

    public static class StartQuest extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleQuest.getInstance(Integer.parseInt(splitted[1])).start(c.getPlayer(), Integer.parseInt(splitted[2]));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!startquest <任務ID> - 開始任務").toString();

        }
    }

    public static class CompleteQuest extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleQuest.getInstance(Integer.parseInt(splitted[1])).complete(c.getPlayer(), Integer.parseInt(splitted[2]), Integer.parseInt(splitted[3]));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!completequest <任務ID> - 完成任務").toString();

        }
    }

    public static class FStartQuest extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleQuest.getInstance(Integer.parseInt(splitted[1])).forceStart(c.getPlayer(), Integer.parseInt(splitted[2]), splitted.length >= 4 ? splitted[3] : null);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!fstartquest <任務ID> - 強制開始任務").toString();

        }
    }

    public static class FCompleteQuest extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 2) {
                return false;
            }
            MapleQuest.getInstance(Integer.parseInt(splitted[1])).forceComplete(c.getPlayer(), Integer.parseInt(splitted[2]));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!fcompletequest <任務ID> - 強制完成任務").toString();

        }
    }

    public static class FStartOther extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {

            MapleQuest.getInstance(Integer.parseInt(splitted[2])).forceStart(c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]), Integer.parseInt(splitted[3]), splitted.length >= 4 ? splitted[4] : null);
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!fstartother - 不知道啥").toString();

        }
    }

    public static class FCompleteOther extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            MapleQuest.getInstance(Integer.parseInt(splitted[2])).forceComplete(c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]), Integer.parseInt(splitted[3]));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!fcompleteother - 不知道啥").toString();

        }
    }

    public static class log extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            boolean next = false;
            boolean Action = false;
            String LogType = null;
            String[] Log = {"傷害", "聊天", "商城", "廣播", "精靈商人", "黑板", "卷軸"};
            StringBuilder show_log = new StringBuilder();
            for (String s : Log) {
                show_log.append(s);
                show_log.append(" / ");
            }
            if (splitted.length < 3) {
                c.getPlayer().dropMessage("目前Log種類: " + show_log.toString());
                return false;
            }
            if (!splitted[1].contains("開") && !splitted[1].contains("關")) {
                return false;
            }
            if (splitted[1].contains("開") && splitted[1].contains("關")) {
                c.getPlayer().dropMessage("請問這位管理員到底是要開還是關呢?");
                return true;
            }

            for (int i = 0; i < Log.length; i++) {
                if (splitted[2].contains(Log[i])) {
                    next = true;
                    LogType = Log[i];
                    break;
                }
            }
            Action = splitted[1].contains("開");
            if (!next) {
                c.getPlayer().dropMessage("目前Log種類: " + show_log.toString());
                return true;
            }

            switch (LogType) {
                case "廣播":
                    ServerConstants.LOG_MEGA = Action;
                    break;
                case "傷害":
                    ServerConstants.LOG_DAMAGE = Action;
                    break;
                case "聊天":
                    ServerConstants.LOG_CHAT = Action;
                    break;
                case "商城":
                    ServerConstants.LOG_CSBUY = Action;
                    break;
                case "精靈商人":
                    ServerConstants.LOG_MERCHANT = Action;
                    break;
                case "黑板":
                    ServerConstants.LOG_CHALKBOARD = Action;
                    break;
                case "卷軸":
                    ServerConstants.LOG_SCROLL = Action;
                    break;
            }
            String msg = "[GM 密語] 管理員[" + c.getPlayer().getName() + "] " + splitted[1] + "了" + LogType + "的Log";
            World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, msg));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!log 開/關 Log種類名稱").toString();
        }

    }

    public static class RemoveItem extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 3) {
                return false;
            }
            MapleCharacter chr;
            String name = splitted[1];
            int id = Integer.parseInt(splitted[2]);
            int ch = World.Find.findChannel(name);
            if (ch <= 0) {
                c.getPlayer().dropMessage(6, "玩家必須上線");
                return true;
            }
            chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(name);

            if (chr == null) {
                c.getPlayer().dropMessage(6, "此玩家並不存在");
            } else {
                chr.removeAll(id, false);
                c.getPlayer().dropMessage(6, "所有ID為 " + id + " 的道具已經從 " + name + " 身上被移除了");
            }
            return true;

        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!RemoveItem <角色名稱> <物品ID> - 移除玩家身上的道具").toString();
        }
    }

    public static class RemoveItemOff extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String splitted[]) {
            if (splitted.length < 3) {
                return false;
            }
            try {
                Connection con = DatabaseConnection.getConnection();
                int item = Integer.parseInt(splitted[1]);
                String name = splitted[2];
                int id = 0, quantity = 0;
                List<Long> inventoryitemid = new LinkedList();
                boolean isEquip = GameConstants.isEquip(item);

                if (MapleCharacter.getCharacterByName(name) == null) {
                    c.getPlayer().dropMessage(5, "角色不存在資料庫。");
                    return true;
                } else {
                    id = MapleCharacter.getCharacterByName(name).getId();
                }

                PreparedStatement ps = con.prepareStatement("select inventoryitemid, quantity from inventoryitems WHERE itemid = ? and characterid = ?");
                ps.setInt(1, item);
                ps.setInt(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if (isEquip) {
                            long Equipid = rs.getLong("inventoryitemid");
                            if (Equipid != 0) {
                                inventoryitemid.add(Equipid);
                            }
                            quantity++;
                        } else {
                            quantity += rs.getInt("quantity");
                        }
                    }
                }
                if (quantity == 0) {
                    c.getPlayer().dropMessage(5, "玩家[" + name + "]沒有物品[" + item + "]在背包。");
                    return true;
                }

                if (isEquip) {
                    StringBuilder Sql = new StringBuilder();
                    Sql.append("Delete from inventoryequipment WHERE inventoryitemid = ");
                    for (int i = 0; i < inventoryitemid.size(); i++) {
                        Sql.append(inventoryitemid.get(i));
                        if (i < (inventoryitemid.size() - 1)) {
                            Sql.append(" OR inventoryitemid = ");
                        }
                    }
                    ps = con.prepareStatement(Sql.toString());
                    ps.executeUpdate();
                }

                ps = con.prepareStatement("Delete from inventoryitems WHERE itemid = ? and characterid = ?");
                ps.setInt(1, item);
                ps.setInt(2, id);
                ps.executeUpdate();
                ps.close();

                c.getPlayer().dropMessage(6, "已經從 " + name + " 身上被移除了道具 ID[" + item + "] 數量x" + quantity);
                return true;
            } catch (SQLException e) {
                return true;
            }
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!RemoveItemOff <物品ID> <角色名稱> - 移除玩家身上的道具").toString();
        }
    }

    public static class autoban extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            ServerConstants.setAutoban(!ServerConstants.getAutoban());
            c.getPlayer().dropMessage("自動封鎖: " + (ServerConstants.getAutoban() ? "開啟" : "關閉"));
            System.out.println("自動封鎖: " + (ServerConstants.getAutoban() ? "開啟" : "關閉"));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!Autoban - 自動封鎖開關").toString();
        }
    }

    public static class autodc extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            ServerConstants.autodc = (!ServerConstants.autodc);
            c.getPlayer().dropMessage("自動DC: " + (ServerConstants.autodc ? "開啟" : "關閉"));
            System.out.println("自動DC: " + (ServerConstants.autodc ? "開啟" : "關閉"));
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("!autodc - 自動DC開關").toString();
        }
    }
}
