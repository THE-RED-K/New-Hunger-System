package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 进食获得的消化值事件（对标 AppleSkin 的 {@code FoodValuesEvent}）。
 * <p>
 * 玩家吃完食物后、写入消化 Capability <b>之前</b>在服务端广播。默认值：
 * <ul>
 *   <li>消化值 = {@code nutrition}（或配置覆盖 / 代码登记值）；</li>
 *   <li>消化饱和度 = 消化值 × {@code saturationModifier} × 2.0F
 *       （与原版 {@code FoodData.eat()} 的 {@code nutrition × saturationModifier × 2} 同构）。</li>
 * </ul>
 * 监听器可改这两个值以做数据驱动（按物品/标签调整、按状态加成等），
 * 或 {@link #setCanceled(boolean)} 取消本次进食的消化收益。
 * <p>
 * 原版直接加到 FoodData 的饥饿值/饱和度已被本模组还原，事件只影响消化侧。
 *
 * @author THEREDK
 */
@Cancelable
public final class FoodDigestionEvent extends Event {

    private final Player player;
    private final ItemStack itemStack;
    private final FoodProperties foodProperties;
    private float digestion;
    private float digestionSaturation;

    public FoodDigestionEvent(Player player, ItemStack itemStack, FoodProperties foodProperties,
                              float digestion, float digestionSaturation) {
        this.player = player;
        this.itemStack = itemStack;
        this.foodProperties = foodProperties;
        this.digestion = digestion;
        this.digestionSaturation = digestionSaturation;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public FoodProperties getFoodProperties() {
        return foodProperties;
    }

    /** 默认（未修改前）的消化值 = 食物 nutrition */
    public float getDefaultDigestion() {
        return foodProperties.getNutrition();
    }

    public float getDigestion() {
        return digestion;
    }

    public void setDigestion(float digestion) {
        this.digestion = digestion;
    }

    public float getDigestionSaturation() {
        return digestionSaturation;
    }

    public void setDigestionSaturation(float digestionSaturation) {
        this.digestionSaturation = digestionSaturation;
    }
}
