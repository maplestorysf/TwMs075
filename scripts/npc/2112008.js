function action(mode, type, selection) {
	    cm.removeAll(4001130);
	    cm.removeAll(4001131);
	    cm.removeAll(4001132);
	    cm.removeAll(4001133);
	    cm.removeAll(4001134);
	    cm.removeAll(4001135);
	var em = cm.getEventManager(cm.getMapId() == 926100600 ? "Romeo" : "Juliet");
    if (em != null) {
	var itemid = cm.getMapId() == 926100600 ? 4001160 : 4001159;
	if (!cm.canHold(itemid, 1)) {
	    cm.sendOk("請空出一個其他欄位。");
	    cm.dispose();
	    return;
	}
	cm.gainItem(itemid, 1);
    }
    cm.gainExpR(90000);
    cm.warp(926100700,0);
    cm.dispose();
}