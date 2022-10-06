package handling.cashshop;

import java.net.InetSocketAddress;

import handling.MapleServerHandler;
import handling.channel.PlayerStorage;
import handling.mina.MapleCodecFactory;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.common.IoAcceptor;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import server.MTSStorage;
import server.ServerProperties;

public class CashShopServer {

    private static String ip;
    private static InetSocketAddress InetSocketadd;
    private static int port = 8600;
    private static IoAcceptor acceptor;
    private static PlayerStorage players, playersMTS;
    private static boolean finishedShutdown = false;

    public static final void run_startup_configurations() {
        port = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.cashshop.port"));
        ip = ServerProperties.getProperty("net.sf.odinms.world.host") + ":" + port;

        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());

        acceptor = new SocketAcceptor();
        final SocketAcceptorConfig cfg = new SocketAcceptorConfig();
        cfg.getSessionConfig().setTcpNoDelay(true);
        cfg.setDisconnectOnUnbind(true);
        cfg.getFilterChain().addLast("codec", new ProtocolCodecFilter(new MapleCodecFactory()));
        players = new PlayerStorage(-10);
        playersMTS = new PlayerStorage(-20);
        try {
            InetSocketadd = new InetSocketAddress(port);
            acceptor.bind(InetSocketadd, new MapleServerHandler(-1, true), cfg);
            System.out.println("【購物商城】  - 監聽端口: " + port);
        } catch (final Exception e) {
            System.err.println("綁定端口 " + port + " 失敗");
            e.printStackTrace();
            throw new RuntimeException("Binding failed.", e);
        }
    }

    public static final String getIP() {
        return ip;
    }

    public static final PlayerStorage getPlayerStorage() {
        return players;
    }

    public static final PlayerStorage getPlayerStorageMTS() {
        return playersMTS;
    }

    public static final void shutdown() {
        if (finishedShutdown) {
            return;
        }
        System.out.println("[購物商城] 準備關閉...");
        System.out.println("[購物商城] 儲存資料中...");
        players.disconnectAll();
        playersMTS.disconnectAll();
        MTSStorage.getInstance().saveBuyNow(true);
        System.out.println("[購物商城] 解除綁定端口...");
        acceptor.unbindAll();
        finishedShutdown = true;
        System.out.println("[購物商城] 關閉完成...");
    }

    public static boolean isShutdown() {
        return finishedShutdown;
    }
}
