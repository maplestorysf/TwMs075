package server;

import client.SkillFactory;
import client.inventory.MapleInventoryIdentifier;
import constants.ServerConstants;
import handling.MapleServerHandler;
import handling.channel.ChannelServer;
import handling.channel.MapleGuildRanking;
import handling.login.LoginServer;
import handling.cashshop.CashShopServer;
import handling.login.LoginInformationProvider;
import handling.world.World;
import java.sql.SQLException;
import database.DatabaseConnection;
import handling.world.guild.MapleGuild;
import java.sql.PreparedStatement;
import server.Timer.*;
import server.events.MapleOxQuizFactory;
import server.life.MapleLifeFactory;
import server.life.MapleMonsterInformationProvider;
import server.life.MobSkillFactory;
import server.life.PlayerNPC;
import server.quest.MapleQuest;
import java.util.concurrent.atomic.AtomicInteger;

public class Start {

    public static long startTime = System.currentTimeMillis();
    public static final Start instance = new Start();
    public static AtomicInteger CompletedLoadingThreads = new AtomicInteger(0);

    public void run() throws InterruptedException {
        System.setProperty("net.sf.odinms.wzpath", "wz");

        if (Boolean.parseBoolean(ServerProperties.getProperty("net.sf.odinms.world.admin")) || ServerConstants.Use_Localhost) {
            ServerConstants.Use_Fixed_IV = false;
            System.out.println("[!!! 管理員模式開啟 !!!]");
        }

        if (ServerConstants.AUTO_REGISTER) {
            System.out.println("【自動註冊】開啟");
        } else {
            System.out.println("【自動註冊】關閉");
        }

        resetAllLoginState();

        System.out.println("[" + ServerProperties.getProperty("net.sf.odinms.login.serverName") + "] 版本 " + ServerConstants.MAPLE_VERSION + "." + ServerConstants.MAPLE_PATCH);

        System.out.println("正在初始化設置...");
        World.init();
        System.out.println("正在載入 Timer系統...");
        WorldTimer.getInstance().start();
        EtcTimer.getInstance().start();
        MapTimer.getInstance().start();
        MobTimer.getInstance().start();
        CloneTimer.getInstance().start();
        EventTimer.getInstance().start();
        BuffTimer.getInstance().start();
        PingTimer.getInstance().start();
        System.out.println("正在載入 公會系統...");
        MapleGuildRanking.getInstance().load();
        MapleGuild.loadAll();
        System.out.println("正在載入 釣魚系統...");
        FishingRewardFactory.getInstance();
        System.out.println("正在載入 任務系統...");
        MapleLifeFactory.loadQuestCounts();
        MapleQuest.initQuests();
        MapleOxQuizFactory.getInstance();
        System.out.print("正在載入 物品資訊...");
        MapleItemInformationProvider.getInstance().runEtc();
        MapleItemInformationProvider.getInstance().runItems();
        System.out.println("正在載入 怪物資訊...");
        MapleMonsterInformationProvider.getInstance().load();
        MapleCarnivalFactory.getInstance();
        MobSkillFactory.getInstance();
        System.out.println("正在載入 速配卷資訊...");
        PredictCardFactory.getInstance().initialize();
        System.out.println("正在載入 技能資訊...");
        SkillFactory.load();
        System.out.println("正在載入 登入資訊...");
        LoginInformationProvider.getInstance();
        System.out.println("正在載入 獎勵系統...");
        RandomRewards.load();
        System.out.println("正在載入 排行資訊...");
        SpeedRunner.loadSpeedRuns();
        // RankingWorker.run();
        System.out.println("正在載入 UID資訊...");
        MapleInventoryIdentifier.getInstance();
        System.out.println("正在載入 商城資訊...");
        CashItemFactory.getInstance().initialize();
        MTSStorage.load();
        System.out.println();
        MapleServerHandler.initiate();
        LoginServer.run_startup_configurations();
        ChannelServer.startChannel_Main();
        CashShopServer.run_startup_configurations();
        CheatTimer.getInstance().register(AutobanManager.getInstance(), 60000);
        Runtime.getRuntime().addShutdownHook(new Thread(new Shutdown()));
        ShutdownServer.registerMBean();
        World.registerRespawn();

        PlayerNPC.loadAll();// touch - so we see database problems early...
        //MapleMonsterInformationProvider.getInstance().addExtra();
        LoginServer.setOn(); //now or later

        // 程式碼移植
        World.gainMaplePoint(60); // 六十分鐘自動給點數
        World.autoSave(5); // 五分鐘自動存檔
        //

        System.out.println();
        System.out.println("【伺服器開啟完畢】");
    }

    public static class Shutdown implements Runnable {

        @Override
        public void run() {
            ShutdownServer.getInstance().run();
            ShutdownServer.getInstance().run();
        }
    }

    public static void main(final String args[]) throws InterruptedException {
        instance.run();
    }

    private void resetAllLoginState() {
        try {
            final PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("UPDATE accounts SET loggedin = 0");
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            throw new RuntimeException("[例外狀況] 無法連線至資料庫.");
        }
    }
}
