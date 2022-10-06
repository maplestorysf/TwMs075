/* Author: Xterminator
	NPC Name: 		Peter
	Map(s): 		Maple Road: Entrance - Mushroom Town Training Camp (3)
	Description: 	Takes you out of Entrace of Mushroom Town Training Camp
*/
var status = -1;

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    mode == 1 ? status++ : status--;
	
    if (status == 0) {
        cm.sendNext("你已經完成了所有的基本訓練。做得很好！你看起來似乎已經準備好開始冒險的旅程了，好。那麼我就送你到下個地圖吧！");
    } else if (status == 1) {
        cm.sendNextPrev("但是你要記住，一旦離開這裡，你將會生在野生怪物的世界，那麼再見了！");
    } else if (status == 2) {
        cm.warp(40000, 0);
        cm.gainExp(3);
        cm.dispose();
    }
}