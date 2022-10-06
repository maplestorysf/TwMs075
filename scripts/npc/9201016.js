/* Salon Seamus
	Amoria Random Hair/Hair Color Change.
*/
var status = -1;
var beauty = 0;
var hair_Colo_new;

function start() {
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == 0 && status == 0) {
	cm.dispose();
	return;
    }
    if (mode == 1)
	status++;
    else
	status--;
    if (status == 0) {
	cm.sendSimple("嗨，我是#p9201016# 如果你有 #b#t5150019##k 或者 #b#t5151016##k 我可以幫助你~ \r\n#L0#使用: #i5150019##t5150019##l\r\n#L1#使用: #i5151016##t5151016##l");
    } else if (status == 1) {
	if (selection == 0) {
	    var hair = cm.getPlayerStat("HAIR");
	    hair_Colo_new = [];
	    beauty = 1;

	    if (cm.getPlayerStat("GENDER") == 0) {
		hair_Colo_new = [30580, 30590, 30280, 30670, 30410, 30200, 30050, 30230, 30290, 30300, 30250];
	    } else {
		hair_Colo_new = [31580, 31590, 31310, 31200, 31150, 31160, 31020, 31260, 31230, 31220, 31110];
	    }
	    for (var i = 0; i < hair_Colo_new.length; i++) {
		hair_Colo_new[i] = hair_Colo_new[i] + (hair % 10);
	    }
	    cm.sendYesNo("是否要使用 #b#t5150019##k");
	} else if (selection == 1) {
	    var currenthaircolo = Math.floor((cm.getPlayerStat("HAIR") / 10)) * 10;
	    hair_Colo_new = [];
	    beauty = 2;

	    for (var i = 0; i < 8; i++) {
		hair_Colo_new[i] = currenthaircolo + i;
	    }
	    cm.sendYesNo("是否要使用 #b#t5150019##k");
	}
    } else if (status == 2){
	if (beauty == 1){
	    if (cm.setRandomAvatar(5150019, hair_Colo_new) == 1) {
		cm.sendOk("享受！");
	    } else {
		cm.sendOk("痾...您貌似沒有#b#t5150019##k。");
	    }
	} else {
	    if (cm.setRandomAvatar(5151016, hair_Colo_new) == 1) {
		cm.sendOk("享受！");
	    } else {
		cm.sendOk("痾...您貌似沒有#b#t5151016##k。");
	    }
	}
	cm.dispose();
    }
}