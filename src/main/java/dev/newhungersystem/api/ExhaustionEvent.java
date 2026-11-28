package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 活动消耗事件：本模组管理的每一项消耗在写入原版消耗值之前触发。
 * <p>
 * 可修改消耗量，也可取消整次消耗（例如装备减耗、种族特性、难度修正）。
 * 只覆盖本模组接管的路径（移动、跳跃、攻击、受伤、挖掘、直接饥饿消耗）；
 * 其它模组直接调用 {@code Player#causeFoodExhaustion} 的消耗不会经过本事件。
 *
 * @author THEREDK
 */
@Cancelable
public class ExhaustionEvent extends Event {

    public enum Source {
        SWIM,
        WALK_UNDER_WATER,
        WALK_ON_WATER,
        SPRINT,
        CROUCH,
        WALK,
        CLIMB,
        ATTACK,
        HURT,
        SPRINT_JUMP,
        JUMP,
        BLOCK_BREAK,
        DIRECT_HUNGER,
        OTHER
    }

    private final Player player;
    private final Source source;
    private float amount;

    public ExhaustionEvent(Player player, float amount, Source source) {
        this.player = player;
        this.amount = Math.max(0.0F, amount);
        this.source = source;
    }

    public Player getPlayer() {
        return player;
    }

    public Source getSource() {
        return source;
    }

    /** 消耗值（与 {@code maxExhaustion} 同单位） */
    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = Math.max(0.0F, amount);
    }
}
