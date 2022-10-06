package handling.channel.handler;

import java.awt.Point;
import java.util.List;

import client.MapleClient;
import client.MapleCharacter;
import client.anticheat.CheatingOffense;
import client.inventory.MapleInventoryType;
import client.status.MonsterStatus;
import handling.world.World;
import server.MapleInventoryManipulator;
import server.Randomizer;
import server.maps.MapleMap;
import server.life.MapleMonster;
import server.life.MobSkill;
import server.life.MobSkillFactory;
import server.maps.MapleNodes.MapleNodeInfo;
import server.movement.AbsoluteLifeMovement;
import server.movement.AbstractLifeMovement;
import server.movement.LifeMovement;
import server.movement.LifeMovementFragment;

import tools.FileoutputUtil;
import tools.Pair;
import tools.StringUtil;
import tools.packet.MobPacket;
import tools.data.LittleEndianAccessor;
import tools.packet.CWvsContext;

public class MobHandler {

    public static final void MoveMonster(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null) {
            return; //?
        }
        final int oid = slea.readInt();
        final MapleMonster monster = chr.getMap().getMonsterByOid(oid);

        if (monster == null) {
            chr.addMoveMob(oid);
            return;
        }
        if (monster.getLinkCID() > 0) {
            return;
        }
        final short moveid = slea.readShort();
        final boolean useSkill = slea.readByte() > 0;
        final byte skill = slea.readByte();
        final int unk = slea.readInt();
        int realskill = 0;
        int level = 0;

        if (useSkill) {// && (skill == -1 || skill == 0)) {
            final byte size = monster.getNoSkills();
            boolean used = false;

            if (size > 0) {
                final Pair<Integer, Integer> skillToUse = monster.getSkills().get((byte) Randomizer.nextInt(size));
                realskill = skillToUse.getLeft();
                level = skillToUse.getRight();
                // Skill ID and Level
                final MobSkill mobSkill = MobSkillFactory.getMobSkill(realskill, level);

                if (mobSkill != null && !mobSkill.checkCurrentBuff(chr, monster)) {
                    final long now = System.currentTimeMillis();
                    final long ls = monster.getLastSkillUsed(realskill);

                    if (ls == 0 || (((now - ls) > mobSkill.getCoolTime()) && !mobSkill.onlyOnce())) {
                        monster.setLastSkillUsed(realskill, now, mobSkill.getCoolTime());

                        final int reqHp = (int) (((float) monster.getHp() / monster.getMobMaxHp()) * 100); // In case this monster have 2.1b and above HP
                        if (reqHp <= mobSkill.getHP()) {
                            used = true;
                            mobSkill.applyEffect(chr, monster, true);
                        }
                    }
                }
            }
            if (!used) {
                realskill = 0;
                level = 0;
            }
        }
//        final List<Pair<Integer, Integer>> unk3 = new ArrayList<>();
//        final byte size1 = slea.readByte();
//        for (int i = 0; i < size1; i++) {
//            unk3.add(new Pair<>(Integer.valueOf(slea.readShort()), Integer.valueOf(slea.readShort())));
//        }
//        final List<Integer> unk2 = new ArrayList<>();
//        final byte size = slea.readByte();
//        for (int i = 0; i < size; i++) {
//            unk2.add(Integer.valueOf(slea.readShort()));
//        }
        slea.skip(1);
        slea.skip(4); // sometimes 0, 1
        slea.skip(4); // original pos
        final Point startPos = monster.getPosition();
        List<LifeMovementFragment> res = null;
        try {
            res = MovementParse.parseMovement(slea, 2);
        } catch (ArrayIndexOutOfBoundsException e) {
            FileoutputUtil.outputFileError(FileoutputUtil.Movement_Log, e);
            FileoutputUtil.log(FileoutputUtil.Movement_Log, "MOBID " + monster.getId() + ", AIOBE Type2:\n" + slea.toString(true));
            return;
        }
        if (res != null && res.size() > 0) {
            final MapleMap map = chr.getMap();
         //  for (final LifeMovementFragment move : res) {
            // if (move instanceof AbsoluteLifeMovement) {
            //   final Point endPos = ((LifeMovement) move).getPosition();
            //   if (endPos.x < (map.getLeft() - 250) || endPos.y < (map.getTop() - 250) || endPos.x > (map.getRight() + 250) || endPos.y > (map.getBottom() + 250)) { //experimental
            //           chr.getCheatTracker().checkMoveMonster(endPos);
            // return;
            //        }
            //      }
            //  }
            try {
                CheckMobVac(c, monster, res, startPos);
            } catch (Exception ex) {
            }
            c.sendPacket(MobPacket.moveMonsterResponse(monster.getObjectId(), moveid, monster.getMp(), monster.isControllerHasAggro(), realskill, level));
            if (slea.available() != 9/*slea.available() < 11 || slea.available() > 35*/) {
                // [byte][int-pos][int-pos][5-same for all mobs][21]
                //70 F4 81 CE 14 00 00 00 00 00 00 00 CB A2 03 00 05 00 00 00 02
                FileoutputUtil.log(FileoutputUtil.PacketEx_Log, "slea.available != 9 (movement parsing error)\n" + slea.toString(true));
                //c.getSession().close();
                return;
            }
            MovementParse.updatePosition(res, monster, -1);
            final Point endPos = monster.getPosition();//getTruePosition
            map.moveMonster(monster, endPos);
            map.broadcastMessage(chr, MobPacket.moveMonster(useSkill, skill, unk, monster.getObjectId(), startPos, res), endPos);
            //     chr.getCheatTracker().checkMoveMonster(endPos);
        }
    }

    public static final void FriendlyDamage(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMap map = chr.getMap();
        if (map == null) {
            return;
        }
        final MapleMonster mobfrom = map.getMonsterByOid(slea.readInt());
        slea.skip(4); // Player ID
        final MapleMonster mobto = map.getMonsterByOid(slea.readInt());

        if (mobfrom != null && mobto != null && mobto.getStats().isFriendly()) {
            final int damage = (mobto.getStats().getLevel() * Randomizer.nextInt(mobto.getStats().getLevel())) / 2; // Temp for now until I figure out something more effective
            mobto.damage(chr, damage, true);
            checkShammos(chr, mobto, map);
        }
    }

    public static final void MobBomb(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMap map = chr.getMap();
        if (map == null) {
            return;
        }
        final MapleMonster mobfrom = map.getMonsterByOid(slea.readInt());
        slea.skip(4); // something, 9E 07
        slea.readInt(); //-204?

        if (mobfrom != null && mobfrom.getBuff(MonsterStatus.MONSTER_BOMB) != null) {
            /* not sure
             12D -    0B 3D 42 00 EC 05 00 00 32 FF FF FF 00 00 00 00 00 00 00 00
             <monsterstatus done>
             108 - 07 0B 3D 42 00 EC 05 00 00 32 FF FF FF 01 00 00 00 7B 00 00 00
             */
        }
    }

    public static final void checkShammos(final MapleCharacter chr, final MapleMonster mobto, final MapleMap map) {
        if (!mobto.isAlive() && mobto.getStats().isEscort()) { //shammos
            for (MapleCharacter chrz : map.getCharactersThreadsafe()) { //check for 2022698
                if (chrz.getParty() != null && chrz.getParty().getLeader().getId() == chrz.getId()) {
                    //leader
                    if (chrz.haveItem(2022698)) {
                        MapleInventoryManipulator.removeById(chrz.getClient(), MapleInventoryType.USE, 2022698, 1, false, true);
                        mobto.heal((int) mobto.getMobMaxHp(), mobto.getMobMaxMp(), true);
                        return;
                    }
                    break;
                }
            }
            map.broadcastMessage(CWvsContext.serverNotice(6, "Your party has failed to protect the monster."));
            final MapleMap mapp = chr.getMap().getForcedReturnMap();
            for (MapleCharacter chrz : map.getCharactersThreadsafe()) {
                chrz.changeMap(mapp, mapp.getPortal(0));
            }
        } else if (mobto.getStats().isEscort() && mobto.getEventInstance() != null) {
            mobto.getEventInstance().setProperty("HP", String.valueOf(mobto.getHp()));
        }
    }

    public static final void MonsterBomb(final int oid, final MapleCharacter chr) {
//        final MapleMonster monster = chr.getMap().getMonsterByOid(oid);
//
//        if (monster == null || !chr.isAlive() || chr.isHidden() || monster.getLinkCID() > 0) {
//            return;
//        }
//        final byte selfd = monster.getStats().getSelfD();
//        if (selfd != -1) {
//            chr.getMap().killMonster(monster, chr, false, false, selfd);
//        }
    }

    public static final void AutoAggro(final int monsteroid, final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null || chr.isHidden()) { //no evidence :)
            return;
        }
        final MapleMonster monster = chr.getMap().getMonsterByOid(monsteroid);

        if (monster != null && chr.getTruePosition().distanceSq(monster.getTruePosition()) < 200000 && monster.getLinkCID() <= 0) {
            if (monster.getController() != null) {
                if (chr.getMap().getCharacterById(monster.getController().getId()) == null) {
                    monster.switchController(chr, true);
                } else {
                    monster.switchController(monster.getController(), true);
                }
            } else {
                monster.switchController(chr, true);
            }
        }
    }

    public static final void HypnotizeDmg(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMonster mob_from = chr.getMap().getMonsterByOid(slea.readInt()); // From
        slea.skip(4); // Player ID
        final int to = slea.readInt(); // mobto
        slea.skip(1); // Same as player damage, -1 = bump, integer = skill ID
        final int damage = slea.readInt();
//	slea.skip(1); // Facing direction
//	slea.skip(4); // Some type of pos, damage display, I think

        final MapleMonster mob_to = chr.getMap().getMonsterByOid(to);

        if (mob_from != null && mob_to != null && mob_to.getStats().isFriendly()) { //temp for now
            if (damage > 30000) {
                return;
            }
            mob_to.damage(chr, damage, true);
            checkShammos(chr, mob_to, chr.getMap());
        }
    }

    public static final void DisplayNode(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMonster mob_from = chr.getMap().getMonsterByOid(slea.readInt()); // From
        if (mob_from != null) {
            chr.getClient().sendPacket(MobPacket.getNodeProperties(mob_from, chr.getMap()));
        }
    }

    public static final void MobNode(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final MapleMonster mob_from = chr.getMap().getMonsterByOid(slea.readInt()); // From
        final int newNode = slea.readInt();
        final int nodeSize = chr.getMap().getNodes().size();
        if (mob_from != null && nodeSize > 0) {
            final MapleNodeInfo mni = chr.getMap().getNode(newNode);
            if (mni == null) {
                return;
            }
            if (mni.attr == 2) { //talk
                switch (chr.getMapId() / 100) {
                    case 9211200:
                    case 9211201:
                    case 9211202:
                    case 9211203:
                    case 9211204:
                        chr.getMap().talkMonster("Please escort me carefully.", 5120035, mob_from.getObjectId()); //temporary for now. itemID is located in WZ file
                        break;
                    case 9320001:
                    case 9320002:
                    case 9320003:
                        chr.getMap().talkMonster("Please escort me carefully.", 5120051, mob_from.getObjectId()); //temporary for now. itemID is located in WZ file
                        break;
                }
            }
            mob_from.setLastNode(newNode);
            if (chr.getMap().isLastNode(newNode)) { //the last node on the map.
                switch (chr.getMapId() / 100) {
                    case 9211200:
                    case 9211201:
                    case 9211202:
                    case 9211203:
                    case 9211204:
                    case 9320001:
                    case 9320002:
                    case 9320003:
                        chr.getMap().broadcastMessage(CWvsContext.serverNotice(5, "Proceed to the next stage."));
                        chr.getMap().removeMonster(mob_from);
                        break;

                }
            }
        }
    }

    public static void CheckMobVac(MapleClient c, MapleMonster monster, List<LifeMovementFragment> res, Point startPos) {
        MapleCharacter chr = c.getPlayer();
        try {
            boolean fly = monster.getStats().getFly();
            Point endPos = null;
            int reduce_x = 0;
            int reduce_y = 0;
            for (LifeMovementFragment move : res) {
                if ((move instanceof AbstractLifeMovement)) {
                    endPos = ((LifeMovement) move).getPosition();
                    try {
                        reduce_x = Math.abs(startPos.x - endPos.x);
                        reduce_y = Math.abs(startPos.y - endPos.y);
                    } catch (Exception ex) {
                    }
                }
            }

            if (!fly) {
                int GeneallyDistance_y = 150;
                int GeneallyDistance_x = 200;
                int Check_x = 250;
                int max_x = 450;
                switch (chr.getMapId()) {
                    case 100040001:
                    case 926013500:
                        GeneallyDistance_y = 200;
                        break;
                    case 200010300:
                        GeneallyDistance_x = 1000;
                        GeneallyDistance_y = 500;
                        break;
                    case 220010600:
                    case 926013300:
                        GeneallyDistance_x = 200;
                        break;
                    case 211040001:
                        GeneallyDistance_x = 220;
                        break;
                    case 101030105:
                        GeneallyDistance_x = 250;
                        break;
                    case 541020500:
                        Check_x = 300;
                        break;
                }
                switch (monster.getId()) {
                    case 4230100:
                        GeneallyDistance_y = 200;
                        break;
                    case 9410066:
                        Check_x = 1000;
                        break;
                }
                if (GeneallyDistance_x > max_x) {
                    max_x = GeneallyDistance_x;
                }
                if (((reduce_x > GeneallyDistance_x || reduce_y > GeneallyDistance_y) && reduce_y != 0) || (reduce_x > Check_x && reduce_y == 0) || reduce_x > max_x) {
                    chr.addMobVac();
                    if (c.getPlayer().getMobVac() % 50 == 0) {
                        c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.吸怪, "(地圖: " + chr.getMapId() + " 怪物數量:" + chr.getMobVac() + ")");
                        if (c.getPlayer().getMobVac() % 150 == 0) {
                            World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM密語] " + chr.getName() + " (編號: " + chr.getId() + ")使用吸怪(" + chr.getMobVac() + ")! - 地圖:" + chr.getMapId() + "(" + chr.getMap().getMapName() + ")"));
                        }
                        StringBuilder sb = new StringBuilder();
                        sb.append("\r\n");
                        sb.append(FileoutputUtil.CurrentReadable_TimeGMT());
                        sb.append(" 玩家: ");
                        sb.append(StringUtil.getRightPaddedStr(c.getPlayer().getName(), ' ', 13));
                        sb.append("(編號:");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(c.getPlayer().getId()), ' ', 5));
                        sb.append(" )怪物: ");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(monster.getId()), ' ', 7));
                        sb.append("(");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(monster.getObjectId()), ' ', 6));
                        sb.append(")");
                        sb.append(" 地圖: ");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(c.getPlayer().getMapId()), ' ', 9));
                        sb.append(" 初始座標:");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(startPos.x), ' ', 4));
                        sb.append(",");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(startPos.y), ' ', 4));
                        sb.append(" 移動座標:");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(endPos.x), ' ', 4));
                        sb.append(",");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(endPos.y), ' ', 4));
                        sb.append(" 相差座標:");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(reduce_x), ' ', 4));
                        sb.append(",");
                        sb.append(StringUtil.getRightPaddedStr(String.valueOf(reduce_y), ' ', 4));
                        FileoutputUtil.logToFile("外掛/吸怪.txt", sb.toString());
                        if (chr.hasGmLevel(1)) {
                            c.getPlayer().dropMessage("觸發吸怪 --  x: " + reduce_x + ", y: " + reduce_y);
                        }
                    }
                }
            }

        } catch (Exception ex) {

        }
    }

}
