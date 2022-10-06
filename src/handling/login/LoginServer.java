package handling.login;

import client.MapleClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import handling.MapleServerHandler;
import handling.mina.MapleCodecFactory;
import java.util.Collection;
import java.util.HashSet;
import java.util.WeakHashMap;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.common.IoAcceptor;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import server.ServerProperties;
import tools.Pair;

public class LoginServer {

    public static int port = 8484;
    private static InetSocketAddress InetSocketadd;
    private static IoAcceptor acceptor;
    private static Map<Integer, Integer> load = new HashMap<>();
    private static String serverName, eventMessage;
    private static byte flag;
    private static int maxCharacters, userLimit, usersOn = 0;
    private static boolean finishedShutdown = true, adminOnly = false;
    private static HashMap<Integer, Pair<String, String>> loginAuth = new HashMap<>();
    private static HashSet<String> loginIPAuth = new HashSet<>();
    private static AccountStorage clients;

    public static void putLoginAuth(int chrid, String ip, String tempIP) {
        loginAuth.put(chrid, new Pair<>(ip, tempIP));
        loginIPAuth.add(ip);
    }

    public static Pair<String, String> getLoginAuth(int chrid) {
        return loginAuth.remove(chrid);
    }

    public static boolean containsIPAuth(String ip) {
        return loginIPAuth.contains(ip);
    }

    public static void removeIPAuth(String ip) {
        loginIPAuth.remove(ip);
    }

    public static void addIPAuth(String ip) {
        loginIPAuth.add(ip);
    }

    public static final void addChannel(final int channel) {
        load.put(channel, 0);
    }

    public static final void removeChannel(final int channel) {
        load.remove(channel);
    }

    public static final void run_startup_configurations() {
        userLimit = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.login.userlimit"));
        serverName = ServerProperties.getProperty("net.sf.odinms.login.serverName");
        eventMessage = ServerProperties.getProperty("net.sf.odinms.login.eventMessage");
        flag = Byte.parseByte(ServerProperties.getProperty("net.sf.odinms.login.flag"));
        adminOnly = Boolean.parseBoolean(ServerProperties.getProperty("net.sf.odinms.world.admin", "false"));
        maxCharacters = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.login.maxCharacters"));
        port = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.login.port"));

        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());

        acceptor = new SocketAcceptor();
        final SocketAcceptorConfig cfg = new SocketAcceptorConfig();
        cfg.getSessionConfig().setTcpNoDelay(true);
        cfg.setDisconnectOnUnbind(true);
        cfg.getFilterChain().addLast("codec", new ProtocolCodecFilter(new MapleCodecFactory()));
        try {
            InetSocketadd = new InetSocketAddress(port);
            acceptor.bind(InetSocketadd, new MapleServerHandler(-1, false), cfg);
            System.out.println("【登入伺服器】- 監聽端口: " + port);
        } catch (IOException e) {
            System.err.println("Binding to port " + port + " failed" + e);
        }
    }

    public static final void shutdown() {
        if (finishedShutdown) {
            return;
        }
        System.out.println("【登入伺服器】 關閉中...");
        acceptor.unbindAll();
        System.out.println("【登入伺服器】 關閉完畢...");
        finishedShutdown = true; //nothing. lol
    }

    public static final String getServerName() {
        return serverName;
    }

    public static final String getTrueServerName() {
        return serverName;
    }

    public static final String getEventMessage() {
        return eventMessage;
    }

    public static final byte getFlag() {
        return flag;
    }

    public static final int getMaxCharacters() {
        return maxCharacters;
    }

    public static final Map<Integer, Integer> getLoad() {
        return load;
    }

    public static void setLoad(final Map<Integer, Integer> load_, final int usersOn_) {
        load = load_;
        usersOn = usersOn_;
    }

    public static final void setEventMessage(final String newMessage) {
        eventMessage = newMessage;
    }

    public static final void setFlag(final byte newflag) {
        flag = newflag;
    }

    public static final int getUserLimit() {
        return userLimit;
    }

    public static final int getUsersOn() {
        return usersOn;
    }

    public static final void setUserLimit(final int newLimit) {
        userLimit = newLimit;
    }

    public static final int getNumberOfSessions() {
        return acceptor.getManagedSessions(InetSocketadd).size();
    }

    public static final void setAdminOnly(boolean enable) {
        adminOnly = enable;
    }

    public static final boolean isAdminOnly() {
        return adminOnly;
    }

    public static final boolean isShutdown() {
        return finishedShutdown;
    }

    public static final void setOn() {
        finishedShutdown = false;
    }

    public static boolean containClient(MapleClient client) {
        Collection<MapleClient> cls = getClientStorage().getAllClientsThreadSafe();
        for (MapleClient c : cls) {
            if (c == null) {
                continue;
            }
            if (client == c) {
                return true;
            }
        }
        return false;
    }

    public static void forceRemoveClient(MapleClient client, boolean remove) {
        Collection<MapleClient> cls = getClientStorage().getAllClientsThreadSafe();
        for (MapleClient c : cls) {
            if (c == null) {
                continue;
            }
            if (c.getAccID() == client.getAccID() || c == client) {
                if (c != client) {
                    c.unLockDisconnect();
                }
                if (remove) {
                    removeClient(c);
                }
            }
        }
    }

    public static void forceRemoveClient(MapleClient client) {
        forceRemoveClient(client, true);
    }

    public static AccountStorage getClientStorage() {
        if (clients == null) {
            clients = new AccountStorage();
        }
        return clients;
    }

    public static final void addClient(final MapleClient c) {
        getClientStorage().registerAccount(c);
    }

    public static final void removeClient(final MapleClient c) {
        getClientStorage().deregisterAccount(c);
    }
}
