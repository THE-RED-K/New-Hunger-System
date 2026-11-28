package dev.newhungersystem.api;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;

/**
 * 食物判定工具 (对齐 AppleSkin {@code FoodHelper})。
 * <p>
 * - {@link #isRotten}：食物 effects 里只要存在一个 {@link MobEffectCategory#HARMFUL}
 *   效果，就视为"腐坏/有害食物" (腐肉 = HUNGER、河豚 = POISON 等)，切 rotten 贴图组。
 * - {@link #canConsume} / {@link #getHeldFoodStack}：用 {@code player.canEat(canAlwaysEat)}
 *   做可食用门槛，与 AppleSkin 一致；主手不可吃才回退副手。
 */
public final class FoodHelper {

    private FoodHelper() {
    }

    public static boolean isFood(ItemStack stack, Player player) {
        return !stack.isEmpty() && stack.getItem().getFoodProperties(stack, player) != null;
    }

    /**
     * AppleSkin {@code FoodHelper.canConsume} 同款门槛: 先判食物，再用
     * {@link Player#canEat(boolean)} 判"此刻能不能吃"。饥饿值满（且非 alwaysEat）
     * 或旁观模式下返回 false，HUD 预显不闪。
     */
    public static boolean canConsume(ItemStack stack, Player player) {
        if (!isFood(stack, player) || player == null) {
            return false;
        }
        FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        return food != null && player.canEat(food.canAlwaysEat());
    }

    /**
     * 手持食物: 主手可吃则主手，否则（按配置）回退副手。
     * AppleSkin 语义是"主手不可吃 → 换副手"，不是"主手不是食物 → 才看副手"。
     */
    public static ItemStack getHeldFoodStack(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (canConsume(mainHand, player)) {
            return mainHand;
        }
        if (ClientConfig.offhand()) {
            ItemStack offHand = player.getOffhandItem();
            if (canConsume(offHand, player)) {
                return offHand;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 构造默认显示值并广播 {@link FoodValuesEvent}，返回处理后的事件。
     * <p>
     * 默认值与进食结算同源：配置覆盖 &gt; 代码登记 &gt; nutrition × digestionPerNutrition，
     * 消化饱和度 = 消化值 × saturationModifier × 2。客户端模组可在事件里替换
     * {@link FoodValuesEvent#modifiedFoodValues}，HUD / Tooltip / 回血估算都读取它。
     *
     * @return 事件；非食物或 player 为 null 时返回 {@code null}
     */
    public static FoodValuesEvent postFoodValuesEvent(Player player, ItemStack stack) {
        if (player == null || !isFood(stack, player)) {
            return null;
        }
        FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        if (food == null) {
            return null;
        }
        FoodValues defaults = DigestionHelper.defaultFoodValues(player, stack, food);
        FoodValues values = new FoodValues(defaults.digestion, defaults.digestionSaturation);
        FoodValuesEvent event = new FoodValuesEvent(player, stack, values, values);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    /** 食物是否带任意一个 HARMFUL 效果；非食物/无效果返回 false */
    public static boolean isRotten(ItemStack stack, Player player) {
        if (!isFood(stack, player) || player == null) {
            return false;
        }
        FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        if (food == null) {
            return false;
        }
        for (Pair<MobEffectInstance, Float> pair : food.getEffects()) {
            MobEffectInstance effect = pair.getFirst();
            if (effect != null && effect.getEffect() != null
                    && effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                return true;
            }
        }
        return false;
    }
}
