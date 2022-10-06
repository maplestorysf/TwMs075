package handling.channel.handler;

import client.MapleCharacter;
import client.MapleClient;
import constants.GameConstants;
import handling.channel.ChannelServer;
import handling.world.MapleParty;
import handling.world.MaplePartyCharacter;
import handling.world.PartyOperation;
import handling.world.World;
import server.maps.Event_DojoAgent;
import server.quest.MapleQuest;
import tools.data.LittleEndianAccessor;
import tools.packet.CWvsContext;
import tools.packet.CWvsContext.PartyPacket;

public class PartyHandler {

    public static final void DenyPartyRequest(final LittleEndianAccessor slea, final MapleClient c) {
        final int action = slea.readByte();
        String from = slea.readMapleAsciiString();
        String to = slea.readMapleAsciiString(); //wtf?
        MapleCharacter cfrom = c.getChannelServer().getPlayerStorage().getCharacterByName(from);
        if (cfrom != null) {
            cfrom.getClient().sendPacket(CWvsContext.PartyPacket.partyStatusMessage(22, c.getPlayer().getName()));
        }
//        if (action == 0x32 && GameConstants.GMS) { //TODO JUMP
//            final MapleCharacter chr = c.getPlayer().getMap().getCharacterById(slea.readInt());
//            if (chr != null && chr.getParty() == null && c.getPlayer().getParty() != null && c.getPlayer().getParty().getLeader().getId() == c.getPlayer().getId() && c.getPlayer().getParty().getMembers().size() < 6 && c.getPlayer().getParty().getExpeditionId() <= 0 && chr.getQuestNoAdd(MapleQuest.getInstance(GameConstants.PARTY_INVITE)) == null && c.getPlayer().getQuestNoAdd(MapleQuest.getInstance(GameConstants.PARTY_REQUEST)) == null) {
//                chr.setParty(c.getPlayer().getParty());
//                World.Party.updateParty(c.getPlayer().getParty().getId(), PartyOperation.JOIN, new MaplePartyCharacter(chr));
//                chr.receivePartyMemberHP();
//                chr.updatePartyMemberHP();
//            }
//            return;
//        }
//        final int partyid = slea.readInt();
//        if (c.getPlayer().getParty() == null && c.getPlayer().getQuestNoAdd(MapleQuest.getInstance(GameConstants.PARTY_INVITE)) == null) {
//            MapleParty party = World.Party.getParty(partyid);
//            if (party != null) {
//                if (party.getExpeditionId() > 0) {
//                    c.getPlayer().dropMessage(5, "You may not do party operations while in a raid.");
//                    return;
//                }
//                if (action == (GameConstants.GMS ? 0x1F : 0x1B)) { //accept
//                    if (party.getMembers().size() < 6) {
//                        c.getPlayer().setParty(party);
//                        World.Party.updateParty(partyid, PartyOperation.JOIN, new MaplePartyCharacter(c.getPlayer()));
//                        c.getPlayer().receivePartyMemberHP();
//                        c.getPlayer().updatePartyMemberHP();
//                    } else {
//                        c.sendPacket(PartyPacket.partyStatusMessage(22, null));
//                    }
//                } else if (action != (GameConstants.GMS ? 0x1E : 0x16)) {
//                    final MapleCharacter cfrom = c.getChannelServer().getPlayerStorage().getCharacterById(party.getLeader().getId());
//                    if (cfrom != null) { // %s has denied the party request.
//                        cfrom.getClient().sendPacket(PartyPacket.partyStatusMessage(23, c.getPlayer().getName()));
//                    }
//                }
//            } else {
//                c.getPlayer().dropMessage(5, "The party you are trying to join does not exist");
//            }
//        } else {
//            c.getPlayer().dropMessage(5, "You can't join the party as you are already in one");
//        }

    }

    public static final void PartyOperation(final LittleEndianAccessor slea, final MapleClient c) {
        final int operation = slea.readByte();
        MapleParty party = c.getPlayer().getParty();
        MaplePartyCharacter partyplayer = new MaplePartyCharacter(c.getPlayer());

        switch (operation) {
            case 1: // create
                if (party == null) {
                    party = World.Party.createParty(partyplayer);
                    c.getPlayer().setParty(party);
                    c.sendPacket(PartyPacket.partyCreated(party.getId()));

                } else {
                    if (party.getExpeditionId() > 0) {
                        c.getPlayer().dropMessage(5, "You may not do party operations while in a raid.");
                        return;
                    }
                    if (partyplayer.equals(party.getLeader()) && party.getMembers().size() == 1) { //only one, reupdate
                        c.sendPacket(PartyPacket.partyCreated(party.getId()));
                    } else {
                        c.getPlayer().dropMessage(5, "You can't create a party as you are already in one");
                    }
                }
                break;
            case 2: // leave
                if (party != null) { //are we in a party? o.O"
                    if (party.getExpeditionId() > 0) {
                        c.getPlayer().dropMessage(5, "You may not do party operations while in a raid.");
                        return;
                    }
                    if (partyplayer.equals(party.getLeader())) { // disband
                        if (GameConstants.isDojo(c.getPlayer().getMapId())) {
                            Event_DojoAgent.failed(c.getPlayer());
                        }
                        if (c.getPlayer().getPyramidSubway() != null) {
                            c.getPlayer().getPyramidSubway().fail(c.getPlayer());
                        }
                        World.Party.updateParty(party.getId(), PartyOperation.DISBAND, partyplayer);
                        if (c.getPlayer().getEventInstance() != null) {
                            c.getPlayer().getEventInstance().disbandParty();
                        }
                    } else {
                        if (GameConstants.isDojo(c.getPlayer().getMapId())) {
                            Event_DojoAgent.failed(c.getPlayer());
                        }
                        if (c.getPlayer().getPyramidSubway() != null) {
                            c.getPlayer().getPyramidSubway().fail(c.getPlayer());
                        }
                        World.Party.updateParty(party.getId(), PartyOperation.LEAVE, partyplayer);
                        if (c.getPlayer().getEventInstance() != null) {
                            c.getPlayer().getEventInstance().leftParty(c.getPlayer());
                        }
                    }
                    c.getPlayer().setParty(null);
                }
                break;
            case 3: // accept invitation
                final int partyid = slea.readInt();
                if (party == null) {
                    party = World.Party.getParty(partyid);
                    if (party != null) {
                        if (party.getExpeditionId() > 0) {
                            c.getPlayer().dropMessage(5, "You may not do party operations while in a raid.");
                            return;
                        }
                        if (party.getMembers().size() < 6 && c.getPlayer().getQuestNoAdd(MapleQuest.getInstance(GameConstants.PARTY_INVITE)) == null) {
                            c.getPlayer().setParty(party);
                            World.Party.updateParty(party.getId(), PartyOperation.JOIN, partyplayer);
                            c.getPlayer().receivePartyMemberHP();
                            c.getPlayer().updatePartyMemberHP();
                        } else {
                            c.sendPacket(PartyPacket.partyStatusMessage(16, null));
                        }
                    } else {
                        c.getPlayer().dropMessage(5, "The party you are trying to join does not exist");
                    }
                } else {
                    c.getPlayer().dropMessage(5, "You can't join the party as you are already in one");
                }
                break;
            case 4: // invite
                if (party == null) {
                    party = World.Party.createParty(partyplayer);
                    c.getPlayer().setParty(party);
                    c.sendPacket(PartyPacket.partyCreated(party.getId()));
                }
                // TODO store pending invitations and check against them
                final String theName = slea.readMapleAsciiString();
                final int theCh = World.Find.findChannel(theName);
                if (theCh > 0) {
                    final MapleCharacter invited = ChannelServer.getInstance(theCh).getPlayerStorage().getCharacterByName(theName);
                    if (invited != null && invited.getParty() == null && invited.getQuestNoAdd(MapleQuest.getInstance(GameConstants.PARTY_INVITE)) == null) {
                        if (party.getExpeditionId() > 0) {
                            c.getPlayer().dropMessage(5, "You may not do party operations while in a raid.");
                            return;
                        }
                        if (party.getMembers().size() < 6) {
                            invited.getClient().sendPacket(PartyPacket.partyInvite(c.getPlayer()));
                        } else {
                            c.sendPacket(PartyPacket.partyStatusMessage(14, null));
                        }
                    } else {
                        c.sendPacket(PartyPacket.partyStatusMessage(16, null));
                    }
                } else {
                    c.sendPacket(PartyPacket.partyStatusMessage(18, null));
                }
                break;
            case 5: // expel
                if (party != null && partyplayer != null && partyplayer.equals(party.getLeader())) {
                    if (party.getExpeditionId() > 0) {
                        c.getPlayer().dropMessage(5, "You may not do party operations while in a raid.");
                        return;
                    }
                    final MaplePartyCharacter expelled = party.getMemberById(slea.readInt());
                    if (expelled != null) {
                        if (GameConstants.isDojo(c.getPlayer().getMapId()) && expelled.isOnline()) {
                            Event_DojoAgent.failed(c.getPlayer());
                        }
                        if (c.getPlayer().getPyramidSubway() != null && expelled.isOnline()) {
                            c.getPlayer().getPyramidSubway().fail(c.getPlayer());
                        }
                        World.Party.updateParty(party.getId(), PartyOperation.EXPEL, expelled);
                        if (c.getPlayer().getEventInstance() != null) {
                            /*if leader wants to boot someone, then the whole party gets expelled
                             TODO: Find an easier way to get the character behind a MaplePartyCharacter
                             possibly remove just the expellee.*/
                            if (expelled.isOnline()) {
                                c.getPlayer().getEventInstance().disbandParty();
                            }
                        }
                    }
                }
                break;
            case 6: // change leader
                if (party != null) {
                    if (party.getExpeditionId() > 0) {
                        c.getPlayer().dropMessage(5, "You may not do party operations while in a raid.");
                        return;
                    }
                    final MaplePartyCharacter newleader = party.getMemberById(slea.readInt());
                    if (newleader != null && partyplayer.equals(party.getLeader())) {
                        World.Party.updateParty(party.getId(), PartyOperation.CHANGE_LEADER, newleader);
                    }
                }
                break;
            case 7: //request to  join a party
                if (party != null) {
                    if (c.getPlayer().getEventInstance() != null || c.getPlayer().getPyramidSubway() != null || party.getExpeditionId() > 0 || GameConstants.isDojo(c.getPlayer().getMapId())) {
                        c.getPlayer().dropMessage(5, "You may not do party operations while in a raid.");
                        return;
                    }
                    if (partyplayer.equals(party.getLeader())) { // disband
                        World.Party.updateParty(party.getId(), PartyOperation.DISBAND, partyplayer);
                    } else {
                        World.Party.updateParty(party.getId(), PartyOperation.LEAVE, partyplayer);
                    }
                    c.getPlayer().setParty(null);
                }
                final int partyid_ = slea.readInt();
//                if (GameConstants.GMS) {
//                    //TODO JUMP
//                    party = World.Party.getParty(partyid_);
//                    if (party != null && party.getMembers().size() < 6) {
//                        if (party.getExpeditionId() > 0) {
//                            c.getPlayer().dropMessage(5, "You may not do party operations while in a raid.");
//                            return;
//                        }
//                        final MapleCharacter cfrom = c.getPlayer().getMap().getCharacterById(party.getLeader().getId());
//                        if (cfrom != null && cfrom.getQuestNoAdd(MapleQuest.getInstance(GameConstants.PARTY_REQUEST)) == null) {
//                            c.sendPacket(PartyPacket.partyStatusMessage(50, c.getPlayer().getName()));
//                            cfrom.getClient().sendPacket(PartyPacket.partyRequestInvite(c.getPlayer()));
//                        } else {
//                            c.getPlayer().dropMessage(5, "Player was not found or player is not accepting party requests.");
//                        }
//                    }
//                }
                break;
            case 8: //allow party requests
                if (slea.readByte() > 0) {
                    c.getPlayer().getQuestRemove(MapleQuest.getInstance(GameConstants.PARTY_REQUEST));
                } else {
                    c.getPlayer().getQuestNAdd(MapleQuest.getInstance(GameConstants.PARTY_REQUEST));
                }
                break;
            default:
                System.out.println("Unhandled Party function." + operation);
                break;
        }
    }

    public static final void AllowPartyInvite(final LittleEndianAccessor slea, final MapleClient c) {
        if (slea.readByte() > 0) {
            c.getPlayer().getQuestRemove(MapleQuest.getInstance(GameConstants.PARTY_INVITE));
        } else {
            c.getPlayer().getQuestNAdd(MapleQuest.getInstance(GameConstants.PARTY_INVITE));
        }
    }
}
