var status = -1;

function start() {
	status = -1;
	action(1, 0, 0);
}

function action(mode, type, selection) {
	if (mode == 1)
		status++;
	else
		status--;
	if (status == 0) {
		cm.removeAll(4001117);
		cm.removeAll(4001120);
		cm.removeAll(4001121);
		cm.removeAll(4001122);
		cm.sendSimple("#b#L0#我要離開#l\r\n#L1#我要兌換海賊王帽子#l#k");
	} else if (status == 1) {
		if (selection == 0) {
			cm.warp(251010404, 0);
		} else if (selection == 1) {
			cm.sendSimple("您好您需要什麼幫忙呢?\r\n#L1#我要換#b#t1002571##i1002571##k#l\r\n#L2#我要換#b#t1002572##i1002572##k(#t4031435# x 30)#l\r\n#L3#我要換#b#t1002573##i1002573##k(#t4031435# x 150)#l\r\n#L4#我要換#b#t1002574##i1002573##k(#t4031435# x 300)#l");
		}
	} else if (status == 2) {
		switch (selection) {
		case 1:
			if (cm.haveItem(1002571, 1)) {
				cm.sendNext("請確認身上有帽子！");
				cm.dispose();
				return;
			}
			if (cm.canHold(1002571)) {
				cm.sendNext("來免費給你一頂請好好珍惜0.0");
				cm.gainItem(1002571, 1);
			} else {
				cm.sendNext("請確認是否有足夠的空間。");
			}
			break;
		case 2:
			if (cm.haveItem(1002572, 1) || !cm.haveItem(4031435, 30) || !cm.haveItem(1002571, 1)) {
				cm.sendNext("請確認身上是否有帽子或者碎片不足！");
				cm.dispose();
				return;
			}
			if (cm.canHold(1002572)) {
				cm.sendNext("來免費給你一頂請好好珍惜0.0");
				cm.gainItem(1002572, 1, true);
				cm.gainItem(1002571, -1);
				cm.gainItem(4031435, -30);
			} else {
				cm.sendNext("請確認是否有足夠的空間。");
			}
			break;
		case 3:
			if (cm.haveItem(1002573, 1) || !cm.haveItem(4031435, 150) || !cm.haveItem(1002572, 1)) {
				cm.sendNext("請確認身上是否有帽子或者碎片不足！");
				cm.dispose();
				return;
			}
			if (cm.canHold(1002573)) {
				cm.sendNext("來免費給你一頂請好好珍惜0.0");
				cm.gainItem(1002573, 1, true);
				cm.gainItem(4031435, -150);
			} else {
				cm.sendNext("請確認是否有足夠的空間。");
			}
			break;
		case 4:
			if (cm.haveItem(1002574, 1) || !cm.haveItem(4031435, 300) || !cm.haveItem(1002573, 1)) {
				cm.sendNext("請確認身上是否有帽子或者碎片不足！");
				cm.dispose();
				return;
			}
			if (cm.canHold(1002574)) {
				cm.sendNext("來免費給你一頂請好好珍惜0.0");
				cm.gainItem(1002574, 1, true);
				cm.gainItem(4031435, -300);
			} else {
				cm.sendNext("請確認是否有足夠的空間。");
			}
			break;
		}
		cm.dispose();
	}

}
