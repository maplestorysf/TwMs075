function enter(pi) {
    if (pi.getEvent("向上攀升").isRunning() && pi.getEvent("向上攀升").isCharCorrect(pi.getPortal().getName(), pi.getMapId())) {
        pi.warp(pi.getMapId() == 109030003 ? 109050000 : (pi.getMapId() + 1), 0);
    } else {
        pi.warpS(pi.getMapId(), 0);
    }
}
