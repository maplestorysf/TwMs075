package handling.channel.handler;

import java.awt.Point;
import java.util.List;

import client.inventory.Item;
import client.Skill;
import client.SkillFactory;
import client.SkillMacro;
import constants.GameConstants;
import client.inventory.MapleInventoryType;
import client.MapleBuffStat;
import client.MapleClient;
import client.MapleCharacter;
import client.PlayerStats;
import client.anticheat.CheatingOffense;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import constants.BattleConstants;
import constants.BattleConstants.PokemonAbility;
import constants.BattleConstants.PokemonMap;
import constants.MapConstants;
import database.DatabaseConnection;
import handling.channel.ChannelServer;
import java.lang.ref.WeakReference;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import server.events.MapleEvent;
import server.events.MapleEventType;
import java.util.Collections;
import java.util.LinkedList;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.MapleStatEffect;
import server.MaplePortal;
import server.PokemonBattle;
import server.Randomizer;
import server.Timer.CloneTimer;
import server.events.MapleSnowball.MapleSnowballs;
import server.life.MapleMonster;
import server.life.MobAttackInfo;
import server.life.MobSkill;
import server.life.MobSkillFactory;
import server.maps.MapleMap;
import server.maps.FieldLimitType;
import server.movement.LifeMovementFragment;
import server.quest.MapleQuest;
import tools.FileoutputUtil;
import tools.packet.CField;
import tools.Pair;
import tools.packet.MTSCSPacket;
import tools.data.LittleEndianAccessor;
import tools.packet.CField.EffectPacket;
import tools.packet.CField.UIPacket;
import tools.packet.CWvsContext;
import tools.packet.CWvsContext.InventoryPacket;
import tools.packet.PetPacket;

public class PlayerHandler {

    public static int isFinisher(final int skillid) {
        switch (skillid) {
            case 1111003:
            case 1111004:
            case 1111006:
            case 1111005:
                return 10;
            case 11111002:
            case 11111003:
                return 10;
        }
        return 0;
    }

    public static void ChangeSkillMacro(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final int num = slea.readByte();
        String name;
        int shout, skill1, skill2, skill3;
        SkillMacro macro;

        for (int i = 0; i < num; i++) {
            name = slea.readMapleAsciiString();
            shout = slea.readByte();
            skill1 = slea.readInt();
            skill2 = slea.readInt();
            skill3 = slea.readInt();

            macro = new SkillMacro(skill1, skill2, skill3, name, shout, i);
            chr.updateMacros(i, macro);
        }
    }

    public static final void ChangeKeymap(final LittleEndianAccessor slea, final MapleCharacter chr) {
        if (slea.available() > 8 && chr != null) { // else = pet auto pot
            slea.skip(4); //0
            final int numChanges = slea.readInt();

            for (int i = 0; i < numChanges; i++) {
                final int key = slea.readInt();
                final byte type = slea.readByte();
                final int action = slea.readInt();
                if (type == 1 && action >= 1000) { //0 = normal key, 1 = skill, 2 = item
                    final Skill skil = SkillFactory.getSkill(action);
                    if (skil != null) { //not sure about aran tutorial skills..lol
                        if ((!skil.isFourthJob() && !skil.isBeginnerSkill() && skil.isInvisible() && chr.getSkillLevel(skil) <= 0) || GameConstants.isLinkedAranSkill(action) || action % 10000 < 1000 || action >= 91000000) { //cannot put on a key
                            continue;
                        }
                    }
                }
                chr.changeKeybinding(key, type, action);
            }
        } else if (chr != null) {
            final int type = slea.readInt(), data = slea.readInt();
            switch (type) {
                case 1:
                    if (data <= 0) {
                        chr.getQuestRemove(MapleQuest.getInstance(GameConstants.HP_ITEM));
                    } else {
                        chr.getQuestNAdd(MapleQuest.getInstance(GameConstants.HP_ITEM)).setCustomData(String.valueOf(data));
                    }
                    break;
                case 2:
                    if (data <= 0) {
                        chr.getQuestRemove(MapleQuest.getInstance(GameConstants.MP_ITEM));
                    } else {
                        chr.getQuestNAdd(MapleQuest.getInstance(GameConstants.MP_ITEM)).setCustomData(String.valueOf(data));
                    }
                    break;
            }
        }
    }

    public static final void UseTitle(final int itemId, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null) {
            return;
        }
        final Item toUse = chr.getInventory(MapleInventoryType.SETUP).findById(itemId);
        if (toUse == null) {
            return;
        }
        if (itemId <= 0) {
            chr.getQuestRemove(MapleQuest.getInstance(GameConstants.ITEM_TITLE));
        } else {
            chr.getQuestNAdd(MapleQuest.getInstance(GameConstants.ITEM_TITLE)).setCustomData(String.valueOf(itemId));
        }
        chr.getMap().broadcastMessage(chr, CField.showTitle(chr.getId(), itemId), false);
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void UseChair(final int itemId, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null) {
            return;
        }
        final Item toUse = chr.getInventory(MapleInventoryType.SETUP).findById(itemId);
        if (toUse == null) {
            chr.getCheatTracker().registerOffense(CheatingOffense.USING_UNAVAILABLE_ITEM, Integer.toString(itemId));
            return;
        }
        if (GameConstants.isFishingMap(chr.getMapId()) && itemId == 3011000) {
            if (chr.getStat().canFish) {
                chr.startFishingTask();
            }
        }
        chr.setChair(itemId);
        chr.getMap().broadcastMessage(chr, CField.showChair(chr.getId(), itemId), false);
        c.sendPacket(CWvsContext.enableActions());
    }

    public static final void CancelChair(final short id, final MapleClient c, final MapleCharacter chr) {
        if (id == -1) { // Cancel Chair
            chr.cancelFishingTask();
            chr.setChair(0);
            c.sendPacket(CField.cancelChair(-1));
            if (chr.getMap() != null) {
                chr.getMap().broadcastMessage(chr, CField.showChair(chr.getId(), 0), false);
            }
        } else { // Use In-Map Chair
            chr.setChair(id);
            c.sendPacket(CField.cancelChair(id));
        }
    }

    public static final void TrockAddMap(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        final byte addrem = slea.readByte();
        final byte vip = slea.readByte();

        if (vip == 1) {
            if (addrem == 0) {
                chr.deleteFromRocks(slea.readInt());
            } else if (addrem == 1) {
                if (!FieldLimitType.VipRock.check(chr.getMap().getFieldLimit())) {
                    if (c.getPlayer().getMapId() != 180000000) {
                        chr.addRockMap();
                    } else {
                        chr.dropMessage(1, "你不能儲存這張地圖");
                    }
                }
            }
        } else if (addrem == 0) {
            chr.deleteFromRegRocks(slea.readInt());
        } else if (addrem == 1) {
            if (!FieldLimitType.VipRock.check(chr.getMap().getFieldLimit())) {
                if (c.getPlayer().getMapId() <= 197010000 && c.getPlayer().getMapId() != 180000000) {
                    chr.addRegRockMap();
                } else {
                    chr.dropMessage(1, "你不能儲存這張地圖");
                }
            }
        }
        c.sendPacket(MTSCSPacket.OnMapTransferResult(chr, vip, addrem == 0));
    }

    public static final void CharInfoRequest(final int objectid, final MapleClient c, final MapleCharacter chr) {
        if (c.getPlayer() == null || c.getPlayer().getMap() == null) {
            return;
        }
        final MapleCharacter player = c.getPlayer().getMap().getCharacterById(objectid);
        c.sendPacket(CWvsContext.enableActions());
        if (player != null && !player.isClone()) {
            if (!player.isGM() || c.getPlayer().isGM()) {
                if (c.getPlayer().getId() == objectid) {
                    if (!c.getPlayer().getExcluded().equals("") && c.getPlayer().getPet(0) != null) {
                        c.sendPacket(PetPacket.showPetUpdate(player, player.getPet(0).getUniqueId(), (byte) 0, player.getExcluded()));
                    }
                }
                c.sendPacket(CWvsContext.charInfo(player, c.getPlayer().getId() == objectid));
            }
        }
    }

    public static final void CharInfoSetRequest(final LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer() == null) {
            return;
        }
        int mode = slea.readByte();
        switch (mode) {
            case 0:
                c.getPlayer().setMessage(slea.readMapleAsciiString());
                break;
            case 1:
                c.getPlayer().setExpression(slea.readByte());
                break;
            case 2:
                c.getPlayer().setBlood(slea.readByte());
                c.getPlayer().setMonth(slea.readByte());
                c.getPlayer().setDay(slea.readByte());
                c.getPlayer().setConstellation(slea.readByte());
                break;
            default:
                break;
        }
    }

    public static final void TakeDamage(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        //System.out.println("Take Damage :" + slea.toString());
        slea.skip(4); // randomized
        final byte type = slea.readByte(); //-4 is mist, -3 and -2 are map damage.
        slea.skip(1); // Element - 0x00 = elementless, 0x01 = ice, 0x02 = fire, 0x03 = lightning
        int damage = slea.readInt();
        boolean isDeadlyAttack = false;
        boolean pPhysical = false;
        int oid = 0;
        int monsteridfrom = 0;
        int fake = 0;
        int mpattack = 0;
        int skillid = 0;
        int pID = 0;
        int pDMG = 0;
        byte direction = 0;
        byte pType = 0;
        Point pPos = new Point(0, 0);
        MapleMonster attacker = null;
        if (chr == null || chr.isHidden() || chr.getMap() == null) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (chr.isGM() && chr.isInvincible()) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (GameConstants.CakeMap(chr.getMapId())) {
            MapleMap map = c.getChannelServer().getMapFactory().getMap(749020920);
            chr.changeMap(map, map.getPortal(0));
            chr.dropMessage(5, "因為被Cake發現，所以你被kick asshole。");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final PlayerStats stats = chr.getStat();
        if (type != -2 && type != -3 && type != -4) { // Not map damage
            monsteridfrom = slea.readInt();
            oid = slea.readInt();
            attacker = chr.getMap().getMonsterByOid(oid);
            direction = slea.readByte(); // Knock direction

            if (attacker == null || attacker.getId() != monsteridfrom || attacker.getLinkCID() > 0 || attacker.isFake() || attacker.getStats().isFriendly()) {
                return;
            }
            if (type != -1 && damage > 0) { // Bump damage
                final MobAttackInfo attackInfo = attacker.getStats().getMobAttack(type);
                if (attackInfo != null) {
                    if (attackInfo.isElement && stats.TER > 0 && Randomizer.nextInt(100) < stats.TER) {
                        System.out.println("Avoided ER from mob id: " + monsteridfrom);
                        return;
                    }
                    if (attackInfo.isDeadlyAttack()) {
                        isDeadlyAttack = true;
                        mpattack = stats.getMp() - 1;
                    } else {
                        mpattack += attackInfo.getMpBurn();
                    }
                    final MobSkill skill = MobSkillFactory.getMobSkill(attackInfo.getDiseaseSkill(), attackInfo.getDiseaseLevel());
                    if (skill != null && (damage == -1 || damage > 0)) {
                        skill.applyEffect(chr, attacker, false);
                    }
                    attacker.setMp(attacker.getMp() - attackInfo.getMpCon());
                }
            }
//            skillid = slea.readInt();
//            pDMG = slea.readInt(); // we don't use this, incase packet edit..
//            final byte defType = slea.readByte();
//            slea.skip(1); // ?
//            if (defType == 1) { // Guard
//                final Skill bx = SkillFactory.getSkill(31110008);
//                final int bof = chr.getTotalSkillLevel(bx);
//                if (bof > 0) {
//                    final MapleStatEffect eff = bx.getEffect(bof);
//                    if (Randomizer.nextInt(100) <= eff.getX()) { // estimate
//                        chr.handleForceGain(oid, 31110008, eff.getZ());
//                    }
//                }
//            }
//            if (skillid != 0) {
//                pPhysical = slea.readByte() > 0;
//                pID = slea.readInt();
//                pType = slea.readByte();
//                slea.skip(4); // Mob position garbage
//                pPos = slea.readPos();
//            }
        }
        if (damage == -1) {
            fake = 4020002 + ((chr.getJob() / 10 - 40) * 100000);
            if (fake != 4120002 && fake != 4220002) {
                fake = 4120002;
            }
            if (type == -1 && chr.getJob() == 122 && attacker != null && chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -10) != null) {
                if (chr.getTotalSkillLevel(1220006) > 0) {
                    final MapleStatEffect eff = SkillFactory.getSkill(1220006).getEffect(chr.getTotalSkillLevel(1220006));
                    attacker.applyStatus(chr, new MonsterStatusEffect(MonsterStatus.STUN, 1, 1220006, null, false), false, eff.getDuration(), true, eff);
                    fake = 1220006;
                }
            }
            if (chr.getTotalSkillLevel(fake) <= 0) {
                return;
            }
        } else if (damage < -1 || damage > 200000) {
            //AutobanManager.getInstance().addPoints(c, 1000, 60000, "Taking abnormal amounts of damge from " + monsteridfrom + ": " + damage);
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (chr.getStat().dodgeChance > 0 && Randomizer.nextInt(100) < chr.getStat().dodgeChance) {
            c.sendPacket(EffectPacket.showForeignEffect(20));
            return;
        }
        if (pPhysical && skillid == 1201007 && chr.getTotalSkillLevel(1201007) > 0) { // Only Power Guard decreases damage
            damage = (damage - pDMG);
            if (damage > 0) {
                final MapleStatEffect eff = SkillFactory.getSkill(1201007).getEffect(chr.getTotalSkillLevel(1201007));
                long enemyDMG = Math.min((damage * (eff.getY() / 100)), (attacker.getMobMaxHp() / 2));
                if (enemyDMG > pDMG) {
                    enemyDMG = pDMG; // ;)
                }
                if (enemyDMG > 1000) { // just a rough estimation, we cannot reflect > 1k
                    enemyDMG = 1000; // too bad
                }
                attacker.damage(chr, enemyDMG, true, 1201007);
            } else {
                damage = 1;
            }
        }
        chr.getCheatTracker().checkTakeDamage(damage);
        Pair<Double, Boolean> modify = chr.modifyDamageTaken((double) damage, attacker);
        damage = modify.left.intValue();
        if (damage > 0) {
            chr.getCheatTracker().setAttacksWithoutHit(false);

            if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
                chr.cancelMorphs();
            }
            // if (slea.available() == 3 || slea.available() == 4) {
            //     byte level = slea.readByte();
            //     if (level > 0) {
            //         final MobSkill skill = MobSkillFactory.getMobSkill(slea.readShort(), level);
            //          if (skill != null) {
            //             skill.applyEffect(chr, attacker, false);
            //         }
            //      }
            //  }
            boolean mpAttack = chr.getBuffedValue(MapleBuffStat.MECH_CHANGE) != null && chr.getBuffSource(MapleBuffStat.MECH_CHANGE) != 35121005;
            if (chr.getBuffedValue(MapleBuffStat.MAGIC_GUARD) != null) {
                int hploss = 0, mploss = 0;
                if (isDeadlyAttack) {
                    if (stats.getHp() > 1) {
                        hploss = stats.getHp() - 1;
                    }
                    if (stats.getMp() > 1) {
                        mploss = stats.getMp() - 1;
                    }
                    if (chr.getBuffedValue(MapleBuffStat.INFINITY) != null) {
                        mploss = 0;
                    }
                    chr.addMPHP(-hploss, -mploss);
                    //} else if (mpattack > 0) {
                    //    chr.addMPHP(-damage, -mpattack);
                } else {
                    mploss = (int) (damage * (chr.getBuffedValue(MapleBuffStat.MAGIC_GUARD).doubleValue() / 100.0)) + mpattack;
                    hploss = damage - mploss;
                    if (chr.getBuffedValue(MapleBuffStat.INFINITY) != null) {
                        mploss = 0;
                    } else if (mploss > stats.getMp()) {
                        mploss = stats.getMp();
                        hploss = damage - mploss + mpattack;
                    }
                    chr.addMPHP(-hploss, -mploss);
                }

            } else if (chr.getStat().mesoGuardMeso > 0) {
                damage = (damage % 2 == 0) ? damage / 2 : (damage / 2 + 1);

                final int mesoloss = (int) (damage * (chr.getBuffedValue(MapleBuffStat.MESOGUARD).doubleValue() / 100.0));
                if (chr.getMeso() < mesoloss) {
                    chr.gainMeso(-chr.getMeso(), false);
                    chr.cancelBuffStats(MapleBuffStat.MESOGUARD);
                } else {
                    chr.gainMeso(-mesoloss, false);
                }
                if (isDeadlyAttack && stats.getMp() > 1) {
                    mpattack = stats.getMp() - 1;
                }
                chr.addMPHP(-damage, -mpattack);
            } else if (isDeadlyAttack) {
                chr.addMPHP(stats.getHp() > 1 ? -(stats.getHp() - 1) : 0, stats.getMp() > 1 && !mpAttack ? -(stats.getMp() - 1) : 0);
            } else {
                chr.addMPHP(-damage, mpAttack ? 0 : -mpattack);
            }
            chr.handleBattleshipHP(-damage);
            if (chr.inPVP() && chr.getStat().getHPPercent() <= 20) {
                SkillFactory.getSkill(chr.getStat().getSkillByJob(93, chr.getJob())).getEffect(1).applyTo(chr);
            }
        }
        byte offset = 0;
        int offset_d = 0;
//        if (slea.available() == 1) {
//            offset = slea.readByte();
//            if (offset == 1 && slea.available() >= 4) {
//                offset_d = slea.readInt();
//            }
//            if (offset < 0 || offset > 2) {
//                offset = 0;
//            }
//        }
        //c.sendPacket(CWvsContext.enableActions());
        chr.getMap().broadcastMessage(chr, CField.damagePlayer(chr.getId(), type, damage, monsteridfrom, direction, skillid, pDMG, pPhysical, pID, pType, pPos, offset, offset_d, fake), false);
        // 20160803
        if (type == -1 && damage > 0) { // Bump damage
            final MapleMonster monster = chr.getMap().getMonsterByOid(oid);

            if (monster == null || !chr.isAlive() || chr.isHidden() || monster.getLinkCID() > 0) {
                return;
            }
            final byte selfd = monster.getStats().getSelfD();
            if (selfd != -1) {
                chr.getMap().killMonster(monster, chr, false, false, selfd);
            }
        }
    }

    public static final void UseItemEffect(final int itemId, final MapleClient c, final MapleCharacter chr) {
        final Item toUse = chr.getInventory(MapleInventoryType.CASH).findById(itemId);
        if (toUse == null || toUse.getItemId() != itemId || toUse.getQuantity() < 1) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (itemId != 5510000) {
            chr.setItemEffect(itemId);
        }
        chr.getMap().broadcastMessage(chr, CField.itemEffect(chr.getId(), itemId), false);
    }

    public static final void CancelItemEffect(final int id, final MapleCharacter chr) {
        chr.cancelEffect(MapleItemInformationProvider.getInstance().getItemEffect(-id), false, -1);
    }

    public static final void CancelBuffHandler(final int sourceid, final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null) {
            return;
        }
        final Skill skill = SkillFactory.getSkill(sourceid);

        if (skill.isChargeSkill()) {
            chr.setKeyDownSkill_Time(0);
            chr.getMap().broadcastMessage(chr, CField.skillCancel(chr, sourceid), false);
        } else {
            chr.cancelEffect(skill.getEffect(1), false, -1);
        }
    }

    public static final void CancelMech(final LittleEndianAccessor slea, final MapleCharacter chr) {
        if (chr == null) {
            return;
        }
        int sourceid = slea.readInt();
        if (sourceid % 10000 < 1000 && SkillFactory.getSkill(sourceid) == null) {
            sourceid += 1000;
        }
        final Skill skill = SkillFactory.getSkill(sourceid);
        if (skill == null) { //not sure
            return;
        }
        if (skill.isChargeSkill()) {
            chr.setKeyDownSkill_Time(0);
            chr.getMap().broadcastMessage(chr, CField.skillCancel(chr, sourceid), false);
        } else {
            chr.cancelEffect(skill.getEffect(slea.readByte()), false, -1);
        }
    }

    public static final void QuickSlot(final LittleEndianAccessor slea, final MapleCharacter chr) {
        if (slea.available() == 32 && chr != null) {
            final StringBuilder ret = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                ret.append(slea.readInt()).append(",");
            }
            ret.deleteCharAt(ret.length() - 1);
            chr.getQuestNAdd(MapleQuest.getInstance(GameConstants.QUICK_SLOT)).setCustomData(ret.toString());
        }
    }

    public static final void SkillEffect(final LittleEndianAccessor slea, final MapleCharacter chr) {
        final int skillId = slea.readInt();
        if (skillId >= 91000000) { //guild/recipe? no
            chr.getClient().sendPacket(CWvsContext.enableActions());
            return;
        }
        final byte level = slea.readByte();
        final short direction = slea.readShort();
        final byte unk = slea.readByte(); // Added on v.82

        final Skill skill = SkillFactory.getSkill(GameConstants.getLinkedAranSkill(skillId));
        if (chr == null || skill == null || chr.getMap() == null) {
            return;
        }
        final int skilllevel_serv = chr.getTotalSkillLevel(skill);

        if (skilllevel_serv > 0 && skilllevel_serv == level && (skillId == 33101005 || skill.isChargeSkill())) {
            chr.setKeyDownSkill_Time(System.currentTimeMillis());
            if (skillId == 33101005) {
                chr.setLinkMid(slea.readInt(), 0);
            }
            chr.getMap().broadcastMessage(chr, CField.skillEffect(chr, skillId, level, direction, unk), false);
        }
    }

    public static final void SpecialMove(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.hasBlockedInventory() || chr.getMap() == null || slea.available() < 9) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        slea.skip(4); // Tick
        int skillid = slea.readInt();
        if (skillid >= 91000000) { //guild/recipe? no
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        int skillLevel = slea.readByte();
        final Skill skill = SkillFactory.getSkill(skillid);
        if (skill == null || chr.getSkillLevel(skillid) != skillLevel || (GameConstants.isAngel(skillid) && (chr.getStat().equippedSummon % 10000) != (skillid % 10000)) || (chr.inPVP() && skill.isPVPDisabled())) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (GameConstants.isEventMap(chr.getMapId())) {
            for (MapleEventType t : MapleEventType.values()) {
                final MapleEvent e = ChannelServer.getInstance(chr.getClient().getChannel()).getEvent(t);
                if (e.isRunning() && !chr.isGM()) {
                    for (int i : e.getType().mapids) {
                        if (chr.getMapId() == i) {
                            chr.dropMessage(5, "You may not use that here.");
                            return; //non-skill cannot use
                        }
                    }
                }
            }
        }
        skillLevel = chr.getTotalSkillLevel(skillid);
        final MapleStatEffect effect = skill.getEffect(skillLevel);
        if (effect.isMPRecovery() && chr.getStat().getHp() < (chr.getStat().getMaxHp() / 100) * 10) { //less than 10% hp
            c.getPlayer().dropMessage(5, "You do not have the HP to use this skill.");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (effect.getCooldown(chr) > 0 && !chr.isGM()) {
            if (chr.skillisCooling(skillid)) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            if (skillid != 5221006) { // Battleship
                c.sendPacket(CField.skillCooldown(skillid, effect.getCooldown(chr)));
                chr.addCooldown(skillid, System.currentTimeMillis(), effect.getCooldown(chr) * 1000);
            }
        }
        //chr.checkFollow(); //not msea-like but ALEX'S WISHES
        switch (skillid) {
            case 1121001:
            case 1221001:
            case 1321001:
//                final byte number_of_mobs = slea.readByte();
//                slea.skip(3);
                for (int i = 0; i < 1/*number_of_mobs*/; i++) {
                    int mobId = slea.readInt();

                    final MapleMonster mob = chr.getMap().getMonsterByOid(mobId);
                    if (mob != null) {
//			chr.getMap().broadcastMessage(chr, CField.showMagnet(mobId, slea.readByte()), chr.getTruePosition());
                        mob.switchController(chr, mob.isControllerHasAggro());
                        mob.applyStatus(chr, new MonsterStatusEffect(MonsterStatus.STUN, 1, skillid, null, false), false, effect.getDuration(), true, effect);
                    }
                }
                chr.getMap().broadcastMessage(chr, EffectPacket.showBuffeffect(chr.getId(), skillid, 1, chr.getLevel(), skillLevel, slea.readByte()), chr.getTruePosition());
                c.sendPacket(CWvsContext.enableActions());
                break;
            default:
                Point pos = null;
                if (slea.available() == 5 || slea.available() == 7) {
                    pos = slea.readPos();
                }
                if (effect.isMagicDoor()) { // Mystic Door
                    if (!FieldLimitType.MysticDoor.check(chr.getMap().getFieldLimit())) {
                        if (chr.skillisCooling(2311002)) {
                            c.sendPacket(CWvsContext.enableActions());
                            return;
                        }
                        c.sendPacket(CField.skillCooldown(2311002, 2));
                        chr.addCooldown(2311002, System.currentTimeMillis(), 2 * 1000);
                        effect.applyTo(c.getPlayer(), pos);
                    } else {
                        c.sendPacket(CWvsContext.enableActions());
                    }

                } else {
                    final int mountid = MapleStatEffect.parseMountInfo(c.getPlayer(), skill.getId());
                    if (mountid != 0 && mountid != GameConstants.getMountItem(skill.getId(), c.getPlayer()) && !c.getPlayer().isIntern() && c.getPlayer().getBuffedValue(MapleBuffStat.MONSTER_RIDING) == null && c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -122) == null) {
                        if (!GameConstants.isMountItemAvailable(mountid, c.getPlayer().getJob())) {
                            c.sendPacket(CWvsContext.enableActions());
                            return;
                        }
                    }
                    effect.applyTo(c.getPlayer(), pos);
                }
                break;
        }
    }

    public static final void closeRangeAttack(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr, final boolean energy) {
        if (chr == null || (energy && chr.getBuffedValue(MapleBuffStat.ENERGY_CHARGE) == null && chr.getBuffedValue(MapleBuffStat.BODY_PRESSURE) == null && chr.getBuffedValue(MapleBuffStat.DARK_AURA) == null && chr.getBuffedValue(MapleBuffStat.TORNADO) == null && chr.getBuffedValue(MapleBuffStat.SUMMON) == null && chr.getBuffedValue(MapleBuffStat.RAINING_MINES) == null && chr.getBuffedValue(MapleBuffStat.TELEPORT_MASTERY) == null)) {
            return;
        }
        if (chr.hasBlockedInventory() || chr.getMap() == null) {
            return;
        }
        AttackInfo attack = DamageParse.parseDmgM(slea, chr);
        if (attack == null) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final boolean mirror = chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null;
        double maxdamage = chr.getStat().getCurrentMaxBaseDamage();
        final Item shield = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem((short) -10);
        int attackCount = (shield != null && shield.getItemId() / 10000 == 134 ? 2 : 1);
        int skillLevel = 0;
        MapleStatEffect effect = null;
        Skill skill = null;

        if (attack.skill != 0) {
            skill = SkillFactory.getSkill(GameConstants.getLinkedAranSkill(attack.skill));
            if (skill == null || (GameConstants.isAngel(attack.skill) && (chr.getStat().equippedSummon % 10000) != (attack.skill % 10000))) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            skillLevel = chr.getTotalSkillLevel(skill);
            effect = attack.getAttackEffect(chr, skillLevel, skill);
            if (effect == null) {
                return;
            }
            if (GameConstants.isEventMap(chr.getMapId())) {
                for (MapleEventType t : MapleEventType.values()) {
                    final MapleEvent e = ChannelServer.getInstance(chr.getClient().getChannel()).getEvent(t);
                    if (e.isRunning() && !chr.isGM()) {
                        for (int i : e.getType().mapids) {
                            if (chr.getMapId() == i) {
                                chr.dropMessage(5, "You may not use that here.");
                                return; //non-skill cannot use
                            }
                        }
                    }
                }
            }
            maxdamage *= (effect.getDamage() + chr.getStat().getDamageIncrease(attack.skill)) / 100.0;
            attackCount = effect.getAttackCount();

            if (effect.getCooldown(chr) > 0 && !chr.isGM() && !energy) {
                if (chr.skillisCooling(attack.skill)) {
                    c.sendPacket(CWvsContext.enableActions());
                    return;
                }
                c.sendPacket(CField.skillCooldown(attack.skill, effect.getCooldown(chr)));
                chr.addCooldown(attack.skill, System.currentTimeMillis(), effect.getCooldown(chr) * 1000);
            }
        }
        attack = DamageParse.Modify_AttackCrit(attack, chr, 1, effect);
        attackCount *= (mirror ? 2 : 1);
        if (!energy) {
            if ((chr.getMapId() == 109060000 || chr.getMapId() == 109060002 || chr.getMapId() == 109060004) && attack.skill == 0) {
                MapleSnowballs.hitSnowball(chr);
            }
            // handle combo orbconsume
            int numFinisherOrbs = 0;
            final Integer comboBuff = chr.getBuffedValue(MapleBuffStat.COMBO);

            if (isFinisher(attack.skill) > 0) { // finisher
                if (comboBuff != null) {
                    numFinisherOrbs = comboBuff - 1;
                }
                if (numFinisherOrbs <= 0) {
                    return;
                }
                chr.handleOrbconsume(isFinisher(attack.skill));
            }
            // handle sacrifice hp loss
            //after BIG BANG, TEMP
            if (attack.targets > 0 && attack.skill == 1211002) { // handle charged blow
                final int advcharge_level = chr.getSkillLevel(SkillFactory.getSkill(1220010));
                if (advcharge_level > 0) {
                    if (!SkillFactory.getSkill(1220010).getEffect(advcharge_level).makeChanceResult()) {
                        chr.cancelEffectFromBuffStat(MapleBuffStat.WK_CHARGE);
                        chr.cancelEffectFromBuffStat(MapleBuffStat.LIGHTNING_CHARGE);
                    }
                } else {
                    chr.cancelEffectFromBuffStat(MapleBuffStat.WK_CHARGE);
                    chr.cancelEffectFromBuffStat(MapleBuffStat.LIGHTNING_CHARGE);
                }
            }
        }
        chr.checkFollow();
        if (!chr.isHidden()) {
            chr.getMap().broadcastMessage(chr, CField.closeRangeAttack(chr.getId(), attack.tbyte, attack.skill, skillLevel, attack.display, attack.speed, attack.allDamage, energy, chr.getLevel(), chr.getStat().passive_mastery(), attack.unk, attack.charge), chr.getTruePosition());
        } else {
            chr.getMap().broadcastGMMessage(chr, CField.closeRangeAttack(chr.getId(), attack.tbyte, attack.skill, skillLevel, attack.display, attack.speed, attack.allDamage, energy, chr.getLevel(), chr.getStat().passive_mastery(), attack.unk, attack.charge), false);
        }
        DamageParse.applyAttack(attack, skill, c.getPlayer(), attackCount, maxdamage, effect, mirror ? AttackType.NON_RANGED_WITH_MIRROR : AttackType.NON_RANGED);
        WeakReference<MapleCharacter>[] clones = chr.getClones();
        for (int i = 0; i < clones.length; i++) {
            if (clones[i].get() != null) {
                final MapleCharacter clone = clones[i].get();
                final Skill skil2 = skill;
                final int skillLevel2 = skillLevel;
                final int attackCount2 = attackCount;
                final double maxdamage2 = maxdamage;
                final MapleStatEffect eff2 = effect;
                final AttackInfo attack2 = DamageParse.DivideAttack(attack, chr.isGM() ? 1 : 4);
                CloneTimer.getInstance().schedule(new Runnable() {

                    public void run() {
                        if (!clone.isHidden()) {
                            clone.getMap().broadcastMessage(CField.closeRangeAttack(clone.getId(), attack2.tbyte, attack2.skill, skillLevel2, attack2.display, attack2.speed, attack2.allDamage, energy, clone.getLevel(), clone.getStat().passive_mastery(), attack2.unk, attack2.charge));
                        } else {
                            clone.getMap().broadcastGMMessage(clone, CField.closeRangeAttack(clone.getId(), attack2.tbyte, attack2.skill, skillLevel2, attack2.display, attack2.speed, attack2.allDamage, energy, clone.getLevel(), clone.getStat().passive_mastery(), attack2.unk, attack2.charge), false);
                        }
                        DamageParse.applyAttack(attack2, skil2, chr, attackCount2, maxdamage2, eff2, mirror ? AttackType.NON_RANGED_WITH_MIRROR : AttackType.NON_RANGED);
                    }
                }, 500 * i + 500);
            }
        }
    }

    public static final void rangedAttack(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null) {
            return;
        }
        if (chr.hasBlockedInventory() || chr.getMap() == null) {
            return;
        }
        AttackInfo attack = DamageParse.parseDmgR(slea, chr);
        if (attack == null) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        int bulletCount = 1, skillLevel = 0;
        MapleStatEffect effect = null;
        Skill skill = null;
        boolean AOE = attack.skill == 4111004;
        boolean noBullet = (chr.getJob() >= 3500 && chr.getJob() <= 3512) || GameConstants.isCannon(chr.getJob()) || GameConstants.isMercedes(chr.getJob());
        if (attack.skill != 0) {
            skill = SkillFactory.getSkill(GameConstants.getLinkedAranSkill(attack.skill));
            if (skill == null || (GameConstants.isAngel(attack.skill) && (chr.getStat().equippedSummon % 10000) != (attack.skill % 10000))) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            skillLevel = chr.getTotalSkillLevel(skill);
            effect = attack.getAttackEffect(chr, skillLevel, skill);
            if (effect == null) {
                return;
            }
            if (GameConstants.isEventMap(chr.getMapId())) {
                for (MapleEventType t : MapleEventType.values()) {
                    final MapleEvent e = ChannelServer.getInstance(chr.getClient().getChannel()).getEvent(t);
                    if (e.isRunning() && !chr.isGM()) {
                        for (int i : e.getType().mapids) {
                            if (chr.getMapId() == i) {
                                chr.dropMessage(5, "You may not use that here.");
                                return; //non-skill cannot use
                            }
                        }
                    }
                }
            }
            switch (attack.skill) {
                case 13101005:
                case 21110004: // Ranged but uses attackcount instead
                case 14101006: // Vampure
                case 21120006:
                case 11101004:
                case 1077:
                case 1078:
                case 1079:
                case 11077:
                case 11078:
                case 11079:
                case 15111007:
                case 13111007: //Wind Shot
                case 33101007:
                case 33101002:
                case 33121002:
                case 33121001:
                case 21100004:
                case 21110011:
                case 21100007:
                case 21000004:
                case 5121002:
                case 4121003:
                case 4221003:
                case 3111004: // arrow rain
                case 3211004: // arrow eruption
                    AOE = true;
                    bulletCount = effect.getAttackCount();
                    break;
                case 35121005:
                case 35111004:
                case 35121013:
                    AOE = true;
                    bulletCount = 6;
                    break;
                default:
                    bulletCount = effect.getBulletCount();
                    break;
            }
            if (noBullet && effect.getBulletCount() < effect.getAttackCount()) {
                bulletCount = effect.getAttackCount();
            }
            if (effect.getCooldown(chr) > 0 && !chr.isGM() && ((attack.skill != 35111004 && attack.skill != 35121013) || chr.getBuffSource(MapleBuffStat.MECH_CHANGE) != attack.skill)) {
                if (chr.skillisCooling(attack.skill)) {
                    c.sendPacket(CWvsContext.enableActions());
                    return;
                }
                c.sendPacket(CField.skillCooldown(attack.skill, effect.getCooldown(chr)));
                chr.addCooldown(attack.skill, System.currentTimeMillis(), effect.getCooldown(chr) * 1000);
            }
        }
        attack = DamageParse.Modify_AttackCrit(attack, chr, 2, effect);
        final Integer ShadowPartner = chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER);
        if (ShadowPartner != null) {
            bulletCount *= 2;
        }
        int projectile = 0, visProjectile = 0;
        if (!AOE && chr.getBuffedValue(MapleBuffStat.SOULARROW) == null && !noBullet) {
            Item ipp = chr.getInventory(MapleInventoryType.USE).getItem(attack.slot);
            if (ipp == null) {
                return;
            }
            projectile = ipp.getItemId();

            if (attack.csstar > 0) {
                if (chr.getInventory(MapleInventoryType.CASH).getItem(attack.csstar) == null) {
                    return;
                }
                visProjectile = chr.getInventory(MapleInventoryType.CASH).getItem(attack.csstar).getItemId();
            } else {
                visProjectile = projectile;
            }
            // Handle bulletcount
            if (chr.getBuffedValue(MapleBuffStat.SPIRIT_CLAW) == null) {
                int bulletConsume = bulletCount;
                if (effect != null && effect.getBulletConsume() != 0) {
                    bulletConsume = effect.getBulletConsume() * (ShadowPartner != null ? 2 : 1);
                }
                if (chr.getJob() == 412 && bulletConsume > 0 && ipp.getQuantity() < MapleItemInformationProvider.getInstance().getSlotMax(projectile)) {
                    final Skill expert = SkillFactory.getSkill(4120010);
                    if (chr.getTotalSkillLevel(expert) > 0) {
                        final MapleStatEffect eff = expert.getEffect(chr.getTotalSkillLevel(expert));
                        if (eff.makeChanceResult()) {
                            ipp.setQuantity((short) (ipp.getQuantity() + 1));
                            c.sendPacket(InventoryPacket.updateInventorySlot(MapleInventoryType.USE, ipp, false));
                            bulletConsume = 0; //regain a star after using
                            c.sendPacket(InventoryPacket.getInventoryStatus());
                        }
                    }
                }
                if (bulletConsume > 0) {
                    if (!MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, projectile, bulletConsume, false, true)) {
                        chr.dropMessage(5, "You do not have enough arrows/bullets/stars.");
                        return;
                    }
                }
            }
        } else if (chr.getJob() >= 3500 && chr.getJob() <= 3512) {
            visProjectile = 2333000;
        } else if (GameConstants.isCannon(chr.getJob())) {
            visProjectile = 2333001;
        }
        double basedamage;
        int projectileWatk = 0;
        if (projectile != 0) {
            projectileWatk = MapleItemInformationProvider.getInstance().getWatkForProjectile(projectile);
        }
        final PlayerStats statst = chr.getStat();
        switch (attack.skill) {
            case 4001344: // Lucky Seven
            case 4121007: // Triple Throw
            case 14001004: // Lucky seven
            case 14111005: // Triple Throw
                basedamage = Math.max(statst.getCurrentMaxBaseDamage(), (float) ((float) ((statst.getTotalLuk() * 5.0f) * (statst.getTotalWatk() + projectileWatk)) / 100));
                break;
            case 4111004: // Shadow Meso
//		basedamage = ((effect.getMoneyCon() * 10) / 100) * effect.getProb(); // Not sure
                basedamage = 53000;
                break;
            default:
                basedamage = statst.getCurrentMaxBaseDamage();
                switch (attack.skill) {
                    case 3101005: // arrowbomb is hardcore like that
                        basedamage *= effect.getX() / 100.0;
                        break;
                }
                break;
        }
        if (effect != null) {
            basedamage *= (effect.getDamage() + statst.getDamageIncrease(attack.skill)) / 100.0;

            int money = effect.getMoneyCon();
            if (money != 0) {
                if (money > chr.getMeso()) {
                    money = chr.getMeso();
                }
                chr.gainMeso(-money, false);
            }
        }
        chr.checkFollow();
        if (!chr.isHidden()) {
            if (attack.skill == 3211006) {
                chr.getMap().broadcastMessage(chr, CField.strafeAttack(chr.getId(), attack.tbyte, attack.skill, skillLevel, attack.display, attack.speed, visProjectile, attack.allDamage, attack.position, chr.getLevel(), chr.getStat().passive_mastery(), attack.unk, chr.getTotalSkillLevel(3220010)), chr.getTruePosition());
            } else {
                chr.getMap().broadcastMessage(chr, CField.rangedAttack(chr.getId(), attack.tbyte, attack.skill, skillLevel, attack.display, attack.speed, visProjectile, attack.allDamage, attack.position, chr.getLevel(), chr.getStat().passive_mastery(), attack.unk), chr.getTruePosition());
            }
        } else if (attack.skill == 3211006) {
            chr.getMap().broadcastGMMessage(chr, CField.strafeAttack(chr.getId(), attack.tbyte, attack.skill, skillLevel, attack.display, attack.speed, visProjectile, attack.allDamage, attack.position, chr.getLevel(), chr.getStat().passive_mastery(), attack.unk, chr.getTotalSkillLevel(3220010)), false);
        } else {
            chr.getMap().broadcastGMMessage(chr, CField.rangedAttack(chr.getId(), attack.tbyte, attack.skill, skillLevel, attack.display, attack.speed, visProjectile, attack.allDamage, attack.position, chr.getLevel(), chr.getStat().passive_mastery(), attack.unk), false);
        }
        DamageParse.applyAttack(attack, skill, chr, bulletCount, basedamage, effect, ShadowPartner != null ? AttackType.RANGED_WITH_SHADOWPARTNER : AttackType.RANGED);

        WeakReference<MapleCharacter>[] clones = chr.getClones();
        for (int i = 0; i < clones.length; i++) {
            if (clones[i].get() != null) {
                final MapleCharacter clone = clones[i].get();
                final Skill skil2 = skill;
                final MapleStatEffect eff2 = effect;
                final double basedamage2 = basedamage;
                final int bulletCount2 = bulletCount;
                final int visProjectile2 = visProjectile;
                final int skillLevel2 = skillLevel;
                final AttackInfo attack2 = DamageParse.DivideAttack(attack, chr.isGM() ? 1 : 4);
                CloneTimer.getInstance().schedule(new Runnable() {

                    public void run() {
                        if (!clone.isHidden()) {
                            if (attack2.skill == 3211006) {
                                clone.getMap().broadcastMessage(CField.strafeAttack(clone.getId(), attack2.tbyte, attack2.skill, skillLevel2, attack2.display, attack2.speed, visProjectile2, attack2.allDamage, attack2.position, clone.getLevel(), clone.getStat().passive_mastery(), attack2.unk, chr.getTotalSkillLevel(3220010)));
                            } else {
                                clone.getMap().broadcastMessage(CField.rangedAttack(clone.getId(), attack2.tbyte, attack2.skill, skillLevel2, attack2.display, attack2.speed, visProjectile2, attack2.allDamage, attack2.position, clone.getLevel(), clone.getStat().passive_mastery(), attack2.unk));
                            }
                        } else if (attack2.skill == 3211006) {
                            clone.getMap().broadcastGMMessage(clone, CField.strafeAttack(clone.getId(), attack2.tbyte, attack2.skill, skillLevel2, attack2.display, attack2.speed, visProjectile2, attack2.allDamage, attack2.position, clone.getLevel(), clone.getStat().passive_mastery(), attack2.unk, chr.getTotalSkillLevel(3220010)), false);
                        } else {
                            clone.getMap().broadcastGMMessage(clone, CField.rangedAttack(clone.getId(), attack2.tbyte, attack2.skill, skillLevel2, attack2.display, attack2.speed, visProjectile2, attack2.allDamage, attack2.position, clone.getLevel(), clone.getStat().passive_mastery(), attack2.unk), false);
                        }
                        DamageParse.applyAttack(attack2, skil2, chr, bulletCount2, basedamage2, eff2, AttackType.RANGED);
                    }
                }, 500 * i + 500);
            }
        }
    }

    public static final void MagicDamage(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.hasBlockedInventory() || chr.getMap() == null) {
            return;
        }
        AttackInfo attack = DamageParse.parseDmgMa(slea, chr);
        if (attack == null) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final Skill skill = SkillFactory.getSkill(GameConstants.getLinkedAranSkill(attack.skill));
        if (skill == null || (GameConstants.isAngel(attack.skill) && (chr.getStat().equippedSummon % 10000) != (attack.skill % 10000))) {
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        final int skillLevel = chr.getTotalSkillLevel(skill);
        final MapleStatEffect effect = attack.getAttackEffect(chr, skillLevel, skill);
        if (effect == null) {
            return;
        }
        attack = DamageParse.Modify_AttackCrit(attack, chr, 3, effect);
        if (GameConstants.isEventMap(chr.getMapId())) {
            for (MapleEventType t : MapleEventType.values()) {
                final MapleEvent e = ChannelServer.getInstance(chr.getClient().getChannel()).getEvent(t);
                if (e.isRunning() && !chr.isGM()) {
                    for (int i : e.getType().mapids) {
                        if (chr.getMapId() == i) {
                            chr.dropMessage(5, "You may not use that here.");
                            return; //non-skill cannot use
                        }
                    }
                }
            }
        }
        double maxdamage = chr.getStat().getCurrentMaxBaseDamage() * (effect.getDamage() + chr.getStat().getDamageIncrease(attack.skill)) / 100.0;
        if (GameConstants.isPyramidSkill(attack.skill)) {
            maxdamage = 1;
        } else if (GameConstants.isBeginnerJob(skill.getId() / 10000) && skill.getId() % 10000 == 1000) {
            maxdamage = 40;
        }
        if (effect.getCooldown(chr) > 0 && !chr.isGM()) {
            if (chr.skillisCooling(attack.skill)) {
                c.sendPacket(CWvsContext.enableActions());
                return;
            }
            c.sendPacket(CField.skillCooldown(attack.skill, effect.getCooldown(chr)));
            chr.addCooldown(attack.skill, System.currentTimeMillis(), effect.getCooldown(chr) * 1000);
        }
        chr.checkFollow();
        if (!chr.isHidden()) {
            chr.getMap().broadcastMessage(chr, CField.magicAttack(chr.getId(), attack.tbyte, attack.skill, skillLevel, attack.display, attack.speed, attack.allDamage, attack.charge, chr.getLevel(), attack.unk), chr.getTruePosition());
        } else {
            chr.getMap().broadcastGMMessage(chr, CField.magicAttack(chr.getId(), attack.tbyte, attack.skill, skillLevel, attack.display, attack.speed, attack.allDamage, attack.charge, chr.getLevel(), attack.unk), false);
        }
        DamageParse.applyAttackMagic(attack, skill, c.getPlayer(), effect, maxdamage);
        WeakReference<MapleCharacter>[] clones = chr.getClones();
        for (int i = 0; i < clones.length; i++) {
            if (clones[i].get() != null) {
                final MapleCharacter clone = clones[i].get();
                final Skill skil2 = skill;
                final MapleStatEffect eff2 = effect;
                final double maxd = maxdamage;
                final int skillLevel2 = skillLevel;
                final AttackInfo attack2 = DamageParse.DivideAttack(attack, chr.isGM() ? 1 : 4);
                CloneTimer.getInstance().schedule(new Runnable() {

                    public void run() {
                        if (!clone.isHidden()) {
                            clone.getMap().broadcastMessage(CField.magicAttack(clone.getId(), attack2.tbyte, attack2.skill, skillLevel2, attack2.display, attack2.speed, attack2.allDamage, attack2.charge, clone.getLevel(), attack2.unk));
                        } else {
                            clone.getMap().broadcastGMMessage(clone, CField.magicAttack(clone.getId(), attack2.tbyte, attack2.skill, skillLevel2, attack2.display, attack2.speed, attack2.allDamage, attack2.charge, clone.getLevel(), attack2.unk), false);
                        }
                        DamageParse.applyAttackMagic(attack2, skil2, chr, eff2, maxd);
                    }
                }, 500 * i + 500);
            }
        }
    }

    public static final void DropMeso(final int meso, final MapleCharacter chr) {
        if (!chr.isAlive() || (meso < 10 || meso > 50000) || (meso > chr.getMeso())) {
            chr.getClient().sendPacket(CWvsContext.enableActions());
            return;
        }
        chr.gainMeso(-meso, false, true);
        chr.getMap().spawnMesoDrop(meso, chr.getTruePosition(), chr, chr, true, (byte) 0);
        chr.getCheatTracker().checkDrop(true);
    }

    public static final void ChangeEmotion(final int emote, final MapleCharacter chr) {
        if (emote > 7) {
            final int emoteid = 5159992 + emote;
            final MapleInventoryType type = GameConstants.getInventoryType(emoteid);
            if (chr.getInventory(type).findById(emoteid) == null) {
                chr.getCheatTracker().registerOffense(CheatingOffense.USING_UNAVAILABLE_ITEM, Integer.toString(emoteid));
                return;
            }
        }
        if (emote > 0 && chr != null && chr.getMap() != null && !chr.isHidden()) { //O_o
            chr.getMap().broadcastMessage(chr, CField.facialExpression(chr, emote), false);
            WeakReference<MapleCharacter>[] clones = chr.getClones();
            for (int i = 0; i < clones.length; i++) {
                if (clones[i].get() != null) {
                    final MapleCharacter clone = clones[i].get();
                    CloneTimer.getInstance().schedule(new Runnable() {

                        public void run() {
                            clone.getMap().broadcastMessage(CField.facialExpression(clone, emote));
                        }
                    }, 500 * i + 500);
                }
            }
        }
    }

    public static final void Heal(final LittleEndianAccessor slea, final MapleCharacter chr) {
        if (chr == null) {
            return;
        }
        slea.readByte();
        slea.readShort();
        slea.readByte();
        int healHP = slea.readShort();
        int healMP = slea.readShort();

        final PlayerStats stats = chr.getStat();

        if (stats.getHp() <= 0) {
            return;
        }
//        final long now = System.currentTimeMillis();
        if (healHP != 0 /*&& chr.canHP(now + 1000)*/) {
//            if (healHP > stats.getHealHP()) {
//                //chr.getCheatTracker().registerOffense(CheatingOffense.REGEN_HIGH_HP, String.valueOf(healHP));
//                healHP = (int) stats.getHealHP();
//            }
            chr.addHP(healHP);
        }
        if (healMP != 0 /*&& chr.canMP(now + 1000)*/) { //just for lag
//            if (healMP > stats.getHealMP()) {
//                //chr.getCheatTracker().registerOffense(CheatingOffense.REGEN_HIGH_MP, String.valueOf(healMP));
//                healMP = (int) stats.getHealMP();
//            }
            chr.addMP(healMP);
        }
    }

    public static final void MovePlayer(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        //System.out.println("Move Player " + slea.toString());
        slea.skip(1); // portal count
        slea.skip(4); // crc?
        if (chr == null) {
            return;
        }
        final Point Original_Pos = chr.getPosition(); // 4 bytes Added on v.80 MSEA
        List<LifeMovementFragment> res;
        try {
            res = MovementParse.parseMovement(slea, 1);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("AIOBE Type1:\n" + slea.toString(true));
            return;
        }

        if (res != null && c.getPlayer().getMap() != null) {
            if (slea.available() < 11 || slea.available() > 26) { // estimation, should be exact 18
                return;
            }
            final MapleMap map = c.getPlayer().getMap();

            if (chr.isHidden()) {
                chr.setLastRes(res);
                c.getPlayer().getMap().broadcastGMMessage(chr, CField.movePlayer(chr.getId(), res, Original_Pos), false);
            } else {
                c.getPlayer().getMap().broadcastMessage(c.getPlayer(), CField.movePlayer(chr.getId(), res, Original_Pos), false);
            }

            MovementParse.updatePosition(res, chr, 0);
            final Point pos = chr.getTruePosition();
            map.movePlayer(chr, pos);
            if (chr.getFollowId() > 0 && chr.isFollowOn() && chr.isFollowInitiator()) {
                final MapleCharacter fol = map.getCharacterById(chr.getFollowId());
                if (fol != null) {
                    final Point original_pos = fol.getPosition();
                    fol.getClient().sendPacket(CField.moveFollow(Original_Pos, original_pos, pos, res));
                    MovementParse.updatePosition(res, fol, 0);
                    map.movePlayer(fol, pos);
                    map.broadcastMessage(fol, CField.movePlayer(fol.getId(), res, original_pos), false);
                } else {
                    chr.checkFollow();
                }
            }
            WeakReference<MapleCharacter>[] clones = chr.getClones();
            for (int i = 0; i < clones.length; i++) {
                if (clones[i].get() != null) {
                    final MapleCharacter clone = clones[i].get();
                    final List<LifeMovementFragment> res3 = res;
                    CloneTimer.getInstance().schedule(new Runnable() {

                        public void run() {
                            try {
                                if (clone.getMap() == map) {
                                    if (clone.isHidden()) {
                                        map.broadcastGMMessage(clone, CField.movePlayer(clone.getId(), res3, Original_Pos), false);
                                    } else {
                                        map.broadcastMessage(clone, CField.movePlayer(clone.getId(), res3, Original_Pos), false);
                                    }
                                    MovementParse.updatePosition(res3, clone, 0);
                                    map.movePlayer(clone, pos);
                                }
                            } catch (Exception e) {
                                //very rarely swallowed
                            }
                        }
                    }, 500 * i + 500);
                }
            }
            int count = c.getPlayer().getFallCounter();
            final boolean samepos = pos.y > c.getPlayer().getOldPosition().y && Math.abs(pos.x - c.getPlayer().getOldPosition().x) < 5;
            if (samepos && (pos.y > (map.getBottom() + 250) || map.getFootholds().findBelow(pos) == null)) {
                if (count > 5) {
                    c.getPlayer().changeMap(map, map.getPortal(0));
                    c.getPlayer().setFallCounter(0);
                } else {
                    c.getPlayer().setFallCounter(++count);
                }
            } else if (count > 0) {
                c.getPlayer().setFallCounter(0);
            }
            c.getPlayer().setOldPosition(pos);
            if (!samepos && c.getPlayer().getBuffSource(MapleBuffStat.DARK_AURA) == 32120000) { //dark aura
                c.getPlayer().getStatForBuff(MapleBuffStat.DARK_AURA).applyMonsterBuff(c.getPlayer());
            } else if (!samepos && c.getPlayer().getBuffSource(MapleBuffStat.YELLOW_AURA) == 32120001) { //yellow aura
                c.getPlayer().getStatForBuff(MapleBuffStat.YELLOW_AURA).applyMonsterBuff(c.getPlayer());
            }
            final PokemonMap mapp = BattleConstants.getMap(c.getPlayer().getMapId());
            if (!samepos && c.getPlayer().getBattler(0) != null && mapp != null && !c.getPlayer().isHidden() && !c.getPlayer().hasBlockedInventory() && Randomizer.nextInt(c.getPlayer().getBattler(0).getAbility() == PokemonAbility.Stench ? 20 : (c.getPlayer().getBattler(0).getAbility() == PokemonAbility.Illuminate ? 5 : 10)) == 0) { //1/20 chance of encounter
                LinkedList<Pair<Integer, Integer>> set = BattleConstants.getMobs(mapp);
                if (set == null) { // not loaded
                    return;
                }
                Collections.shuffle(set);
                int resulting = 0;
                for (Pair<Integer, Integer> i : set) {
                    if (Randomizer.nextInt(i.right) == 0) { //higher evolutions have lower chance
                        resulting = i.left;
                        break;
                    }
                }
                if (resulting > 0) {
                    final PokemonBattle wild = new PokemonBattle(c.getPlayer(), resulting, mapp);
                    c.getPlayer().changeMap(wild.getMap(), wild.getMap().getPortal(mapp.portalId));
                    if (c.getPlayer() != null) { //... ugh hate dcs
                        c.getPlayer().setBattle(wild);
                        wild.initiate(c.getPlayer(), mapp);
                    }
                }
            }
        }
    }

    public static final void ChangeMapSpecial(final String portal_name, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null) {
            return;
        }
        final MaplePortal portal = chr.getMap().getPortal(portal_name);
//	slea.skip(2);

        if (portal != null && !chr.hasBlockedInventory()) {
            portal.enterPortal(c);
        } else {
            c.sendPacket(CWvsContext.enableActions());
        }
    }

    public static final void ChangeMap(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null) {
            return;
        }
        if (slea.available() != 0) {
            slea.readByte(); // 1 = from dying 2 = regular portals
            final int targetid = slea.readInt(); // FF FF FF FF
            MaplePortal portal = null;
            try {
                portal = chr.getMap().getPortal(slea.readMapleAsciiString());
            } catch (Exception ex) {
            }
            slea.skip(1);
            final boolean wheel = slea.readShort() > 0 && !MapConstants.isEventMap(chr.getMapId()) && chr.haveItem(5510000, 1, false, true);

            if (targetid != -1 && !chr.isAlive()) {
                chr.setStance(0);
                if (chr.getEventInstance() != null && chr.getEventInstance().revivePlayer(chr) && chr.isAlive()) {
                    return;
                }
                if (chr.getPyramidSubway() != null) {
                    chr.getStat().setHp((short) 50, chr);
                    chr.getPyramidSubway().fail(chr);
                    return;
                }

                if (!wheel) {
                    chr.getStat().setHp((short) 50, chr);

                    final MapleMap to = chr.getMap().getReturnMap();
                    chr.changeMap(to, to.getPortal(0));
                } else {
                    //   c.sendPacket(MTSCSPacket.useWheel((byte) (chr.getInventory(MapleInventoryType.CASH).countById(5510000) - 1)));
                    chr.getStat().setHp((chr.getStat().getMaxHp() / 100) * 40, chr);
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, 5510000, 1, true, false);

                    final MapleMap to = chr.getMap();
                    chr.changeMap(to, to.getPortal(0));
                }
            } else if (targetid != -1 && chr.isGM()) {
                final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                chr.changeMap(to, to.getPortal(0));

            } else if (targetid != -1 && !chr.isGM()) {
                final int divi = chr.getMapId() / 100;
                if (divi == 9130401) { // Only allow warp if player is already in Intro map, or else = hack

                    if (targetid == 130000000 || targetid / 100 == 9130401) { // Cygnus introduction
                        final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                        chr.changeMap(to, to.getPortal(0));
                    }
                } else if (divi == 9140900) { // Aran Introduction
                    if (targetid == 914090011 || targetid == 914090012 || targetid == 914090013 || targetid == 140090000) {
                        final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                        chr.changeMap(to, to.getPortal(0));
                    }
                } else if (divi == 9140901 && targetid == 140000000) {
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    c.sendPacket(CWvsContext.enableActions());
                    final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                    chr.changeMap(to, to.getPortal(0));
                } else if (divi == 9140902 && (targetid == 140030000 || targetid == 140000000)) { //thing is. dont really know which one!
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    c.sendPacket(CWvsContext.enableActions());
                    final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                    chr.changeMap(to, to.getPortal(0));
                } else if (divi == 9000900 && targetid / 100 == 9000900 && targetid > chr.getMapId()) {
                    final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                    chr.changeMap(to, to.getPortal(0));
                } else if (divi / 1000 == 9000 && targetid / 100000 == 9000) {
                    if (targetid < 900090000 || targetid > 900090004) { //1 movie
                        c.sendPacket(UIPacket.IntroDisableUI(false));
                        c.sendPacket(UIPacket.IntroLock(false));
                        c.sendPacket(CWvsContext.enableActions());
                    }
                    final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                    chr.changeMap(to, to.getPortal(0));
                } else if (divi / 10 == 1020 && targetid == 1020000) { // Adventurer movie clip Intro
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    c.sendPacket(CWvsContext.enableActions());
                    final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                    chr.changeMap(to, to.getPortal(0));

                } else if (chr.getMapId() == 900090101 && targetid == 100030100) {
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    c.sendPacket(CWvsContext.enableActions());
                    final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                    chr.changeMap(to, to.getPortal(0));
                } else if (chr.getMapId() == 2010000 && targetid == 104000000) {
                    c.sendPacket(UIPacket.IntroDisableUI(false));
                    c.sendPacket(UIPacket.IntroLock(false));
                    c.sendPacket(CWvsContext.enableActions());
                    final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                    chr.changeMap(to, to.getPortal(0));
                } else if (chr.getMapId() == 106020001 || chr.getMapId() == 106020502) {
                    if (targetid == (chr.getMapId() - 1)) {
                        c.sendPacket(UIPacket.IntroDisableUI(false));
                        c.sendPacket(UIPacket.IntroLock(false));
                        c.sendPacket(CWvsContext.enableActions());
                        final MapleMap to = ChannelServer.getInstance(c.getChannel()).getMapFactory().getMap(targetid);
                        chr.changeMap(to, to.getPortal(0));
                    }
                }
            } else if (portal != null) {
                portal.enterPortal(c);
            } else {
                c.sendPacket(CWvsContext.enableActions());
            }
        }
    }

    public static final void InnerPortal(final LittleEndianAccessor slea, final MapleClient c, final MapleCharacter chr) {
        if (chr == null || chr.getMap() == null) {
            return;
        }
        final MaplePortal portal = chr.getMap().getPortal(slea.readMapleAsciiString());
        final int toX = slea.readShort();
        final int toY = slea.readShort();
//	slea.readShort(); // Original X pos
//	slea.readShort(); // Original Y pos

        if (portal == null) {
            return;
        } else if (portal.getPosition().distanceSq(chr.getTruePosition()) > 22500 && !chr.isGM()) {
            chr.getCheatTracker().registerOffense(CheatingOffense.USING_FARAWAY_PORTAL);
            return;
        }
        chr.getMap().movePlayer(chr, new Point(toX, toY));
        chr.checkFollow();
    }

    public static final void snowBall(LittleEndianAccessor slea, MapleClient c) {
        //B2 00
        //01 [team]
        //00 00 [unknown]
        //89 [position]
        //01 [stage]
        c.sendPacket(CWvsContext.enableActions());
        //empty, we do this in closerange
    }

    public static final void leftKnockBack(LittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer().getMapId() / 10000 == 10906) { //must be in snowball map or else its like infinite FJ
            c.sendPacket(CField.leftKnockBack());
            c.sendPacket(CWvsContext.enableActions());
        }
    }

    public static final void ReIssueMedal(LittleEndianAccessor slea, MapleClient c, MapleCharacter chr) {
        final MapleQuest q = MapleQuest.getInstance(slea.readShort());
        final int itemid = q.getMedalItem();
        if (itemid != slea.readInt() || itemid <= 0 || q == null || chr.getQuestStatus(q.getId()) != 2) {
            c.sendPacket(UIPacket.reissueMedal(itemid, 4));
            return;
        }
        if (chr.haveItem(itemid, 1, true, true)) {
            c.sendPacket(UIPacket.reissueMedal(itemid, 3));
            return;
        }
        if (!MapleInventoryManipulator.checkSpace(c, itemid, (short) 1, "")) {
            c.sendPacket(UIPacket.reissueMedal(itemid, 2));
            return;
        }
        if (chr.getMeso() < 100) {
            c.sendPacket(UIPacket.reissueMedal(itemid, 1));
            return;
        }
        chr.gainMeso(-100, true, true);
        MapleInventoryManipulator.addById(c, itemid, (short) 1, "Redeemed item through medal quest " + q.getId() + " on " + FileoutputUtil.CurrentReadable_Date());
        c.sendPacket(UIPacket.reissueMedal(itemid, 0));
    }

    public static final void handleLogout(final LittleEndianAccessor slea, MapleClient c) {
        String ACname = null;
        try {
            ACname = slea.readMapleAsciiString();
        } catch (NegativeArraySizeException ex) {

        }
        if (ACname != null) {
            int LoginState = 0;
            int id = 0;
            String ip = "";
            StringBuilder sb = new StringBuilder();
            sb.append("select id, loggedin from accounts where name = ?");
            if (c != null) {
                ip = c.getSessionIPAddress();
                sb.append(" and SessionIP = ?");
            }
            try (PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement(sb.toString())) {
                ps.setString(1, ACname);
                if (c != null) {
                    ps.setString(2, ip);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        LoginState = rs.getInt("loggedin");
                        id = rs.getInt("id");
                    }
                }
            } catch (Exception ex) {
                System.err.println("[handleLogout] 處理登出出錯" + ex);
            }
            if (c != null) {
                c.setAccID(id);
                if (LoginState > 0) {
                    c.updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN, c.getSessionIPAddress());
                }
            }
        }
    }
}
