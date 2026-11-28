package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/**
 * 呕吐完成后的通知事件（不可取消）。
 * <p>
 * 此时消化值 / 消化饱和度 / 进位缓存已被清空、反胃效果已施加、同步已标记。
 * 与 {@link DigestionVomitEvent} 的区别：后者在呕吐<b>之前</b>触发且可取消。
 *
 * @author THEREDK
 */
public class DigestionVomitPostEvent extends Event {

    private final Player player;
    private final Digestion digestion;

    public DigestionVomitPostEvent(Player player, Digestion digestion) {
        this.player = player;
        this.digestion = digestion;
    }

    public Player getPlayer() {
        return player;
    }

    public Digestion getDigestion() {
        return digestion;
    }
}
