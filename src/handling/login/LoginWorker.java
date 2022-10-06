package handling.login;

import java.util.Map;
import java.util.Map.Entry;

import client.MapleClient;
import handling.channel.ChannelServer;
import handling.login.handler.CharLoginHandler;
import handling.world.World;
import server.Timer.PingTimer;
import tools.packet.CWvsContext;
import tools.packet.LoginPacket;

public class LoginWorker {

    private static long lastUpdate = 0;

    public static void registerClient(final MapleClient c) {
        if (LoginServer.isAdminOnly() && !c.isGm()) {
            c.sendPacket(CWvsContext.serverNotice(1, "伺服器目前正在維修中.\r\n目前管理員正在測試物品.\r\n請稍後等待維修。"));
            c.sendPacket(LoginPacket.getLoginFailed(7));
            return;
        }

        if (System.currentTimeMillis() - lastUpdate > 600000) { // Update once every 10 minutes
            lastUpdate = System.currentTimeMillis();
            final Map<Integer, Integer> load = ChannelServer.getChannelLoad();
            int usersOn = 0;
            if (load == null || load.size() <= 0) { // In an unfortunate event that client logged in before load
                lastUpdate = 0;
                c.sendPacket(LoginPacket.getLoginFailed(7));
                return;
            }
            final double loadFactor = 1200 / ((double) LoginServer.getUserLimit() / load.size());
            for (Entry<Integer, Integer> entry : load.entrySet()) {
                usersOn += entry.getValue();
                load.put(entry.getKey(), Math.min(1200, (int) (entry.getValue() * loadFactor)));
            }
            LoginServer.setLoad(load, usersOn);
            lastUpdate = System.currentTimeMillis();
        }

        if (c.finishLogin() == 0) {
            if (c.getSecondPassword() == null) {
                c.sendPacket(LoginPacket.getGenderNeeded());
            } else {
                LoginServer.forceRemoveClient(c);
                ChannelServer.forceRemovePlayerByAccId(c, c.getAccID());
                World.clearChannelChangeDataByAccountId(c.getAccID());
                LoginServer.getClientStorage().registerAccount(c);
                c.sendPacket(LoginPacket.getAuthSuccessRequest(c));
                CharLoginHandler.ServerListRequest(c);
            }
            c.setIdleTask(PingTimer.getInstance().schedule(new Runnable() {

                public void run() {
                    c.getSession().close();
                }
            }, 10 * 60 * 10000));
        } else {
            c.sendPacket(LoginPacket.getLoginFailed(7));
        }
    }
}
