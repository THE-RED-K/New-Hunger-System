package dev.newhungersystem.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

import java.util.Optional;

/**
 * 消化系统对外 API 门面 (供其它模组 / 整合包调用)。
 * <p>
 * 所有写操作都会同步做三件事，避免只改数据留下"脏状态"：
 * <ol>
 *   <li>改 Capability 数据（含 {@code foodCarry} 进位缓存与属性因子缓存）；</li>
 *   <li>清掉本模组施加的属性修饰器并按当前状态重算（{@link DigestionEvents#clearModifiers} /
 *       {@link DigestionEvents#refreshStats}）；</li>
 *   <li>服务端玩家即时同步到客户端 HUD（{@link ModNetwork#syncTo}）。</li>
 * </ol>
 * 若只想改数据、不触发上述副作用，可直接对 {@link Digestion} 调用其 setter / {@code reset()}。
 *
 * @author THEREDK
 */
public final class DigestionHelper {

    private DigestionHelper() {
    }

    /** 取玩家的消化 Capability；未附加时返回 null */
    public static Digestion get(Player player) {
        return player.getCapability(DigestionCapability.DIGESTION).orElse(null);
    }

    /** 取玩家的消化 Capability（Optional 形式，便于链式调用） */
    public static Optional<Digestion> optional(Player player) {
        return player.getCapability(DigestionCapability.DIGESTION).resolve();
    }

    /**
     * 完全重置：消化值 / 消化饱和度 / {@code foodCarry} 归零，属性修饰器清空后重算，同步客户端。
     * 不动原版饥饿值 / 饱和度。
     *
     * @return 玩家是否持有该 Capability
     */
    public static boolean reset(Player player) {
        return apply(player, false);
    }

    /** 重置为配置里的开局数值（等同新玩家），其余同 {@link #reset} */
    public static boolean resetToStart(Player player) {
        return apply(player, true);
    }

    /**
     * 增加消化值；超出上限的部分不再静默丢弃，而是广播 {@link DigestionOverflowEvent}。
     *
     * @return 实际被吸收的量
     */
    public static float addDigestion(Player player, float amount) {
        Digestion data = get(player);
        if (data == null) {
            return 0.0F;
        }
        float before = data.getDigestion();
        // 事件由 Digestion 数据层统一广播 (含第三方直接拿 Capability 写入的路径), 此处不重复发
        data.addDigestion(amount);
        return data.getDigestion() - before;
    }

    /**
     * 绕过饱和度、直接消耗饥饿值。
     * <p>
     * 消耗单位与 {@code causeFoodExhaustion} 相同（{@code maxExhaustion} 点消耗值 = 1 点饥饿值），
     * 但不再进入 {@code exhaustionLevel} 的“先烧饱和度”结算；小数部分累积在
     * {@link Digestion#getDirectHungerCarry()} 中，满 1 点才真正扣饥饿值。
     * 创造 / 旁观 / 客户端调用直接忽略；扣减后同步收敛饱和度以维持 {@code sat <= foodLevel}。
     *
     * @param exhaustionAmount 消耗值（与其它活动消耗同一单位）
     */
    public static void consumeHungerDirect(Player player, float exhaustionAmount) {
        if (player == null || exhaustionAmount <= 0.0F || player.isCreative() || player.isSpectator()
                || player.level().isClientSide()) {
            return;
        }
        Digestion data = get(player);
        if (data == null) {
            return;
        }
        float points = exhaustionAmount / Math.max(0.1F, HungerConfig.maxExhaustion());
        float carry = data.getDirectHungerCarry() + points;
        int whole = (int) carry;
        if (whole > 0) {
            carry -= whole;
            FoodData food = player.getFoodData();
            int newFood = Math.max(0, food.getFoodLevel() - whole);
            food.setFoodLevel(newFood);
            food.setSaturation(Math.min(food.getSaturationLevel(), newFood));
        }
        data.setDirectHungerCarry(carry);
    }

    /** 增加消化饱和度；超出上限部分由 {@link Digestion} 广播 {@link DigestionOverflowEvent} */
    public static float addDigestionSaturation(Player player, float amount) {
        Digestion data = get(player);
        if (data == null) {
            return 0.0F;
        }
        float before = data.getDigestionSaturation();
        data.addDigestionSaturation(amount);
        return data.getDigestionSaturation() - before;
    }

    /**
     * 代码登记层：给某物品指定绝对的进食消化值（模组用）。
     * <p>
     * 整合包可通过配置 {@code [food] digestionOverrides}（{@code "modid:item=value"}）覆盖本值，
     * 因此模组不应假设自己的值一定生效；最终值仍会经 {@link FoodDigestionEvent} 广播。
     *
     * @return 是否成功登记（物品为 null 或值为负时拒绝）
     */
    public static boolean registerFoodValue(net.minecraft.world.item.Item item, float digestion) {
        return FoodDigestionValues.register(item, digestion);
    }

    /**
     * 便捷入口：一次同时增加消化值与消化饱和度，并刷新属性/同步。
     * 等价于分别调用 {@link #addDigestion} / {@link #addDigestionSaturation} 后 {@code finish()}。
     *
     * @return 玩家是否持有该 Capability
     */
    public static boolean add(Player player, float digestion, float digestionSaturation) {
        if (get(player) == null) {
            return false;
        }
        addDigestion(player, digestion);
        addDigestionSaturation(player, digestionSaturation);
        finish(player);
        return true;
    }

    /** 直接设定消化值与消化饱和度（会被各自上限 clamp，溢出同样广播事件），并刷新属性/同步 */
    public static boolean set(Player player, float digestion, float digestionSaturation) {
        Digestion data = get(player);
        if (data == null) {
            return false;
        }
        addDigestion(player, digestion - data.getDigestion());
        // setDigestion 会按需下调饱和度, 这里按当前值再补到目标 (仍受限于消化值)
        addDigestionSaturation(player, digestionSaturation - data.getDigestionSaturation());
        finish(player);
        return true;
    }

    private static boolean apply(Player player, boolean toStart) {
        Digestion data = get(player);
        if (data == null) {
            return false;
        }
        if (toStart) {
            data.resetToStart();
        } else {
            data.reset();
        }
        finish(player);
        return true;
    }

    private static void finish(Player player) {
        DigestionEvents.clearModifiers(player);
        if (player instanceof ServerPlayer serverPlayer) {
            DigestionEvents.refreshStats(serverPlayer);
            ModNetwork.syncTo(serverPlayer);
        }
    }
}
