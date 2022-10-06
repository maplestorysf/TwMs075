package handling.channel.handler;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import client.Skill;
import constants.GameConstants;
import client.inventory.Item;
import client.MapleBuffStat;
import client.MapleCharacter;
import client.inventory.MapleInventoryType;
import client.PlayerStats;
import client.SkillFactory;
import client.anticheat.CheatTracker;
import client.anticheat.CheatingOffense;
import client.status.MonsterStatus;
import client.status.MonsterStatusEffect;
import constants.ServerConstants;
import handling.world.World;
import java.util.Collections;
import java.util.Map;
import server.MapleStatEffect;
import server.Randomizer;
import server.life.Element;
import server.life.ElementalEffectiveness;
import server.life.MapleMonster;
import server.life.MapleMonsterStats;
import server.maps.MapleMap;
import server.maps.MapleMapItem;
import server.maps.MapleMapObject;
import server.maps.MapleMapObjectType;
import tools.packet.CField;
import tools.AttackPair;
import tools.FileoutputUtil;
import tools.Pair;
import tools.data.LittleEndianAccessor;
import tools.packet.CWvsContext;

public class DamageParse {

    public static void applyAttack(final AttackInfo attack, final Skill theSkill, final MapleCharacter player, int attackCount, final double maxDamagePerMonster, final MapleStatEffect effect, final AttackType attack_type) {

        // 玩家是否存活
        if (!player.isAlive()) {
            player.getCheatTracker().registerOffense(CheatingOffense.ATTACKING_WHILE_DEAD);
            return;
        }

        // 攻擊速度檢測 [尚未完成]
        if (attack.real) {
            player.getCheatTracker().checkAttack(attack.skill, attack.lastAttackTickCount);
        }

        if (attack.skill != 0) {
            // 使用技能
            if (effect == null) {
                player.getClient().sendPacket(CWvsContext.enableActions());
                return;
            }

            /* 檢測打怪數量 */
            if (attack.targets > effect.getMobCount()) {
                if (player.hasGmLevel(1)) {
                    player.dropMessage("打怪數量異常,技能代碼: " + attack.skill + " 封包怪物量 : " + attack.targets + " 服務端怪物量 :" + effect.getMobCount());
                } else {
                    player.getCheatTracker().registerOffense(CheatingOffense.攻擊怪物數量異常, " 玩家: " + player.getName() + "(" + player.getLevel() + ") 地圖: " + player.getMapId() + "技能代碼: " + attack.skill + " 技能等級: " + player.getSkillLevel(effect.getSourceId()) + " 封包怪物量 : " + attack.targets + " 服務端怪物量 :" + effect.getMobCount());
                    FileoutputUtil.logToFile("外掛/打怪數量異常.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家: " + player.getName() + "(" + player.getLevel() + ") 地圖: " + player.getMapId() + "技能代碼: " + attack.skill + " 技能等級: " + player.getSkillLevel(effect.getSourceId()) + " 封包怪物量 : " + attack.targets + " 服務端怪物量 :" + effect.getMobCount());
                    World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM 密語系統] " + player.getName() + " (等級 " + player.getLevel() + ") " + "攻擊怪物數量異常。 " + "封包怪物量 " + attack.targets + " 服務端怪物量 " + effect.getMobCount() + " 技能ID " + attack.skill));
                }
                return;
            }

            /* 檢測技能次數 */
            int last = attackCount;
            boolean mirror_fix = false;
            if (player.getJob() >= 411 && player.getJob() <= 412) {
                mirror_fix = true;
            }
            if (mirror_fix) {
                last *= 2;
            }
            if (attack.hits > last) {
                if (player.hasGmLevel(1)) {
                    player.dropMessage("攻擊次數異常攻擊次數 " + attack.hits + " 正確攻擊次數 " + last + " 技能ID " + attack.skill);
                } else {
                    player.getCheatTracker().registerOffense(CheatingOffense.技能攻擊次數異常, "玩家: " + player.getName() + "(" + player.getLevel() + ") 地圖: " + player.getMapId() + " 技能代碼: " + attack.skill + " 技能等級: " + player.getSkillLevel(attack.skill) + " 攻擊次數 : " + attack.hits + " 正常攻擊次數 :" + last);
                    World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM 密語系統] " + player.getName() + " (等級 " + player.getLevel() + ") 攻擊次數異常已自動封鎖。 玩家攻擊次數 " + attack.hits + " 正確次數 " + last + " 技能ID " + attack.skill));
                    FileoutputUtil.logToFile("外掛/技能攻擊次數.txt", "\r\n" + FileoutputUtil.CurrentReadable_TimeGMT() + "玩家: " + player.getName() + "(" + player.getLevel() + ") 地圖: " + player.getMapId() + " 技能代碼: " + attack.skill + " 技能等級: " + player.getSkillLevel(attack.skill) + " 攻擊次數 : " + attack.hits + " 正常攻擊次數 :" + last);
                }
                return;
            }
        }

        if (attack.hits > 0 && attack.targets > 0) {
            // Don't ever do this. it's too expensive.
            if (!player.getStat().checkEquipDurabilitys(player, -1)) { //i guess this is how it works ?
                player.dropMessage(5, "An item has run out of durability but has no inventory room to go to.");
                return;
            } //lol
        }

        int totDamage = 0;
        final MapleMap map = player.getMap();

        if (attack.skill == 4211006) { // 楓幣炸彈
            for (AttackPair oned : attack.allDamage) {
                if (oned.attack != null) {
                    continue;
                }
                final MapleMapObject mapobject = map.getMapObject(oned.objectid, MapleMapObjectType.ITEM);

                if (mapobject != null) {
                    final MapleMapItem mapitem = (MapleMapItem) mapobject;
                    mapitem.getLock().lock();
                    try {
                        if (mapitem.getMeso() > 0) {
                            if (mapitem.isPickedUp()) {
                                return;
                            }
                            map.removeMapObject(mapitem);
                            map.broadcastMessage(CField.explodeDrop(mapitem.getObjectId()));
                            mapitem.setPickedUp(true);
                        } else {
                            player.getCheatTracker().registerOffense(CheatingOffense.楓幣炸彈異常, " OID道具為 : " + mapitem.getItemId());
                            return;
                        }
                    } finally {
                        mapitem.getLock().unlock();
                    }
                } else {
                    player.getCheatTracker().registerOffense(CheatingOffense.楓幣炸彈異常, " OID道具不存在");
                    return; // etc explosion, exploding nonexistant things, etc.
                }
            }
        }
        int fixeddmg, totDamageToOneMonster = 0;
        long hpMob = 0;
        final PlayerStats stats = player.getStat();

        int CriticalDamage = stats.passive_sharpeye_percent();
        int ShdowPartnerAttackPercentage = 0;
        if (attack_type == AttackType.RANGED_WITH_SHADOWPARTNER || attack_type == AttackType.NON_RANGED_WITH_MIRROR) {
            final MapleStatEffect shadowPartnerEffect = player.getStatForBuff(MapleBuffStat.SHADOWPARTNER);
            if (shadowPartnerEffect != null) {
                ShdowPartnerAttackPercentage += shadowPartnerEffect.getX();
            }
            attackCount /= 2; // hack xD
        }

        // 隱分身爆擊
        ShdowPartnerAttackPercentage *= (CriticalDamage + 100) / 100;

        byte overallAttackCount; // Tracking of Shadow Partner additional damage.
        double maxDamagePerHit = 0;
        MapleMonster monster;
        MapleMonsterStats monsterstats;
        boolean Tempest;

        for (final AttackPair oned : attack.allDamage) {
            monster = map.getMonsterByOid(oned.objectid);

            if (monster != null && monster.getLinkCID() <= 0) {
                totDamageToOneMonster = 0;
                hpMob = monster.getMobMaxHp();
                monsterstats = monster.getStats();
                fixeddmg = monsterstats.getFixedDamage();
                Tempest = attack.skill == 1221011;
                maxDamagePerHit = CalculateMaxWeaponDamagePerHit(player, monster, attack, theSkill, effect, maxDamagePerMonster, CriticalDamage);
                overallAttackCount = 0; // Tracking of Shadow Partner additional damage.
                Integer eachd;
                for (Pair<Integer, Boolean> eachde : oned.attack) {
                    eachd = eachde.left;
                    overallAttackCount++;
                    /* 確認是否超過預計傷害*/
                    if (!GameConstants.isElseSkill(attack.skill)) {
                        int atk = 200000;
                        boolean ban = false;
                        if (player.getLevel() < 6) {
                            atk = 25;
                        } else if (player.getLevel() < 10) {
                            atk = 250;
                        } else if (player.getLevel() <= 20) {
                            atk = 1000;
                        } else if (player.getLevel() <= 30) {
                            atk = 2500;
                        } else if (player.getLevel() <= 60) {
                            atk = 8000;
                        }
                        if (eachd >= atk && eachd > maxDamagePerHit) {
                            ban = true;
                        }
                        if (eachd == monster.getMobMaxHp()) {
                            ban = false;
                        }
                        if (player.hasGmLevel(1)) {
                            ban = false;
                        }
                        if (ban) {
                            boolean apple = false;
                            if (player.getBuffSource(MapleBuffStat.WATK) == 2022179 || player.getBuffSource(MapleBuffStat.MATK) == 2022179 || player.getBuffSource(MapleBuffStat.WDEF) == 2022179) {
                                apple = true;
                            }
                            FileoutputUtil.logToFile("封鎖/傷害異常.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家<" + player.getLevel() + ">: " + player.getName() + " 怪物 " + monster.getId() + " 地圖: " + player.getMapId() + " 技能代碼: " + attack.skill + " 最高傷害: " + atk + " 本次傷害 :" + eachd + " 預計傷害: " + (int) maxDamagePerHit + "是否為BOSS: " + monster.getStats().isBoss() + " 紫色蘋果: " + apple);
                            World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, "[封鎖系統] " + player.getName() + " 因為傷害異常而被管理員永久停權。"));
                            World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM 密語系統] " + player.getName() + " (等級 " + player.getLevel() + ") " + "傷害異常。 " + "最高傷害 " + atk + " 本次傷害 " + eachd + " 技能ID " + attack.skill));
                            player.ban(player.getName() + "傷害異常", true, true, false);
                            player.getClient().getSession().close();
                            return;
                        }

                    }

                    // 隱分身攻擊傷害處理
                    if (overallAttackCount - 1 == attackCount) {
                        double min = maxDamagePerHit;
                        double shadow = (ShdowPartnerAttackPercentage == 0D ? 1D : ShdowPartnerAttackPercentage);
                        if (ShdowPartnerAttackPercentage != 0) {
                            min = maxDamagePerHit / 100.0D;
                        }
                        double dam = (monsterstats.isBoss() ? stats.bossdam_r : stats.dam_r);
                        double last = min * (shadow * dam / 100.0D);
                        maxDamagePerHit = last;
                    }

                    if (fixeddmg != -1) {
                        if (monsterstats.getOnlyNoramlAttack()) {
                            eachd = attack.skill != 0 ? 0 : fixeddmg;
                        } else {
                            eachd = fixeddmg;
                        }
                    } else if (monsterstats.getOnlyNoramlAttack()) {
                        eachd = attack.skill != 0 ? 0 : Math.min(eachd, (int) maxDamagePerHit);  // 轉換為伺服器計算的傷害
                    } else if (!player.isGM()) {

                        if (Tempest) {
                            if (eachd > monster.getMobMaxHp()) {
                                player.getCheatTracker().registerOffense(CheatingOffense.傷害過高, " 技能: " + attack.skill + " 怪物: " + monster.getId() + " 最大Hp: " + monster.getMobMaxHp() + " 本次傷害: " + eachd);
                                eachd = (int) Math.min(monster.getMobMaxHp(), Integer.MAX_VALUE);
                            }

                        } else if (!monster.isBuffed(MonsterStatus.DAMAGE_IMMUNITY) && !monster.isBuffed(MonsterStatus.MAGIC_IMMUNITY) && !monster.isBuffed(MonsterStatus.MAGIC_DAMAGE_REFLECT)) {
                            if (eachd > maxDamagePerHit) {
                                player.getCheatTracker().registerOffense(CheatingOffense.傷害過高, "[傷害: " + eachd + ", 預期: " + (long) maxDamagePerHit + ", 怪物: " + monster.getId() + "] [職業: " + player.getJob() + ", 等級: " + player.getLevel() + ", 技能: " + attack.skill + "]");

                                // 確認相同傷害
                                if (attack.real) {
                                    player.getCheatTracker().checkSameDamage(eachd, maxDamagePerHit);
                                }

                                if (eachd > maxDamagePerHit * 2) {
                                    if (ServerConstants.LOG_DAMAGE) {
                                        FileoutputUtil.logToFile("紀錄/傷害計算/傷害計算修正.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家: " + player.getName() + "(" + player.getLevel() + ") 職業: " + player.getJob() + " 怪物:" + monster.getId() + " 技能: " + attack.skill + " 封包傷害 :" + eachd + " 預計傷害 :" + (int) maxDamagePerHit + " 是否為BOSS: " + monster.getStats().isBoss());
                                    }
                                    player.getCheatTracker().registerOffense(CheatingOffense.傷害過高2, "[傷害: " + eachd + ", 預期: " + (long) maxDamagePerHit + ", 怪物: " + monster.getId() + "] [職業: " + player.getJob() + ", 等級: " + player.getLevel() + ", 技能: " + attack.skill + "]");
                                    eachd = (int) (maxDamagePerHit * 2); // 轉換為伺服器計算的傷害
                                }

                            }
                        } else if (eachd > maxDamagePerHit) {
                            if (ServerConstants.LOG_DAMAGE) {
                                FileoutputUtil.logToFile("紀錄/傷害計算/傷害計算修正.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家: " + player.getName() + "(" + player.getLevel() + ") 職業: " + player.getJob() + " 怪物:" + monster.getId() + " 技能: " + attack.skill + " 封包傷害 :" + eachd + " 預計傷害 :" + (int) maxDamagePerHit + " 是否為BOSS: " + monster.getStats().isBoss());
                            }
                            eachd = (int) (maxDamagePerHit);
                            player.getCheatTracker().registerOffense(CheatingOffense.傷害過高2, "[傷害: " + eachd + ", 預期: " + (long) maxDamagePerHit + ", 怪物: " + monster.getId() + "] [職業: " + player.getJob() + ", 等級: " + player.getLevel() + ", 技能: " + attack.skill + "]");
                        }
                    }
                    if (player == null) { // o_O
                        return;
                    }
                    totDamageToOneMonster += eachd;
                    //force the miss even if they dont miss. popular wz edit
                    if ((eachd == 0 || monster.getId() == 9700021) && player.getPyramidSubway() != null) { //miss
                        player.getPyramidSubway().onMiss(player);
                    }
                }

                totDamage += totDamageToOneMonster;
                player.checkMonsterAggro(monster);

                double range = player.getPosition().distanceSq(monster.getPosition());
                double SkillRange = GameConstants.getAttackRange(player, effect, attack);
                if (player.getDebugMessage() && range > SkillRange) {
                    player.dropMessage("技能[" + attack.skill + "] 預計範圍: " + (int) SkillRange + " 實際範圍: " + (int) range + "");
                }
                if (range > SkillRange && !player.inBossMap()) { // 815^2 <-- the most ranged attack in the game is Flame Wheel at 815 range
                    player.getCheatTracker().registerOffense(CheatingOffense.全圖打, "攻擊範圍異常,技能:" + attack.skill + "(" + SkillFactory.getName(attack.skill) + ")　怪物:" + monster.getId() + " 正常範圍:" + (int) SkillRange + " 計算範圍:" + (int) range + " 地圖: " + player.getMapId() + " (" + player.getMap().getMapName() + ")"); // , Double.toString(Math.sqrt(distance))
                    if (range > SkillRange * 2) {
                        player.getCheatTracker().registerOffense(CheatingOffense.全圖打, "超大範圍攻擊,技能:" + attack.skill + "(" + SkillFactory.getName(attack.skill) + ")　怪物:" + monster.getId() + " 正常範圍:" + (int) SkillRange + " 計算範圍:" + (int) range + " 地圖: " + player.getMapId() + " (" + player.getMap().getMapName() + ")"); // , Double.toString(Math.sqrt(distance))
                    }
                    return;
                }

                // pickpocket
                if (player.getBuffedValue(MapleBuffStat.PICKPOCKET) != null) {
                    switch (attack.skill) {
                        case 0:
                        case 4001334:
                        case 4201005:
                        case 4211002:
                        case 4211004:
                        case 4221003:
                        case 4221007:
                            handlePickPocket(player, monster, oned);
                            break;
                    }
                }

                if (totDamageToOneMonster > 0 || attack.skill == 1221011) {
                    if (attack.skill != 1221011) {
                        monster.damage(player, totDamageToOneMonster, true, attack.skill);
                    } else {
                        monster.damage(player, (monster.getStats().isBoss() ? 500000 : (monster.getHp() - 1)), true, attack.skill);
                    }

                    if (monster.isBuffed(MonsterStatus.WEAPON_DAMAGE_REFLECT)) { //test
                        player.addHP(-(7000 + Randomizer.nextInt(8000))); //this is what it seems to be?
                    }
                    player.onAttack(monster.getMobMaxHp(), monster.getMobMaxMp(), attack.skill, monster.getObjectId(), totDamage);
                    switch (attack.skill) {
                        case 14001004:
                        case 14111002:
                        case 14111005:
                        case 4301001:
                        case 4311002:
                        case 4311003:
                        case 4331000:
                        case 4331004:
                        case 4331005:
                        case 4341002:
                        case 4341004:
                        case 4341005:
                        case 4331006:
                        case 4341009:
                        case 4221007: // Boomerang Stab
                        case 4221001: // Assasinate
                        case 4211002: // Assulter
                        case 4201005: // Savage Blow
                        case 4001002: // Disorder
                        case 4001334: // Double Stab
                        case 4121007: // Triple Throw
                        case 4111005: // Avenger
                        case 4001344: { // Lucky Seven
                            // Venom
                            int[] skills = {4120005, 4220005, 4340001, 14110004};
                            for (int i : skills) {
                                final Skill skill = SkillFactory.getSkill(i);
                                if (player.getTotalSkillLevel(skill) > 0) {
                                    final MapleStatEffect venomEffect = skill.getEffect(player.getTotalSkillLevel(skill));
                                    if (venomEffect.makeChanceResult()) {
                                        monster.applyStatus(player, new MonsterStatusEffect(MonsterStatus.POISON, 1, i, null, false), true, venomEffect.getDuration(), true, venomEffect);
                                    }
                                    break;
                                }
                            }

                            break;
                        }
                        case 4201004: { //steal
                            monster.handleSteal(player);
                            break;
                        }
                        //case 21101003: // body pressure
                        case 21000002: // Double attack
                        case 21100001: // Triple Attack
                        case 21100002: // Pole Arm Push
                        case 21100004: // Pole Arm Smash
                        case 21110002: // Full Swing
                        case 21110003: // Pole Arm Toss
                        case 21110004: // Fenrir Phantom
                        case 21110006: // Whirlwind
                        case 21110007: // (hidden) Full Swing - Double Attack
                        case 21110008: // (hidden) Full Swing - Triple Attack
                        case 21120002: // Overswing
                        case 21120005: // Pole Arm finale
                        case 21120006: // Tempest
                        case 21120009: // (hidden) Overswing - Double Attack
                        case 21120010: { // (hidden) Overswing - Triple Attack
                            if (player.getBuffedValue(MapleBuffStat.WK_CHARGE) != null && !monster.getStats().isBoss()) {
                                final MapleStatEffect eff = player.getStatForBuff(MapleBuffStat.WK_CHARGE);
                                if (eff != null) {
                                    monster.applyStatus(player, new MonsterStatusEffect(MonsterStatus.SPEED, eff.getX(), eff.getSourceId(), null, false), false, eff.getY() * 1000, true, eff);
                                }
                            }
                            if (player.getBuffedValue(MapleBuffStat.BODY_PRESSURE) != null && !monster.getStats().isBoss()) {
                                final MapleStatEffect eff = player.getStatForBuff(MapleBuffStat.BODY_PRESSURE);

                                if (eff != null && eff.makeChanceResult() && !monster.isBuffed(MonsterStatus.NEUTRALISE)) {
                                    monster.applyStatus(player, new MonsterStatusEffect(MonsterStatus.NEUTRALISE, 1, eff.getSourceId(), null, false), false, eff.getX() * 1000, true, eff);
                                }
                            }
                            break;
                        }
                        default: //passives attack bonuses
                            break;
                    }
                    if (totDamageToOneMonster > 0) {
                        Item weapon_ = player.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -11);
                        if (weapon_ != null) {
                            MonsterStatus stat = GameConstants.getStatFromWeapon(weapon_.getItemId()); //10001 = acc/darkness. 10005 = speed/slow.
                            if (stat != null && Randomizer.nextInt(100) < GameConstants.getStatChance()) {
                                final MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(stat, GameConstants.getXForStat(stat), GameConstants.getSkillForStat(stat), null, false);
                                monster.applyStatus(player, monsterStatusEffect, false, 10000, false, null);
                            }
                        }
                        if (player.getBuffedValue(MapleBuffStat.BLIND) != null) {
                            final MapleStatEffect eff = player.getStatForBuff(MapleBuffStat.BLIND);

                            if (eff != null && eff.makeChanceResult()) {
                                final MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(MonsterStatus.ACC, eff.getX(), eff.getSourceId(), null, false);
                                monster.applyStatus(player, monsterStatusEffect, false, eff.getY() * 1000, true, eff);
                            }

                        }
                        if (player.getBuffedValue(MapleBuffStat.HAMSTRING) != null) {
                            final MapleStatEffect eff = player.getStatForBuff(MapleBuffStat.HAMSTRING);

                            if (eff != null && eff.makeChanceResult()) {
                                final MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(MonsterStatus.SPEED, eff.getX(), 3121007, null, false);
                                monster.applyStatus(player, monsterStatusEffect, false, eff.getY() * 1000, true, eff);
                            }
                        }
                        if (player.getJob() == 121 || player.getJob() == 122) { // WHITEKNIGHT
                            final Skill skill = SkillFactory.getSkill(1211006);
                            if (player.isBuffFrom(MapleBuffStat.WK_CHARGE, skill)) {
                                final MapleStatEffect eff = skill.getEffect(player.getTotalSkillLevel(skill));
                                final MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(MonsterStatus.FREEZE, 1, skill.getId(), null, false);
                                monster.applyStatus(player, monsterStatusEffect, false, eff.getY() * 2000, true, eff);
                            }
                            final Skill skill1 = SkillFactory.getSkill(1211005);
                            if (player.isBuffFrom(MapleBuffStat.WK_CHARGE, skill1)) {
                                MapleStatEffect eff = skill1.getEffect(player.getSkillLevel(skill1));
                                MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(MonsterStatus.FREEZE, 1, skill1.getId(), null, false);
                                monster.applyStatus(player, monsterStatusEffect, false, eff.getY() * 2000, monster.getStats().isBoss(), eff);
                            }
                        }
                    }
                    if (effect != null && effect.getMonsterStati().size() > 0) {
                        if (effect.makeChanceResult()) {
                            for (Map.Entry<MonsterStatus, Integer> z : effect.getMonsterStati().entrySet()) {
                                monster.applyStatus(player, new MonsterStatusEffect(z.getKey(), z.getValue(), theSkill.getId(), null, false), effect.isPoison(), effect.getDuration(), true, effect);
                            }
                        }
                    }
                }
            }
        }
        if (attack.skill == 4331003 && (hpMob <= 0 || totDamageToOneMonster < hpMob)) {
            return;
        }
        if (hpMob > 0 && totDamageToOneMonster > 0 && PlayerHandler.isFinisher(attack.skill) != 10) {
            player.afterAttack(attack.targets, attack.hits, attack.skill);
        }
        if (attack.skill != 0 && (attack.targets > 0 || (attack.skill != 4331003 && attack.skill != 4341002)) && !GameConstants.isNoDelaySkill(attack.skill)) {
            if (effect != null) {
                effect.applyTo(player, attack.position);
            }
        }
        if (totDamage > 1) {
            final CheatTracker tracker = player.getCheatTracker();

            tracker.setAttacksWithoutHit(true);
            if (tracker.getAttacksWithoutHit() > 50) {
                tracker.registerOffense(CheatingOffense.ATTACK_WITHOUT_GETTING_HIT, Integer.toString(tracker.getAttacksWithoutHit()));
            }
        }
    }

    public static final void applyAttackMagic(final AttackInfo attack, final Skill theSkill, final MapleCharacter player, final MapleStatEffect effect, double maxDamagePerHit) {
        if (!player.isAlive()) {
            player.getCheatTracker().registerOffense(CheatingOffense.ATTACKING_WHILE_DEAD);
            return;
        }
        if (attack.real) {
            player.getCheatTracker().checkAttack(attack.skill, attack.lastAttackTickCount);
        }
        /* 檢測打怪數量 */
        if (attack.targets > effect.getMobCount()) {
            if (player.hasGmLevel(1)) {
                player.dropMessage("打怪數量異常,技能代碼: " + attack.skill + " 封包怪物量 : " + attack.targets + " 服務端怪物量 :" + effect.getMobCount());
            } else {
                player.getCheatTracker().registerOffense(CheatingOffense.攻擊怪物數量異常, " 玩家: " + player.getName() + "(" + player.getLevel() + ") 地圖: " + player.getMapId() + "技能代碼: " + attack.skill + " 技能等級: " + player.getSkillLevel(effect.getSourceId()) + " 封包怪物量 : " + attack.targets + " 服務端怪物量 :" + effect.getMobCount());
                FileoutputUtil.logToFile("封鎖/打怪數量異常.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家: " + player.getName() + "(" + player.getLevel() + ") 地圖: " + player.getMapId() + "技能代碼: " + attack.skill + " 技能等級: " + player.getSkillLevel(effect.getSourceId()) + " 封包怪物量 : " + attack.targets + " 服務端怪物量 :" + effect.getMobCount());
                World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM 密語系統] " + player.getName() + " (等級 " + player.getLevel() + ") " + "攻擊怪物數量異常。 " + "封包怪物量 " + attack.targets + " 服務端怪物量 " + effect.getMobCount() + " 技能ID " + attack.skill));
            }
            return;
        }

        /* 檢測技能次數 */
        int last = effect.getAttackCount();
        boolean mirror_fix = false;
        if (player.getJob() >= 411 && player.getJob() <= 412) {
            mirror_fix = true;
        }
        if (mirror_fix) {
            last *= 2;
        }
        if (attack.hits > last) {
            if (player.hasGmLevel(1)) {
                player.dropMessage("攻擊次數異常攻擊次數 " + attack.hits + " 正確攻擊次數 " + last + " 技能ID " + attack.skill);
            } else {
                player.getCheatTracker().registerOffense(CheatingOffense.技能攻擊次數異常, "玩家: " + player.getName() + "(" + player.getLevel() + ") 地圖: " + player.getMapId() + " 技能代碼: " + attack.skill + " 技能等級: " + player.getSkillLevel(attack.skill) + " 攻擊次數 : " + attack.hits + " 正常攻擊次數 :" + last);
                World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM 密語系統] " + player.getName() + " (等級 " + player.getLevel() + ") 攻擊次數異常。 玩家攻擊次數 " + attack.hits + " 正確次數 " + last + " 技能ID " + attack.skill));
                FileoutputUtil.logToFile("封鎖/技能攻擊次數.txt", "\r\n" + FileoutputUtil.CurrentReadable_TimeGMT() + "玩家: " + player.getName() + "(" + player.getLevel() + ") 地圖: " + player.getMapId() + " 技能代碼: " + attack.skill + " 技能等級: " + player.getSkillLevel(attack.skill) + " 攻擊次數 : " + attack.hits + " 正常攻擊次數 :" + last);
            }
            return;
        }

        if (attack.hits > 0 && attack.targets > 0) {
            if (!player.getStat().checkEquipDurabilitys(player, -1)) { //i guess this is how it works ?
                player.dropMessage(5, "An item has run out of durability but has no inventory room to go to.");
                return;
            }
        }

        final PlayerStats stats = player.getStat();
        final Element element = player.getBuffedValue(MapleBuffStat.ELEMENT_RESET) != null ? Element.NEUTRAL : theSkill.getElement();

        double MaxDamagePerHit = 0;
        int totDamageToOneMonster, totDamage = 0, fixeddmg;
        byte overallAttackCount;
        boolean Tempest;
        MapleMonsterStats monsterstats;
        int CriticalDamage = stats.passive_sharpeye_percent();
        final Skill eaterSkill = SkillFactory.getSkill(GameConstants.getMPEaterForJob(player.getJob()));
        final int eaterLevel = player.getTotalSkillLevel(eaterSkill);

        final MapleMap map = player.getMap();

        for (final AttackPair oned : attack.allDamage) {
            final MapleMonster monster = map.getMonsterByOid(oned.objectid);

            if (monster != null && monster.getLinkCID() <= 0) {
                Tempest = monster.getStatusSourceID(MonsterStatus.FREEZE) == 21120006 && !monster.getStats().isBoss();
                totDamageToOneMonster = 0;
                monsterstats = monster.getStats();
                fixeddmg = monsterstats.getFixedDamage();
                MaxDamagePerHit = CalculateMaxMagicDamagePerHit(player, theSkill, monster, monsterstats, stats, element, CriticalDamage, maxDamagePerHit, effect);
                overallAttackCount = 0;
                Integer eachd;
                for (Pair<Integer, Boolean> eachde : oned.attack) {
                    eachd = eachde.left;
                    overallAttackCount++;
                    /* 確認是否超過預計傷害*/
                    if (!GameConstants.isElseSkill(attack.skill)) {
                        if (GameConstants.Novice_Skill(attack.skill)) {//新手技能
                            int lv = player.getSkillLevel(attack.skill);
                            MapleStatEffect eff = SkillFactory.getSkill(attack.skill).getEffect(lv);
                            if (!player.haveItem(eff.getItemCon(), eff.getItemConNo(), false, true)) {
                                FileoutputUtil.logToFile("封鎖/修改技能WZ.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家<" + player.getLevel() + ">: " + player.getName() + " 修改技能WZ。沒有鍋牛殼使用投擲術 怪物 " + monster.getId() + " 地圖: " + player.getMapId() + " 技能代碼: " + attack.skill + " 技能等級 " + lv + " 預計傷害: " + (int) maxDamagePerHit + "是否為BOSS: " + monster.getStats().isBoss());
                                World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, "[封鎖系統] " + player.getName() + " 因為使用不法程式而被管理員永久停權。"));
                                World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM 密語系統] " + player.getName() + " (等級 " + player.getLevel() + ") " + "修改技能WZ。沒有鍋牛殼使用投擲術 技能ID " + attack.skill + "  技能等級 " + lv));
                                player.ban(player.getName() + "修改技能WZ", true, true, false);
                                player.getClient().getSession().close();
                                return;
                            }
                            int fixdam = eff.getFixDamage();
                            if (eachd > fixdam) {
                                FileoutputUtil.logToFile("封鎖/傷害異常.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家<" + player.getLevel() + ">: " + player.getName() + " 怪物 " + monster.getId() + " 地圖: " + player.getMapId() + " 技能代碼: " + attack.skill + " 技能等級 " + lv + " 最高傷害: " + fixdam + " 本次傷害 :" + eachd + " 預計傷害: " + (int) maxDamagePerHit + "是否為BOSS: " + monster.getStats().isBoss());
                                World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, "[封鎖系統] " + player.getName() + " 因為使用不法程式而被管理員永久停權。"));
                                World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM 密語系統] " + player.getName() + " (等級 " + player.getLevel() + ") " + "傷害異常。 " + "最高傷害 " + fixdam + " 本次傷害 " + eachd + " 技能ID " + attack.skill + " 技能等級 " + lv));
                                player.ban(player.getName() + "傷害異常", true, true, false);
                                player.getClient().getSession().close();
                                return;
                            }
                        }
                        int atk = 200000;
                        if ((player.getLevel() >= 10)) {
                            boolean ban = false;
                            if (player.getLevel() <= 20) {
                                atk = 1000;
                            } else if (player.getLevel() <= 30) {
                                atk = 2500;
                            } else if (player.getLevel() <= 60) {
                                atk = 8000;
                            }
                            if (eachd >= atk && eachd > MaxDamagePerHit) {
                                ban = true;
                            }
                            if (eachd == monster.getMobMaxHp()) {
                                ban = false;
                            }
                            if (player.hasGmLevel(1)) {
                                ban = false;
                            }
                            if (ban) {
                                boolean apple = false;
                                if (player.getBuffSource(MapleBuffStat.WATK) == 2022179 || player.getBuffSource(MapleBuffStat.MATK) == 2022179 || player.getBuffSource(MapleBuffStat.WDEF) == 2022179) {
                                    apple = true;
                                }
                                FileoutputUtil.logToFile("封鎖/傷害異常.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家<" + player.getLevel() + ">: " + player.getName() + " 怪物 " + monster.getId() + " 地圖: " + player.getMapId() + " 技能代碼: " + attack.skill + " 最高傷害: " + atk + " 本次傷害 :" + eachd + " 預計傷害: " + (int) maxDamagePerHit + "是否為BOSS: " + monster.getStats().isBoss() + " 紫色蘋果: " + apple);
                                World.Broadcast.broadcastMessage(CWvsContext.serverNotice(6, "[封鎖系統] " + player.getName() + " 因為傷害異常而被管理員永久停權。"));
                                World.Broadcast.broadcastGMMessage(CWvsContext.serverNotice(6, "[GM 密語系統] " + player.getName() + " (等級 " + player.getLevel() + ") " + "傷害異常。 " + "最高傷害 " + atk + " 本次傷害 " + eachd + " 技能ID " + attack.skill));
                                player.ban(player.getName() + "傷害異常", true, true, false);
                                player.getClient().getSession().close();
                                return;
                            }
                        }
                    }
                    if (fixeddmg != -1) {
                        eachd = monsterstats.getOnlyNoramlAttack() ? 0 : fixeddmg;
                    } else if (monsterstats.getOnlyNoramlAttack()) {
                        eachd = 0;
                    } else if (!player.isGM()) {
                        if (Tempest) {
                            if (eachd > monster.getMobMaxHp()) {
                                eachd = (int) Math.min(monster.getMobMaxHp(), Integer.MAX_VALUE);
                                player.getCheatTracker().registerOffense(CheatingOffense.魔法傷害過高, "[傷害: " + eachd + ", 預計: " + (long) MaxDamagePerHit + ", 怪物: " + monster.getId() + "] [職業: " + player.getJob() + ", 等級: " + player.getLevel() + ", 技能: " + attack.skill + "]");
                            }
                        } else if (!monster.isBuffed(MonsterStatus.MAGIC_IMMUNITY) && !monster.isBuffed(MonsterStatus.MAGIC_DAMAGE_REFLECT)) {
                            if (eachd > MaxDamagePerHit) {
                                player.getCheatTracker().registerOffense(CheatingOffense.魔法傷害過高, "[傷害: " + eachd + ", 預計: " + (long) MaxDamagePerHit + ", 怪物: " + monster.getId() + "] [職業: " + player.getJob() + ", 等級: " + player.getLevel() + ", 技能: " + attack.skill + "]");

                                // 檢測相同商害
                                if (attack.real) {
                                    player.getCheatTracker().checkSameDamage(eachd, MaxDamagePerHit);
                                }

                                if (eachd > MaxDamagePerHit * 2) {
                                    if (ServerConstants.LOG_DAMAGE) {
                                        FileoutputUtil.logToFile("紀錄/傷害計算/魔法傷害計算修正.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家: " + player.getName() + "(" + player.getLevel() + ") 職業: " + player.getJob() + " 怪物:" + monster.getId() + " 技能: " + attack.skill + " 封包傷害 :" + eachd + " 預計傷害 :" + (int) maxDamagePerHit + "是否為BOSS: " + monster.getStats().isBoss(), false, true);
                                    }
                                    player.getCheatTracker().registerOffense(CheatingOffense.魔法傷害過高2, "[傷害: " + eachd + ", 預計: " + (long) MaxDamagePerHit + ", 怪物: " + monster.getId() + "] [職業: " + player.getJob() + ", 等級: " + player.getLevel() + ", 技能: " + attack.skill + "]");
                                    eachd = (int) (MaxDamagePerHit * 2); // 轉換為伺服器計算的傷害
                                }
                            }
                        } else if (eachd > MaxDamagePerHit) {
                            if (ServerConstants.LOG_DAMAGE) {
                                FileoutputUtil.logToFile("紀錄/傷害計算/魔法傷害計算修正.txt", "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家: " + player.getName() + "(" + player.getLevel() + ") 職業: " + player.getJob() + " 怪物:" + monster.getId() + " 技能: " + attack.skill + " 封包傷害 :" + eachd + " 預計傷害 :" + (int) maxDamagePerHit + "是否為BOSS: " + monster.getStats().isBoss(), false, true);
                            }
                            eachd = (int) (MaxDamagePerHit);
                        }
                    }
                    totDamageToOneMonster += eachd;
                }
                totDamage += totDamageToOneMonster;
                player.checkMonsterAggro(monster);

                double range = player.getPosition().distanceSq(monster.getPosition());
                double SkillRange = GameConstants.getAttackRange(player, effect, attack);
                if (player.getDebugMessage() && range > SkillRange) {
                    player.dropMessage("技能[" + attack.skill + "] 預計範圍: " + (int) SkillRange + " 實際範圍: " + (int) range + "");
                }
                if (range > SkillRange && !player.inBossMap()) { // 815^2 <-- the most ranged attack in the game is Flame Wheel at 815 range
                    player.getCheatTracker().registerOffense(CheatingOffense.全圖打, "攻擊範圍異常,技能:" + attack.skill + "(" + SkillFactory.getName(attack.skill) + ")　怪物:" + monster.getId() + " 正常範圍:" + (int) SkillRange + " 計算範圍:" + (int) range + " 地圖: " + player.getMapId() + " (" + player.getMap().getMapName() + ")"); // , Double.toString(Math.sqrt(distance))
                    if (range > SkillRange * 2) {
                        player.getCheatTracker().registerOffense(CheatingOffense.全圖打, "超大範圍攻擊,技能:" + attack.skill + "(" + SkillFactory.getName(attack.skill) + ")　怪物:" + monster.getId() + " 正常範圍:" + (int) SkillRange + " 計算範圍:" + (int) range + " 地圖: " + player.getMapId() + " (" + player.getMap().getMapName() + ")"); // , Double.toString(Math.sqrt(distance))
                    }
                    return;
                }
                if (attack.skill == 2301002 && !monsterstats.getUndead()) {
                    player.getCheatTracker().registerOffense(CheatingOffense.群體治癒攻擊不死系怪物, "\r\n " + FileoutputUtil.CurrentReadable_TimeGMT() + " 玩家<" + player.getLevel() + ">: " + player.getName() + " 怪物 " + monster.getId() + " 地圖: " + player.getMapId() + " 技能代碼: " + attack.skill + " 使用群體治癒攻擊非不死系怪物");
                    return;
                }

                if (totDamageToOneMonster > 0) {
                    monster.damage(player, totDamageToOneMonster, true, attack.skill);
                    if (monster.isBuffed(MonsterStatus.MAGIC_DAMAGE_REFLECT)) { //test
                        player.addHP(-(7000 + Randomizer.nextInt(8000))); //this is what it seems to be?
                    }
                    if (player.getBuffedValue(MapleBuffStat.SLOW) != null) {
                        final MapleStatEffect eff = player.getStatForBuff(MapleBuffStat.SLOW);

                        if (eff != null && eff.makeChanceResult() && !monster.isBuffed(MonsterStatus.SPEED)) {
                            monster.applyStatus(player, new MonsterStatusEffect(MonsterStatus.SPEED, eff.getX(), eff.getSourceId(), null, false), false, eff.getY() * 1000, true, eff);
                        }
                    }
                    player.onAttack(monster.getMobMaxHp(), monster.getMobMaxMp(), attack.skill, monster.getObjectId(), totDamage);

                    switch (attack.skill) {
                        case 2221003:
                            monster.setTempEffectiveness(Element.ICE, effect.getDuration());
                            break;
                        case 2121003:
                            monster.setTempEffectiveness(Element.FIRE, effect.getDuration());
                            break;
                    }

                    if (effect != null && effect.getMonsterStati().size() > 0) {
                        if (effect.makeChanceResult()) {
                            for (Map.Entry<MonsterStatus, Integer> z : effect.getMonsterStati().entrySet()) {
                                monster.applyStatus(player, new MonsterStatusEffect(z.getKey(), z.getValue(), theSkill.getId(), null, false), effect.isPoison(), effect.getDuration(), true, effect);
                            }
                        }
                    }

                    if (eaterLevel > 0) {
                        eaterSkill.getEffect(eaterLevel).applyPassive(player, monster);
                    }

                }
            }
        }

        if (attack.skill != 2301002) {
            effect.applyTo(player);
        }

        if (totDamage > 1) {
            final CheatTracker tracker = player.getCheatTracker();
            tracker.setAttacksWithoutHit(true);

            if (tracker.getAttacksWithoutHit() > 50) {
                tracker.registerOffense(CheatingOffense.ATTACK_WITHOUT_GETTING_HIT, Integer.toString(tracker.getAttacksWithoutHit()));
            }
        }

    }

    private static final double CalculateMaxMagicDamagePerHit(final MapleCharacter chr, final Skill skill, final MapleMonster monster, final MapleMonsterStats mobstats, final PlayerStats stats, final Element elem, final Integer sharpEye, final double maxDamagePerMonster, final MapleStatEffect attackEffect) {
        final int dLevel = Math.max(mobstats.getLevel() - chr.getLevel(), 0) * 2;
        int HitRate = Math.min((int) Math.floor(Math.sqrt(stats.getAccuracy())) - (int) Math.floor(Math.sqrt(mobstats.getEva())) + 100, 100);
        if (dLevel > HitRate) {
            HitRate = dLevel;
        }
        HitRate -= dLevel;
        if (HitRate <= 0 && !(GameConstants.isBeginnerJob(skill.getId() / 10000) && skill.getId() % 10000 == 1000)) { // miss :P or HACK :O
            return 0;
        }
        double elemMaxDamagePerMob;
        int CritPercent = sharpEye;
        final ElementalEffectiveness ee = monster.getEffectiveness(elem);
        switch (ee) {
            case IMMUNE:
                elemMaxDamagePerMob = 1;
                break;
            default:
                elemMaxDamagePerMob = ElementalStaffAttackBonus(elem, maxDamagePerMonster * ee.getValue(), stats);
                break;
        }
        // Calculate monster magic def
        // Min damage = (MIN before defense) - MDEF*.6
        // Max damage = (MAX before defense) - MDEF*.5
        int MDRate = monster.getStats().getMDRate();
        MonsterStatusEffect pdr = monster.getBuff(MonsterStatus.MDEF);
        if (pdr != null) {
            MDRate += pdr.getX();
        }
        elemMaxDamagePerMob -= elemMaxDamagePerMob * (Math.max(MDRate - stats.ignoreTargetDEF - attackEffect.getIgnoreMob(), 0) / 100.0);
        // Calculate Sharp eye bonus
        elemMaxDamagePerMob += ((double) elemMaxDamagePerMob / 100.0) * CritPercent;
//	if (skill.isChargeSkill()) {
//	    elemMaxDamagePerMob = (float) ((90 * ((System.currentTimeMillis() - chr.getKeyDownSkill_Time()) / 1000) + 10) * elemMaxDamagePerMob * 0.01);
//	}
//      if (skill.isChargeSkill() && chr.getKeyDownSkill_Time() == 0) {
//          return 1;
//      }
        elemMaxDamagePerMob *= (monster.getStats().isBoss() ? chr.getStat().bossdam_r : chr.getStat().dam_r) / 100.0;
        final MonsterStatusEffect imprint = monster.getBuff(MonsterStatus.IMPRINT);
        if (imprint != null) {
            elemMaxDamagePerMob += (elemMaxDamagePerMob * imprint.getX() / 100.0);
        }
        elemMaxDamagePerMob += (elemMaxDamagePerMob * chr.getDamageIncrease(monster.getObjectId()) / 100.0);

        if (GameConstants.isBeginnerJob(skill.getId() / 10000)) {
            switch (skill.getId() % 10000) {
                case 1000:
                    elemMaxDamagePerMob = 40;
                    break;
                case 1020:
                    elemMaxDamagePerMob = 1;
                    break;
                case 1009:
                    elemMaxDamagePerMob = (monster.getStats().isBoss() ? monster.getMobMaxHp() / 30 * 100 : monster.getMobMaxHp());
                    break;
            }
        }
        elemMaxDamagePerMob *= 3;
        switch (skill.getId()) {
            case 2321007: // 天使之箭
                elemMaxDamagePerMob *= 3;
                break;
            case 2101004: // 火焰箭
            case 2111006: // 火毒合擊
            case 2121006: // 劇毒麻痺
            case 2211003: // 落雷凝聚
            case 2221006: // 閃電連擊
                elemMaxDamagePerMob *= 2;
                break;
            case 2321001: // 核爆術
            case 2221001: // 核爆術
            case 2121001: // 核爆術
                elemMaxDamagePerMob *= 2.5;
                break;
            case 2121007: // 火流星
            case 2221007: // 暴風雪
            case 2321008: // 天怒
                elemMaxDamagePerMob *= 6;
                break;
        }
        if (chr.getDebugMessage()) {
            chr.dropMessage("[傷害計算]" + skill.getName() + "(" + skill.getId() + ")屬性傷害：" + (int) Math.ceil(elemMaxDamagePerMob) + " BOSS傷害：" + (int) Math.ceil(((monster.getStats().isBoss()) ? chr.getStat().bossdam_r : chr.getStat().dam_r) - 100) + "%");
        }

        if (elemMaxDamagePerMob > 99999) {
            elemMaxDamagePerMob = 99999;
        } else if (elemMaxDamagePerMob <= 0) {
            elemMaxDamagePerMob = 1;
        }

        return elemMaxDamagePerMob;
    }

    private static final double ElementalStaffAttackBonus(final Element elem, double elemMaxDamagePerMob, final PlayerStats stats) {
        switch (elem) {
            case FIRE:
                return (elemMaxDamagePerMob / 100) * (stats.element_fire + stats.getElementBoost(elem));
            case ICE:
                return (elemMaxDamagePerMob / 100) * (stats.element_ice + stats.getElementBoost(elem));
            case LIGHTING:
                return (elemMaxDamagePerMob / 100) * (stats.element_light + stats.getElementBoost(elem));
            case POISON:
                return (elemMaxDamagePerMob / 100) * (stats.element_psn + stats.getElementBoost(elem));
            default:
                return (elemMaxDamagePerMob / 100) * (stats.def + stats.getElementBoost(elem));
        }
    }

    private static void handlePickPocket(final MapleCharacter player, final MapleMonster mob, AttackPair oned) {
        final int maxmeso = player.getBuffedValue(MapleBuffStat.PICKPOCKET);
        for (final Pair<Integer, Boolean> eachde : oned.attack) {
            final Integer eachd = eachde.left;
            if (player.getStat().pickRate >= 100 || Randomizer.nextInt(99) < player.getStat().pickRate) {
                player.getMap().spawnMesoDrop(Math.min((int) Math.max(((double) eachd / (double) 20000) * (double) maxmeso, (double) 1), maxmeso), new Point((int) (mob.getTruePosition().getX() + Randomizer.nextInt(100) - 50), (int) (mob.getTruePosition().getY())), mob, player, false, (byte) 0);
            }
        }
    }

    private static double CalculateMaxWeaponDamagePerHit(final MapleCharacter player, final MapleMonster monster, final AttackInfo attack, final Skill theSkill, final MapleStatEffect attackEffect, double maximumDamageToMonster, final Integer CriticalDamagePercent) {
        final int dLevel = Math.max(monster.getStats().getLevel() - player.getLevel(), 0) * 2;
        int HitRate = Math.min((int) Math.floor(Math.sqrt(player.getStat().getAccuracy())) - (int) Math.floor(Math.sqrt(monster.getStats().getEva())) + 100, 100);
        if (dLevel > HitRate) {
            HitRate = dLevel;
        }
        HitRate -= dLevel;
        if (HitRate <= 0 && !(GameConstants.isBeginnerJob(attack.skill / 10000) && attack.skill % 10000 == 1000) && !GameConstants.isPyramidSkill(attack.skill) && !GameConstants.isMulungSkill(attack.skill) && !GameConstants.isInflationSkill(attack.skill)) { // miss :P or HACK :O
            return 0;
        }
        if (player.getMapId() / 1000000 == 914 || player.getMapId() / 1000000 == 927) { //aran
            return 99999;
        }

        List<Element> elements = new ArrayList<>();
        boolean defined = false;
        int CritPercent = CriticalDamagePercent;
        int PDRate = monster.getStats().getPDRate();
        MonsterStatusEffect pdr = monster.getBuff(MonsterStatus.WDEF);
        if (pdr != null) {
            PDRate += pdr.getX(); //x will be negative usually
        }
        if (theSkill != null) {
            elements.add(theSkill.getElement());
            if (GameConstants.isBeginnerJob(theSkill.getId() / 10000)) {
                switch (theSkill.getId() % 10000) {
                    case 1000:
                        maximumDamageToMonster = 40;
                        defined = true;
                        break;
                    case 1020:
                        maximumDamageToMonster = 1;
                        defined = true;
                        break;
                    case 1009:
                        maximumDamageToMonster = (monster.getStats().isBoss() ? monster.getMobMaxHp() / 30 * 100 : monster.getMobMaxHp());
                        defined = true;
                        break;
                }
            }
            switch (theSkill.getId()) {
                case 1311005:
                    PDRate = (monster.getStats().isBoss() ? PDRate : 0);
                    break;
                case 3221001:
                    maximumDamageToMonster *= attackEffect.getMobCount();
                    defined = true;
                    break;
                case 3101005:
                    defined = true; //can go past 500000
                    break;
                case 4221001:
                    maximumDamageToMonster *= 2.5;
                    break;
                case 3221007: //snipe
                case 1221009: //BLAST FK
                case 4331003: //Owl Spirit
                    if (!monster.getStats().isBoss()) {
                        maximumDamageToMonster = (monster.getMobMaxHp());
                        defined = true;
                    }
                    break;
                case 1221011://Heavens Hammer
                    maximumDamageToMonster = (monster.getStats().isBoss() ? 500000 : (monster.getHp() - 1));
                    defined = true;
                    break;
                case 3211006: //Sniper Strafe
//                    if (monster.getStatusSourceID(MonsterStatus.FREEZE) == 3211003) { //blizzard in effect
//                        defined = true;
//                        maximumDamageToMonster = 99999;
//                    }
                    break;
            }
        }
        double elementalMaxDamagePerMonster = maximumDamageToMonster;
        if (player.getJob() == 311 || player.getJob() == 312 || player.getJob() == 321 || player.getJob() == 322) {
            // 致命箭
            Skill mortal = SkillFactory.getSkill(player.getJob() == 311 || player.getJob() == 312 ? 3110001 : 3210001);
            if (player.getTotalSkillLevel(mortal) > 0) {
                final MapleStatEffect mort = mortal.getEffect(player.getTotalSkillLevel(mortal));
                if (mort != null && monster.getHPPercent() < mort.getX()) {
                    elementalMaxDamagePerMonster = 99999;
                    defined = true;
                }
            }
        }

        if (!defined || (theSkill != null && theSkill.getId() == 3221001)) {
            if (player.getBuffedValue(MapleBuffStat.WK_CHARGE) != null) {
                int chargeSkillId = player.getBuffSource(MapleBuffStat.WK_CHARGE);

                switch (chargeSkillId) {
                    case 1211003: // 烈焰之劍
                    case 1211004: // 烈焰之棍
                        elements.add(Element.FIRE);
                        break;
                    case 1211005: // 寒冰之劍
                    case 1211006: // 寒冰之棍
                        elements.add(Element.ICE);
                        break;
                    case 1211007: // 雷鳴之劍
                    case 1211008: // 雷鳴之棍
                        elements.add(Element.LIGHTING);
                        break;
                    case 1221003: // 聖靈之劍
                    case 1221004: // 聖靈之棍
                        elements.add(Element.HOLY);
                        break;
                }
            }
            if (player.getBuffedValue(MapleBuffStat.LIGHTNING_CHARGE) != null) {
                elements.add(Element.LIGHTING);
            }
            if (player.getBuffedValue(MapleBuffStat.ELEMENT_RESET) != null) {
                elements.clear();
            }
            if (elements.size() > 0) {
                double elementalEffect;

                switch (attack.skill) {
                    case 3211003:
                    case 3111003: // inferno and blizzard
                        elementalEffect = attackEffect.getX() / 100.0;
                        break;
                    default:
                        elementalEffect = (0.5 / elements.size());
                        break;
                }
                for (Element element : elements) {
                    switch (monster.getEffectiveness(element)) {
                        case IMMUNE:
                            elementalMaxDamagePerMonster = 1;
                            break;
                        case WEAK:
                            elementalMaxDamagePerMonster *= (1.0 + elementalEffect + player.getStat().getElementBoost(element));
                            break;
                        case STRONG:
                            elementalMaxDamagePerMonster *= (1.0 - elementalEffect - player.getStat().getElementBoost(element));
                            break;
                    }
                }
            }
            // Calculate mob def
            elementalMaxDamagePerMonster -= elementalMaxDamagePerMonster * (Math.max(PDRate - Math.max(player.getStat().ignoreTargetDEF, 0) - Math.max(attackEffect == null ? 0 : attackEffect.getIgnoreMob(), 0), 0) / 100.0);

            // Calculate passive bonuses + Sharp Eye
            elementalMaxDamagePerMonster += ((double) elementalMaxDamagePerMonster / 100.0) * CritPercent;

//	    if (theSkill.isChargeSkill()) {
//	        elementalMaxDamagePerMonster = (double) (90 * (System.currentTimeMillis() - player.getKeyDownSkill_Time()) / 2000 + 10) * elementalMaxDamagePerMonster * 0.01;
//	    }
            if (theSkill != null && theSkill.isChargeSkill() && player.getKeyDownSkill_Time() == 0) {
                return 0;
            }
            final MonsterStatusEffect imprint = monster.getBuff(MonsterStatus.IMPRINT);
            if (imprint != null) {
                elementalMaxDamagePerMonster += (elementalMaxDamagePerMonster * imprint.getX() / 100.0);
            }

            elementalMaxDamagePerMonster += (elementalMaxDamagePerMonster * player.getDamageIncrease(monster.getObjectId()) / 100.0);
            elementalMaxDamagePerMonster *= (monster.getStats().isBoss() && attackEffect != null ? (player.getStat().bossdam_r + attackEffect.getBossDamage()) : player.getStat().dam_r) / 100.0;
        }
        if (player.getDebugMessage()) {
            player.dropMessage("[傷害計算]屬性傷害：" + (int) Math.ceil(elementalMaxDamagePerMonster) + " BOSS傷害：" + (int) Math.ceil(((monster.getStats().isBoss()) ? player.getStat().bossdam_r : player.getStat().dam_r) - 100) + "%");
        }
        if (elementalMaxDamagePerMonster > 99999) {
            if (!defined) {
                elementalMaxDamagePerMonster = 99999;
            }
        } else if (elementalMaxDamagePerMonster <= 0) {
            elementalMaxDamagePerMonster = 1;
        }
        return elementalMaxDamagePerMonster;
    }

    public static final AttackInfo DivideAttack(final AttackInfo attack, final int rate) {
        attack.real = false;
        if (rate <= 1) {
            return attack; //lol
        }
        for (AttackPair p : attack.allDamage) {
            if (p.attack != null) {
                for (Pair<Integer, Boolean> eachd : p.attack) {
                    eachd.left /= rate; //too ex.
                }
            }
        }
        return attack;
    }

    public static final AttackInfo Modify_AttackCrit(final AttackInfo attack, final MapleCharacter chr, final int type, final MapleStatEffect effect) {
        if (attack.skill != 4211006 && attack.skill != 3211003 && attack.skill != 4111004) { //blizz + shadow meso + m.e no crits
            final int CriticalRate = chr.getStat().passive_sharpeye_rate() + (effect == null ? 0 : effect.getCr());
            final boolean shadow = chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null && (type == 1 || type == 2);
            final List<Integer> damages = new ArrayList<>(), damage = new ArrayList<>();
            int hit, toCrit, mid_att;
            for (AttackPair p : attack.allDamage) {
                if (p.attack != null) {
                    hit = 0;
                    mid_att = shadow ? (p.attack.size() / 2) : p.attack.size();
                    //grab the highest hits
                    toCrit = attack.skill == 4221001 || attack.skill == 3221007 || attack.skill == 23121003 || attack.skill == 4341005 || attack.skill == 4331006 || attack.skill == 21120005 ? mid_att : 0;
                    if (toCrit == 0) {
                        for (Pair<Integer, Boolean> eachd : p.attack) {
                            if (!eachd.right && hit < mid_att) {
                                if (eachd.left > 999999 || Randomizer.nextInt(100) < CriticalRate) {
                                    toCrit++;
                                }
                                damage.add(eachd.left);
                            }
                            hit++;
                        }
                        if (toCrit == 0) {
                            damage.clear();
                            continue; //no crits here
                        }
                        Collections.sort(damage); //least to greatest
                        for (int i = damage.size(); i > damage.size() - toCrit; i--) {
                            damages.add(damage.get(i - 1));
                        }
                        damage.clear();
                    }
                    hit = 0;
                    for (Pair<Integer, Boolean> eachd : p.attack) {
                        if (!eachd.right) {
                            if (attack.skill == 4221001) { //assassinate never crit first 3, always crit last
                                eachd.right = (hit == 4 && Randomizer.nextInt(100) < 90);
                            } else if (attack.skill == 3221007 || attack.skill == 23121003 || attack.skill == 21120005 || attack.skill == 4341005 || attack.skill == 4331006 || eachd.left > 999999) { //snipe always crit
                                eachd.right = true;
                            } else if (hit >= mid_att) { //shadowpartner copies second half to first half
                                eachd.right = p.attack.get(hit - mid_att).right;
                            } else {
                                //rough calculation
                                eachd.right = damages.contains(eachd.left);
                            }
                        }
                        hit++;
                    }
                    damages.clear();
                }
            }
        }
        return attack;
    }

    public static final AttackInfo parseDmgMa(final LittleEndianAccessor lea, final MapleCharacter chr) {
        //System.out.println("parseDmgMa.." + lea.toString());
        final AttackInfo ret = new AttackInfo();

        lea.skip(1);
        ret.tbyte = lea.readByte();
        //System.out.println("TBYTE: " + tbyte);
        ret.targets = (byte) ((ret.tbyte >>> 4) & 0xF);
        ret.hits = (byte) (ret.tbyte & 0xF);
        ret.skill = lea.readInt();
        if (ret.skill >= 91000000) { //guild/recipe? no
            return null;
        }
        if (GameConstants.isMagicChargeSkill(ret.skill)) {
            ret.charge = lea.readInt();
        } else {
            ret.charge = -1;
        }
        ret.unk = lea.readByte();
        ret.display = lea.readUShort();
        ret.speed = lea.readByte(); // Confirmed
        ret.lastAttackTickCount = lea.readInt(); // Ticks

        int damage, oid;
        List<Pair<Integer, Boolean>> allDamageNumbers;
        ret.allDamage = new ArrayList<>();

        for (int i = 0; i < ret.targets; i++) {
            oid = lea.readInt();
            //if (chr.getMap().isTown()) {
            //    final MapleMonster od = chr.getMap().getMonsterByOid(oid);
            //    if (od != null && od.getLinkCID() > 0) {
            //	    return null;
            //    }
            //}
            lea.skip(14); // [1] Always 6?, [3] unk, [4] Pos1, [4] Pos2, [2] seems to change randomly for some attack

            allDamageNumbers = new ArrayList<>();

            for (int j = 0; j < ret.hits; j++) {
                damage = lea.readInt();
                allDamageNumbers.add(new Pair<>(damage, false));
                //System.out.println("parseDmgMa Damage: " + damage);
            }
            ret.allDamage.add(new AttackPair(oid, allDamageNumbers));
        }
        ret.position = lea.readPos();

        return ret;
    }

    public static final AttackInfo parseDmgM(final LittleEndianAccessor lea, final MapleCharacter chr) {
        //System.out.println("parseDmgM.." + lea.toString());
        final AttackInfo ret = new AttackInfo();
        lea.skip(1);
        ret.tbyte = lea.readByte();
        //System.out.println("TBYTE: " + tbyte);
        ret.targets = (byte) ((ret.tbyte >>> 4) & 0xF);
        ret.hits = (byte) (ret.tbyte & 0xF);
        ret.skill = lea.readInt();
        if (ret.skill >= 91000000) { //guild/recipe? no
            return null;
        }
        switch (ret.skill) {
            case 5101004: // 狂暴衝擊
            case 5201002: // 炸彈投擲
                ret.charge = lea.readInt();
                break;
            default:
                ret.charge = 0;
                break;
        }
        ret.unk = lea.readByte();
        ret.display = lea.readUShort();
        ret.speed = lea.readByte(); // Confirmed
        ret.lastAttackTickCount = lea.readInt(); // Ticks

        ret.allDamage = new ArrayList<>();

        if (ret.skill == 4211006) { // Meso Explosion
            return parseMesoExplosion(lea, ret, chr);
        }
        int damage, oid;
        List<Pair<Integer, Boolean>> allDamageNumbers;

        for (int i = 0; i < ret.targets; i++) {
            oid = lea.readInt();
            //if (chr.getMap().isTown()) {
            //    final MapleMonster od = chr.getMap().getMonsterByOid(oid);
            //if (od != null && od.getLinkCID() > 0) {
            //    return null;
            //    }
            //}
            lea.skip(14); // [1] Always 6?, [3] unk, [4] Pos1, [4] Pos2, [2] seems to change randomly for some attack

            allDamageNumbers = new ArrayList<>();

            for (int j = 0; j < ret.hits; j++) {
                damage = lea.readInt();
                //System.out.println("parseDmgM Damage: " + damage);
                allDamageNumbers.add(new Pair<>(damage, false));
            }
            ret.allDamage.add(new AttackPair(oid, allDamageNumbers));
        }
        ret.position = lea.readPos();
        return ret;
    }

    public static final AttackInfo parseDmgR(final LittleEndianAccessor lea, final MapleCharacter chr) {
        //System.out.println("parseDmgR.." + lea.toString());
        final AttackInfo ret = new AttackInfo();

        lea.skip(1); // portal count
        ret.tbyte = lea.readByte();
        //System.out.println("TBYTE: " + tbyte);
        ret.targets = (byte) ((ret.tbyte >>> 4) & 0xF);
        ret.hits = (byte) (ret.tbyte & 0xF);
        ret.skill = lea.readInt();
        if (ret.skill >= 91000000) { //guild/recipe? no
            return null;
        }

        switch (ret.skill) {
            case 3121004: // 暴風神射
            case 3221001: // 光速神弩
            case 5221004: // 瞬‧迅雷
                lea.skip(4); // extra 4 bytes
                break;
        }
        ret.charge = -1;
        ret.unk = lea.readByte();
        ret.display = lea.readUShort();
        if (ret.skill == 23111001) { // Leap Tornado
            lea.skip(4); // 7D 00 00 00
            lea.skip(4); // pos A0 FC FF FF 
            // could it be a rectangle?
            lea.skip(4); // 1D 00 00 00		
        }
        ret.speed = lea.readByte(); // Confirmed
        ret.lastAttackTickCount = lea.readInt(); // Ticks
        ret.slot = (byte) lea.readShort();
        ret.csstar = (byte) lea.readShort();
        ret.AOE = lea.readByte(); // is AOE or not, TT/ Avenger = 41, Showdown = 0

        int damage, oid;
        List<Pair<Integer, Boolean>> allDamageNumbers;
        ret.allDamage = new ArrayList<>();

        for (int i = 0; i < ret.targets; i++) {
            oid = lea.readInt();
            //if (chr.getMap().isTown()) {
            //    final MapleMonster od = chr.getMap().getMonsterByOid(oid);
            //    if (od != null && od.getLinkCID() > 0) {
            //	    return null;
            //    }
            //}
            lea.skip(14); // [1] Always 6?, [3] unk, [4] Pos1, [4] Pos2, [2] seems to change randomly for some attack

            allDamageNumbers = new ArrayList<>();
            for (int j = 0; j < ret.hits; j++) {
                damage = lea.readInt();
                allDamageNumbers.add(new Pair<>(damage, false));
                //System.out.println("parseDmgR Hit " + j + " from " + i + " to mobid " + oid + ", damage " + damage);
            }
            ret.allDamage.add(new AttackPair(oid, allDamageNumbers));
        }
        ret.position = lea.readPos();

        return ret;
    }

    public static final AttackInfo parseMesoExplosion(final LittleEndianAccessor lea, final AttackInfo ret, final MapleCharacter chr) {
        //System.out.println(lea.toString(true));
        byte bullets;
        if (ret.hits == 0) {
            lea.skip(4);
            bullets = lea.readByte();
            for (int j = 0; j < bullets; j++) {
                ret.allDamage.add(new AttackPair(lea.readInt(), null));
                lea.skip(1);
            }
            lea.skip(2); // 8F 02
            return ret;
        }
        int oid;
        List<Pair<Integer, Boolean>> allDamageNumbers;

        for (int i = 0; i < ret.targets; i++) {
            oid = lea.readInt();
            //if (chr.getMap().isTown()) {
            //    final MapleMonster od = chr.getMap().getMonsterByOid(oid);
            //    if (od != null && od.getLinkCID() > 0) {
            //	    return null;
            //    }
            //}
            lea.skip(12);
            bullets = lea.readByte();
            allDamageNumbers = new ArrayList<>();
            for (int j = 0; j < bullets; j++) {
                allDamageNumbers.add(new Pair<>(lea.readInt(), false)); //m.e. never crits
            }
            ret.allDamage.add(new AttackPair(oid, allDamageNumbers));
        }
        lea.skip(4);
        bullets = lea.readByte();

        for (int j = 0; j < bullets; j++) {
            ret.allDamage.add(new AttackPair(lea.readInt(), null));
            lea.skip(1);
        }
        // 8F 02/ 63 02

        return ret;
    }
}
