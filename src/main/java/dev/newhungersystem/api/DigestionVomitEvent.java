package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 呕吐事件：消化值达到 {@code vomitThresholdPercent} 时、清空数据与给药水<b>之前</b>广播。
 * <p>
 * {@link #setCanceled(boolean)} 可阻止本次呕吐（消化值会保持在阈值以上，下一个 10-tick
 * 周期会再次触发本事件；监听器若想彻底解决，应同时通过 {@link #getData()} 下调消化值）。
 * 也可改反胃时长/等级，或把 {@code nauseaDuration} 设为 0 表示只清空不给效果。
 *
 * @author THEREDK
 */
@Cancelable
public final class DigestionVomitEvent extends Event {

    private final Player player;
    private final Digestion data;
    private final float digestionAtTrigger;
    private int nauseaDuration;
    private int nauseaAmplifier;

    public DigestionVomitEvent(Player player, Digestion data, float digestionAtTrigger,
                               int nauseaDuration, int nauseaAmplifier) {
        this.player = player;
        this.data = data;
        this.digestionAtTrigger = digestionAtTrigger;
        this.nauseaDuration = nauseaDuration;
        this.nauseaAmplifier = nauseaAmplifier;
    }

    public Player getPlayer() {
        return player;
    }

    public Digestion getData() {
        return data;
    }

    public float getDigestionAtTrigger() {
        return digestionAtTrigger;
    }

    public int getNauseaDuration() {
        return nauseaDuration;
    }

    public void setNauseaDuration(int nauseaDuration) {
        this.nauseaDuration = Math.max(0, nauseaDuration);
    }

    public int getNauseaAmplifier() {
        return nauseaAmplifier;
    }

    public void setNauseaAmplifier(int nauseaAmplifier) {
        this.nauseaAmplifier = Math.max(0, nauseaAmplifier);
    }
}
