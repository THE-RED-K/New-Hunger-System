package dev.newhungersystem.api;

/**
 * 客户端缓存的消化数据 (由服务端 DigestionSyncPacket 同步), 供 HUD 读取。
 */
public final class ClientDigestionData {

    public static float digestion;
    public static float digestionSaturation;
    /** 消耗值 (原版不同步到客户端, 由我们自己的同步包带来) */
    public static float exhaustion;
    /** 直接饥饿账本 (0..1); 饱和度为 0 时 HUD 用它画第二条消耗条 */
    public static float directHungerCarry;

    /** 服务端权威上限 (配置 base + 加成); 收到同步后客户端以它为准, 避免专用服两侧配置不一致 */
    public static double serverMaxHunger;
    public static double serverMaxSaturation;
    public static double serverMaxDigestion;
    public static double serverMaxDigestionSaturation;
    public static boolean hasServerCaps;

    private ClientDigestionData() {
    }
}
