package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/**
 * 消化值 / 消化饱和度溢出事件。
 * <p>
 * 当一次 {@code addDigestion} / {@code addDigestionSaturation}（进食、饱和效果、API 写入等）
 * 请求加入的量超过上限时，超出部分原本会被 clamp 静默丢弃；现在改为在 Forge 总线上广播本事件，
 * 让整合包/其它模组自行决定怎么处理（转饥饿值、吞掉、记录、转缓存…）。
 * <p>
 * 事件在服务端玩家数据被 clamp <b>之后</b>同步发出，因此 {@code accepted}/{@code overflow} 已是最终值。
 * 若某个监听器已经完整消费了这次溢出，可调用 {@link #setHandled(boolean)} 标记，
 * 内置溢出缓存（{@code OverflowCacheHandler}，LOWEST 优先级）会跳过它。
 *
 * @author THEREDK
 */
public final class DigestionOverflowEvent extends Event {

    public enum Type {
        DIGESTION,
        DIGESTION_SATURATION
    }

    private final Player player;
    private final Digestion data;
    private final Type type;
    /** 本次请求加入的量（可能为负，表示消耗） */
    private final float requested;
    /** 实际被吸收的量 */
    private final float accepted;
    /** 被 clamp 丢弃的量（&gt; 0 时本事件才有意义） */
    private final float overflow;
    private boolean handled;

    public DigestionOverflowEvent(Player player, Digestion data, Type type,
                                  float requested, float accepted, float overflow) {
        this.player = player;
        this.data = data;
        this.type = type;
        this.requested = requested;
        this.accepted = accepted;
        this.overflow = overflow;
    }

    public Player getPlayer() {
        return player;
    }

    /** 玩家当前的消化 Capability（可直接读写，例如把溢出转成饥饿值后再写回缓存） */
    public Digestion getData() {
        return data;
    }

    public Type getType() {
        return type;
    }

    public float getRequested() {
        return requested;
    }

    public float getAccepted() {
        return accepted;
    }

    public float getOverflow() {
        return overflow;
    }

    public boolean isHandled() {
        return handled;
    }

    /** 标记该溢出已被消费；内置缓存将不再重复处理 */
    public void setHandled(boolean handled) {
        this.handled = handled;
    }
}
