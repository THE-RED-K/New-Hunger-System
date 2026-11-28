package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 饥饿值归零后的饿死伤害事件（每次伤害间隔触发一次）。
 * <p>
 * 可修改伤害量或取消本次伤害。取消时计时器已归零，下一次伤害会在完整的
 * {@code starveDamageIntervalTicks} 之后再次尝试，不会每 tick 重试。
 *
 * @author THEREDK
 */
@Cancelable
public class StarvationDamageEvent extends Event {

    private final Player player;
    private float amount;

    public StarvationDamageEvent(Player player, float amount) {
        this.player = player;
        this.amount = Math.max(0.0F, amount);
    }

    public Player getPlayer() {
        return player;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = Math.max(0.0F, amount);
    }
}
