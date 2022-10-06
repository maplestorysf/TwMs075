package handling.channel;

import client.MapleCharacter;
import client.MapleClient;
import database.DatabaseConnection;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import handling.MapleServerHandler;
import handling.cashshop.CashShopServer;
import handling.login.LoginServer;
import handling.mina.MapleCodecFactory;
import handling.world.CheaterData;
import handling.world.World;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import scripting.EventScriptManager;
import server.MapleSquad;
import server.MapleSquad.MapleSquadType;
import server.maps.MapleMapFactory;
import server.shops.HiredMerchant;
import server.life.PlayerNPC;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.SimpleByteBufferAllocator;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.SocketAcceptorConfig;
import org.apache.mina.transport.socket.nio.SocketAcceptor;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import server.ServerProperties;
import server.events.*;
import server.maps.AramiaFireWorks;
import server.maps.MapleMapObject;
import server.shops.MaplePlayerShop;
import tools.ConcurrentEnumMap;
import tools.FileoutputUtil;
import tools.packet.CWvsContext;

public class ChannelServer {

    public static long serverStartTime;
    private int expRate, mesoRate, dropRate, cashRate = 3, traitRate = 1;
    private short port = 8585;
    private static final short DEFAULT_PORT = 8585;
    private int channel, running_MerchantID = 0, flags = 0, running_PlayerShopID = 0;
    private String serverMessage, ip, serverName;
    private boolean shutdown = false, finishedShutdown = false, MegaphoneMuteState = false, adminOnly = false;
    private PlayerStorage players;
    private IoAcceptor acceptor;
    private final MapleMapFactory mapFactory;
    private EventScriptManager eventSM;
    private AramiaFireWorks works = new AramiaFireWorks();
    private static final Map<Integer, ChannelServer> instances = new HashMap<>();
    private final Map<MapleSquadType, MapleSquad> mapleSquads = new ConcurrentEnumMap<>(MapleSquadType.class);
    private final Map<Integer, HiredMerchant> merchants = new HashMap<>();
    private final Map<Integer, MaplePlayerShop> playershops = new HashMap<>();
    private final List<PlayerNPC> playerNPCs = new LinkedList<>();
    private final ReentrantReadWriteLock merchLock = new ReentrantReadWriteLock(); //merchant
    private final ReentrantReadWriteLock playerLock = new ReentrantReadWriteLock(); //player

    private int eventmap = -1;
    private final Map<MapleEventType, MapleEvent> events = new EnumMap<>(MapleEventType.class);

    private ChannelServer(final int channel) {
        this.channel = channel;
        mapFactory = new MapleMapFactory(channel);
    }

    public static Set<Integer> getAllInstance() {
        return new HashSet<>(instances.keySet());
    }

    public final void loadEvents() {
        if (events.size() != 0) {
            return;
        }
        events.put(MapleEventType.採集的樂趣, new MapleCoconut(channel, MapleEventType.採集的樂趣));
        events.put(MapleEventType.農夫的樂趣, new MapleCoconut(channel, MapleEventType.農夫的樂趣));
        events.put(MapleEventType.障礙競走, new MapleFitness(channel, MapleEventType.障礙競走));
        events.put(MapleEventType.向上攀升, new MapleOla(channel, MapleEventType.向上攀升));
        events.put(MapleEventType.選邊站, new MapleOxQuiz(channel, MapleEventType.選邊站));
        events.put(MapleEventType.滾雪球, new MapleSnowball(channel, MapleEventType.滾雪球));
        events.put(MapleEventType.生存遊戲, new MapleSurvival(channel, MapleEventType.生存遊戲));
        events.put(MapleEventType.黃金傳說, new MapleJewel(channel, MapleEventType.黃金傳說));
    }

    public final void run_startup_configurations() {
        setChannel(channel); //instances.put
        try {
            expRate = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.world.exp"));
            mesoRate = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.world.meso"));
            dropRate = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.world.drop"));
            serverMessage = ServerProperties.getProperty("net.sf.odinms.world.serverMessage");
            serverName = ServerProperties.getProperty("net.sf.odinms.login.serverName");
            flags = Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.world.flags", "0"));
            adminOnly = Boolean.parseBoolean(ServerProperties.getProperty("net.sf.odinms.world.admin", "false"));
            eventSM = new EventScriptManager(this, ServerProperties.getProperty("net.sf.odinms.channel.events").split(","));
            port = (short) ((ServerProperties.getProperty("net.sf.odinms.channel.net.port", DEFAULT_PORT) + channel) - 1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ip = ServerProperties.getProperty("net.sf.odinms.channel.net.interface") + ":" + port;

        ByteBuffer.setUseDirectBuffers(false);
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());

        acceptor = new SocketAcceptor();
        final SocketAcceptorConfig acceptor_config = new SocketAcceptorConfig();
        acceptor_config.getSessionConfig().setTcpNoDelay(true);
        acceptor_config.setDisconnectOnUnbind(true);
        acceptor_config.getFilterChain().addLast("codec", new ProtocolCodecFilter(new MapleCodecFactory()));
        players = new PlayerStorage(channel);
        loadEvents();

        try {
            acceptor.bind(new InetSocketAddress(port), new MapleServerHandler(channel, false), acceptor_config);
            System.out.println("【頻道 " + channel + "】    - 監聽端口: " + port + "");
            eventSM.init();
        } catch (IOException e) {
            System.out.println("綁定端口: " + port + " 失敗 (頻道: " + getChannel() + ")" + e);
        }
    }

    public final void shutdown() {
        if (finishedShutdown) {
            return;
        }
        broadcastPacket(CWvsContext.serverNotice(0, "【頻道" + getChannel() + "】 正在關閉中."));

        // dc all clients by hand so we get sessionClosed...
        shutdown = true;

        System.out.println("【頻道" + getChannel() + "】 儲存角色資料...");

        //getPlayerStorage().disconnectAll();
        System.out.println("【頻道" + getChannel() + "】 解除端口綁定中...");

        try {
            if (acceptor != null) {
                acceptor.unbindAll();
                acceptor = null;
                System.out.println("【頻道" + getChannel() + "】 解除端口成功");
            }
        } catch (Exception e) {
            System.out.println("【頻道" + getChannel() + "】 解除端口失敗");
        }

        //temporary while we dont have !addchannel
        instances.remove(channel);
        setFinishShutdown();
    }

    public final void unbind() {
        acceptor.unbindAll();
    }

    public final boolean hasFinishedShutdown() {
        return finishedShutdown;
    }

    public final MapleMapFactory getMapFactory() {
        return mapFactory;
    }

    public static final ChannelServer newInstance(final int channel) {
        return new ChannelServer(channel);
    }

    public static final ChannelServer getInstance(final int channel) {
        return instances.get(channel);
    }

    public static Set<Integer> getAllChannels() {
        return new HashSet<>(instances.keySet());
    }

    public final void addPlayer(final MapleCharacter chr) {
        getPlayerStorage().registerPlayer(chr);
    }

    public final PlayerStorage getPlayerStorage() {
        if (players == null) { //wth
            players = new PlayerStorage(channel); //wthhhh
        }
        return players;
    }

    public final void removePlayer(final MapleCharacter chr) {
        getPlayerStorage().deregisterPlayer(chr);

    }

    public final void removePlayer(final int idz, final String namez) {
        getPlayerStorage().deregisterPlayer(idz, namez);

    }

    public final String getServerMessage() {
        return serverMessage;
    }

    public final void setServerMessage(final String newMessage) {
        serverMessage = newMessage;
        broadcastPacket(CWvsContext.serverMessage(serverMessage));
    }

    public final void broadcastPacket(final byte[] data) {
        getPlayerStorage().broadcastPacket(data);
    }

    public final void broadcastSmegaPacket(final byte[] data) {
        getPlayerStorage().broadcastSmegaPacket(data);
    }

    public final void broadcastGMPacket(final byte[] data) {
        getPlayerStorage().broadcastGMPacket(data);
    }

    public final void broadcastGMPacket(final byte[] data, int type) {
        playerLock.readLock().lock();
        try {
            for (MapleCharacter chr : players.getAllCharacters()) {
                if (chr.isStaff() && chr.getShowMessage(type)) {
                    chr.getClient().sendPacket(data);
                }
            }
        } finally {
            playerLock.readLock().unlock();
        }
    }

    public final int getExpRate() {
        return expRate;
    }

    public final void setExpRate(final int expRate) {
        this.expRate = expRate;
    }

    public final int getCashRate() {
        return cashRate;
    }

    public final int getChannel() {
        return channel;
    }

    public final void setChannel(final int channel) {
        instances.put(channel, this);
        LoginServer.addChannel(channel);
    }

    public static final ArrayList<ChannelServer> getAllInstances() {
        return new ArrayList<>(instances.values());
    }

    public final String getIP() {
        return ip;
    }

    public final boolean isShutdown() {
        return shutdown;
    }

    public final int getLoadedMaps() {
        return mapFactory.getLoadedMaps();
    }

    public final EventScriptManager getEventSM() {
        return eventSM;
    }

    public final void reloadEvents() {
        eventSM.cancel();
        eventSM = new EventScriptManager(this, ServerProperties.getProperty("net.sf.odinms.channel.events").split(","));
        eventSM.init();
    }

    public final int getMesoRate() {
        return mesoRate;
    }

    public final void setMesoRate(final int mesoRate) {
        this.mesoRate = mesoRate;
    }

    public final int getDropRate() {
        return dropRate;
    }

    public final void setDropRate(final int dropRate) {
        this.dropRate = dropRate;
    }

    public static final void startChannel_Main() {
        serverStartTime = System.currentTimeMillis();

        for (int i = 0; i < Integer.parseInt(ServerProperties.getProperty("net.sf.odinms.channel.count", "0")); i++) {
            newInstance(i + 1).run_startup_configurations();
        }
    }

    public Map<MapleSquadType, MapleSquad> getAllSquads() {
        return Collections.unmodifiableMap(mapleSquads);
    }

    public final MapleSquad getMapleSquad(final String type) {
        return getMapleSquad(MapleSquadType.valueOf(type.toLowerCase()));
    }

    public final MapleSquad getMapleSquad(final MapleSquadType type) {
        return mapleSquads.get(type);
    }

    public final boolean addMapleSquad(final MapleSquad squad, final String type) {
        final MapleSquadType types = MapleSquadType.valueOf(type.toLowerCase());
        if (types != null && !mapleSquads.containsKey(types)) {
            mapleSquads.put(types, squad);
            squad.scheduleRemoval();
            return true;
        }
        return false;
    }

    public final boolean removeMapleSquad(final MapleSquadType types) {
        if (types != null && mapleSquads.containsKey(types)) {
            mapleSquads.remove(types);
            return true;
        }
        return false;
    }

    public final int closeAllPlayerShop() {
        int ret = 0;
        merchLock.writeLock().lock();
        try {
            final Iterator<Map.Entry<Integer, MaplePlayerShop>> playershops_ = playershops.entrySet().iterator();
            while (playershops_.hasNext()) {
                MaplePlayerShop hm = playershops_.next().getValue();
                hm.closeShop(true, false);
                hm.getMap().removeMapObject(hm);
                playershops_.remove();
                ret++;
            }
        } finally {
            merchLock.writeLock().unlock();
        }
        return ret;
    }

    public final int closeAllMerchant() {
        int ret = 0;
        merchLock.writeLock().lock();
        try {
            final Iterator<Entry<Integer, HiredMerchant>> merchants_ = merchants.entrySet().iterator();
            while (merchants_.hasNext()) {
                HiredMerchant hm = merchants_.next().getValue();
                hm.closeShop(true, false);
                //HiredMerchantSave.QueueShopForSave(hm);
                hm.getMap().removeMapObject(hm);
                merchants_.remove();
                ret++;
            }
        } finally {
            merchLock.writeLock().unlock();
        }
        //hacky
//        for (int i = 910000001; i <= 910000022; i++) {
//            for (MapleMapObject mmo : mapFactory.getMap(i).getAllHiredMerchantsThreadsafe()) {
//                ((HiredMerchant) mmo).closeShop(true, false);
//                //HiredMerchantSave.QueueShopForSave((HiredMerchant) mmo);
//                ret++;
//            }
//        }
        return ret;
    }

    public final int addPlayerShop(final MaplePlayerShop PlayerShop) {
        merchLock.writeLock().lock();

        int runningmer = 0;
        try {
            runningmer = running_PlayerShopID;
            playershops.put(running_PlayerShopID, PlayerShop);
            running_PlayerShopID++;
        } finally {
            merchLock.writeLock().unlock();
        }
        return runningmer;
    }

    public final int addMerchant(final HiredMerchant hMerchant) {
        merchLock.writeLock().lock();
        try {
            running_MerchantID++;
            merchants.put(running_MerchantID, hMerchant);
            return running_MerchantID;
        } finally {
            merchLock.writeLock().unlock();
        }
    }

    public final void removeMerchant(final HiredMerchant hMerchant) {
        merchLock.writeLock().lock();

        try {
            merchants.remove(hMerchant.getStoreId());
        } finally {
            merchLock.writeLock().unlock();
        }
    }

    public final boolean containsMerchant(final int accid, int cid) {
        boolean contains = false;

        merchLock.readLock().lock();
        try {
            final Iterator itr = merchants.values().iterator();

            while (itr.hasNext()) {
                HiredMerchant hm = (HiredMerchant) itr.next();
                if (hm.getOwnerAccId() == accid || hm.getOwnerId() == cid) {
                    contains = true;
                    break;
                }
            }
        } finally {
            merchLock.readLock().unlock();
        }
        return contains;
    }

    public final List<HiredMerchant> searchMerchant(final int itemSearch) {
        final List<HiredMerchant> list = new LinkedList<>();
        merchLock.readLock().lock();
        try {
            final Iterator itr = merchants.values().iterator();

            while (itr.hasNext()) {
                HiredMerchant hm = (HiredMerchant) itr.next();
                if (hm.searchItem(itemSearch).size() > 0) {
                    list.add(hm);
                }
            }
        } finally {
            merchLock.readLock().unlock();
        }
        return list;
    }

    public final void toggleMegaphoneMuteState() {
        this.MegaphoneMuteState = !this.MegaphoneMuteState;
    }

    public final boolean getMegaphoneMuteState() {
        return MegaphoneMuteState;
    }

    public int getEvent() {
        return eventmap;
    }

    public final void setEvent(final int ze) {
        this.eventmap = ze;
    }

    public MapleEvent getEvent(final MapleEventType t) {
        return events.get(t);
    }

    public final Collection<PlayerNPC> getAllPlayerNPC() {
        return playerNPCs;
    }

    public final void addPlayerNPC(final PlayerNPC npc) {
        if (playerNPCs.contains(npc)) {
            return;
        }
        playerNPCs.add(npc);
        getMapFactory().getMap(npc.getMapId()).addMapObject(npc);
    }

    public final void removePlayerNPC(final PlayerNPC npc) {
        if (playerNPCs.contains(npc)) {
            playerNPCs.remove(npc);
            getMapFactory().getMap(npc.getMapId()).removeMapObject(npc);
        }
    }

    public final String getServerName() {
        return serverName;
    }

    public final void setServerName(final String sn) {
        this.serverName = sn;
    }

    public final String getTrueServerName() {
        return serverName;
    }

    public final int getPort() {
        return port;
    }

    public static final Set<Integer> getChannelServer() {
        return new HashSet<>(instances.keySet());
    }

    public final void setPrepareShutdown() {
        this.shutdown = true;
        System.out.println("【頻道" + getChannel() + "】 準備關閉.");
    }

    public final void setFinishShutdown() {
        this.finishedShutdown = true;
        System.out.println("【頻道" + getChannel() + "】 已經關閉完成.");
    }

    public final boolean isAdminOnly() {
        return adminOnly;
    }

    public final static int getChannelCount() {
        return instances.size();
    }

    public final int getTempFlag() {
        return flags;
    }

    public static Map<Integer, Integer> getChannelLoad() {
        Map<Integer, Integer> ret = new HashMap<>();
        for (ChannelServer cs : instances.values()) {
            ret.put(cs.getChannel(), cs.getConnectedClients());
        }
        return ret;
    }

    public int getConnectedClients() {
        return getPlayerStorage().getConnectedClients();
    }

    public List<CheaterData> getCheaters() {
        List<CheaterData> cheaters = getPlayerStorage().getCheaters();

        Collections.sort(cheaters);
        return cheaters;
    }

    public List<CheaterData> getReports() {
        List<CheaterData> cheaters = getPlayerStorage().getReports();

        Collections.sort(cheaters);
        return cheaters;
    }

    public void broadcastMessage(byte[] message) {
        broadcastPacket(message);
    }

    public void broadcastSmega(byte[] message) {
        broadcastSmegaPacket(message);
    }

    public void broadcastGMMessage(byte[] message) {
        broadcastGMPacket(message);
    }

    public void broadcastGMMessage(byte[] message, int type) {
        broadcastGMPacket((message), type);
    }

    public AramiaFireWorks getFireWorks() {
        return works;
    }

    public int getTraitRate() {
        return traitRate;
    }

    public void saveAll() {
        int ppl = 0;
        List<MapleCharacter> all = this.players.getAllCharactersThreadSafe();
        for (MapleCharacter chr : all) {
            try {
                chr.saveToDB(false, false);
            } catch (Exception e) {
                System.out.println("[自動存檔] 角色:" + chr.getName() + " 儲存失敗.");
            }
        }
    }

    public static void forceRemovePlayerByAccId(MapleClient client, int accid) {
        for (ChannelServer ch : ChannelServer.getAllInstances()) {
            Collection<MapleCharacter> chrs = ch.getPlayerStorage().getAllCharactersThreadSafe();
            for (MapleCharacter c : chrs) {
                if (c.getAccountID() == accid) {
                    try {
                        if (c.getClient() != null) {
                            if (c.getClient() != client) {
                                c.getClient().unLockDisconnect();
                            }
                        }
                    } catch (Exception ex) {
                    }
                    chrs = ch.getPlayerStorage().getAllCharactersThreadSafe();
                    if (chrs.contains(c)) {
                        ch.removePlayer(c);
                    }
                }
            }
        }
        try {
            Collection<MapleCharacter> chrs = CashShopServer.getPlayerStorage().getAllCharactersThreadSafe();
            for (MapleCharacter c : chrs) {
                if (c.getAccountID() == accid) {
                    try {
                        if (c.getClient() != null) {
                            if (c.getClient() != client) {
                                c.getClient().unLockDisconnect();
                            }
                        }
                    } catch (Exception ex) {
                    }
                }
            }
        } catch (Exception ex) {

        }
    }

    public static void forceRemovePlayerByCharName(MapleClient client, String Name) {
        for (ChannelServer ch : ChannelServer.getAllInstances()) {
            Collection<MapleCharacter> chrs = ch.getPlayerStorage().getAllCharactersThreadSafe();
            for (MapleCharacter c : chrs) {
                if (c.getName().equalsIgnoreCase(Name)) {
                    try {
                        if (c.getClient() != null) {
                            if (c.getClient() != client) {
                                c.getClient().unLockDisconnect();
                            }
                        }
                    } catch (Exception ex) {
                    }
                    chrs = ch.getPlayerStorage().getAllCharactersThreadSafe();
                    if (chrs.contains(c)) {
                        ch.removePlayer(c);
                    }
                    c.getMap().removePlayer(c);
                }
            }
        }
        Collection<MapleCharacter> chrs = CashShopServer.getPlayerStorage().getAllCharactersThreadSafe();
        for (MapleCharacter c : chrs) {
            if (c.getName().equalsIgnoreCase(Name)) {
                try {
                    if (c.getClient() != null) {
                        if (c.getClient() != client) {
                            c.getClient().unLockDisconnect();
                        }
                    }
                } catch (Exception ex) {
                }
                chrs = CashShopServer.getPlayerStorage().getAllCharactersThreadSafe();
                if (chrs.contains(c)) {
                    CashShopServer.getPlayerStorage().deregisterPlayer(c);
                }
            }
        }
    }

    public static void forceRemovePlayerByCharId(MapleClient client, int charId) {
        for (ChannelServer ch : ChannelServer.getAllInstances()) {
            Collection<MapleCharacter> chrs = ch.getPlayerStorage().getAllCharactersThreadSafe();
            for (MapleCharacter c : chrs) {
                if (c.getId() == charId) {
                    try {
                        if (c.getClient() != null) {
                            if (c.getClient() != client) {
                                c.getClient().unLockDisconnect();
                            }
                        }
                    } catch (Exception ex) {
                    }
                    chrs = ch.getPlayerStorage().getAllCharactersThreadSafe();
                    if (chrs.contains(c)) {
                        ch.removePlayer(c);
                    }
                }
            }
        }
    }

    public static List<String> loadCharacterNamesByAccId(int accId) {
        List<String> Acc = new LinkedList<>();
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT name FROM characters WHERE accountid = ?");
            ps.setInt(1, accId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Acc.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("error loading characters names by id " + e);
        }
        return Acc;
    }

    public static void forceRemovePlayerByCharNameFromDataBase(MapleClient client, List<String> Name) {

        for (ChannelServer ch : ChannelServer.getAllInstances()) {
            for (final String name : Name) {
                if (ch.getPlayerStorage().getCharacterByName(name) != null) {
                    MapleCharacter c = ch.getPlayerStorage().getCharacterByName(name);
                    try {
                        if (c.getClient() != null) {
                            if (c.getClient() != client) {
                                c.getClient().unLockDisconnect();
                            }
                        }
                    } catch (Exception ex) {
                    }
                    if (ch.getPlayerStorage().getAllCharactersThreadSafe().contains(c)) {
                        ch.removePlayer(c);
                    }
                    c.getMap().removePlayer(c);
                }
            }
        }

        for (final String name : Name) {
            if (CashShopServer.getPlayerStorage().getCharacterByName(name) != null) {
                MapleCharacter c = CashShopServer.getPlayerStorage().getCharacterByName(name);
                try {
                    if (c.getClient() != null) {
                        if (c.getClient() != client) {
                            c.getClient().unLockDisconnect();
                        }
                    }
                } catch (Exception ex) {
                }
            }

        }
    }

}
