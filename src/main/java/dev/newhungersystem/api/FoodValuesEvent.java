package dev.newhungersystem.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

/**
 * 客户端"显示值"事件 (对标 AppleSkin {@code FoodValuesEvent})。
 * <p>
 * AppleSkin 在 HUD/Tooltip 渲染时广播该事件，让客户端模组/整合包按物品改写预览值。
 * 本模组原本只有服务端的 {@link FoodDigestionEvent}，客户端拿不到入口；本事件补上这一缺口。
 * <p>
 * 广播时机：手持食物 HUD 预显、食物 Tooltip 收集、血条回血估算各自读取显示值时。
 * 三者都通过 {@link FoodHelper#postFoodValuesEvent} 构造并广播，因此同一物品在三处
 * 会得到一致的预览数值。监听器只需替换 {@link #modifiedFoodValues}：
 *
 * <pre>{@code
 * @SubscribeEvent
 * public static void onFoodValues(FoodValuesEvent event) {
 *     if (event.itemStack.is(Items.GOLDEN_CARROT)) {
 *         event.modifiedFoodValues = new FoodValues(12.0F, 6.0F);
 *     }
 * }
 * }</pre>
 *
 * 本事件是纯显示层修改，<b>不会</b>改变实际进食结算；要改实际收益请用
 * {@link FoodDigestionEvent} (服务端) 或 {@link FoodDigestionValues} 的覆盖配置。
 */
public final class FoodValuesEvent extends Event {

    /** 未修改前的默认显示值 (来自配置覆盖 > 代码登记 > nutrition × digestionPerNutrition)；可改写 */
    public FoodValues defaultFoodValues;
    /** 监听器可替换为任意新值；渲染代码只读取这个字段 */
    public FoodValues modifiedFoodValues;
    public final ItemStack itemStack;
    public final Player player;

    public FoodValuesEvent(Player player, ItemStack itemStack,
                           FoodValues defaultFoodValues, FoodValues modifiedFoodValues) {
        this.player = player;
        this.itemStack = itemStack;
        this.defaultFoodValues = defaultFoodValues;
        this.modifiedFoodValues = modifiedFoodValues;
    }
}
