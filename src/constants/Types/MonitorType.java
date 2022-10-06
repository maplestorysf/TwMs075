package constants.Types;

/**
 *
 * @author Windyboy
 */
public class MonitorType {

    // 已完成
    public final static int 聊天訊息 = 0;
    public final static int 私密訊息 = 1;
    public final static int 交易訊息 = 2;
    public final static int 精靈商人訊息 = 3;
    public final static int 角色登入訊息 = 4;
    public final static int 新角色登入訊息 = 5;
    public final static int 角色換頻訊息 = 6;
    public final static int 新角色換頻訊息 = 7;
    public final static int 角色創建訊息 = 8;
    public final static int 角色刪除訊息 = 9;
    public final static int 帳號註冊訊息 = 10;
    public final static int 帳號登入訊息 = 11;
    public final static int 卷軸訊息 = 12;
    public final static int 黑板訊息 = 13;
    public final static int 丟裝訊息 = 14;
    public final static int 撿裝訊息 = 15;

    // 未完成
    public final static int BOSS進場訊息 = 999;
    public final static int 倉庫訊息 = 999;

    public static String getStringByID(int id) {
        String name = "None";
        switch (id) {
            case 0:
                name = "聊天訊息";
                break;
            case 1:
                name = "私密訊息";
                break;
            case 2:
                name = "交易訊息";
                break;
            case 3:
                name = "精靈商人訊息";
                break;
            case 4:
                name = "角色登入訊息";
                break;
            case 5:
                name = "新角色登入訊息";
                break;
            case 6:
                name = "角色換頻訊息";
                break;
            case 7:
                name = "新角色換頻訊息";
                break;
            case 8:
                name = "角色創建訊息";
                break;
            case 9:
                name = "角色刪除訊息";
                break;
            case 10:
                name = "帳號註冊訊息";
                break;
            case 11:
                name = "帳號登入訊息";
                break;
            case 12:
                name = "卷軸訊息";
                break;
            case 13:
                name = "黑板訊息";
                break;
            case 14:
                name = "丟裝訊息";
                break;
            case 15:
                name = "撿裝訊息";
                break;
            default:
                break;
        }
        return name;
    }

}
