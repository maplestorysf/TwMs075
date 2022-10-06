/*package server;

 import database.DatabaseConnection;
 import handling.cashshop.CashShopServer;
 import handling.channel.ChannelServer;
 import handling.login.LoginServer;
 import handling.world.World;
 import java.util.Set;
 import server.Timer.*;

 public class ShutdownServer implements Runnable, ShutdownServerMBean {

 private static final ShutdownServer instance = new ShutdownServer();
 public static boolean running = false;

 public static ShutdownServer getInstance() {
 return instance;
 }

 public void shutdown() {
 //   this.run();
 }

 @Override
 public void run() {
 synchronized (this) {
 if (running) { //Run once!
 return;
 }
 running = true;
 }
 // 商店優先儲存
 int ret = 0;
 for (ChannelServer cserv : ChannelServer.getAllInstances()) {
 ret += cserv.closeAllMerchant();
 }
 System.out.println("共儲存了 " + ret + " 個精靈商人");
 ret = 0;
 for (ChannelServer cserv : ChannelServer.getAllInstances()) {
 ret += cserv.closeAllPlayerShop();
 }
 System.out.println("共儲存了 " + ret + " 個個人執照商店");

 World.Guild.save();
 System.out.println("公會資料儲存完畢");
 World.Alliance.save();
 System.out.println("聯盟資料儲存完畢");
 World.Family.save();
 System.out.println("家族資料儲存完畢");

 System.out.println("關閉伺服器第一階段已完成...");

 try {
 Set<Integer> channels = ChannelServer.getAllChannels();

 for (Integer channel : channels) {
 try {
 ChannelServer cs = ChannelServer.getInstance(channel);
 cs.saveAll();
 cs.setPrepareShutdown();
 cs.shutdown();
 } catch (Exception e) {
 System.out.println("頻道" + String.valueOf(channel) + " 關閉失敗.");
 }
 }
 } catch (Exception e) {
 System.err.println("THROW" + e);
 }

 try {
 LoginServer.shutdown();
 System.out.println("登陸伺服器關閉完成.");
 } catch (Exception e) {
 System.out.println("登陸伺服器關閉失敗");
 }

 try {
 CashShopServer.shutdown();
 System.out.println("購物商城伺服器關閉完成.");
 } catch (Exception e) {
 System.out.println("購物商城伺服器關閉失敗");
 }

 try {
 DatabaseConnection.closeAll();
 System.out.println("資料庫清除連線完成");
 } catch (Exception e) {
 System.out.println("資料庫清除連線失敗");
 }

 WorldTimer.getInstance().stop();
 MapTimer.getInstance().stop();
 MobTimer.getInstance().stop();
 BuffTimer.getInstance().stop();
 CloneTimer.getInstance().stop();
 EventTimer.getInstance().stop();
 EtcTimer.getInstance().stop();
 PingTimer.getInstance().stop();

 System.out.println("Timer 關閉完成");
 System.out.println("關閉伺服器第二階段已完成...");

 }
 }
 */
package server;

import java.sql.SQLException;

import database.DatabaseConnection;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.login.LoginServer;
import handling.world.World;
import java.lang.management.ManagementFactory;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import server.Timer.*;
import tools.packet.CWvsContext;

public class ShutdownServer implements ShutdownServerMBean {

    public static ShutdownServer instance;

    public static void registerMBean() {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            instance = new ShutdownServer();
            mBeanServer.registerMBean(instance, new ObjectName("server:type=ShutdownServer"));
        } catch (Exception e) {
            System.out.println("Error registering Shutdown MBean");
            e.printStackTrace();
        }
    }

    public static ShutdownServer getInstance() {
        return instance;
    }

    public int mode = 0;

    public void shutdown() {//can execute twice
        run();
    }

    @Override
    public void run() {
        if (mode == 0) {
            // 商店優先儲存
            int ret = 0;
            for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                ret += cserv.closeAllMerchant();
            }
            System.out.println("共儲存了 " + ret + " 個精靈商人");
            ret = 0;
            for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                ret += cserv.closeAllPlayerShop();
            }
            System.out.println("共儲存了 " + ret + " 個個人執照商店");
            World.Guild.save();
            System.out.println("公會資料儲存完畢");

            System.out.println("關閉伺服器第一階段已完成.....");
            mode++;
        } else if (mode == 1) {
            mode++;
            System.out.println("關閉伺服器第二階段開始");

            try {
                Set<Integer> channels = ChannelServer.getAllChannels();

                for (Integer channel : channels) {
                    try {
                        ChannelServer cs = ChannelServer.getInstance(channel);
                        cs.saveAll();
                        cs.setPrepareShutdown();
                        cs.shutdown();
                    } catch (Exception e) {
                        System.out.println("頻道" + String.valueOf(channel) + " 關閉失敗.");
                    }
                }
            } catch (Exception e) {
                System.err.println("THROW" + e);
            }

            try {
                LoginServer.shutdown();
                System.out.println("登陸伺服器關閉完成.");
            } catch (Exception e) {
                System.out.println("登陸伺服器關閉失敗");
            }

            try {
                CashShopServer.shutdown();
                System.out.println("購物商城伺服器關閉完成.");
            } catch (Exception e) {
                System.out.println("購物商城伺服器關閉失敗");
            }

            try {
                DatabaseConnection.closeAll();
                System.out.println("資料庫清除連線完成");
            } catch (Exception e) {
                System.out.println("資料庫清除連線失敗");
            }

            WorldTimer.getInstance().stop();
            MapTimer.getInstance().stop();
            MobTimer.getInstance().stop();
            BuffTimer.getInstance().stop();
            CloneTimer.getInstance().stop();
            EventTimer.getInstance().stop();
            EtcTimer.getInstance().stop();
            PingTimer.getInstance().stop();
            System.out.println("Timer 關閉完成");
            System.out.println("關閉伺服器第二階段已完成...");

        }
    }
}
