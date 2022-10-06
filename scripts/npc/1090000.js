/* Dances with Balrog
	Warrior Job Advancement
	Victoria Road : Warriors' Sanctuary (102000003)

	Custom Quest 100003, 100005
*/
var status = 0;
var jobId;
var jobName;
var mapId


function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == 0 && status == 2) {
        cm.sendOk("請重試.");
        cm.dispose();
        return;
    }
    if (mode == 1)
        status++;
    else
        status--;
    if (status == 0) {
        if (cm.getMapId() == 912010200 || cm.haveItem(4031059, 1)) {
            if (cm.getQuestStatus(6370) == 1) {
                cm.removeAll(4031059);
                cm.teachSkill(5221006, 0, 10);
                cm.forceCompleteQuest(6370);
            } else if (cm.getQuestStatus(6330) == 1) {
                cm.removeAll(4031059);
                cm.teachSkill(5121003, 0, 10);
                cm.forceCompleteQuest(6330);
            }
            cm.warp(120000101, 0);
            cm.sendOk("恭喜完成任務！！");
            cm.dispose();
        }
        if (cm.getJob() == 0) {
            if (cm.getPlayer().getLevel() >= 10) {
                cm.sendNext("你要轉職成為一位 #r海盜#k ?");
            } else {
                cm.sendOk("你還不能轉職成為 #r海盜#k 蔡B8.");
                cm.dispose();
            }
        } else {
            if (cm.getPlayer().getLevel() >= 30 && cm.getJob() == 500) { // 海盜
                if (cm.haveItem(4031012, 1)) {
                    if (cm.haveItem(4031012, 1)) {
                        status = 20;
                        cm.sendNext("我看到你完成了測試. 想要繼續轉職請點下一頁!");
                    } else {
                        cm.sendOk("請去找 #r海盜轉職教官#k.")
                        cm.dispose();
                    }
                } else {
                    status = 10;
                    cm.sendNext("你已經可以轉職了,要轉職請點下一頁.");
                }
            } else if (cm.getPlayer().getLevel() >= 70 && cm.getJob() == 510 || cm.getJob() == 520) {
                if (cm.haveItem(4031059, 1)) {
                    cm.gainItem(4031057, 1);
                    cm.gainItem(4031059, -1);
                    cm.warp(211000001, 0);
                } else {
                    cm.sendOk("嗨, #b#h0##k! 我需要一個 #b黑符#k. 快去找異次元空間拿給我");
                    cm.startQuest(100101);
                }
                cm.dispose();
            } else if (cm.isQuestActive(6370)) {
                var dd = cm.getEventManager("KyrinTrainingGroundC");
                if (dd != null) {
                    dd.startInstance(cm.getPlayer());
                } else {
                    cm.sendOk("未知的錯誤請稍後在嘗試。");
                }
            } else if (cm.isQuestActive(6330)) {
                var dd = cm.getEventManager("KyrinTrainingGroundV");
                if (dd != null) {
                    dd.startInstance(cm.getPlayer());
                } else {
                    cm.sendOk("未知的錯誤請稍後在嘗試。");
                }
            } else {
                cm.sendOk("你好,我是卡伊琳-海盜轉職官.");
                cm.dispose();
            }
        }
    } else if (status == 1) {
        cm.sendNextPrev("一旦轉職了就不能反悔,如果不想轉職請點上一頁.");
    } else if (status == 2) {
        cm.sendYesNo("你真的要成為一位 #r海盜#k ?");
    } else if (status == 3) {
        if (cm.getJob() == 0) {
            cm.changeJob(500); // 海盜
            //cm.resetStats(4, 4, 4, 4);
        }
        cm.gainItem(1482014, 1);
        cm.gainItem(1492014, 1);
        cm.gainItem(2330000, 600);
        cm.gainItem(2330000, 600);
        cm.gainItem(2330000, 600);
        cm.sendOk("轉職成功 ! 請去開創天下吧.");
        cm.dispose();
    } else if (status == 11) {
        cm.sendNextPrev("你可以選擇你要轉職成為一位 #r打手#k, #r槍手#k.")
    } else if (status == 12) {
        cm.askAcceptDecline("但是我必須先測試你,你準備好了嗎 ?");
    } else if (status == 13) {
        cm.sendSimple("你想要成為什麼 ? #b\r\n#L0#打手#l\r\n#L1#槍手#l#k");
    } else if (status == 14) {
        var jobName;
        if (selection == 0) {
            jobName = "打手";
            MapId = "108000502";
        } else if (selection == 1) {
            jobName = "槍手";
            MapId = "108000500";
        }
        cm.sendYesNo("你真的要成為一位 #r" + jobName + "#k?");
    } else if (status == 15) {
        cm.warp(MapId);
        cm.sendOk("請去找 #b海盜轉職教官#k . 他會幫助你的.");
        cm.dispose();
    } else if (status == 21) {
        cm.sendSimple("你想要成為什麼 ? #b\r\n#L0#打手#l\r\n#L1#槍手#l#k");
    } else if (status == 22) {
        var jobName;
        if (selection == 0) {
            jobName = "打手";
            job = 510;
        } else if (selection == 1) {
            jobName = "槍手";
            job = 520;
        }
        cm.sendYesNo("你真的要成為一位 #r" + jobName + "#k?");
    } else if (status == 23) {
        cm.changeJob(job);
        if (cm.haveItem(4031857) && cm.haveItem(4031012, 1)) {
            cm.gainItem(4031857, -15);
            cm.gainItem(4031012, -1);
            cm.sendOk("轉職成功 ! 請去開創天下吧.");
            cm.dispose();
        } else if (cm.haveItem(4031856) && cm.haveItem(4031012, 1)) {
            cm.gainItem(4031856, -15);
            cm.gainItem(4031012, -1);
            cm.sendOk("轉職成功 ! 請去開創天下吧.");
            cm.dispose();
        } else {
            cm.removeAll(4031857);
            cm.removeAll(4031856);
            cm.removeAll(4031012);
            cm.sendOk("轉職成功 ! 請去開創天下吧.");
            cm.dispose();
        }
    }
}