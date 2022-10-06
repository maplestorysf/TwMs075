/* Kedrick
Fishking King NPC
 */
var status = -1;
var sel;

function action(mode, type, selection) {
	if (mode == 1) {
		status++;
	} else {
		status--;
	}

	if (status == 0) {
		cm.sendSimple("有什麼需要幫忙的嗎??\n\r #b#L0#進入釣魚場。#l \n\r #L1#買些魚餌。#l \n\r #L2#購買釣魚椅子。#l \n\r #L3#使用高級魚餌。#l \n\r #L4#教我如何釣魚。#l");
	} else if (status == 1) {
		sel = selection;
		if (sel == 0) {
			if (cm.haveItem(5340000) || cm.haveItem(5340001)) {
				if (cm.haveItem(3011000)) {
					cm.saveLocation("FISHING");
					cm.warp(741000200, 0);
					cm.dispose();
				} else {
					cm.sendNext("你必須有魚餌才能釣魚!");
					cm.safeDispose();
				}
			} else {
				cm.sendNext("你必須有釣竿才能釣魚!");
				cm.safeDispose();
			}
		} else if (sel == 1) {
			cm.sendYesNo("額小兄弟，你要購買魚餌阿來吧我提供最新鮮的魚餌30萬楓幣就能買一組魚餌");
		} else if (sel == 2) {
			if (cm.haveItem(3011000)) {
				cm.sendNext("你已經有釣魚椅子了！");
			} else {
				if (cm.canHold(3011000) && cm.getMeso() >= 50000) {
					cm.gainMeso(-50000);
					cm.gainItem(3011000, 1);
					cm.sendNext("開心釣魚去吧！");
				} else {
					cm.sendOk("請確認你的背包空間是否足夠，或者你是否有足夠的楓幣。");
				}
			}
			cm.safeDispose();
		} else if (sel == 3) {
			if (cm.canHold(2300001, 480) && cm.haveItem(5350000, 1)) {
				if (!cm.haveItem(2300001, 480)) {
					cm.gainItem(2300001, 120);
					cm.gainItem(5350000, -1);
					cm.sendNext("開心釣魚去吧！");
				} else {
					cm.sendNext("你已經有魚餌了！");
				}
			} else {
				cm.sendOk("請確認你的背包空間是否足夠，或者你是否有高級魚餌。");
			}
			cm.safeDispose();
		} else if (sel == 4) {
			cm.sendOk("你必須10等以上然後帶著普通or高級魚餌 還有釣竿和釣魚用的椅子來找我進入釣魚場接著坐下椅子就可以開始釣魚了！");
			cm.safeDispose();
		}
	} else if (status == 2) {
		if (sel == 1) {
			if (cm.canHold(2300000, 120) && cm.getMeso() >= 300000) {
				if (!cm.haveItem(2300000)) {
					cm.gainMeso(-300000);
					cm.gainItem(2300000, 120);
					cm.sendNext("開心釣魚去吧！");
				} else {
					cm.sendNext("你已經有一組魚餌了！");
				}
			} else {
				cm.sendOk("請確認你的背包空間是否足夠，或者你是否有足夠的楓幣。");
			}
			cm.safeDispose();
		}
	}
}
