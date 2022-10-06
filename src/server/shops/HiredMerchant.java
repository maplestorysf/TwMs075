package server.shops;

import java.util.concurrent.ScheduledFuture;
import client.inventory.Item;
import client.inventory.ItemFlag;
import constants.GameConstants;
import client.MapleCharacter;
import client.MapleClient;
import constants.ServerConstants;
import handling.channel.ChannelServer;
import java.util.LinkedList;
import java.util.List;
import server.MapleInventoryManipulator;
import server.MapleItemInformationProvider;
import server.Timer.EtcTimer;
import server.maps.MapleMapObjectType;
import tools.FileoutputUtil;
import tools.packet.CWvsContext;
import tools.packet.PlayerShopPacket;

public class HiredMerchant extends AbstractPlayerStore {

    public ScheduledFuture<?> schedule;
    private List<String> blacklist;
    private int storeid;
    private long start;

    public HiredMerchant(MapleCharacter owner, int itemId, String desc) {
        super(owner, itemId, desc, "", 3);
        start = System.currentTimeMillis();
        blacklist = new LinkedList<>();
        this.schedule = EtcTimer.getInstance().schedule(new Runnable() {

            @Override
            public void run() {
                if (getMCOwner() != null && getMCOwner().getPlayerShop() == HiredMerchant.this) {
                    getMCOwner().setPlayerShop(null);
                }
                removeAllVisitors(-1, -1);
                closeShop(true, true);
            }
        }, 1000 * 60 * 60 * 24);
    }

    public byte getShopType() {
        return IMaplePlayerShop.HIRED_MERCHANT;
    }

    public final void setStoreid(final int storeid) {
        this.storeid = storeid;
    }

    public List<MaplePlayerShopItem> searchItem(final int itemSearch) {
        final List<MaplePlayerShopItem> itemz = new LinkedList<>();
        for (MaplePlayerShopItem item : items) {
            if (item.item.getItemId() == itemSearch && item.bundles > 0) {
                itemz.add(item);
            }
        }
        return itemz;
    }

    @Override
    public void buy(MapleClient c, int item, short quantity) {
        final MaplePlayerShopItem pItem = items.get(item);
        final Item shopItem = pItem.item;
        final Item newItem = shopItem.copy();
        final short perbundle = newItem.getQuantity();
        final int theQuantity = (pItem.price * quantity);
        newItem.setQuantity((short) (quantity * perbundle));

        short flag = newItem.getFlag();

        if (ItemFlag.KARMA_EQ.check(flag)) {
            newItem.setFlag((short) (flag - ItemFlag.KARMA_EQ.getValue()));
        } else if (ItemFlag.KARMA_USE.check(flag)) {
            newItem.setFlag((short) (flag - ItemFlag.KARMA_USE.getValue()));
        }
        if (!c.getPlayer().canHold(newItem.getItemId())) {// 精靈商人BUG
            c.getPlayer().dropMessage(1, "您的背包滿了");
            c.sendPacket(CWvsContext.enableActions());
            return;
        }
        if (MapleInventoryManipulator.checkSpace(c, newItem.getItemId(), newItem.getQuantity(), newItem.getOwner())) {
            final int gainmeso = getMeso() + theQuantity - GameConstants.EntrustedStoreTax(theQuantity);
            if (gainmeso > 0) {
                setMeso(gainmeso);
                pItem.bundles -= quantity; // Number remaining in the store
                MapleInventoryManipulator.addFromDrop(c, newItem, false);
                bought.add(new BoughtItem(newItem.getItemId(), quantity, theQuantity, c.getPlayer().getName()));
                c.getPlayer().gainMeso(-theQuantity, false);
                saveItems();
                MapleCharacter chr = getMCOwnerWorld();
                if (chr != null) {
                    chr.dropMessage(5, "道具 " + MapleItemInformationProvider.getInstance().getName(newItem.getItemId()) + " (" + perbundle + ") × " + quantity + " 已被其他玩家購買，還剩下：" + pItem.bundles + " 個");
                }
                if (ServerConstants.LOG_MERCHANT) {
                    FileoutputUtil.logToFile("紀錄/監聽/購買紀錄/精靈商人.txt", "\r\n 時間　[" + FileoutputUtil.CurrentReadable_Time() + "] IP: " + c.getSessionIPAddress() + " 玩家 " + c.getPlayer().getName() + " 從  " + getOwnerName() + " 的精靈商人購買了" + MapleItemInformationProvider.getInstance().getName(newItem.getItemId()) + " (" + newItem.getItemId() + ") x" + quantity + " 單個價錢為 : " + pItem.price);
                }
            } else {
                c.getPlayer().dropMessage(1, "您的背包滿了，請檢查您的背包！");
                c.sendPacket(CWvsContext.enableActions());
            }
        } else {
            c.getPlayer().dropMessage(1, "Your inventory is full.");
            c.sendPacket(CWvsContext.enableActions());
        }
    }

    @Override
    public void closeShop(boolean saveItems, boolean remove) {
        if (schedule != null) {
            schedule.cancel(false);
        }
        if (saveItems) {
            saveItems();
            items.clear();
        }
        if (remove) {
            ChannelServer.getInstance(channel).removeMerchant(this);
            getMap().broadcastMessage(PlayerShopPacket.destroyHiredMerchant(getOwnerId()));
        }
        getMap().removeMapObject(this);
        schedule = null;
    }

    public int getTimeLeft() {
        return (int) ((System.currentTimeMillis() - start) / 1000);
    }

    public final int getStoreId() {
        return storeid;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.HIRED_MERCHANT;
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        if (isAvailable()) {
            client.sendPacket(PlayerShopPacket.destroyHiredMerchant(getOwnerId()));
        }
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        if (isAvailable()) {
            client.sendPacket(PlayerShopPacket.spawnHiredMerchant(this));
        }
    }

    public final boolean isInBlackList(final String bl) {
        return blacklist.contains(bl);
    }

    public final void addBlackList(final String bl) {
        blacklist.add(bl);
    }

    public final void removeBlackList(final String bl) {
        blacklist.remove(bl);
    }

    public final void sendBlackList(final MapleClient c) {
        c.sendPacket(PlayerShopPacket.MerchantBlackListView(blacklist));
    }

    public final void sendVisitor(final MapleClient c) {
        c.sendPacket(PlayerShopPacket.MerchantVisitorView(visitors));
    }
}
