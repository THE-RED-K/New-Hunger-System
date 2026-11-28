package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/**
 * 消化转化速率事件：每次（每 0.5s）计算基础速率时触发。
 * <p>
 * 已包含药水倍率（助消化 / 消化不良 / 瞬间消化），但<b>不含</b>过载加速
 * （过载加速在事件之后乘算，见 {@link StatFactors#digConversionMultiplier}）。
 * 监听器可以修改基础速率，例如让某个器官/装备加快或减慢消化。
 *
 * @author THEREDK
 */
public class DigestionRateEvent extends Event {

    private final Player player;
    private final Digestion digestion;
    private float rate;

    public DigestionRateEvent(Player player, Digestion digestion, float rate) {
        this.player = player;
        this.digestion = digestion;
        this.rate = rate;
    }

    public Player getPlayer() {
        return player;
    }

    public Digestion getDigestion() {
        return digestion;
    }

    /** 基础转化速率（每 0.5s 的消化值减少量） */
    public float getRate() {
        return rate;
    }

    public void setRate(float rate) {
        this.rate = Math.max(0.0F, rate);
    }
}
