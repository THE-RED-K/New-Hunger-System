package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/**
 * 消化值 / 消化饱和度发生变化后的通知事件（不可取消、只读）。
 * <p>
 * 由 {@link Digestion#setDigestion} / {@link Digestion#setDigestionSaturation} 在值确实变化后广播，
 * 读档、重置等直接赋值不会触发。监听器在事件里再次写值不会递归广播。
 *
 * @author THEREDK
 */
public class DigestionChangedEvent extends Event {

    public enum Type {
        DIGESTION,
        DIGESTION_SATURATION
    }

    private final Player player;
    private final Digestion digestion;
    private final Type type;
    private final float oldValue;
    private final float newValue;

    public DigestionChangedEvent(Player player, Digestion digestion, Type type, float oldValue, float newValue) {
        this.player = player;
        this.digestion = digestion;
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Player getPlayer() {
        return player;
    }

    public Digestion getDigestion() {
        return digestion;
    }

    public Type getType() {
        return type;
    }

    public float getOldValue() {
        return oldValue;
    }

    public float getNewValue() {
        return newValue;
    }

    public float getDelta() {
        return newValue - oldValue;
    }
}
