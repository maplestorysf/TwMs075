package client.messages.commands;

import constants.GameConstants;
import client.MapleClient;
import client.MapleStat;
import client.inventory.MapleInventory;
import client.inventory.MapleInventoryType;
import constants.ServerConstants;
import constants.ServerConstants.PlayerGMRank;
import handling.channel.ChannelServer;
import scripting.NPCScriptManager;
import server.life.MapleMonster;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import java.util.Arrays;
import tools.StringUtil;
import handling.world.World;
import java.util.Calendar;
import server.MapleInventoryManipulator;
import tools.FileoutputUtil;
import tools.packet.CWvsContext;

/**
 *
 * @author Emilyx3
 */
public class PlayerCommand {

    public static PlayerGMRank getPlayerLevelRequired() {
        return ServerConstants.PlayerGMRank.普通玩家;
    }

    public static class 幫助 extends help {

        @Override
        public String getMessage() {
            return new StringBuilder().append("@幫助 - 幫助").toString();
        }
    }

    public static class help extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            c.getPlayer().dropNPC(""
                    + "\t\t #i3994014##i3994018##i3994070##i3994061##i3994005##i3991038##i3991004#\r\n"
                    + "\t\t#fMob/0100101.img/move/1##b 親愛的： #h \r\n"
                    + " #fMob/0100101.img/move/1##k\r\r\n"
                    + "\t\t#fMob/0130101.img/move/1##g[以下是" + c.getChannelServer().getServerName() + " 玩家指令]#k#fMob/0130101.img/move/1#\r\n"
                    + "\t  #r▇▇▆▅▄▃▂#d萬用指令區#r▂▃▄▅▆▇▇\r\n"
                    + "\t\t#b@清除道具 <裝備欄/消耗欄/裝飾欄/其他欄/特殊欄> <開始格數> <結束格數>#k - #r<清除背包道具>#k\r\n"
                    + "\t\t#b@在線人數#k - #r<查詢當前伺服器人數>#k\r\n"
                    + "\t\t#b@查看#k - #r<解除異常+查看當前狀態>#k\r\n"
                    + "\t\t#b@獎勵#k - #r<領取在線點數>#k\r\n"
                    + "\t\t#b@怪物#k - #r<查看身邊怪物訊息>#k\r\n"
                    + "\t\t#b@expfix#k - #r<經驗歸零(修復假死)>#k\r\n"
                    + "\t\t#b@CGM <訊息>#k - #r<傳送訊息給GM>#k\r\n"
                    + "\t\t#b@存檔/@save#k - #r<存檔>#k\r\n"
                    + "\t\t#b@TSmega#k - #r<開/關所有廣播>#k\r\n"
                    + "\t  #g▇▇▆▅▄▃▂#dNPＣ指令區#g▂▃▄▅▆▇▇\r\n"
                    //    + "\t\t#b@bspq#k - #r<BOSSPQ兌換NPC>#k\r\n"
                    + "\t\t#b@萬能/@npc#k - #r<工具箱>#k\r\n"
                    + "\t\t#b@猜拳/@pk#k - #r<小遊戲>#k\r\n"
                    + "\t\t#b@event#k - #r<參加活動>#k\r\n"
            );
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@help - 幫助").toString();
        }
    }

    public abstract static class OpenNPCCommand extends CommandExecute {

        protected int npc = -1;
        private static final int[] npcs = { //Ish yur job to make sure these are in order and correct ;(
            9010017,
            9000001,
            9000000,
            9330082,
            9000019};

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (npc != 1 && c.getPlayer().getMapId() != 910000000) { //drpcash can use anywhere
                for (int i : GameConstants.blockedMaps) {
                    if (c.getPlayer().getMapId() == i) {
                        c.getPlayer().dropMessage(1, "你不能在這裡使用指令.");
                        return true;
                    }
                }
                if (c.getPlayer().getLevel() < 10) {
                    c.getPlayer().dropMessage(1, "你的等級必須是10等.");
                    return true;
                }
                if (c.getPlayer().getMap().getSquadByMap() != null || c.getPlayer().getEventInstance() != null || c.getPlayer().getMap().getEMByMap() != null || c.getPlayer().getMapId() >= 990000000/* || FieldLimitType.VipRock.check(c.getPlayer().getMap().getFieldLimit())*/) {
                    c.getPlayer().dropMessage(1, "你不能在這裡使用指令.");
                    return true;
                }
                if ((c.getPlayer().getMapId() >= 680000210 && c.getPlayer().getMapId() <= 680000502) || (c.getPlayer().getMapId() / 1000 == 980000 && c.getPlayer().getMapId() != 980000000) || (c.getPlayer().getMapId() / 100 == 1030008) || (c.getPlayer().getMapId() / 100 == 922010) || (c.getPlayer().getMapId() / 10 == 13003000)) {
                    c.getPlayer().dropMessage(1, "你不能在這裡使用指令.");
                    return true;
                }
            }
            NPCScriptManager.getInstance().start(c, npcs[npc]);
            return true;
        }
    }

    public static class 丟裝 extends DropCash {

        @Override
        public String getMessage() {
            return new StringBuilder().append("@丟裝 - 呼叫清除現金道具npc").toString();
        }
    }

    public static class DropCash extends OpenNPCCommand {

        public DropCash() {
            npc = 0;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@dropbash - 呼叫清除現金道具npc").toString();
        }

    }

    public static class event extends OpenNPCCommand {

        public event() {
            npc = 1;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@event - 呼叫活動npc").toString();
        }
    }

    public static class npc extends 萬能 {

        @Override
        public String getMessage() {
            return new StringBuilder().append("@npc - 呼叫萬能npc").toString();
        }
    }

    public static class 萬能 extends OpenNPCCommand {

        public 萬能() {
            npc = 2;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@萬能 - 呼叫萬能npc").toString();
        }
    }
//
//    public static class bspq extends OpenNPCCommand {
//
//        public bspq() {
//            npc = 3;
//        }
//
//        @Override
//        public String getMessage() {
//            return new StringBuilder().append("@bspq - 呼叫Boss挑戰npc").toString();
//        }
//    }

    public static class pk extends 猜拳 {

        @Override
        public String getMessage() {
            return new StringBuilder().append("@pk - 呼叫猜拳npc").toString();
        }
    }

    public static class 猜拳 extends OpenNPCCommand {

        public 猜拳() {
            npc = 4;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@猜拳 - 呼叫猜拳npc").toString();
        }
    }

    public static class save extends 存檔 {
    }

    public static class 存檔 extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            try {
                c.getPlayer().saveToDB(true, true);
                c.getPlayer().dropMessage(5, "保存成功！");
            } catch (UnsupportedOperationException ex) {
                c.getPlayer().dropMessage(5, "保存失敗！");
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@save - 存檔").toString();
        }
    }

    public static class expfix extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            c.getPlayer().setExp(0);
            c.getPlayer().updateSingleStat(MapleStat.EXP, c.getPlayer().getExp());
            c.getPlayer().dropMessage(5, "經驗修復完成");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@expfix - 經驗歸零").toString();
        }
    }

    public static class 在線人數 extends online {

    }

    public static class online extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            int channelOnline = c.getChannelServer().getConnectedClients();
            int totalOnline = 0;
            /*伺服器總人數*/
            for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                totalOnline += cserv.getConnectedClients();
            }
            c.getPlayer().dropMessage(6, new StringBuilder().append("當前").append(c.getChannel()).append("頻道: ").append(channelOnline).append("人   ").append("當前伺服器總計線上人數: ").append(totalOnline).append("個").toString());
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@online - 查看線上人數").toString();
        }
    }

    public static class TSmega extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            c.getPlayer().setSmega();
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@TSmega - 開/關閉廣播").toString();
        }
    }

    public static class 解卡 extends ea {

        @Override
        public String getMessage() {
            return new StringBuilder().append("@解卡 - 解卡").toString();
        }
    }

    public static class 查看 extends ea {

        @Override
        public String getMessage() {
            return new StringBuilder().append("@查看 - 解卡").toString();
        }
    }

    public static class ea extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            c.removeClickedNPC();
            NPCScriptManager.getInstance().dispose(c);
            c.sendPacket(CWvsContext.enableActions());
            c.getPlayer().dropMessage(1, "解卡完畢..");
            c.getPlayer().dropMessage(6, "當前系統時間" + FileoutputUtil.CurrentReadable_Time() + " 星期" + getDayOfWeek());
            c.getPlayer().dropMessage(6, "經驗值倍率 " + ((Math.round(c.getPlayer().getEXPMod()) * 100) * Math.round(c.getPlayer().getStat().expBuff / 100.0)) + "%, 掉寶倍率 " + Math.round(c.getPlayer().getDropMod() * (c.getPlayer().getStat().dropBuff / 100.0) * 100) + "%, 楓幣倍率 " + Math.round((c.getPlayer().getStat().mesoBuff / 100.0) * 100) + "% VIP經驗加成：" + c.getPlayer().getVipExpRate() + "%");
            c.getPlayer().dropMessage(6, "目前剩餘 " + c.getPlayer().getCSPoints(1) + " GASH " + c.getPlayer().getCSPoints(2) + " 楓葉點數 ");
            c.getPlayer().dropMessage(6, "當前延遲 " + c.getPlayer().getClient().getLatency() + " 毫秒");
            c.getPlayer().dropMessage(6, "已使用:" + c.getPlayer().getHpApUsed() + " 張能力重置捲");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@ea - 解卡").toString();
        }

        public static String getDayOfWeek() {
            int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1;
            String dd = String.valueOf(dayOfWeek);
            switch (dayOfWeek) {
                case 0:
                    dd = "日";
                    break;
                case 1:
                    dd = "一";
                    break;
                case 2:
                    dd = "二";
                    break;
                case 3:
                    dd = "三";
                    break;
                case 4:
                    dd = "四";
                    break;
                case 5:
                    dd = "五";
                    break;
                case 6:
                    dd = "六";
                    break;
            }
            return dd;
        }
    }

    public static class 怪物 extends mob {

        @Override
        public String getMessage() {
            return new StringBuilder().append("@怪物 - 查看怪物狀態").toString();
        }
    }

    public static class mob extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            MapleMonster monster = null;
            for (final MapleMapObject monstermo : c.getPlayer().getMap().getMapObjectsInRange(c.getPlayer().getPosition(), 100000, Arrays.asList(MapleMapObjectType.MONSTER))) {
                monster = (MapleMonster) monstermo;
                if (monster.isAlive()) {
                    c.getPlayer().dropMessage(6, "怪物 " + monster.toString());
                }
            }
            if (monster == null) {
                c.getPlayer().dropMessage(6, "找不到地圖上的怪物");
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@mob - 查看怪物狀態").toString();
        }
    }

    public static class CGM extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            boolean autoReply = false;

            if (splitted.length < 2) {
                return false;
            }
            String talk = StringUtil.joinStringFrom(splitted, 1);
            if (c.getPlayer().isGM()) {
                c.getPlayer().dropMessage(6, "因為你自己是GM所以無法使用此指令,可以嘗試!cngm <訊息> 來建立GM聊天頻道~");
            } else if (!c.getPlayer().getCheatTracker().GMSpam(100000, 1)) { // 1 minutes.
                boolean fake = false;
                boolean showmsg = true;

                // 管理員收不到，玩家有顯示傳送成功
                if (ServerConstants.getBlackList().containsKey(c.getAccID())) {
                    fake = true;
                }

                // 管理員收不到，玩家沒顯示傳送成功
                if (talk.contains("搶") && talk.contains("圖")) {
                    c.getPlayer().dropMessage(1, "搶圖自行解決！！");
                    fake = true;
                    showmsg = false;
                } else if ((talk.contains("被") && talk.contains("騙")) || (talk.contains("點") && talk.contains("騙"))) {
                    c.getPlayer().dropMessage(1, "被騙請自行解決");
                    fake = true;
                    showmsg = false;
                } else if ((talk.contains("被") && talk.contains("盜"))) {
                    c.getPlayer().dropMessage(1, "被盜請自行解決");
                    fake = true;
                    showmsg = false;
                } else if (talk.contains("刪") && ((talk.contains("角") || talk.contains("腳")) && talk.contains("錯"))) {
                    c.getPlayer().dropMessage(1, "刪錯角色請自行解決");
                    fake = true;
                    showmsg = false;
                } else if (talk.contains("亂") && (talk.contains("名") && talk.contains("聲"))) {
                    c.getPlayer().dropMessage(1, "請自行解決");
                    fake = true;
                    showmsg = false;
                }

                // 管理員收的到，自動回復
                if (talk.toUpperCase().contains("VIP") && ((talk.contains("領") || (talk.contains("獲"))) && talk.contains("取"))) {
                    c.getPlayer().dropMessage(1, "VIP將會於儲值後一段時間後自行發放，請耐心等待");
                    autoReply = true;
                } else if ((talk.contains("商人") && talk.contains("吃")) || (talk.contains("商店") && talk.contains("補償"))) {
                    c.getPlayer().dropMessage(1, "目前精靈商人裝備和楓幣有機率被吃\r\n如被吃了請務必將當時的情況完整描述給管理員\r\n\r\nPS: 不會補償任何物品");
                    autoReply = true;
                } else if (talk.contains("檔") && talk.contains("案") && talk.contains("受") && talk.contains("損")) {
                    c.getPlayer().dropMessage(1, "檔案受損請重新解壓縮主程式唷");
                    autoReply = true;
                } else if ((talk.contains("缺") || talk.contains("少")) && ((talk.contains("技") && talk.contains("能") && talk.contains("點")) || talk.toUpperCase().contains("SP"))) {
                    c.getPlayer().dropMessage(1, "缺少技能點請重練，沒有其他方法了唷");
                    autoReply = true;

                }

                if (showmsg) {
                    c.getPlayer().dropMessage(6, "訊息已經寄送給GM了!");
                }

                if (!fake) {
                    World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[管理員幫幫忙]頻道 " + c.getPlayer().getClient().getChannel() + " 玩家 [" + c.getPlayer().getName() + "] (" + c.getPlayer().getId() + "): " + talk + (autoReply ? " -- (系統已自動回復)" : "")));
                }

                FileoutputUtil.logToFile("紀錄/系統/管理員幫幫忙.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家[" + c.getPlayer().getName() + "] 帳號[" + c.getAccountName() + "]: " + talk + (autoReply ? " -- (系統已自動回復)" : "") + "\r\n");
            } else {
                c.getPlayer().dropMessage(6, "為了防止對GM刷屏所以每1分鐘只能發一次.");
            }
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@cgm - 跟GM回報").toString();
        }
    }

    public static class 清除道具 extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            if (splitted.length < 4) {
                return false;
            }
            MapleInventory inv;
            MapleInventoryType type;
            String Column = "null";
            int start = -1;
            int end = -1;
            try {
                Column = splitted[1];
                start = Integer.parseInt(splitted[2]);
                end = Integer.parseInt(splitted[3]);
            } catch (Exception ex) {
            }
            if (start == -1 || end == -1) {
                c.getPlayer().dropMessage("@清除道具 <裝備欄/消耗欄/裝飾欄/其他欄/特殊欄> <開始格數> <結束格數>");
                return true;
            }
            if (start < 1) {
                start = 1;
            }
            if (end > 96) {
                end = 96;
            }

            switch (Column) {
                case "裝備欄":
                    type = MapleInventoryType.EQUIP;
                    break;
                case "消耗欄":
                    type = MapleInventoryType.USE;
                    break;
                case "裝飾欄":
                    type = MapleInventoryType.SETUP;
                    break;
                case "其他欄":
                    type = MapleInventoryType.ETC;
                    break;
                case "特殊欄":
                    type = MapleInventoryType.CASH;
                    break;
                default:
                    type = null;
                    break;
            }
            if (type == null) {
                c.getPlayer().dropMessage("@清除道具 <裝備欄/消耗欄/裝飾欄/其他欄/特殊欄> <開始格數> <結束格數>");
                return true;
            }
            inv = c.getPlayer().getInventory(type);

            for (int i = start; i <= end; i++) {
                if (inv.getItem((short) i) != null) {
                    MapleInventoryManipulator.removeFromSlot(c, type, (short) i, inv.getItem((short) i).getQuantity(), true);
                }
            }
            FileoutputUtil.logToFile("紀錄/指令/玩家指令.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress().split(":")[0] + " 帳號: " + c.getAccountName() + " 玩家: " + c.getPlayer().getName() + " 使用了指令 " + StringUtil.joinStringFrom(splitted, 0));
            c.getPlayer().dropMessage(6, "您已經清除了第 " + start + " 格到 " + end + "格的" + Column + "道具");
            return true;
        }

        @Override
        public String getMessage() {
            return new StringBuilder().append("@清除道具 <裝備欄/消耗欄/裝飾欄/其他欄/特殊欄> <開始格數> <結束格數>").toString();
        }
    }

    public static class 獎勵 extends CommandExecute {

        @Override
        public boolean execute(MapleClient c, String[] splitted) {
            int gain = c.getPlayer().getMP();
            if (gain <= 0) {
                c.getPlayer().dropMessage(5, "目前無獎勵可領取。");
                return true;
            }
            if (splitted.length < 2) {
                c.getPlayer().dropMessage(5, "目前楓葉點數： " + c.getPlayer().getCSPoints(2));
                c.getPlayer().dropMessage(5, "目前獎勵累積 " + gain + " 楓葉點數，領取獎勵請輸入【@獎勵 領取】指令");
            } else if ("領取".equals(splitted[1])) {
                gain = c.getPlayer().getMP();
                c.getPlayer().modifyCSPoints(2, gain, true);
                c.getPlayer().setMP(0);
                c.getPlayer().saveToDB(false, false);
                c.getPlayer().dropMessage(5, "恭喜獲得 " + gain + " 楓葉點數，目前楓葉點數： " + c.getPlayer().getCSPoints(2));
            }
            return true;
        }

        public String getMessage() {
            return new StringBuilder().append("@獎勵 ").toString();
        }
    }

    /*
     public static class dpm extends CommandExecute {

     @Override
     public boolean execute(final MapleClient c, String splitted[]) {
     if (c.getPlayer().getMapId() == 100000000 && c.getPlayer().getLevel() >= 70 || !c.getPlayer().isGM()) {
     if (!c.getPlayer().isTestingDPS()) {
     c.getPlayer().toggleTestingDPS();
     c.getPlayer().dropMessage(5, "請持續攻擊怪物1分鐘，來測試您的每秒輸出！");
     final MapleMonster mm = MapleLifeFactory.getMonster(9001007);
     int distance = ((c.getPlayer().getJob() >= 300 && c.getPlayer().getJob() < 413) || (c.getPlayer().getJob() >= 1300 && c.getPlayer().getJob() < 1500) || (c.getPlayer().getJob() >= 520 && c.getPlayer().getJob() < 600)) ? 125 : 50;
     Point p = new Point(c.getPlayer().getPosition().x - distance, c.getPlayer().getPosition().y);
     mm.setBelongTo(c.getPlayer());
     final long newhp = Long.MAX_VALUE;
     OverrideMonsterStats overrideStats = new OverrideMonsterStats();
     overrideStats.setOHp(newhp);
     mm.setHp(newhp);
     mm.setOverrideStats(overrideStats);
     c.getPlayer().getMap().spawnMonsterOnGroundBelow(mm, p);
     final MapleMap nowMap = c.getPlayer().getMap();
     Timer.EventTimer.getInstance().schedule(new Runnable() {
     @Override
     public void run() {
     long health = mm.getHp();
     nowMap.killMonster1(mm);
     long dps = (newhp - health) / 15;
     if (dps > c.getPlayer().getDPS()) {
     c.getPlayer().dropMessage(6, "你的DPM是 " + dps + ". 這是一個新的紀錄！");
     c.getPlayer().setDPS(dps);
     c.getPlayer().savePlayer();
     c.getPlayer().toggleTestingDPS();
     } else {
     c.getPlayer().dropMessage(6, "你的DPM是 " + dps + ". 您目前的紀錄是 " + c.getPlayer().getDPS() + ".");
     c.getPlayer().toggleTestingDPS();
     }

     }
     }, 60000);
     } else {
     c.getPlayer().dropMessage(5, "請先把你的這回DPM測試完畢。");
     return true;
     }
     } else {
     c.getPlayer().dropMessage(5, "只能在弓箭手村測試DPM，並且等級符合70以上。");
     return true;
     }
     return true;
     }

     @Override
     public String getMessage() {
     return new StringBuilder().append("").toString();
     }
     }
     EnterCashShop
     public static final void EnterCashShop(final MapleClient c, final MapleCharacter chr, final boolean mts) {
     if (res == 1) {
     chr.dropMessage(5, "角色保存成功！");
     }
     if (chr.isTestingDPS()) {
     final MapleMonster mm = MapleLifeFactory.getMonster(9001007);
     if(chr.getMap() != null)
     chr.getMap().Killdpm(true);
     chr.toggleTestingDPS();
     chr.dropMessage(5, "已停止當前的DPM測試。");
     }

    
     */
}
