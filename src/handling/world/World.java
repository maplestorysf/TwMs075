package handling.world;

import client.BuddyList;
import client.BuddyList.BuddyAddResult;
import client.BuddyList.BuddyOperation;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import client.BuddylistEntry;

import client.MapleBuffStat;
import client.MapleCharacter;

import client.MapleCoolDownValueHolder;
import client.MapleDiseaseValueHolder;
import client.inventory.MapleInventoryType;
import client.inventory.MaplePet;
import client.inventory.PetDataFactory;
import client.status.MonsterStatusEffect;
import database.DatabaseConnection;

import handling.cashshop.CashShopServer;
import handling.channel.ChannelServer;
import handling.channel.PlayerStorage;
import handling.login.LoginServer;
import handling.world.exped.ExpeditionType;
import handling.world.exped.MapleExpedition;
import handling.world.exped.PartySearch;
import handling.world.exped.PartySearchType;
import handling.world.guild.MapleBBSThread;
import handling.world.guild.MapleGuild;
import handling.world.guild.MapleGuildCharacter;
import java.util.EnumMap;
import server.Randomizer;
import server.Timer;
import server.Timer.WorldTimer;
import server.life.MapleMonster;
import server.maps.MapleMap;
import server.maps.MapleMapItem;
import tools.CollectionUtil;
import tools.FileoutputUtil;
import tools.packet.CField;
import tools.packet.CWvsContext;
import tools.packet.CWvsContext.BuddylistPacket;
import tools.packet.CWvsContext.GuildPacket;
import tools.packet.CWvsContext.PartyPacket;
import tools.packet.PetPacket;

public class World {

    //Touch everything...
    public static void init() {
        World.Find.findChannel(0);
        World.Messenger.getMessenger(0);
        World.Party.getParty(0);
    }

    public static String getStatus() {
        StringBuilder ret = new StringBuilder();
        int totalUsers = 0;
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            ret.append("Channel ");
            ret.append(cs.getChannel());
            ret.append(": ");
            int channelUsers = cs.getConnectedClients();
            totalUsers += channelUsers;
            ret.append(channelUsers);
            ret.append(" users\n");
        }
        ret.append("Total users online: ");
        ret.append(totalUsers);
        ret.append("\n");
        return ret.toString();
    }

    public static Map<Integer, Integer> getConnected() {
        Map<Integer, Integer> ret = new HashMap<>();
        int total = 0;
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            int curConnected = cs.getConnectedClients();
            ret.put(cs.getChannel(), curConnected);
            total += curConnected;
        }
        ret.put(0, total);
        return ret;
    }

    public static List<CheaterData> getCheaters() {
        List<CheaterData> allCheaters = new ArrayList<>();
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            allCheaters.addAll(cs.getCheaters());
        }
        Collections.sort(allCheaters);
        return CollectionUtil.copyFirst(allCheaters, 20);
    }

    public static List<CheaterData> getReports() {
        List<CheaterData> allCheaters = new ArrayList<>();
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            allCheaters.addAll(cs.getReports());
        }
        Collections.sort(allCheaters);
        return CollectionUtil.copyFirst(allCheaters, 20);
    }

    public static boolean isConnected(String charName) {
        return Find.findChannel(charName) > 0;
    }

    public static void toggleMegaphoneMuteState() {
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            cs.toggleMegaphoneMuteState();
        }
    }

    public static void ChannelChange_Data(CharacterTransfer Data, int characterid, int toChannel) {
        getStorage(toChannel).registerPendingPlayer(Data, characterid);
    }

    public static boolean isCharacterListConnected(List<String> charName) {
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            for (final String c : charName) {
                if (cs.getPlayerStorage().getCharacterByName(c) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasMerchant(int accountID, int characterID) {
        for (ChannelServer cs : ChannelServer.getAllInstances()) {
            if (cs.containsMerchant(accountID, characterID)) {
                return true;
            }
        }
        return false;
    }

    public static PlayerStorage getStorage(int channel) {
        if (channel == -20) {
            return CashShopServer.getPlayerStorageMTS();
        } else if (channel == -10) {
            return CashShopServer.getPlayerStorage();
        }
        return ChannelServer.getInstance(channel).getPlayerStorage();
    }

    public static int getPendingCharacterSize() {
        int ret = CashShopServer.getPlayerStorage().pendingCharacterSize() + CashShopServer.getPlayerStorageMTS().pendingCharacterSize();
        for (ChannelServer cserv : ChannelServer.getAllInstances()) {
            ret += cserv.getPlayerStorage().pendingCharacterSize();
        }
        return ret;
    }

    public static boolean isChannelAvailable(final int ch) {
        if (ChannelServer.getInstance(ch) == null || ChannelServer.getInstance(ch).getPlayerStorage() == null) {
            return false;
        }
        return ChannelServer.getInstance(ch).getPlayerStorage().getConnectedClients() < (ch == 1 ? 600 : 400);
    }

    public static class Party {

        private static Map<Integer, MapleParty> parties = new HashMap<>();
        private static Map<Integer, MapleExpedition> expeds = new HashMap<>();
        private static Map<PartySearchType, List<PartySearch>> searches = new EnumMap<>(PartySearchType.class);
        private static final AtomicInteger runningPartyId = new AtomicInteger(1), runningExpedId = new AtomicInteger(1);

        static {
            try {
                PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("UPDATE characters SET party = -1, fatigue = 0");
                ps.executeUpdate();
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            for (PartySearchType pst : PartySearchType.values()) {
                searches.put(pst, new ArrayList<>()); //according to client, max 10, even though theres page numbers ?!
            }
        }

        public static void partyChat(int partyid, String chattext, String namefrom) {
            partyChat(partyid, chattext, namefrom, 1);
        }

        public static void expedChat(int expedId, String chattext, String namefrom) {
            MapleExpedition party = getExped(expedId);
            if (party == null) {
                return;
            }
            for (int i : party.getParties()) {
                partyChat(i, chattext, namefrom, 4);
            }
        }

        public static void expedPacket(int expedId, byte[] packet, MaplePartyCharacter exception) {
            MapleExpedition party = getExped(expedId);
            if (party == null) {
                return;
            }
            for (int i : party.getParties()) {
                partyPacket(i, packet, exception);
            }
        }

        public static void partyPacket(int partyid, byte[] packet, MaplePartyCharacter exception) {
            MapleParty party = getParty(partyid);
            if (party == null) {
                return;
            }

            for (MaplePartyCharacter partychar : party.getMembers()) {
                int ch = Find.findChannel(partychar.getName());
                if (ch > 0 && (exception == null || partychar.getId() != exception.getId())) {
                    MapleCharacter chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(partychar.getName());
                    if (chr != null) { //Extra check just in case
                        chr.getClient().sendPacket(packet);
                    }
                }
            }
        }

        public static void partyChat(int partyid, String chattext, String namefrom, int mode) {
            MapleParty party = getParty(partyid);
            if (party == null) {
                return;
            }

            for (MaplePartyCharacter partychar : party.getMembers()) {
                int ch = Find.findChannel(partychar.getName());
                if (ch > 0) {
                    MapleCharacter chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(partychar.getName());
                    if (chr != null && !chr.getName().equalsIgnoreCase(namefrom)) { //Extra check just in case
                        chr.getClient().sendPacket(CField.multiChat(namefrom, chattext, mode));
                        if (chr.getClient().isMonitored()) {
                            World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM Message] " + namefrom + " said to " + chr.getName() + " (Party): " + chattext));
                        }
                    }
                }
            }
        }

        public static void partyMessage(int partyid, String chattext) {
            MapleParty party = getParty(partyid);
            if (party == null) {
                return;
            }

            for (MaplePartyCharacter partychar : party.getMembers()) {
                int ch = Find.findChannel(partychar.getName());
                if (ch > 0) {
                    MapleCharacter chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(partychar.getName());
                    if (chr != null) { //Extra check just in case
                        chr.dropMessage(5, chattext);
                    }
                }
            }
        }

        public static void expedMessage(int expedId, String chattext) {
            MapleExpedition party = getExped(expedId);
            if (party == null) {
                return;
            }
            for (int i : party.getParties()) {
                partyMessage(i, chattext);
            }
        }

        public static void updateParty(int partyid, PartyOperation operation, MaplePartyCharacter target) {
            MapleParty party = getParty(partyid);
            if (party == null) {
                return; //Don't update, just return. And definitely don't throw a damn exception.
                //throw new IllegalArgumentException("no party with the specified partyid exists");
            }
            final int oldExped = party.getExpeditionId();
            int oldInd = -1;
            if (oldExped > 0) {
                MapleExpedition exped = getExped(oldExped);
                if (exped != null) {
                    oldInd = exped.getIndex(partyid);
                }
            }
            switch (operation) {
                case JOIN:
                    party.addMember(target);
                    if (party.getMembers().size() >= 6) {
                        PartySearch toRemove = getSearchByParty(partyid);
                        if (toRemove != null) {
                            removeSearch(toRemove, "The Party Listing was removed because the party is full.");
                        } else if (party.getExpeditionId() > 0) {
                            MapleExpedition exped = getExped(party.getExpeditionId());
                            if (exped != null && exped.getAllMembers() >= exped.getType().maxMembers) {
                                toRemove = getSearchByExped(exped.getId());
                                if (toRemove != null) {
                                    removeSearch(toRemove, "The Party Listing was removed because the party is full.");
                                }
                            }
                        }
                    }
                    break;
                case EXPEL:
                case LEAVE:
                    party.removeMember(target);
                    break;
                case DISBAND:
                    disbandParty(partyid);
                    break;
                case SILENT_UPDATE:
                case LOG_ONOFF:
                    party.updateMember(target);
                    break;
                case CHANGE_LEADER:
                case CHANGE_LEADER_DC:
                    party.setLeader(target);
                    break;
                default:
                    throw new RuntimeException("Unhandeled updateParty operation " + operation.name());
            }
            if (operation == PartyOperation.LEAVE || operation == PartyOperation.EXPEL) {
                int chz = Find.findChannel(target.getName());
                if (chz > 0) {
                    MapleCharacter chr = getStorage(chz).getCharacterByName(target.getName());
                    if (chr != null) {
                        chr.setParty(null);
//                        if (oldExped > 0) {
//                            chr.getClient().sendPacket(ExpeditionPacket.expeditionMessage(80));
//                        }
                        chr.getClient().sendPacket(PartyPacket.updateParty(chr.getClient().getChannel(), party, operation, target));
                    }
                }
                if (target.getId() == party.getLeader().getId() && party.getMembers().size() > 0) { //pass on lead
                    MaplePartyCharacter lchr = null;
                    for (MaplePartyCharacter pchr : party.getMembers()) {
                        if (pchr != null && (lchr == null || lchr.getLevel() < pchr.getLevel())) {
                            lchr = pchr;
                        }
                    }
                    if (lchr != null) {
                        updateParty(partyid, PartyOperation.CHANGE_LEADER_DC, lchr);
                    }
                }
            }
            if (party.getMembers().size() <= 0) { //no members left, plz disband
                disbandParty(partyid);
            }
            for (MaplePartyCharacter partychar : party.getMembers()) {
                if (partychar == null) {
                    continue;
                }
                int ch = Find.findChannel(partychar.getName());
                if (ch > 0) {
                    MapleCharacter chr = getStorage(ch).getCharacterByName(partychar.getName());
                    if (chr != null) {
                        if (operation == PartyOperation.DISBAND) {
                            chr.setParty(null);
//                            if (oldExped > 0) {
//                                chr.getClient().sendPacket(ExpeditionPacket.expeditionMessage(83));
//                            }
                        } else {
                            chr.setParty(party);
                        }
                        chr.getClient().sendPacket(PartyPacket.updateParty(chr.getClient().getChannel(), party, operation, target));
                    }
                }
            }
//            if (oldExped > 0) {
//                expedPacket(oldExped, ExpeditionPacket.expeditionUpdate(oldInd, party), operation == PartyOperation.LOG_ONOFF || operation == PartyOperation.SILENT_UPDATE ? target : null);
//            }
        }

        public static MapleParty createParty(MaplePartyCharacter chrfor) {
            MapleParty party = new MapleParty(runningPartyId.getAndIncrement(), chrfor);
            parties.put(party.getId(), party);
            return party;
        }

        public static MapleParty createParty(MaplePartyCharacter chrfor, int expedId) {
            ExpeditionType ex = ExpeditionType.getById(expedId);
            MapleParty party = new MapleParty(runningPartyId.getAndIncrement(), chrfor, ex != null ? runningExpedId.getAndIncrement() : -1);
            parties.put(party.getId(), party);
            if (ex != null) {
                final MapleExpedition exp = new MapleExpedition(ex, chrfor.getId(), party.getExpeditionId());
                exp.getParties().add(party.getId());
                expeds.put(party.getExpeditionId(), exp);
            }
            return party;
        }

        public static MapleParty createPartyAndAdd(MaplePartyCharacter chrfor, int expedId) {
            MapleExpedition ex = getExped(expedId);
            if (ex == null) {
                return null;
            }
            MapleParty party = new MapleParty(runningPartyId.getAndIncrement(), chrfor, expedId);
            parties.put(party.getId(), party);
            ex.getParties().add(party.getId());
            return party;
        }

        public static MapleParty getParty(int partyid) {
            return parties.get(partyid);
        }

        public static MapleExpedition getExped(int partyid) {
            return expeds.get(partyid);
        }

        public static MapleExpedition disbandExped(int partyid) {
            PartySearch toRemove = getSearchByExped(partyid);
            if (toRemove != null) {
                removeSearch(toRemove, "The Party Listing was removed because the party disbanded.");
            }
            final MapleExpedition ret = expeds.remove(partyid);
            if (ret != null) {
                for (int p : ret.getParties()) {
                    MapleParty pp = getParty(p);
                    if (pp != null) {
                        updateParty(p, PartyOperation.DISBAND, pp.getLeader());
                    }
                }
            }
            return ret;
        }

        public static MapleParty disbandParty(int partyid) {
            PartySearch toRemove = getSearchByParty(partyid);
            if (toRemove != null) {
                removeSearch(toRemove, "The Party Listing was removed because the party disbanded.");
            }
            final MapleParty ret = parties.remove(partyid);
            if (ret == null) {
                return null;
            }
//            if (ret.getExpeditionId() > 0) {
//                MapleExpedition me = getExped(ret.getExpeditionId());
//                if (me != null) {
//                    final int ind = me.getIndex(partyid);
//                    if (ind >= 0) {
//                        me.getParties().remove(ind);
//                        expedPacket(me.getId(), ExpeditionPacket.expeditionUpdate(ind, null), null);
//                    }
//                }
//            }
            ret.disband();
            return ret;
        }

        public static List<PartySearch> searchParty(PartySearchType pst) {
            return searches.get(pst);
        }

        public static void removeSearch(PartySearch ps, String text) {
            List<PartySearch> ss = searches.get(ps.getType());
            if (ss.contains(ps)) {
                ss.remove(ps);
                ps.cancelRemoval();
                if (ps.getType().exped) {
                    expedMessage(ps.getId(), text);
                } else {
                    partyMessage(ps.getId(), text);
                }
            }
        }

        public static void addSearch(PartySearch ps) {
            searches.get(ps.getType()).add(ps);
        }

        public static PartySearch getSearch(MapleParty party) {
            for (List<PartySearch> ps : searches.values()) {
                for (PartySearch p : ps) {
                    if ((p.getId() == party.getId() && !p.getType().exped) || (p.getId() == party.getExpeditionId() && p.getType().exped)) {
                        return p;
                    }
                }
            }
            return null;
        }

        public static PartySearch getSearchByParty(int partyId) {
            for (List<PartySearch> ps : searches.values()) {
                for (PartySearch p : ps) {
                    if (p.getId() == partyId && !p.getType().exped) {
                        return p;
                    }
                }
            }
            return null;
        }

        public static PartySearch getSearchByExped(int partyId) {
            for (List<PartySearch> ps : searches.values()) {
                for (PartySearch p : ps) {
                    if (p.getId() == partyId && p.getType().exped) {
                        return p;
                    }
                }
            }
            return null;
        }

        public static boolean partyListed(MapleParty party) {
            return getSearchByParty(party.getId()) != null;
        }
    }

    public static class Buddy {

        public static void buddyChat(int[] recipientCharacterIds, int cidFrom, String nameFrom, String chattext) {
            for (int characterId : recipientCharacterIds) {
                int ch = Find.findChannel(characterId);
                if (ch > 0) {
                    MapleCharacter chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(characterId);
                    if (chr != null && chr.getBuddylist().containsVisible(cidFrom)) {
                        chr.getClient().sendPacket(CField.multiChat(nameFrom, chattext, 0));
                        if (chr.getClient().isMonitored()) {
                            World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM Message] " + nameFrom + " said to " + chr.getName() + " (Buddy): " + chattext));
                        }
                    }
                }
            }
        }

        private static void updateBuddies(int characterId, int channel, int[] buddies, boolean offline) {
            for (int buddy : buddies) {
                int ch = Find.findChannel(buddy);
                if (ch > 0) {
                    MapleCharacter chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(buddy);
                    if (chr != null) {
                        BuddylistEntry ble = chr.getBuddylist().get(characterId);
                        if (ble != null && ble.isVisible()) {
                            int mcChannel;
                            if (offline) {
                                ble.setChannel(-1);
                                mcChannel = -1;
                            } else {
                                ble.setChannel(channel);
                                mcChannel = channel - 1;
                            }
                            chr.getClient().sendPacket(BuddylistPacket.updateBuddyChannel(ble.getCharacterId(), mcChannel));
                        }
                    }
                }
            }
        }

        public static void buddyChanged(int cid, int cidFrom, String name, int channel, BuddyOperation operation, String group) {
            int ch = Find.findChannel(cid);
            if (ch > 0) {
                final MapleCharacter addChar = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(cid);
                if (addChar != null) {
                    final BuddyList buddylist = addChar.getBuddylist();
                    switch (operation) {
                        case ADDED:
                            if (buddylist.contains(cidFrom)) {
                                buddylist.put(new BuddylistEntry(name, cidFrom, group, channel, true));
                                addChar.getClient().sendPacket(BuddylistPacket.updateBuddyChannel(cidFrom, channel - 1));
                            }
                            break;
                        case DELETED:
                            if (buddylist.contains(cidFrom)) {
                                buddylist.put(new BuddylistEntry(name, cidFrom, group, -1, buddylist.get(cidFrom).isVisible()));
                                addChar.getClient().sendPacket(BuddylistPacket.updateBuddyChannel(cidFrom, -1));
                            }
                            break;
                    }
                }
            }
        }

        public static BuddyAddResult requestBuddyAdd(String addName, int channelFrom, int cidFrom, String nameFrom, int levelFrom, int jobFrom) {
            int ch = Find.findChannel(cidFrom);
            if (ch > 0) {
                final MapleCharacter addChar = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(addName);
                if (addChar != null) {
                    final BuddyList buddylist = addChar.getBuddylist();
                    if (buddylist.isFull()) {
                        return BuddyAddResult.BUDDYLIST_FULL;
                    }
                    if (!buddylist.contains(cidFrom)) {
                        buddylist.addBuddyRequest(addChar.getClient(), cidFrom, nameFrom, channelFrom, levelFrom, jobFrom);
                    } else if (buddylist.containsVisible(cidFrom)) {
                        return BuddyAddResult.ALREADY_ON_LIST;
                    }
                }
            }
            return BuddyAddResult.OK;
        }

        public static void loggedOn(String name, int characterId, int channel, int[] buddies) {
            updateBuddies(characterId, channel, buddies, false);
        }

        public static void loggedOff(String name, int characterId, int channel, int[] buddies) {
            updateBuddies(characterId, channel, buddies, true);
        }
    }

    public static class Messenger {

        private static Map<Integer, MapleMessenger> messengers = new HashMap<>();
        private static final AtomicInteger runningMessengerId = new AtomicInteger();

        static {
            runningMessengerId.set(1);
        }

        public static MapleMessenger createMessenger(MapleMessengerCharacter chrfor) {
            int messengerid = runningMessengerId.getAndIncrement();
            MapleMessenger messenger = new MapleMessenger(messengerid, chrfor);
            messengers.put(messenger.getId(), messenger);
            return messenger;
        }

        public static void declineChat(String target, String namefrom) {
            int ch = Find.findChannel(target);
            if (ch > 0) {
                ChannelServer cs = ChannelServer.getInstance(ch);
                MapleCharacter chr = cs.getPlayerStorage().getCharacterByName(target);
                if (chr != null) {
                    MapleMessenger messenger = chr.getMessenger();
                    if (messenger != null) {
                        chr.getClient().sendPacket(CField.messengerNote(namefrom, 5, 0));
                    }
                }
            }
        }

        public static MapleMessenger getMessenger(int messengerid) {
            return messengers.get(messengerid);
        }

        public static void leaveMessenger(int messengerid, MapleMessengerCharacter target) {
            MapleMessenger messenger = getMessenger(messengerid);
            if (messenger == null) {
                throw new IllegalArgumentException("No messenger with the specified messengerid exists");
            }
            int position = messenger.getPositionByName(target.getName());
            messenger.removeMember(target);

            for (MapleMessengerCharacter mmc : messenger.getMembers()) {
                if (mmc != null) {
                    int ch = Find.findChannel(mmc.getId());
                    if (ch > 0) {
                        MapleCharacter chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(mmc.getName());
                        if (chr != null) {
                            chr.getClient().sendPacket(CField.removeMessengerPlayer(position));
                        }
                    }
                }
            }
        }

        public static void silentLeaveMessenger(int messengerid, MapleMessengerCharacter target) {
            MapleMessenger messenger = getMessenger(messengerid);
            if (messenger == null) {
                throw new IllegalArgumentException("No messenger with the specified messengerid exists");
            }
            messenger.silentRemoveMember(target);
        }

        public static void silentJoinMessenger(int messengerid, MapleMessengerCharacter target) {
            MapleMessenger messenger = getMessenger(messengerid);
            if (messenger == null) {
                throw new IllegalArgumentException("No messenger with the specified messengerid exists");
            }
            messenger.silentAddMember(target);
        }

        public static void updateMessenger(int messengerid, String namefrom, int fromchannel) {
            MapleMessenger messenger = getMessenger(messengerid);
            int position = messenger.getPositionByName(namefrom);

            for (MapleMessengerCharacter messengerchar : messenger.getMembers()) {
                if (messengerchar != null && !messengerchar.getName().equals(namefrom)) {
                    int ch = Find.findChannel(messengerchar.getName());
                    if (ch > 0) {
                        MapleCharacter chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(messengerchar.getName());
                        if (chr != null) {
                            MapleCharacter from = ChannelServer.getInstance(fromchannel).getPlayerStorage().getCharacterByName(namefrom);
                            chr.getClient().sendPacket(CField.updateMessengerPlayer(namefrom, from, position, fromchannel - 1));
                        }
                    }
                }
            }
        }

        public static void joinMessenger(int messengerid, MapleMessengerCharacter target, String from, int fromchannel) {
            MapleMessenger messenger = getMessenger(messengerid);
            if (messenger == null) {
                throw new IllegalArgumentException("No messenger with the specified messengerid exists");
            }
            messenger.addMember(target);
            int position = messenger.getPositionByName(target.getName());
            for (MapleMessengerCharacter messengerchar : messenger.getMembers()) {
                if (messengerchar != null) {
                    int mposition = messenger.getPositionByName(messengerchar.getName());
                    int ch = Find.findChannel(messengerchar.getName());
                    if (ch > 0) {
                        MapleCharacter chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(messengerchar.getName());
                        if (chr != null) {
                            if (!messengerchar.getName().equals(from)) {
                                MapleCharacter fromCh = ChannelServer.getInstance(fromchannel).getPlayerStorage().getCharacterByName(from);
                                if (fromCh != null) {
                                    chr.getClient().sendPacket(CField.addMessengerPlayer(from, fromCh, position, fromchannel - 1));
                                    fromCh.getClient().sendPacket(CField.addMessengerPlayer(chr.getName(), chr, mposition, messengerchar.getChannel() - 1));
                                }
                            } else {
                                chr.getClient().sendPacket(CField.joinMessenger(mposition));
                            }
                        }
                    }
                }
            }
        }

        public static void messengerChat(int messengerid, String charname, String text, String namefrom) {
            MapleMessenger messenger = getMessenger(messengerid);
            if (messenger == null) {
                throw new IllegalArgumentException("No messenger with the specified messengerid exists");
            }

            for (MapleMessengerCharacter messengerchar : messenger.getMembers()) {
                if (messengerchar != null && !messengerchar.getName().equals(namefrom)) {
                    int ch = Find.findChannel(messengerchar.getName());
                    if (ch > 0) {
                        MapleCharacter chr = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(messengerchar.getName());
                        if (chr != null) {
                            chr.getClient().sendPacket(CField.messengerChat(charname, text));
                        }
                    }
                }
            }
        }

        public static void messengerInvite(String sender, int messengerid, String target, int fromchannel, boolean gm) {

            if (isConnected(target)) {

                int ch = Find.findChannel(target);
                if (ch > 0) {
                    MapleCharacter from = ChannelServer.getInstance(fromchannel).getPlayerStorage().getCharacterByName(sender);
                    MapleCharacter targeter = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(target);
                    if (targeter != null && targeter.getMessenger() == null) {
                        if (!targeter.isIntern() || gm) {
                            targeter.getClient().sendPacket(CField.messengerInvite(sender, messengerid));
                            from.getClient().sendPacket(CField.messengerNote(target, 4, 1));
                        } else {
                            from.getClient().sendPacket(CField.messengerNote(target, 4, 0));
                        }
                    } else {
                        from.getClient().sendPacket(CField.messengerChat(sender, " : " + target + " is already using Maple Messenger"));
                    }
                }
            }

        }
    }

    public static class Guild {

        private static final Map<Integer, MapleGuild> guilds = new LinkedHashMap<>();
        private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

        public static void addLoadedGuild(MapleGuild f) {
            if (f.isProper()) {
                guilds.put(f.getId(), f);
            }
        }

        public static int createGuild(int leaderId, String name) {
            return MapleGuild.createGuild(leaderId, name);
        }

        public static MapleGuild getGuild(int id) {
            MapleGuild ret = null;
            lock.readLock().lock();
            try {
                ret = guilds.get(id);
            } finally {
                lock.readLock().unlock();
            }
            if (ret == null) {
                lock.writeLock().lock();
                try {
                    ret = new MapleGuild(id);
                    if (ret == null || ret.getId() <= 0 || !ret.isProper()) { //failed to load
                        return null;
                    }
                    guilds.put(id, ret);
                } finally {
                    lock.writeLock().unlock();
                }
            }
            return ret; //Guild doesn't exist?
        }

        public static MapleGuild getGuildByName(String guildName) {
            lock.readLock().lock();
            try {
                for (MapleGuild g : guilds.values()) {
                    if (g.getName().equalsIgnoreCase(guildName)) {
                        return g;
                    }
                }
                return null;
            } finally {
                lock.readLock().unlock();
            }
        }

        public static MapleGuild getGuild(MapleCharacter mc) {
            return getGuild(mc.getGuildId());
        }

        public static void setGuildMemberOnline(MapleGuildCharacter mc, boolean bOnline, int channel) {
            MapleGuild g = getGuild(mc.getGuildId());
            if (g != null) {
                g.setOnline(mc.getId(), bOnline, channel);
            }
        }

        public static void guildPacket(int gid, byte[] message) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                g.broadcast(message);
            }
        }

        public static int addGuildMember(MapleGuildCharacter mc) {
            MapleGuild g = getGuild(mc.getGuildId());
            if (g != null) {
                return g.addGuildMember(mc);
            }
            return 0;
        }

        public static int addGuildMember(MapleGuildCharacter mc, boolean show) {
            MapleGuild g = getGuild(mc.getGuildId());
            if (g != null) {
                return g.addGuildMember(mc, show);
            }
            return 0;
        }

        public static void leaveGuild(MapleGuildCharacter mc) {
            MapleGuild g = getGuild(mc.getGuildId());
            if (g != null) {
                g.leaveGuild(mc);
            }
        }

        public static void guildChat(int gid, String name, int cid, String msg) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                g.guildChat(name, cid, msg);
            }
        }

        public static void changeRank(int gid, int cid, int newRank) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                g.changeRank(cid, newRank);
            }
        }

        public static void expelMember(MapleGuildCharacter initiator, String name, int cid) {
            MapleGuild g = getGuild(initiator.getGuildId());
            if (g != null) {
                g.expelMember(initiator, name, cid);
            }
        }

        public static void setGuildNotice(int gid, String notice) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                g.setGuildNotice(notice);
            }
        }

        public static void setGuildLeader(int gid, int cid) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                g.changeGuildLeader(cid);
            }
        }

        public static int getSkillLevel(int gid, int sid) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                return g.getSkillLevel(sid);
            }
            return 0;
        }

        public static boolean purchaseSkill(int gid, int sid, String name, int cid) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                return g.purchaseSkill(sid, name, cid);
            }
            return false;
        }

        public static boolean activateSkill(int gid, int sid, String name) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                return g.activateSkill(sid, name);
            }
            return false;
        }

        public static void memberLevelJobUpdate(MapleGuildCharacter mc) {
            MapleGuild g = getGuild(mc.getGuildId());
            if (g != null) {
                g.memberLevelJobUpdate(mc);
            }
        }

        public static void changeRankTitle(int gid, String[] ranks) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                g.changeRankTitle(ranks);
            }
        }

        public static void setGuildEmblem(int gid, short bg, byte bgcolor, short logo, byte logocolor) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                g.setGuildEmblem(bg, bgcolor, logo, logocolor);
            }
        }

        public static void disbandGuild(int gid) {
            MapleGuild g = getGuild(gid);
            lock.writeLock().lock();
            try {
                if (g != null) {
                    g.disbandGuild();
                    guilds.remove(gid);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        public static void deleteGuildCharacter(int guildid, int charid) {

            //ensure it's loaded on world server
            //setGuildMemberOnline(mc, false, -1);
            MapleGuild g = getGuild(guildid);
            if (g != null) {
                MapleGuildCharacter mc = g.getMGC(charid);
                if (mc != null) {
                    if (mc.getGuildRank() > 1) //not leader
                    {
                        g.leaveGuild(mc);
                    } else {
                        g.disbandGuild();
                    }
                }
            }
        }

        public static boolean increaseGuildCapacity(int gid, boolean b) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                return g.increaseCapacity(b);
            }
            return false;
        }

        public static void gainGP(int gid, int amount) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                g.gainGP(amount);
            }
        }

        public static void gainGP(int gid, int amount, int cid) {
            MapleGuild g = getGuild(gid);
            if (g != null) {
                g.gainGP(amount, false, cid);
            }
        }

        public static int getGP(final int gid) {
            final MapleGuild g = getGuild(gid);
            if (g != null) {
                return g.getGP();
            }
            return 0;
        }

        public static int getInvitedId(final int gid) {
            final MapleGuild g = getGuild(gid);
            if (g != null) {
                return g.getInvitedId();
            }
            return 0;
        }

        public static void setInvitedId(final int gid, final int inviteid) {
            final MapleGuild g = getGuild(gid);
            if (g != null) {
                g.setInvitedId(inviteid);
            }
        }

        public static int getGuildLeader(final int guildName) {
            final MapleGuild mga = getGuild(guildName);
            if (mga != null) {
                return mga.getLeaderId();
            }
            return 0;
        }

        public static int getGuildLeader(final String guildName) {
            final MapleGuild mga = getGuildByName(guildName);
            if (mga != null) {
                return mga.getLeaderId();
            }
            return 0;
        }

        public static void save() {
            System.out.println("儲存公會資料...");
            lock.writeLock().lock();
            try {
                for (MapleGuild a : guilds.values()) {
                    a.writeToDB(false);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        public static List<MapleBBSThread> getBBS(final int gid) {
            final MapleGuild g = getGuild(gid);
            if (g != null) {
                return g.getBBS();
            }
            return null;
        }

        public static int addBBSThread(final int guildid, final String title, final String text, final int icon, final boolean bNotice, final int posterID) {
            final MapleGuild g = getGuild(guildid);
            if (g != null) {
                return g.addBBSThread(title, text, icon, bNotice, posterID);
            }
            return -1;
        }

        public static final void editBBSThread(final int guildid, final int localthreadid, final String title, final String text, final int icon, final int posterID, final int guildRank) {
            final MapleGuild g = getGuild(guildid);
            if (g != null) {
                g.editBBSThread(localthreadid, title, text, icon, posterID, guildRank);
            }
        }

        public static final void deleteBBSThread(final int guildid, final int localthreadid, final int posterID, final int guildRank) {
            final MapleGuild g = getGuild(guildid);
            if (g != null) {
                g.deleteBBSThread(localthreadid, posterID, guildRank);
            }
        }

        public static final void addBBSReply(final int guildid, final int localthreadid, final String text, final int posterID) {
            final MapleGuild g = getGuild(guildid);
            if (g != null) {
                g.addBBSReply(localthreadid, text, posterID);
            }
        }

        public static final void deleteBBSReply(final int guildid, final int localthreadid, final int replyid, final int posterID, final int guildRank) {
            final MapleGuild g = getGuild(guildid);
            if (g != null) {
                g.deleteBBSReply(localthreadid, replyid, posterID, guildRank);
            }
        }

        public static void changeEmblem(int gid, int affectedPlayers, MapleGuild mgs) {
            Broadcast.sendGuildPacket(affectedPlayers, GuildPacket.guildEmblemChange(gid, (short) mgs.getLogoBG(), (byte) mgs.getLogoBGColor(), (short) mgs.getLogo(), (byte) mgs.getLogoColor()), -1, gid);
            setGuildAndRank(affectedPlayers, -1, -1, -1, -1);	//respawn player
        }

        public static void setGuildAndRank(int cid, int guildid, int rank, int contribution, int alliancerank) {
            int ch = Find.findChannel(cid);
            if (ch == -1) {
                // System.out.println("ERROR: cannot find player in given channel");
                return;
            }
            MapleCharacter mc = getStorage(ch).getCharacterById(cid);
            if (mc == null) {
                return;
            }
            boolean bDifferentGuild;
            if (guildid == -1 && rank == -1) { //just need a respawn
                bDifferentGuild = true;
            } else {
                bDifferentGuild = guildid != mc.getGuildId();
                mc.setGuildId(guildid);
                mc.setGuildRank((byte) rank);
                mc.setGuildContribution(contribution);
                mc.setAllianceRank((byte) alliancerank);
                mc.saveGuildStatus();
            }
            if (bDifferentGuild && ch > 0) {
                mc.getMap().broadcastMessage(mc, CField.loadGuildName(mc), false);
                mc.getMap().broadcastMessage(mc, CField.loadGuildIcon(mc), false);
            }
        }
    }

    public static class Broadcast {

        public static void broadcastGMMessageS(byte[] message, int type) {
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                cs.broadcastGMMessage(message, type);
            }
        }

        public static void broadcastSmega(byte[] message) {
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                cs.broadcastSmega(message);
            }
        }

        public static void broadcastGMMessage(byte[] message) {
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                cs.broadcastGMMessage(message);
            }
        }

        public static void broadcastMessage(byte[] message) {
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                cs.broadcastMessage(message);
            }
        }

        public static void sendPacket(List<Integer> targetIds, byte[] packet, int exception) {
            MapleCharacter c;
            for (int i : targetIds) {
                if (i == exception) {
                    continue;
                }
                int ch = Find.findChannel(i);
                if (ch < 0) {
                    continue;
                }
                c = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(i);
                if (c != null) {
                    c.getClient().sendPacket(packet);
                }
            }
        }

        public static void sendPacket(int targetId, byte[] packet) {
            int ch = Find.findChannel(targetId);
            if (ch < 0) {
                return;
            }
            final MapleCharacter c = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(targetId);
            if (c != null) {
                c.getClient().sendPacket(packet);
            }
        }

        public static void sendGuildPacket(int targetIds, byte[] packet, int exception, int guildid) {
            if (targetIds == exception) {
                return;
            }
            int ch = Find.findChannel(targetIds);
            if (ch < 0) {
                return;
            }
            final MapleCharacter c = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterById(targetIds);
            if (c != null && c.getGuildId() == guildid) {
                c.getClient().sendPacket(packet);
            }
        }
    }

    public static class Find {

        private static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private static HashMap<Integer, Integer> idToChannel = new HashMap<>();
        private static HashMap<String, Integer> nameToChannel = new HashMap<>();

        public static void register(int id, String name, int channel) {
            lock.writeLock().lock();
            try {
                idToChannel.put(id, channel);
                nameToChannel.put(name.toLowerCase(), channel);
            } finally {
                lock.writeLock().unlock();
            }
            //System.out.println("Char added: " + id + " " + name + " to channel " + channel);
        }

        public static void forceDeregister(int id) {
            lock.writeLock().lock();
            try {
                idToChannel.remove(id);
            } finally {
                lock.writeLock().unlock();
            }
            //System.out.println("Char removed: " + id);
        }

        public static void forceDeregister(String id) {
            lock.writeLock().lock();
            try {
                nameToChannel.remove(id.toLowerCase());
            } finally {
                lock.writeLock().unlock();
            }
            //System.out.println("Char removed: " + id);
        }

        public static void forceDeregister(int id, String name) {
            lock.writeLock().lock();
            try {
                idToChannel.remove(id);
                nameToChannel.remove(name.toLowerCase());
            } finally {
                lock.writeLock().unlock();
            }
            //System.out.println("Char removed: " + id + " " + name);
        }

        public static int findChannel(int id) {
            Integer ret;
            lock.readLock().lock();
            try {
                ret = idToChannel.get(id);
            } finally {
                lock.readLock().unlock();
            }
            if (ret != null) {
                if (ret != -10 && ret != -20 && ChannelServer.getInstance(ret) == null) { //wha
                    forceDeregister(id);
                    return -1;
                }
                return ret;
            }
            return -1;
        }

        public static int findChannel(String st) {
            Integer ret;
            lock.readLock().lock();
            try {
                ret = nameToChannel.get(st.toLowerCase());
            } finally {
                lock.readLock().unlock();
            }
            if (ret != null) {
                if (ret != -10 && ret != -20 && ChannelServer.getInstance(ret) == null) { //wha
                    forceDeregister(st);
                    return -1;
                }
                return ret;
            }
            return -1;
        }

        public static CharacterIdChannelPair[] multiBuddyFind(int charIdFrom, int[] characterIds) {
            List<CharacterIdChannelPair> foundsChars = new ArrayList<>(characterIds.length);
            for (int i : characterIds) {
                int channel = findChannel(i);
                if (channel > 0) {
                    foundsChars.add(new CharacterIdChannelPair(i, channel));
                }
            }
            Collections.sort(foundsChars);
            return foundsChars.toArray(new CharacterIdChannelPair[foundsChars.size()]);
        }
    }

    private final static int CHANNELS_PER_THREAD = 3;

    public static void registerRespawn() {
        Integer[] chs = ChannelServer.getAllInstance().toArray(new Integer[0]);
        for (int i = 0; i < chs.length; i += CHANNELS_PER_THREAD) {
            WorldTimer.getInstance().register(new Respawn(chs, i), 4500); //divisible by 9000 if possible.
        }
        //3000 good or bad? ive no idea >_>
        //buffs can also be done, but eh
    }

    public static class Respawn implements Runnable { //is putting it here a good idea?

        private int numTimes = 0;
        private final List<ChannelServer> cservs = new ArrayList<>(CHANNELS_PER_THREAD);

        public Respawn(Integer[] chs, int c) {
            StringBuilder s = new StringBuilder("[Respawn Worker] Registered for channels ");
            for (int i = 1; i <= CHANNELS_PER_THREAD && chs.length >= (c + i); i++) {
                cservs.add(ChannelServer.getInstance(c + i));
                s.append(c + i).append(" ");
            }
            //  System.out.println(s.toString());
        }

        @Override
        public void run() {
            numTimes++;
            long now = System.currentTimeMillis();
            for (ChannelServer cserv : cservs) {
                if (!cserv.hasFinishedShutdown()) {
                    for (MapleMap map : cserv.getMapFactory().getAllLoadedMaps()) { //iterating through each map o_x
                        handleMap(map, numTimes, map.getCharactersSize(), now);
                    }
                }
            }
        }
    }

    public static void handleMap(final MapleMap map, final int numTimes, final int size, final long now) {
        if (map.getItemsSize() > 0) {
            for (MapleMapItem item : map.getAllItemsThreadsafe()) {
                if (item.shouldExpire(now)) {
                    item.expire(map);
                } else if (item.shouldFFA(now)) {
                    item.setDropType((byte) 2);
                }
            }
        }
        if (map.characterSize() > 0 || map.getId() == 931000500) { //jaira hack
            if (map.canSpawn(now)) {
                map.respawn(false, now);
            }
            boolean hurt = map.canHurt(now);
            for (MapleCharacter chr : map.getCharactersThreadsafe()) {
                handleCooldowns(chr, numTimes, hurt, now);
            }
            if (map.getMobsSize() > 0) {
                for (MapleMonster mons : map.getAllMonstersThreadsafe()) {
                    if (mons.isAlive() && mons.shouldKill(now)) {
                        map.killMonster(mons);
                    } else if (mons.isAlive() && mons.shouldDrop(now)) {
                        mons.doDropItem(now);
                    } else if (mons.isAlive() && mons.getStatiSize() > 0) {
                        for (MonsterStatusEffect mse : mons.getAllBuffs()) {
                            if (mse.shouldCancel(now)) {
                                mons.cancelSingleStatus(mse);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void handleCooldowns(final MapleCharacter chr, final int numTimes, final boolean hurt, final long now) { //is putting it here a good idea? expensive?
        if (chr.getCooldownSize() > 0) {
            for (MapleCoolDownValueHolder m : chr.getCooldowns()) {
                if (m.startTime + m.length < now) {
                    final int skil = m.skillId;
                    chr.removeCooldown(skil);
                    chr.getClient().sendPacket(CField.skillCooldown(skil, 0));
                }
            }
        }
        if (chr.isAlive()) {
            if (chr.getJob() == 131 || chr.getJob() == 132) {
                if (chr.canBlood(now)) {
                    chr.doDragonBlood();
                }
            }
            if (chr.canRecover(now)) {
                chr.doRecovery();
            }
            if (chr.canHPRecover(now)) {
                chr.addHP((int) chr.getStat().getHealHP());
            }
            if (chr.canMPRecover(now)) {
                chr.addMP((int) chr.getStat().getHealMP());
            }
            if (chr.canFairy(now)) {
                chr.doFairy();
            }
            if (chr.canFish(now)) {
                chr.doFish(now);
            }
            if (chr.canDOT(now)) {
                chr.doDOT();
            }
        }

        if (chr.getDiseaseSize() > 0) {
            for (MapleDiseaseValueHolder m : chr.getAllDiseases()) {
                if (m != null && m.startTime + m.length < now) {
                    chr.dispelDebuff(m.disease);
                }
            }
        }
        if (numTimes % 7 == 0 && chr.getMount() != null && chr.getMount().canTire(now)) {
            chr.getMount().increaseFatigue();
        }
        if (numTimes % 100 == 0) { //we're parsing through the characters anyway (:
            chr.doFamiliarSchedule(now);
            for (MaplePet pet : chr.getSummonedPets()) {
                if (pet.getPetItemId() == 5000054 && pet.getSecondsLeft() > 0) {
                    pet.setSecondsLeft(pet.getSecondsLeft() - 1);
                    if (pet.getSecondsLeft() <= 0) {
                        chr.unequipPet(pet, true, true);
                        return;
                    }
                }
                int newFullness = pet.getFullness() - PetDataFactory.getHunger(pet.getPetItemId());
                if (newFullness <= 5) {
                    pet.setFullness(15);
                    chr.unequipPet(pet, true, true);
                } else {
                    pet.setFullness(newFullness);
                    chr.getClient().sendPacket(PetPacket.updatePet(pet, chr.getInventory(MapleInventoryType.CASH).getItem(pet.getInventoryPosition()), true));
                }
            }
        }
        if (hurt && chr.isAlive()) {
            if (chr.getInventory(MapleInventoryType.EQUIPPED).findById(chr.getMap().getHPDecProtect()) == null) {
                if (chr.getMapId() == 749040100 && chr.getInventory(MapleInventoryType.CASH).findById(5451000) == null) { //minidungeon
                    chr.addHP(-chr.getMap().getHPDec());
                } else if (chr.getMapId() != 749040100) {
                    chr.addHP(-(chr.getMap().getHPDec() - (chr.getBuffedValue(MapleBuffStat.HP_LOSS_GUARD) == null ? 0 : chr.getBuffedValue(MapleBuffStat.HP_LOSS_GUARD).intValue())));
                }
            }
        }
    }

    public static void autoSave(int min) {

        Timer.EventTimer.getInstance().register(new Runnable() {
            @Override
            public void run() {
                for (ChannelServer cs : ChannelServer.getAllInstances()) {
                    for (MapleCharacter chr : cs.getPlayerStorage().getAllCharactersThreadSafe()) {
                        if (chr == null) {
                            break;
                        }
                        // 自動存檔
                        if (chr.getClient().getLoginState() != 5 && chr.getTrade() == null && chr.getConversation() <= 0 && chr.getPlayerShop() == null && chr.getMap() != null) {
                            chr.saveToDB(false, false);
                        }
                    }
                }
            }
        }, min * 60 * 1000, min * 60 * 1000);
    }

    public static void gainMaplePoint(int min) {

        Timer.EventTimer.getInstance().register(new Runnable() {
            @Override
            public void run() {
                Map<MapleCharacter, Integer> giveList = new HashMap();
                // 0
                int vip0 = Randomizer.rand(1, 5);
                // 500
                int vip1 = Randomizer.rand(5, 10);
                // 1000
                int vip2 = Randomizer.rand(10, 20);
                // 5000
                int vip3 = Randomizer.rand(20, 30);
                // 10000
                int vip4 = Randomizer.rand(20, 40);
                // 15000
                int vip5 = Randomizer.rand(30, 50);
                // 20000
                int vip6 = Randomizer.rand(30, 60);
                // 25000
                int vip7 = Randomizer.rand(40, 65);
                // 30000
                int vip8 = Randomizer.rand(45, 70);
                // 
                for (ChannelServer cs : ChannelServer.getAllInstances()) {
                    for (MapleCharacter chr : cs.getPlayerStorage().getAllCharactersThreadSafe()) {
                        if (chr == null) {
                            break;
                        }
                        if (!chr.isAlive()) {
                            break;
                        }
                        int gain = 0;
                        switch (chr.getVip()) {
                            case 0: // 未贊助
                                gain = vip0;
                                break;
                            case 1:
                                gain = vip1;
                                break;
                            case 2:
                                gain = vip2;
                                break;
                            case 3:
                                gain = vip3;
                                break;
                            case 4:
                                gain = vip4;
                                break;
                            case 5:
                                gain = vip5;
                                break;
                            case 6:
                                gain = vip6;
                                break;
                            case 7:
                                gain = vip7;
                                break;
                            case 8:
                                gain = vip8;
                                break;
                        }
                        giveList.put(chr, gain);
                    }
                }
                if (!giveList.isEmpty()) {
                    MapleCharacter.setMP(giveList, true);
                }
            }
        }, min * 60 * 1000, min * 60 * 1000);
    }
    private static final List<Integer> playerSaveLockes = new ArrayList<Integer>();
    private static final ReentrantReadWriteLock saveLock = new ReentrantReadWriteLock();

    public static final boolean isPlayerSaving(Integer accountid) {
        saveLock.readLock().lock();
        boolean ret = false;
        try {
            ret = playerSaveLockes.contains(accountid);
        } finally {
            saveLock.readLock().unlock();
        }
        return ret;
    }

    public static final void addPlayerSaving(Integer accountid) {
        saveLock.writeLock().lock();
        try {
            playerSaveLockes.add(accountid);
        } finally {
            saveLock.writeLock().unlock();
        }
    }

    public static final void removePlayerSaving(Integer accountid) {
        saveLock.writeLock().lock();
        try {
            playerSaveLockes.remove(accountid);
        } finally {
            saveLock.writeLock().unlock();
        }
    }
    public static void clearChannelChangeDataByAccountId(int accountid) {
        try {

                for (ChannelServer cs : ChannelServer.getAllInstances()) {
                    getStorage(cs.getChannel()).deregisterPendingPlayerByAccountId(accountid);
                }
            
            getStorage(-20).deregisterPendingPlayerByAccountId(accountid);
            getStorage( -10).deregisterPendingPlayerByAccountId(accountid);
        } catch (Exception ex) {
            FileoutputUtil.printError("World.txt", "clearChannelChangeDataByAccountId", ex, "帳號ID: " + accountid);
        }
    }
}
