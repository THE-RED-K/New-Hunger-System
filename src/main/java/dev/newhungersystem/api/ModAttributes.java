package dev.newhungersystem.api;

import dev.newhungersystem.NewHungerSystem;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组注册的自定义属性（对标 New Armor System 的 {@code fall_damage_multiplier}）。
 * <p>
 * 三个属性都是「效率乘区」，默认 1.0 = 无惩罚：
 * <ul>
 *   <li>{@code new_hunger_system:hunger_efficiency} —— 饥饿值低于阈值产生的效率（影响攻击力/攻速/移速/挖掘）</li>
 *   <li>{@code new_hunger_system:digestion_efficiency} —— 消化过载产生的效率，作用于
 *       <b>速度类</b>（攻击速度/移动速度/游泳速度/挖掘速度）</li>
 *   <li>{@code new_hunger_system:digestion_attack_efficiency} —— 消化过载产生的效率，
 *       只作用于<b>攻击力</b></li>
 * </ul>
 * 消化类两个属性各自有硬上限（见 {@link StatFactors#MAX_SPEED_PENALTY} /
 * {@link StatFactors#MAX_ATTACK_PENALTY}），读取时统一钳制，所以任何模组把属性压到再低
 * 也不会让速度惩罚超过 60%、攻击力惩罚超过 30%。
 * <p>
 * 本模组每 0.5s 把公式结果写进三者的<b>基础值</b>，再由它们推导出施加在原版属性上的修饰器。
 * 因此其它模组只要给这三个属性加修饰器（例如「兴奋剂」抵消饥饿惩罚），效果会自动流入
 * 攻击力/攻速/移速/挖掘/游泳，无需知道本模组内部 UUID，也不会和原版属性上的修饰器抢乘区。
 * <p>
 * 属性 {@code setSyncable(true)}，客户端可直接读到服务端算出的值（挖掘预测因此与服务端一致）。
 * 用 {@code /attribute <player> new_hunger_system:hunger_efficiency get} 可查。
 *
 * @author THEREDK
 */
public final class ModAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, NewHungerSystem.MOD_ID);

    /** 饥饿值惩罚效率 (1.0 = 无惩罚, 0.0 = 完全残废) */
    public static final RegistryObject<Attribute> HUNGER_EFFICIENCY = ATTRIBUTES.register("hunger_efficiency",
            () -> new RangedAttribute(translationKey("hunger_efficiency"), 1.0D, 0.0D, 1024.0D).setSyncable(true));

    /** 消化过载效率 —— 速度类 (1.0 = 无惩罚; 实际下限受 MAX_SPEED_PENALTY 保护) */
    public static final RegistryObject<Attribute> DIGESTION_EFFICIENCY = ATTRIBUTES.register("digestion_efficiency",
            () -> new RangedAttribute(translationKey("digestion_efficiency"), 1.0D, 0.0D, 1024.0D).setSyncable(true));

    /** 消化过载效率 —— 攻击力 (1.0 = 无惩罚; 实际下限受 MAX_ATTACK_PENALTY 保护) */
    public static final RegistryObject<Attribute> DIGESTION_ATTACK_EFFICIENCY = ATTRIBUTES.register(
            "digestion_attack_efficiency",
            () -> new RangedAttribute(translationKey("digestion_attack_efficiency"), 1.0D, 0.0D, 1024.0D)
                    .setSyncable(true));

    private ModAttributes() {
    }

    private static String translationKey(String name) {
        return "attribute." + NewHungerSystem.MOD_ID + "." + name;
    }

    /** 在 MOD 总线注册属性 */
    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
    }

    /** 把三个属性挂到玩家身上（不挂的话 {@code player.getAttribute} 返回 null） */
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, HUNGER_EFFICIENCY.get());
        event.add(EntityType.PLAYER, DIGESTION_EFFICIENCY.get());
        event.add(EntityType.PLAYER, DIGESTION_ATTACK_EFFICIENCY.get());
    }

    // ---------------------------------------------------------------- 写入 (服务端)

    public static void setHungerEfficiency(Player player, double value) {
        setBase(player.getAttribute(HUNGER_EFFICIENCY.get()), value);
    }

    public static void setDigestionEfficiency(Player player, double value) {
        setBase(player.getAttribute(DIGESTION_EFFICIENCY.get()), value);
    }

    public static void setDigestionAttackEfficiency(Player player, double value) {
        setBase(player.getAttribute(DIGESTION_ATTACK_EFFICIENCY.get()), value);
    }

    private static void setBase(AttributeInstance instance, double value) {
        if (instance != null && Math.abs(instance.getBaseValue() - value) > 1e-4) {
            instance.setBaseValue(value);
        }
    }

    // ---------------------------------------------------------------- 读取 (两侧通用)

    /** 属性是否已挂到该玩家（正常情况下恒为 true） */
    public static boolean available(Player player) {
        return player.getAttribute(HUNGER_EFFICIENCY.get()) != null;
    }

    /** 含其它模组修饰器的最终饥饿效率 */
    public static double hungerEfficiency(Player player) {
        AttributeInstance instance = player.getAttribute(HUNGER_EFFICIENCY.get());
        return instance == null ? 1.0D : instance.getValue();
    }

    /**
     * 含其它模组修饰器的最终消化效率（速度类）。
     * <p>
     * 读取时钳制到 {@code 1 - MAX_SPEED_PENALTY} 以上，保证 60% 的惩罚上限不可被绕过。
     */
    public static double digestionEfficiency(Player player) {
        AttributeInstance instance = player.getAttribute(DIGESTION_EFFICIENCY.get());
        double value = instance == null ? 1.0D : instance.getValue();
        return Mth.clamp(value, 1.0D - StatFactors.MAX_SPEED_PENALTY, 1024.0D);
    }

    /**
     * 含其它模组修饰器的最终消化效率（攻击力）。
     * <p>
     * 读取时钳制到 {@code 1 - MAX_ATTACK_PENALTY} 以上，保证 30% 的惩罚上限不可被绕过。
     */
    public static double digestionAttackEfficiency(Player player) {
        AttributeInstance instance = player.getAttribute(DIGESTION_ATTACK_EFFICIENCY.get());
        double value = instance == null ? 1.0D : instance.getValue();
        return Mth.clamp(value, 1.0D - StatFactors.MAX_ATTACK_PENALTY, 1024.0D);
    }

    /** 攻速/移速/游泳/挖掘 使用的合成效率 (饥饿 × 消化速度效率) */
    public static double combinedEfficiency(Player player) {
        return hungerEfficiency(player) * digestionEfficiency(player);
    }

    /** 攻击力使用的合成效率 (饥饿 × 消化攻击效率) */
    public static double combinedAttackEfficiency(Player player) {
        return hungerEfficiency(player) * digestionAttackEfficiency(player);
    }

    /** 把三个属性复位到 1.0（清理惩罚时用） */
    public static void reset(Player player) {
        setHungerEfficiency(player, 1.0D);
        setDigestionEfficiency(player, 1.0D);
        setDigestionAttackEfficiency(player, 1.0D);
    }
}
