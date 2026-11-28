package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * 食物 → 消化值的默认解析事件。
 * <p>
 * 在「配置覆盖 / 代码登记 / nutrition × digestionPerNutrition」算出默认值之后、
 * 任何消费方（进食结算、HUD、tooltip、回血预估）读取之前触发。
 * 与 {@link FoodDigestionEvent} 的区别：
 * <ul>
 *   <li>本事件作用于<b>默认值解析阶段</b>，修改后会同时影响 tooltip / HUD 与实际进食；</li>
 *   <li>{@link FoodDigestionEvent} 只在真正进食结算时触发，且可取消。</li>
 * </ul>
 *
 * @author THEREDK
 */
public class FoodDigestionValueEvent extends Event {

    private final Player player;
    private final ItemStack stack;
    private final FoodProperties food;
    private float digestion;
    private float digestionSaturation;

    public FoodDigestionValueEvent(Player player, ItemStack stack, FoodProperties food,
                                   float digestion, float digestionSaturation) {
        this.player = player;
        this.stack = stack;
        this.food = food;
        this.digestion = digestion;
        this.digestionSaturation = digestionSaturation;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getStack() {
        return stack;
    }

    public FoodProperties getFood() {
        return food;
    }

    public float getDigestion() {
        return digestion;
    }

    public void setDigestion(float digestion) {
        this.digestion = Math.max(0.0F, digestion);
    }

    public float getDigestionSaturation() {
        return digestionSaturation;
    }

    public void setDigestionSaturation(float digestionSaturation) {
        this.digestionSaturation = Math.max(0.0F, digestionSaturation);
    }
}
