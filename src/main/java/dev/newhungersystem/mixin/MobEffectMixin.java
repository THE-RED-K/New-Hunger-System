package dev.newhungersystem.mixin;

import dev.newhungersystem.api.Digestion;
import dev.newhungersystem.api.DigestionCapability;
import dev.newhungersystem.api.DigestionHelper;
import dev.newhungersystem.api.HungerConfig;
import dev.newhungersystem.api.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原版把"饱和 / 饥饿"两个效果的逻辑集中在 MobEffect.applyEffectTick 里 (1.20.1 没有独立的
 * SaturationMobEffect / HungerMobEffect 类):
 * - HUNGER     -> player.causeFoodExhaustion(0.005F * (amplifier + 1))
 * - SATURATION -> player.getFoodData().eat(amplifier + 1, 1.0F)
 * <p>
 * 本模组在 {@code redirectMobEffects} 开启时改为让它们作用于消化值与消化饱和度
 * (消耗值换算用 {@code hungerEffectExhaustionPerTick} / {@code maxExhaustion} 配置)。
 * 这是非核心改动, 因此用 @Inject(HEAD) + cancel 取消原版行为, 不使用 @Overwrite。
 *
 * @author THEREDK
 */
@Mixin(MobEffect.class)
public abstract class MobEffectMixin {

    @Inject(method = "applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)V",
            at = @At("HEAD"), cancellable = true)
    private void newHungerSystem$routeToDigestion(LivingEntity entity, int amplifier, CallbackInfo ci) {
        if (!HungerConfig.redirectMobEffects()
                || !(entity instanceof Player player) || player.level().isClientSide) {
            return;
        }
        MobEffect self = (MobEffect) (Object) this;
        if (self != MobEffects.SATURATION && self != MobEffects.HUNGER) {
            return;
        }
        Digestion digestion = player.getCapability(DigestionCapability.DIGESTION).orElse(null);
        if (digestion == null) {
            return;
        }
        if (self == MobEffects.SATURATION) {
            // 对应原版 eat(amplifier + 1, 1.0F):
            //   饥饿部分 = (amp+1) -> 消化值;
            //   饱和度部分 = (amp+1) × 1.0 × 2.0 -> 消化饱和度 (缺了 ×2/×(amp+1) 会让 Saturation II 与 I 等效)
            DigestionHelper.addDigestion(player, amplifier + 1);
            DigestionHelper.addDigestionSaturation(player, 2.0F * (amplifier + 1));
            // 瞬时效果, 立即同步; HUNGER 每 tick 都触发, 交给 DigestionEvents 的周期同步避免刷包
            if (player instanceof ServerPlayer serverPlayer) {
                ModNetwork.syncTo(serverPlayer);
            }
        } else {
            // 对应原版 causeFoodExhaustion(perTick * (amplifier + 1)); maxExhaustion 消耗值 = 1 点,
            // 先扣消化饱和度再扣消化值 (对应原版先消耗饱和度再消耗饥饿值)
            float points = HungerConfig.hungerEffectExhaustionPerTick() * (amplifier + 1)
                    / HungerConfig.maxExhaustion();
            float satLoss = Math.min(digestion.getDigestionSaturation(), points);
            DigestionHelper.addDigestionSaturation(player, -satLoss);
            DigestionHelper.addDigestion(player, -(points - satLoss));
        }
        ci.cancel();
    }
}
