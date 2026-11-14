package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 消化值 → 饥饿值 / 消化饱和度 → 饱和度 的转化事件（每 0.5s 广播两次，各一种类型）。
 * <p>
 * 监听器可 {@link #setAmount(float)} 改本次转化量（例如加速/减速消化、按 biome 调整），
 * 或 {@link #setCanceled(boolean)} 完全跳过本 tick 的该种转化。
 * 修改后的量仍会被 clamp 到"当前拥有量"和"可容纳空间"，不可能凭空造出数值。
 *
 * @author THEREDK
 */
@Cancelable
public final class DigestionConversionEvent extends Event {

    public enum Type {
        /** 消化值 → 饥饿值（经 foodCarry 进位缓存） */
        DIGESTION,
        /** 消化饱和度 → 饱和度 */
        DIGESTION_SATURATION
    }

    private final Player player;
    private final Digestion data;
    private final Type type;
    private final float originalAmount;
    private float amount;

    public DigestionConversionEvent(Player player, Digestion data, Type type, float amount) {
        this.player = player;
        this.data = data;
        this.type = type;
        this.originalAmount = amount;
        this.amount = amount;
    }

    public Player getPlayer() {
        return player;
    }

    public Digestion getData() {
        return data;
    }

    public Type getType() {
        return type;
    }

    public float getOriginalAmount() {
        return originalAmount;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }
}
