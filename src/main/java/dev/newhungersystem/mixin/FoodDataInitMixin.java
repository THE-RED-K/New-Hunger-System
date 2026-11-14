package dev.newhungersystem.mixin;

import dev.newhungersystem.api.HungerConfig;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 开局饥饿值/饱和度的构造注入。
 * <p>
 * 单独成类是为了保持默认优先级 1000：{@link FoodDataMixin} 承载着全部 {@code @Overwrite}，
 * 必须用 {@code MixinPriorities.TAKE_OVER_FINAL}（最低档）；注入器若跟它一起压到最低档，
 * 会被后写入的 {@code @Overwrite} 冲掉（见 MixinPriorities 的说明）。
 *
 * @author THEREDK
 */
@Mixin(FoodData.class)
public abstract class FoodDataInitMixin {

    @Shadow
    private int foodLevel;
    @Shadow
    private float saturationLevel;

    /**
     * 原版开局饥饿值/饱和度固定 20/5。改为可配置 (默认 20/5)。
     * 用构造注入 (RETURN) 覆盖原版默认值, 而非 @Overwrite 构造 (Mixin 不支持在 mixin 类中写目标类名构造)。
     *
     * @author THEREDK
     * @reason 让开局饥饿值/饱和度可由配置设置, 而非写死 20/5
     */
    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void onConstruct(CallbackInfo ci) {
        this.foodLevel = (int) HungerConfig.startHunger();
        this.saturationLevel = HungerConfig.startSaturation();
    }
}
