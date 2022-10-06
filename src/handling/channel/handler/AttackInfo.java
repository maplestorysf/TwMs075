package handling.channel.handler;

import java.util.List;
import java.awt.Point;

import client.Skill;
import constants.GameConstants;
import client.MapleCharacter;
import client.SkillFactory;
import server.MapleStatEffect;
import server.AutobanManager;
import tools.AttackPair;

public class AttackInfo {

    public int skill, charge, lastAttackTickCount;
    public List<AttackPair> allDamage;
    public Point position;
    public int display;
    public byte hits, targets, tbyte, speed, csstar, AOE, slot, unk;
    public boolean real = true;

    public final MapleStatEffect getAttackEffect(final MapleCharacter chr, int skillLevel, final Skill skill_) {
        if (GameConstants.isMulungSkill(skill) || GameConstants.isPyramidSkill(skill) || GameConstants.isInflationSkill(skill)) {
            skillLevel = 1;
        } else if (skillLevel <= 0) {
            return null;
        }
        int dd = ((display & 0x8000) != 0 ? (display - 0x8000) : display);
        if (GameConstants.isLinkedAranSkill(skill)) {
            final Skill skillLink = SkillFactory.getSkill(skill);

            if (dd > SkillFactory.Delay.magic6.i && dd != SkillFactory.Delay.shot.i && dd != SkillFactory.Delay.fist.i) {
                if (skillLink.getAnimation() == -1 || Math.abs(skillLink.getAnimation() - dd) > 0x10) {
                    if (skillLink.getAnimation() == -1) {
                        chr.dropMessage(5, "Please report this: animation for skill " + skillLink.getId() + " doesn't exist");
                    } else {
                        AutobanManager.getInstance().autoban(chr.getClient(), "No delay hack, SkillID : " + skillLink.getId() + ", animation: " + dd + ", expected: " + skillLink.getAnimation());
                    }
                    return null;
                }
            }
            return skillLink.getEffect(skillLevel);
        } // i'm too lazy to calculate the new skill types =.=
        /*if (dd > SkillFactory.Delay.magic6.i && dd != SkillFactory.Delay.shot.i && dd != SkillFactory.Delay.fist.i) {
         if (skill_.getAnimation() == -1 || Math.abs(skill_.getAnimation() - dd) > 0x10) {
         if (skill_.getAnimation() == -1) {
         chr.dropMessage(5, "Please report this: animation for skill " + skill_.getId() + " doesn't exist");
         } else {
         AutobanManager.getInstance().autoban(chr.getClient(), "No delay hack, SkillID : " + skill_.getId() + ", animation: " + dd + ", expected: " + skill_.getAnimation());
         }
         return null;
         }
         }*/
        return skill_.getEffect(skillLevel);
    }
}
