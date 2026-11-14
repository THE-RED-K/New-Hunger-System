package dev.newhungersystem.api;

import dev.newhungersystem.NewHungerSystem;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组的药水效果（图标在 {@code textures/mob_effect/<registry name>.png}）。
 *
 * <table>
 *   <tr><th>效果</th><th>作用</th></tr>
 *   <tr><td>{@code digestive_aid}</td><td>每级 +20% 消化转化速率</td></tr>
 *   <tr><td>{@code instant_digestion}</td><td>持续期间整体转化速率 ×10（与助消化/消化不良<b>叠乘</b>）</td></tr>
 *   <tr><td>{@code indigestion}</td><td>每级 -20% 消化转化速率</td></tr>
 * </table>
 *
 * <b>抵消与叠乘</b>：先算加成部分 {@code 1 + 0.2 × (助消化等级 − 消化不良等级)}（下限 0，同级两者正好抵消），
 * 瞬间消化再把结果乘 10。例如「瞬间消化 + 助消化 I」= 1.2 × 10 = 12 倍，「瞬间消化 + 消化不良 I」= 0.8 × 10 = 8 倍。
 * <p>
 * 速率再大也走同一条转化路径（{@link DigestionEvents} 每 0.5s 一次），并且始终受"容量"约束：
 * 转化量不会超过"饥饿值还能装多少"与"不把上层消化饱和度压掉"的较小值，所以既不会溢出浪费，
 * 也不会出现"一次清空消化值"的突兀观感。
 *
 * @author THEREDK
 */
public final class ModMobEffects {

    /** 速率型效果每级改变 20% 消化速率 */
    public static final float PER_LEVEL_RATE = 0.20F;

    /** 瞬间消化在加成之后再乘这个倍率 */
    public static final float INSTANT_RATE_MULTIPLIER = 10.0F;

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, NewHungerSystem.MOD_ID);

    /** 助消化: 每级 +20% 转化速率。颜色取图标 digestive_aid.png 的最亮本体色 (166,40,36) */
    public static final RegistryObject<MobEffect> DIGESTIVE_AID = MOB_EFFECTS.register("digestive_aid",
            () -> new RateEffect(MobEffectCategory.BENEFICIAL, 0xA62824));

    /** 瞬间消化: 转化速率 ×10。颜色取金边中间色 (233,188,66) */
    public static final RegistryObject<MobEffect> INSTANT_DIGESTION = MOB_EFFECTS.register("instant_digestion",
            () -> new MarkerEffect(MobEffectCategory.BENEFICIAL, 0xE9BC42));

    /** 消化不良: 每级 -20% 转化速率。颜色取图标 indigestion.png 的最亮本体色 (118,72,87) */
    public static final RegistryObject<MobEffect> INDIGESTION = MOB_EFFECTS.register("indigestion",
            () -> new RateEffect(MobEffectCategory.HARMFUL, 0x764857));

    private ModMobEffects() {
    }

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }

    /**
     * 当前转化速率倍率（默认 1.0）。
     * <ol>
     *   <li>加成：{@code 0.2 × (助消化等级 − 消化不良等级)}，同级互相抵消，结果下限 0；</li>
     *   <li>瞬间消化：存在时再 ×{@value #INSTANT_RATE_MULTIPLIER}（与上面的加成叠乘）。</li>
     * </ol>
     */
    public static float rateMultiplier(Player player) {
        float bonus = 0.0F;
        MobEffectInstance aid = player.getEffect(DIGESTIVE_AID.get());
        if (aid != null) {
            bonus += PER_LEVEL_RATE * (aid.getAmplifier() + 1);
        }
        MobEffectInstance bad = player.getEffect(INDIGESTION.get());
        if (bad != null) {
            bonus -= PER_LEVEL_RATE * (bad.getAmplifier() + 1);
        }
        float multiplier = Math.max(0.0F, 1.0F + bonus);
        if (player.hasEffect(INSTANT_DIGESTION.get())) {
            multiplier *= INSTANT_RATE_MULTIPLIER;
        }
        return multiplier;
    }

    /** 速率型效果: 只作为等级来源, 逻辑在 {@link DigestionEvents} 的转化里读取 */
    private static final class RateEffect extends MobEffect {

        private RateEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    /** 纯标记型效果 (瞬间消化): 本身不改属性、不 tick, 只被 {@link #rateMultiplier} 查询 */
    private static final class MarkerEffect extends MobEffect {

        private MarkerEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
