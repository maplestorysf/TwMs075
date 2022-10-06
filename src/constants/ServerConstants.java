package constants;

import java.util.HashMap;
import java.util.Map;
import server.Randomizer;
import server.ServerProperties;

public class ServerConstants {

    // 版本設定
    public static final short MAPLE_VERSION = (short) 75;
    public static final String MAPLE_PATCH = "1";

    // IP設定
    public static String[] Gateway_IP;

    // 調試用設定
    public static boolean Use_Fixed_IV = true; // true = disable sniffing, false = server can connect to itself
    public static boolean Use_Localhost = false; // true = packets are logged, false = others can connect to server
    public static boolean TESPIA = false;

    // 拍賣相關設定
    public static final int MIN_MTS = 100; //lowest amount an item can be, GMS = 110
    public static final int MTS_BASE = 0; //+amount to everything, GMS = 500, MSEA = 1000
    public static final int MTS_TAX = 5; //+% to everything, GMS = 10
    public static final int MTS_MESO = 10000; //mesos needed, GMS = 500

    // 自定功能設定
    public static boolean autoban = true; // 自動封鎖開關
    public static boolean autodc = true; // 自動斷線開關
    public static boolean CommandLock = false; // 指令鎖定

    // 髒話過濾
    public static String[] banText = {"幹", "靠", "屎", "糞", "淦", "靠"};
    public static Map<Integer, String> BlackList = new HashMap();
    public static String[] SorrySentence = {
        "天阿 我嘴好臭",
        "好煩喔 每天早上起床洗臉刷牙都會看到智障",
        "我就是個腦殘才會耍智障> <",
        "別懷疑 我就是個死宅砲 好爽喔",
        "我好醜 我該死 我不該講屁話",
        "如果我可以說話 該有多好呢",
        "想念可以說話的日子QAQ",
        "我被禁言 原因一定是我很智障",
        "真好 羨慕你們可以說話",
        "阿阿阿阿阿 好想說話唷 嗚嗚",
        "想上正音班 因為我不會說話",
        "老師拜託交我些單字 因為我只有這幾句",
        "我這人就是無恥 讓GM想這些屁話想的這麼辛苦",
        "為什麼我當初要這麼吵阿  娘 您告訴我",
        "現在的我根本就是NPC 只會說固定幾段話",
        "如果有稱號 我大概只能拿個閉嘴稱號吧",
        "想著過去的種種 但我卻說不出來",
        "如果能讓我有個願望 我希望我會手語",
        "今日報告題目:閉嘴"
    };

    // LOG輸出設定
    public static boolean LOG_DAMAGE = false;
    public static boolean LOG_MERCHANT = true;
    public static boolean LOG_CSBUY = false;
    public static boolean LOG_CHAT = false;
    public static boolean LOG_MEGA = false;
    public static boolean LOG_PACKETS = false;
    public static boolean LOG_CHALKBOARD = false;
    public static boolean LOG_SCROLL = false;

    // Settings.ini 設置檔
    public static boolean DEBUG_MODE = false;
    public static boolean AUTO_REGISTER = false;

    public static final byte Class_Bonus_EXP(final int job) {
        switch (job) {
            case 501:
            case 530:
            case 531:
            case 532:
            case 2300:
            case 2310:
            case 2311:
            case 2312:
            case 3100:
            case 3110:
            case 3111:
            case 3112:
            case 800:
            case 900:
                return 10;
        }
        return 0;
    }

    public static String RandomSorry() {
        String msg = SorrySentence[Randomizer.nextInt(SorrySentence.length)];
        return msg;
    }

    public static Map<Integer, String> getBlackList() {
        return BlackList;
    }

    public static void setBlackList(int accid, String name) {
        BlackList.put(accid, name);
    }

    public static boolean getAutoban() {
        return autoban;
    }

    public static void setAutoban(boolean x) {
        autoban = x;
    }

    public static boolean getCommandLock() {
        return CommandLock;
    }

    public static void setCommandLock(boolean x) {
        CommandLock = x;
    }

    public static boolean isCanTalkText(String text) {
        String message = text.toLowerCase();
        for (int i = 0; i < banText.length; i++) {
            if (message.contains(banText[i])) {
                return false;
            }
        }
        if ((message.contains("垃") && message.contains("圾"))
                || (message.contains("雖") && message.contains("小"))
                || (message.contains("沙") && message.contains("小"))
                || (message.contains("殺") && message.contains("小"))
                || (message.contains("三") && message.contains("小"))
                //
                || (message.contains("北") && message.contains("七"))
                || (message.contains("北") && message.contains("7"))
                || (message.contains("巴") && message.contains("七"))
                || (message.contains("巴") && message.contains("7"))
                || (message.contains("八") && message.contains("七"))
                || (message.contains("八") && message.contains("7"))
                //
                || (message.contains("白") && message.contains("目"))
                || (message.contains("白") && message.contains("癡"))
                || (message.contains("白") && message.contains("吃"))
                || (message.contains("白") && message.contains("ㄔ"))
                || (message.contains("白") && message.contains("ㄘ"))
                //
                || (message.contains("機") && message.contains("車"))
                || (message.contains("機") && message.contains("八"))
                //
                || (message.contains("伶") && message.contains("北"))
                || (message.contains("林") && message.contains("北"))
                //
                || (message.contains("廢") && message.contains("物"))
                || (message.contains("媽") && message.contains("的"))
                || (message.contains("俗") && message.contains("辣"))
                || (message.contains("智") && message.contains("障"))
                || (message.contains("低") && message.contains("能"))
                || (message.contains("乞") && message.contains("丐"))
                || (message.contains("乾") && message.contains("娘"))
                //
                || (message.contains("ㄎ") && message.contains("ㄅ"))
                || (message.contains("ㄌ") && message.contains("ㄐ"))
                || (message.contains("ㄋ") && message.contains("ㄠ") && message.contains("ˇ"))
                || (message.contains("ㄍ") && message.contains("ˋ"))
                //
                //|| (message.contains("0") && message.contains("8"))
                //|| (message.contains("7") && message.contains("8"))
                //
                || (message.contains("e04")) // || (message.contains("e") && message.contains("0") && message.contains("4")) //|| (message.contains("m") && message.contains("d"))
                // || (message.contains("m") && message.contains("b"))
                ) {
            return false;
        }
        return true;
    }

    public static enum PlayerGMRank {

        普通玩家(0),
        實習生(1),
        老實習生(2),
        巡邏者(3),
        管理員(4),
        超級管理員(5),
        領航者(6),
        神(100);
        private char commandPrefix;
        private int level;

        PlayerGMRank(int level) {
            this.commandPrefix = level > 0 ? '!' : '@';
            this.level = level;
        }

        public char getCommandPrefix() {
            return commandPrefix;
        }

        public int getLevel() {
            return level;
        }
    }

    public static enum CommandType {

        NORMAL(0),
        TRADE(1),
        POKEMON(2);
        private int level;

        CommandType(int level) {
            this.level = level;
        }

        public int getType() {
            return level;
        }
    }

    public static void loadSetting() {
        LOG_SCROLL = ServerProperties.getProperty("ScrollLog", LOG_SCROLL);
        LOG_MERCHANT = ServerProperties.getProperty("MerchantLog", LOG_MERCHANT);
        LOG_MEGA = ServerProperties.getProperty("MegaLog", LOG_MEGA);
        LOG_CSBUY = ServerProperties.getProperty("CSLog", LOG_CSBUY);
        LOG_DAMAGE = ServerProperties.getProperty("DamLog", LOG_DAMAGE);
        LOG_CHALKBOARD = ServerProperties.getProperty("ChalkBoardLog", LOG_CHALKBOARD);
        LOG_CHAT = ServerProperties.getProperty("ChatLog", LOG_CHAT);
        DEBUG_MODE = ServerProperties.getProperty("DebugMode", DEBUG_MODE);
        AUTO_REGISTER = ServerProperties.getProperty("AutoRegister", AUTO_REGISTER);
        Gateway_IP = ServerProperties.getProperty("net.sf.odinms.channel.net.interface").split("\\.");
    }

    static {
        loadSetting();
    }

}
