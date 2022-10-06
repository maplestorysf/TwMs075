/* Brittany
	Henesys Random Hair/Hair Color Change.
*/
var status = -1;
var beauty = 0;
var hair_Colo_new;

function action(mode, type, selection) {
    if (mode == 0) {
	cm.dispose();
	return;
    } else {
	status++;
    }

    if (status == 0) {
	cm.sendSimple("嗨，我是#p9120101# 如果你有 #b#t5150008##k 或者 #b#t5151008##k 我就可以幫助你~ \r\n#L0#使用: #i5150008##t5150008##l\r\n#L1#使用: #i5151008##t5151008##l");
    } else if (status == 1) {
	if (selection == 0) {
	    var hair = cm.getPlayerStat("HAIR");
	    hair_Colo_new = [];
	    beauty = 1;

	    if (cm.getPlayerStat("GENDER") == 0) {
		hair_Colo_new = [30230, 30030, 30260, 30280, 30240, 30290, 30020, 30270, 30340, 30710];
	    } else {
		hair_Colo_new = [31310, 31300, 31050, 31040, 31160, 31100, 31410, 31030, 31550];
	    }
	    for (var i = 0; i < hair_Colo_new.length; i++) {
		hair_Colo_new[i] = hair_Colo_new[i] + (hair % 10);
	    }
	    cm.sendYesNo("是否要使用 #b#t5150008##k 來隨機亂抽？？");

	} else if (selection == 1) {
	    var currenthaircolo = Math.floor((cm.getPlayerStat("HAIR") / 10)) * 10;
	    hair_Colo_new = [];
	    beauty = 2;

	    for (var i = 0; i < 8; i++) {
		hair_Colo_new[i] = currenthaircolo + i;
	    }
	    cm.sendYesNo("是否要使用 #b#t5151008##k 來隨機亂抽？？");
	}
    } else if (status == 2){
	if (beauty == 1){
	    if (cm.setRandomAvatar(5150008, hair_Colo_new) == 1) {
		cm.sendOk("享受！");
	    } else {
		cm.sendOk("痾...你好像沒有#t5151009#。");
	    }
	} else {
	    if (cm.setRandomAvatar(5151008, hair_Colo_new) == 1) {
		cm.sendOk("享受！");
	    } else {
		cm.sendOk("痾...你好像沒有#t5151009#。");
	    }
	}
	cm.safeDispose();
    }
}
