package dev.newhungersystem.api;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 内置溢出缓存 —— {@link DigestionOverflowEvent} 的一个消费者（默认关闭）。
 * <p>
 * 开启 {@code enableOverflowCache} 后：
 * <ul>
 *   <li>溢出的消化值 / 消化饱和度存入玩家 Capability 里的缓存（各自上限
 *       {@code overflowCacheCapacity}），不再被丢弃；</li>
 *   <li>呕吐被抑制（缓存取代呕吐，见 DigestionEvents）；</li>
 *   <li>每 0.5s 由 {@link #feed} 按 {@code baseDigestionRate} 把缓存回填进消化系统，
 *       只要消化侧腾出空间即可，形成"胃里排队"的效果。</li>
 * </ul>
 * 监听优先级取 LOWEST，保证其它模组先有机会处理溢出；被 {@code setHandled(true)} 标记的溢出不再入缓存。
 *
 * @author THEREDK
 */
public final class OverflowCacheHandler {

    private OverflowCacheHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDigestionOverflow(DigestionOverflowEvent event) {
        if (!HungerConfig.enableOverflowCache() || event.isHandled() || event.getOverflow() <= 0.0F) {
            return;
        }
        Digestion data = event.getData();
        if (event.getType() == DigestionOverflowEvent.Type.DIGESTION) {
            data.addOverflowDigestionCache(event.getOverflow());
        } else {
            data.addOverflowSaturationCache(event.getOverflow());
        }
        event.setHandled(true);   // 内置缓存已消费, 供后续 MONITOR 监听者识别
    }

    /**
     * 每 0.5s 从缓存回填到消化值 / 消化饱和度（在转化之后调用，此时已腾出空间）。
     *
     * @param rate 本 tick 允许回填的量（{@code baseDigestionRate}）
     * @return 是否有数据变动（决定是否需要同步客户端）
     */
    public static boolean feed(Digestion data, float rate) {
        if (!HungerConfig.enableOverflowCache() || rate <= 0.0F) {
            return false;
        }
        boolean changed = false;

        float room = Math.max(0.0F, Digestion.maxDigestion() - data.getDigestion());
        float transfer = Math.min(Math.min(data.getOverflowDigestionCache(), rate), room);
        if (transfer > 0.0F) {
            data.addOverflowDigestionCache(-transfer);
            data.addDigestion(transfer);           // transfer <= room, 不会再产生溢出
            changed = true;
        }

        float satRoom = Math.max(0.0F, Math.min(Digestion.maxDigestionSaturation(), data.getDigestion())
                - data.getDigestionSaturation());
        float satTransfer = Math.min(Math.min(data.getOverflowSaturationCache(), rate), satRoom);
        if (satTransfer > 0.0F) {
            data.addOverflowSaturationCache(-satTransfer);
            data.addDigestionSaturation(satTransfer);
            changed = true;
        }
        return changed;
    }
}
