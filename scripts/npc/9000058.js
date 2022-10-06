/* global cm */

var status = -1;

function start() {
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode === 1) {
        status++;
    } else if (mode === 0) {
        status--;
    } else {
        cm.dispose();
        return;
    }

    if (status === 0) {
        if (!cm.isQuestFinished(29933)) {
            NewPlayer();
        }
        cm.sendSimple("開店可以擺飛鏢或彈丸哦~#b\r\n" +
                "#L2#我要打開藍色小箱子#l\r\n" +
                "#L3#當鋪裡的大蟾蜍錢包(100等以上才能領)解未來東京任務用#l\r\n" +
                "#L4#我要騎銀色豬豬!!#l\r\n" +
                "#L5#我要進行忍影瞬殺的任務(四轉盜賊限定)#l\r\n" +
                "#L6#我要刪除銀或金寶箱空白道具(並且補償一次道具)#l\r\n" +
                "#L7#我要完成燈泡不能接的任務#l\r\n" +
                /* "#L8#我領取廣播道具#ll\r\n" +
                 "#L9#我領取愛心廣播道具#\l\r\n" + 
                 "#L10#我領取骷簍廣播道具#l\r\n" +*/
                "#L11#我領取精靈商人#l\r\n" +
                "#L12#我要打恰吉#l\r\n" +
                "#L13#我要廣播道具#l\r\n" +
                "#L14#我要補學暴風神射 已有勿點#l\r\n" +
				"#L15#我要補學闇靈治癒 已有勿點#l\r\n" +
                "#L16#我要補學騎寵技能+馬鞍+龍族香水#l\r\n" +
				"#L17#我要買狼的生命水，1瓶1億，購買前請確認其他欄是否有空格");
    } else if (status === 1) {
        var level = cm.getPlayer().getLevel();
        if (selection === 2) {
            if (!cm.haveItem(4031307, 1)) {
                cm.sendOk("#b檢查一下背包有沒有藍色禮物盒哦");
                cm.dispose();
                return;
            }
            cm.gainItem(4031307, -1);
            cm.gainItem(2020020, 100);
            cm.sendOk("#b蛋糕不要吃太多~旅遊愉快~");
        } else if (selection === 3) {
            if (level < 100) {
                cm.sendOk("你的等級還不夠。");
                cm.dispose();
                return;
            }
            cm.gainItem(5252002, 1);
        } else if (selection === 4) {
            if (!cm.haveItem(4000264, 400) || !cm.haveItem(4000266, 400) || !cm.haveItem(4000267, 400) || level < 120 || !cm.canHold(1902001, 1)) {
                cm.sendOk("請檢查一下背包有沒有金色皮革４００個、木頭肩護帶４００個、骷髏肩護帶４００個,或者是你等級不夠");
                cm.dispose();
                return;
            }
            cm.gainItem(4000264, -400);
            cm.gainItem(4000266, -400);
            cm.gainItem(4000267, -400);
            cm.gainItem(1902001, 1);
            cm.sendOk("#b好好珍惜野豬~~");
        } else if (selection === 8 || selection === 9 || selection === 10) { //廣播
            var Item = 0;
            var amount = 0;
            var reqLevel = 0;
            var BossLog = '';
            switch (selection) {
                case 8:
                    Item = 5072000;
                    amount = 5;
                    BossLog = '1';
                    reqLevel = 1;
                    break;
                case 9:
                    Item = 5073000;
                    amount = 10;
                    BossLog = '30';
                    reqLevel = 30;
                    break;
                case 10:
                    Item = 5074000;
                    amount = 5;
                    BossLog = '70';
                    reqLevel = 70;
                    break;
            }
            if (level < reqLevel || cm.getPlayer().getBossLog(BossLog) > 0) {
                cm.sendNext("一天只能領一次或你的等級還不夠。");
                cm.dispose();
                return;
            }

            cm.setBossLog(BossLog);
            cm.gainItem(Item, amount);
            cm.sendNext("已經獲得#i" + Item + "#x" + amount + "。");
        } else if (selection === 11) { //商人
            if (level < 10 || cm.getPlayer().getBossLog('sell') > 1) {
                cm.sendOk("1天只能領一次或你的等級還不夠10等才能領唷。");
                cm.dispose();
                return;
            }
            cm.setBossLog('sell');
            cm.gainItem(5030000, 1);
        } else if (selection === 5) {
            if (cm.getJob() === 412) {
                cm.warp(910300000, 3);
                cm.spawnMonster(9300088, 6, -572, -1894);
            } else if (cm.getJob() === 422) {
                cm.warp(910300000, 3);
                cm.spawnMonster(9300088, 6, -572, -1894);
            } else {
                cm.sendOk("這是跟盜賊有關的事情哦");
            }
        } else if (selection === 6) {
            if (cm.haveItem(2070018)) {
                cm.removeAll(2070018);
                cm.gainItem(5490000, 1);
                cm.gainItem(4280000, 1);
                cm.sendOk("恭喜你刪除完畢並補償了金寶箱");
            } else if (cm.haveItem(1432036)) {
                cm.removeAll(1432036);
                cm.gainItem(5490001, 1);
                cm.gainItem(4280001, 1);
                cm.sendOk("恭喜你刪除完畢並補償了銀寶箱");
            } else {
                cm.sendOk("抱歉你沒有空白道具...");
            }
        } else if (selection === 7) {
            if (cm.getQuestStatus(29507) === 1) {
                cm.gainItem(1142082, 1);
                cm.forceCompleteQuest(29507);
            }
            cm.forceCompleteQuest(3083);
            cm.forceCompleteQuest(8248);
            cm.forceCompleteQuest(8249);
            cm.forceCompleteQuest(8510);
            cm.forceCompleteQuest(20527);
            cm.forceCompleteQuest(50246);
            cm.sendOk("完成任務。");
        } else if (selection === 12) {
            cm.warp(229010000);
			cm.dispose();
        } else if (selection === 13) {
            cm.dispose();
            cm.openNpc(9000056);
            return;
        } else if (selection === 14) {
            if (cm.getPlayer().getQuestStatus(6250) !== 2) {
                cm.sendNext("暴風神射");
                cm.dispose();
                return;
            } 
            cm.teachSkill(3121004, 0, 10);
            cm.sendNext("已經給您暴風神射技能")
                return;
            } else if (cm.getPlayer().getOneTimeLog("暴風神射補學") > 0) {
                cm.sendNext("本功能只能使用一次");
                cm.dispose();
                return;
            
           
        } else if (selection === 15) {
            if (cm.getPlayer().getQuestStatus(6291) !== 2) {
                cm.sendNext("靈魂的魔法陣");
                cm.dispose();
                return;
            } 
            cm.teachSkill(1320008, 0, 5);
            cm.sendNext("已經給您闇靈治癒技能")
        } else if (selection === 16) {
            if (cm.getPlayer().getQuestStatus(6002) !== 2) {
                cm.sendNext("請先完成怪物騎乘任務");
                cm.dispose();
                return;
            } else if (!cm.canHold(1912000) || !cm.canHold(4031509, 1)) {
                cm.sendNext("背包空間不足");
                cm.dispose();
                return;
            } else if (cm.getPlayer().getOneTimeLog("怪物騎乘任務補償") > 0) {
                cm.sendNext("本功能只能使用一次");
                cm.dispose();
                return;
            }
            cm.gainItem(1912000, 1);
            cm.gainItem(4031509, 1);
            cm.teachSkill(1004, 1, 1);
            cm.getPlayer().setOneTimeLog("怪物騎乘任務補償");
            cm.sendNext("已經給您#v4031509##v1912000#以及騎乘技能")
        } else if (selection === 17) {
            if (cm.getPlayer().getMeso() >= 100000000) {
                cm.gainMeso(-100000000);
                cm.gainItem(4032334, 1);
                cm.sendOk("感謝購買!");
                cm.dispose();
            } else {
                cm.sendOk("#d你楓幣不夠哦");
                cm.dispose();
        }
        cm.dispose();
		}
    } else {
        cm.dispose();
    }

}

function NewPlayer() {
    var item = [5000007, 2450000, 1002419, 5030000, 5100000, 5370000, 5180000, 5170000];
    var amount = [1, 10, 1, 1, 1, 1, 1, 1];
    var next = true;
    for (var i = 0; i < item.length; i++) {
        if (!cm.canHold(item[i], amount[i])) {
            next = false;
        }
    }
    if (!next) {
        cm.sendNext("背包空間不足以領新手獎勵唷。");
        cm.dispose();
        return;
    }
    cm.gainPet(5000007, "黑色小豬", 1, 0, 100, 0, 45);
    cm.gainItem(2450000, 10); //獵人的幸運
    cm.gainItemPeriod(1002419, 1, 30);//紅葉黑頭巾
    cm.gainItemPeriod(5030000, 1, 30);//精靈商人
    cm.gainItem(5100000, 1);//賀曲
    cm.gainItemPeriod(5370000, 1, 7);//黑板 7天
    cm.gainItemPeriod(5170000, 1, 30);//取寵物名
    cm.forceCompleteQuest(29933); //完成新手獎勵
    cm.sendNext("歡迎來到 屁屁谷 請使用 @help/@幫助 了解各式指令\r\n\r\n\r\n遊戲愉快^^");
}
