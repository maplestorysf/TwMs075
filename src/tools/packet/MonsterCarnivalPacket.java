package tools.packet;

import client.MapleCharacter;

import handling.SendPacketOpcode;
import java.util.List;
import server.MapleCarnivalParty;
import tools.data.MaplePacketLittleEndianWriter;

public class MonsterCarnivalPacket {

    public static byte[] startMonsterCarnival(final MapleCharacter chr, final int enemyavailable, final int enemytotal) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_START.getValue());
        final MapleCarnivalParty friendly = chr.getCarnivalParty();
        mplew.write(friendly.getTeam());
        mplew.writeShort(chr.getAvailableCP());
        mplew.writeShort(chr.getTotalCP());
        mplew.writeShort(friendly.getAvailableCP()); // ??
        mplew.writeShort(friendly.getTotalCP()); // ??
        mplew.writeShort(enemyavailable);
        mplew.writeShort(enemytotal);
        mplew.writeZeroBytes(10);

        return mplew.getPacket();
    }

    public static byte[] playerDiedMessage(String name, int lostCP, int team) { //CPQ
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_DIED.getValue());
        mplew.write(team); //team
        mplew.writeMapleAsciiString(name);
        mplew.write(lostCP);

        return mplew.getPacket();
    }

    public static byte[] playerLeaveMessage(boolean leader, String name, int team) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_LEAVE.getValue());
        mplew.write(leader ? 7 : 0);
        mplew.write(team); // 0: red, 1: blue
        mplew.writeMapleAsciiString(name);

        return mplew.getPacket();
    }

    public static byte[] CPUpdate(boolean party, int curCP, int totalCP, int team) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        if (!party) {
            mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_OBTAINED_CP.getValue());
        } else {
            mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_PARTY_CP.getValue());
            mplew.write(team);
        }
        mplew.writeShort(curCP);
        mplew.writeShort(totalCP);

        return mplew.getPacket();
    }

    public static byte[] showMCStats(int left, int right) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_STATS.getValue());
        mplew.writeInt(left);
        mplew.writeInt(right);

        return mplew.getPacket();
    }

    public static byte[] playerSummoned(String name, int tab, int number) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_SUMMON.getValue());
        mplew.write(tab);
        mplew.write(number);
        mplew.writeMapleAsciiString(name);

        return mplew.getPacket();
    }

    public static byte[] showMCResult(int mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_RESULT.getValue());
        mplew.write(mode);

        return mplew.getPacket();
    }

    public static byte[] showMCRanking(List<MapleCharacter> players) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_RANKING.getValue());
        mplew.writeShort(players.size());
        for (MapleCharacter i : players) {
            mplew.writeInt(i.getId());
            mplew.writeMapleAsciiString(i.getName());
            mplew.writeInt(10); // points
            mplew.write(0); // team
        }

        return mplew.getPacket();
    }
}
