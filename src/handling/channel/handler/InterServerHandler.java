package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;

import client.SkillFactory;
import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.login.LoginServer;
import handling.world.CharacterIdChannelPair;
import handling.world.CharacterTransfer;
import handling.world.MapleMessenger;
import handling.world.MapleMessengerCharacter;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.PlayerBuffStorage;
import handling.world.World;
import handling.world.guild.MapleGuild;
import server.maps.FieldLimitType;
import server.maps.MapleMap;
import server.maps.SavedLocationType;
import tools.FileoutputUtil;
import tools.packet.CField;
import tools.Pair;
import tools.data.LittleEndianAccessor;
import tools.packet.CWvsContext;
import tools.packet.CWvsContext.BuddylistPacket;
import tools.packet.CWvsContext.GuildPacket;
import tools.packet.MTSCSPacket;

public class InterServerHandler {

    public static final void EnterCS(final MapleClient c, final MapleCharacter chr, final boolean mts) {
        if (chr.hasBlockedInventory() || chr.getMap() == null || chr.getEventInstance() != null || c.getChannelServer() == null) {
            c.sendPacket(CField.serverBlocked(2));
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (mts && chr.getLevel() < 50) {
            chr.dropMessage(1, "你的等級尚未到達50級所以無法進入拍賣。");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (World.getPendingCharacterSize() >= 10) {
            chr.dropMessage(1, "目前伺服器忙碌中，請稍後再嘗試。");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        //if (c.getChannel() == 1 && !c.getPlayer().isGM()) {
        //    c.getPlayer().dropMessage(5, "You may not enter on this channel. Please change channels and try again.");
        //    c.sendPacket(CWvsContext.enableActions());
        //    return;
        //}
        final ChannelServer ch = ChannelServer.getInstance(c.getChannel());

        chr.changeRemoval();

        if (chr.getMessenger() != null) {
            MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(chr);
            World.Messenger.leaveMessenger(chr.getMessenger().getId(), messengerplayer);
        }
        PlayerBuffStorage.addBuffsToStorage(chr.getId(), chr.getAllBuffs());
        PlayerBuffStorage.addCooldownsToStorage(chr.getId(), chr.getCooldowns());
        PlayerBuffStorage.addDiseaseToStorage(chr.getId(), chr.getAllDiseases());
        World.ChannelChange_Data(new CharacterTransfer(chr), chr.getId(), mts ? -20 : -10);
        ch.removePlayer(chr);
        c.updateLoginState(MapleClient.CHANGE_CHANNEL, c.getSessionIPAddress());
        chr.saveToDB(false, false);
        chr.getMap().removePlayer(chr);
        c.sendPacket(CField.getChannelChange(c, Integer.parseInt(CashShopServer.getIP().split(":")[1])));
        c.setPlayer(null);
        c.setReceiving(false);
    }

    public static final void EnterMTS(final MapleClient c, final MapleCharacter chr) {
        if (chr.hasBlockedInventory() || chr.getMap() == null || chr.getEventInstance() != null || c.getChannelServer() == null) {
            chr.dropMessage(1, "Please try again later.");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (chr.getLevel() < 15) {
            chr.dropMessage(1, "You may not enter the Free Market until level 15.");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (chr.getMapId() >= 910000000 && chr.getMapId() <= 910000022) {
            chr.dropMessage(1, "You are already in the Free Market.");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        chr.dropMessage(5, "You will be transported to the Free Market Entrance.");
        chr.saveLocation(SavedLocationType.fromString("FREE_MARKET"));
        final MapleMap warpz = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(910000000);
        if (warpz != null) {
            chr.changeMap(warpz, warpz.getPortal("st00"));
        } else {
            chr.dropMessage(5, "Please try again later.");
        }
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void Loggedin(final int playerid, final MapleClient c) {
        final ChannelServer channelServer = c.getChannelServer();
        MapleCharacter player;
        final CharacterTransfer transfer = channelServer.getPlayerStorage().getPendingCharacter(playerid);

        if (transfer == null) { // Player isn't in storage, probably isn't CC
            player = MapleCharacter.loadCharFromDB(playerid, c, true);
            Pair<String, String> ip = LoginServer.getLoginAuth(playerid);
            String s = c.getSessionIPAddress();
            if (ip == null || !s.substring(s.indexOf('/') + 1, s.length()).equals(ip.left)) {
                if (ip != null) {
                    LoginServer.putLoginAuth(playerid, ip.left, ip.right);
                }
                c.getSession().close();
                return;
            }
            c.setTempIP(ip.right);
        } else {
            player = MapleCharacter.ReconstructChr(transfer, c, true);
        }
        //設置用戶端角色
        c.setPlayer(player);
        //設置用戶端賬號ID
        c.setAccID(player.getAccountID());
        c.loadAccountData(player.getAccountID());
        c.loadVip(player.getAccountID());

        //c.loadAccountMac(player.getAccountID());
        if (!c.CheckIPAddress()) { // Remote hack
            c.setPlayer(null);
            c.getSession().close();
            return;
        }
        LoginServer.forceRemoveClient(c, false);
        ChannelServer.forceRemovePlayerByAccId(c, c.getAccID());
        
        final int state = c.getLoginState();
        boolean allowLogin = false;

        if (state == MapleClient.LOGIN_SERVER_TRANSITION || state == MapleClient.CHANGE_CHANNEL) {
            allowLogin = !World.isCharacterListConnected(c.loadCharacterNames(c.getWorld()));
        }
        if (!allowLogin) {
            c.setPlayer(null);
            c.getSession().close();
            return;
        }

        c.updateLoginState(MapleClient.LOGIN_LOGGEDIN, c.getSessionIPAddress());
        channelServer.addPlayer(player);

        player.giveCoolDowns(PlayerBuffStorage.getCooldownsFromStorage(player.getId()));
        player.silentGiveBuffs(PlayerBuffStorage.getBuffsFromStorage(player.getId()));
        player.giveSilentDebuff(PlayerBuffStorage.getDiseaseFromStorage(player.getId()));
        player.getStat().recalcLocalStats(true, player);

        c.sendPacket(CField.getCharInfo(player));
        c.sendPacket(MTSCSPacket.enableCSUse());

        if (player.isGM()) {
            SkillFactory.getSkill(9001004).getEffect(1).applyTo(player);
        }
//        c.sendPacket(CWvsContext.temporaryStats_Reset()); // .
        player.getMap().addPlayer(player);

        try {
            // Start of buddylist
            final int buddyIds[] = player.getBuddylist().getBuddyIds();
            World.Buddy.loggedOn(player.getName(), player.getId(), c.getChannel(), buddyIds);
            if (player.getParty() != null) {
                final MapleParty party = player.getParty();
                World.Party.updateParty(party.getId(), PartyOperation.LOG_ONOFF, new MaplePartyCharacter(player));
            }
            final CharacterIdChannelPair[] onlineBuddies = World.Find.multiBuddyFind(player.getId(), buddyIds);
            for (CharacterIdChannelPair onlineBuddy : onlineBuddies) {
                player.getBuddylist().get(onlineBuddy.getCharacterId()).setChannel(onlineBuddy.getChannel());
            }
            c.sendPacket(BuddylistPacket.updateBuddylist(player.getBuddylist().getBuddies()));

            // Start of Messenger
            final MapleMessenger messenger = player.getMessenger();
            if (messenger != null) {
                World.Messenger.silentJoinMessenger(messenger.getId(), new MapleMessengerCharacter(c.getPlayer()));
                World.Messenger.updateMessenger(messenger.getId(), c.getPlayer().getName(), c.getChannel());
            }

            // Start of Guild and alliance
            if (player.getGuildId() > 0) {
                // GM上線公會不顯是
                if (!player.isGM()) {
                    World.Guild.setGuildMemberOnline(player.getMGC(), true, c.getChannel());
                }
                c.sendPacket(GuildPacket.showGuildInfo(player));
                final MapleGuild gs = World.Guild.getGuild(player.getGuildId());
                if (gs != null) {
                } else { //guild not found, change guild id
                    player.setGuildId(0);
                    player.setGuildRank((byte) 5);
                    player.setAllianceRank((byte) 5);
                    player.saveGuildStatus();
                }
            }
        } catch (Exception e) {
            FileoutputUtil.outputFileError(FileoutputUtil.Login_Error, e);
        }
        player.getClient().sendPacket(CWvsContext.serverMessage(channelServer.getServerMessage()));
        player.sendMacros();
        player.showNote();
        player.updatePartyMemberHP();
//        player.startFairySchedule(false);
        player.baseSkills(); //fix people who've lost skills.

        c.sendPacket(CField.getKeymap(player.getKeyLayout()));
        player.updatePetAuto();
        player.expirationTask(true, transfer == null);
        if (player.getJob() == 132) { // DARKKNIGHT
            player.checkBerserk();
        }
        player.spawnClones();
        player.spawnSavedPets();
        if (player.getStat().equippedSummon > 0) {
            SkillFactory.getSkill(player.getStat().equippedSummon).getEffect(1).applyTo(player);
        }
//        MapleQuestStatus stat = player.getQuestNoAdd(MapleQuest.getInstance(GameConstants.PENDANT_SLOT));
//        c.sendPacket(CWvsContext.pendantSlot(stat != null && stat.getCustomData() != null && Long.parseLong(stat.getCustomData()) > System.currentTimeMillis()));
//        stat = player.getQuestNoAdd(MapleQuest.getInstance(GameConstants.QUICK_SLOT));
//        c.sendPacket(CField.quickSlot(stat != null && stat.getCustomData() != null ? stat.getCustomData() : null));
//        c.sendPacket(CWvsContext.getFamiliarInfo(player));
    }

    public static final void ChangeChannel(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.hasBlockedInventory() || chr.getEventInstance() != null || chr.getMap() == null || chr.isInBlockedMap() || FieldLimitType.ChannelSwitch.check(chr.getMap().getFieldLimit())) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (World.getPendingCharacterSize() >= 10) {
            chr.dropMessage(1, "這個頻道正在忙碌中請稍後再嘗試。");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final int chc = slea.readByte() + 1;
        chr.updateTick(slea.readInt());
        if (!World.isChannelAvailable(chc)) {
            chr.dropMessage(1, "這個頻道已經滿了請稍後再嘗試。");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        chr.changeChannel(chc);
        // 換頻道更新VIP等級
        c.loadVip(chr.getAccountID());
    }
}
