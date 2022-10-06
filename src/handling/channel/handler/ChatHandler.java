package handling.channel.handler;

import client.MapleClient;
import client.MapleCharacter;
import client.MapleCharacterUtil;
import client.messages.CommandProcessor;
import constants.ServerConstants;
import constants.ServerConstants.CommandType;
import handling.channel.ChannelServer;
import handling.world.MapleMessenger;
import handling.world.MapleMessengerCharacter;
import handling.world.World;
import java.util.Arrays;
import tools.FileoutputUtil;
import tools.packet.CField;
import tools.data.LittleEndianAccessor;
import tools.packet.CWvsContext;

public class ChatHandler {

    public static final void GeneralChat(String text, final byte unk, final MapleClient c, final MapleCharacter chr) {
        if (text.length() > 0 && chr != null && chr.getMap() != null && !CommandProcessor.processCommand(c, text, chr.getBattle() == null ? CommandType.NORMAL : CommandType.POKEMON)) {
            if (!chr.isIntern() && text.length() >= 80) {
                return;
            }
            if (!chr.getCanTalk()) {
                text = ServerConstants.RandomSorry();
            }
            if (chr.getCanTalk() || chr.isStaff()) {
                //Note: This patch is needed to prevent chat packet from being broadcast to people who might be packet sniffing.
                if (chr.isHidden()) {// GM隱身時處理
                    if (chr.isIntern() && !chr.isSuperGM() && unk == 0) {
                        chr.getMap().broadcastGMMessage(chr, CField.getChatText(chr.getId(), text, false, (byte) 1), true);
                        if (unk == 0) {
                            chr.getMap().broadcastGMMessage(chr, CWvsContext.serverNotice(2, chr.getName() + " : " + text), true);
                        }
                    } else {
                        chr.getMap().broadcastGMMessage(chr, CField.getChatText(chr.getId(), text, c.getPlayer().isSuperGM(), unk), true);
                    }
                } else {// GM現身時處理
                    chr.getCheatTracker().checkMsg();
                    if (chr.isIntern() && !chr.isSuperGM() && unk == 0) {//GM處理
                        chr.getMap().broadcastMessage(CField.getChatText(chr.getId(), text, false, (byte) 1), c.getPlayer().getTruePosition());
                        if (unk == 0) {
                            chr.getMap().broadcastMessage(CWvsContext.serverNotice(2, chr.getName() + " : " + text), c.getPlayer().getTruePosition());
                        }
                    } else {//玩家處理
                        chr.getMap().broadcastMessage(CField.getChatText(chr.getId(), text, c.getPlayer().isSuperGM(), unk), c.getPlayer().getTruePosition());
                        if (ServerConstants.LOG_CHAT) {
                            FileoutputUtil.logToFile("紀錄/監聽/聊天/普通聊天.txt", "\r\n" + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 『" + chr.getName() + "』 地圖『" + chr.getMapId() + "』：  " + text);
                        }
                    }
                }
//                if (text.equalsIgnoreCase(c.getChannelServer().getServerName() + " rocks")) {
//                    chr.finishAchievement(11);
//                }
            } else {
                c.sendPacket(CWvsContext.serverNotice(6, "You have been muted and are therefore unable to talk."));
            }
        }
    }

    public static final void Others(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        final int type = slea.readByte();
        final byte numRecipients = slea.readByte();
        if (numRecipients <= 0) {
            return;
        }
        int recipients[] = new int[numRecipients];

        for (byte i = 0; i < numRecipients; i++) {
            recipients[i] = slea.readInt();
        }
        final String chattext = slea.readMapleAsciiString();
        if (chr == null || !chr.getCanTalk()) {
            c.sendPacket(CWvsContext.serverNotice(6, "You have been muted and are therefore unable to talk."));
            return;
        }

        if (c.isMonitored()) {
            String chattype = "未知";
            switch (type) {
                case 0:
                    chattype = "好友";
                    break;
                case 1:
                    chattype = "隊伍";
                    break;
                case 2:
                    chattype = "公會";
                    break;
                case 3:
                    chattype = "聯盟";
                    break;
                case 4:
                    chattype = "遠征";
                    break;
            }
            World.Broadcast.broadcastGMMessage(
                    CWvsContext.serverNotice(6, "[GM 訊息] " + MapleCharacterUtil.makeMapleReadable(chr.getName())
                            + " 說 (" + chattype + "): " + chattext));

        }
        if (chattext.length() <= 0 || CommandProcessor.processCommand(c, chattext, chr.getBattle() == null ? CommandType.NORMAL : CommandType.POKEMON)) {
            return;
        }
        chr.getCheatTracker().checkMsg();
        switch (type) {
            case 0:
                World.Buddy.buddyChat(recipients, chr.getId(), chr.getName(), chattext);
                if (ServerConstants.LOG_CHAT) {
                    FileoutputUtil.logToFile("紀錄/監聽/聊天/好友聊天.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 好友ID: " + Arrays.toString(recipients) + "玩家: " + chr.getName() + " 說了 :" + chattext);
                }
                break;
            case 1:
                if (chr.getParty() == null) {
                    break;
                }
                World.Party.partyChat(chr.getParty().getId(), chattext, chr.getName());
                if (ServerConstants.LOG_CHAT) {
                    FileoutputUtil.logToFile("紀錄/監聽/聊天/隊伍聊天.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 隊伍: " + chr.getParty().getId() + " 玩家: " + chr.getName() + " 說了 :" + chattext);
                }
                break;
            case 2:
                if (chr.getGuildId() <= 0) {
                    break;
                }
                World.Guild.guildChat(chr.getGuildId(), chr.getName(), chr.getId(), chattext);
                if (ServerConstants.LOG_CHAT) {
                    FileoutputUtil.logToFile("紀錄/監聽/聊天/公會聊天.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " 公會: " + chr.getGuildId() + " 玩家: " + chr.getName() + " 說了 :" + chattext);
                }
                break;
        }
    }

    public static final void Messenger(final LittleEndianAccessor slea, final MapleClient c) {
        String input;
        MapleMessenger messenger = c.getPlayer().getMessenger();

        switch (slea.readByte()) {
            case 0x00: // open
                if (messenger == null) {
                    int messengerid = slea.readInt();
                    if (messengerid == 0) { // create
                        c.getPlayer().setMessenger(World.Messenger.createMessenger(new MapleMessengerCharacter(c.getPlayer())));
                    } else { // join
                        messenger = World.Messenger.getMessenger(messengerid);
                        if (messenger != null) {
                            final int position = messenger.getLowestPosition();
                            if (position > -1 && position < 4) {
                                c.getPlayer().setMessenger(messenger);
                                World.Messenger.joinMessenger(messenger.getId(), new MapleMessengerCharacter(c.getPlayer()), c.getPlayer().getName(), c.getChannel());
                            }
                        }
                    }
                }
                break;
            case 0x02: // exit
                if (messenger != null) {
                    final MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(c.getPlayer());
                    World.Messenger.leaveMessenger(messenger.getId(), messengerplayer);
                    c.getPlayer().setMessenger(null);
                }
                break;
            case 0x03: // invite

                if (messenger != null) {
                    final int position = messenger.getLowestPosition();
                    if (position <= -1 || position >= 4) {
                        return;
                    }
                    input = slea.readMapleAsciiString();
                    final MapleCharacter target = c.getChannelServer().getPlayerStorage().getCharacterByName(input);

                    if (target != null) {
                        if (target.getMessenger() == null) {
                            if (!target.isIntern() || c.getPlayer().isIntern()) {
                                c.sendPacket(CField.messengerNote(input, 4, 1));
                                target.getClient().sendPacket(CField.messengerInvite(c.getPlayer().getName(), messenger.getId()));
                            } else {
                                c.sendPacket(CField.messengerNote(input, 4, 0));
                            }
                        } else {
                            c.sendPacket(CField.messengerChat(c.getPlayer().getName(), " : " + target.getName() + " is already using Maple Messenger."));
                        }
                    } else if (World.isConnected(input)) {
                        World.Messenger.messengerInvite(c.getPlayer().getName(), messenger.getId(), input, c.getChannel(), c.getPlayer().isIntern());
                    } else {
                        c.sendPacket(CField.messengerNote(input, 4, 0));
                    }
                }
                break;
            case 0x05: // decline
                final String targeted = slea.readMapleAsciiString();
                final MapleCharacter target = c.getChannelServer().getPlayerStorage().getCharacterByName(targeted);
                if (target != null) { // This channel
                    if (target.getMessenger() != null) {
                        target.getClient().sendPacket(CField.messengerNote(c.getPlayer().getName(), 5, 0));
                    }
                } else // Other channel
                {
                    if (!c.getPlayer().isIntern()) {
                        World.Messenger.declineChat(targeted, c.getPlayer().getName());
                    }
                }
                break;
            case 0x06: // message
                if (messenger != null) {
                    final String charname = slea.readMapleAsciiString();
                    final String text = slea.readMapleAsciiString();
                    final String chattext = charname + "" + text;
                    World.Messenger.messengerChat(messenger.getId(), charname, text, c.getPlayer().getName());
                    if (messenger.isMonitored() && chattext.length() > c.getPlayer().getName().length() + 3) { //name : NOT name0 or name1
                        World.Broadcast.broadcastGMMessage(
                                CWvsContext.serverNotice(
                                        6, "[GM Message] " + MapleCharacterUtil.makeMapleReadable(c.getPlayer().getName()) + "(Messenger: "
                                        + messenger.getMemberNamesDEBUG() + ") said: " + chattext));
                    }
                }
                break;
        }
    }

    public static final void Whisper_Find(final LittleEndianAccessor slea, final MapleClient c) {
        final byte mode = slea.readByte();
        switch (mode) {
            case 68: //buddy
            case 5: { // Find

                final String recipient = slea.readMapleAsciiString();
                MapleCharacter player = c.getChannelServer().getPlayerStorage().getCharacterByName(recipient);
                if (player != null) {
                    if (!player.isIntern() || c.getPlayer().isIntern() && player.isIntern()) {

                        c.sendPacket(CField.getFindReplyWithMap(player.getName(), player.getMap().getId(), mode == 68));
                    } else {
                        c.sendPacket(CField.getWhisperReply(recipient, (byte) 0));
                    }
                } else { // Not found
                    int ch = World.Find.findChannel(recipient);
                    if (ch > 0) {
                        player = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(recipient);
                        if (player == null) {
                            break;
                        }
                        if (player != null) {
                            if (!player.isIntern() || (c.getPlayer().isIntern() && player.isIntern())) {
                                c.sendPacket(CField.getFindReply(recipient, (byte) ch, mode == 68));
                            } else {
                                c.sendPacket(CField.getWhisperReply(recipient, (byte) 0));
                            }
                            return;
                        }
                    }
                    if (ch == -10) {
                        c.sendPacket(CField.getFindReplyWithCS(recipient, mode == 68));
                    } else if (ch == -20) {
                        c.getPlayer().dropMessage(5, "'" + recipient + "' is at the MTS."); //idfc
                    } else {
                        c.sendPacket(CField.getWhisperReply(recipient, (byte) 0));
                    }
                }
                break;
            }
            case 6: { // Whisper
                if (c.getPlayer() == null || c.getPlayer().getMap() == null) {
                    return;
                }

                c.getPlayer().getCheatTracker().checkMsg();
                final String recipient = slea.readMapleAsciiString();
                String text = slea.readMapleAsciiString();
                final int ch = World.Find.findChannel(recipient);
                if (ch > 0) {
                    MapleCharacter player = ChannelServer.getInstance(ch).getPlayerStorage().getCharacterByName(recipient);
                    if (player == null) {
                        break;
                    }
                    if (!c.getPlayer().getCanTalk()) {
                        text = ServerConstants.RandomSorry();
                    }
                    player.getClient().sendPacket(CField.getWhisper(c.getPlayer().getName(), c.getChannel(), text));
                    if (!c.getPlayer().isIntern() && player.isIntern()) {
                        c.sendPacket(CField.getWhisperReply(recipient, (byte) 0));
                    } else {
                        c.sendPacket(CField.getWhisperReply(recipient, (byte) 1));
                    }
                    if (ServerConstants.LOG_CHAT) {
                        FileoutputUtil.logToFile("紀錄/監聽/聊天/私密聊天.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " IP: " + c.getSessionIPAddress() + " <私密>: " + c.getPlayer().getName() + " 密語 " + recipient + " 說了 :" + text);
                    }
                    if (c.isMonitored()) {
                        World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, c.getPlayer().getName() + " whispered " + recipient + " : " + text));
                    } else if (player.getClient().isMonitored()) {
                        World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, c.getPlayer().getName() + " whispered " + recipient + " : " + text));
                    }
                } else {
                    c.sendPacket(CField.getWhisperReply(recipient, (byte) 0));
                }
            }
            break;
        }
    }
}
