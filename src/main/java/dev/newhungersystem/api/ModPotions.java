package dev.newhungersystem.api;

import dev.newhungersystem.NewHungerSystem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 三种药水本体（不含酿造配方 —— 由附属/整合包自己接）。
 * <p>
 * 药水液体的颜色在 1.20.1 里由效果颜色按等级加权平均得出（{@code PotionUtils#getColor}），
 * 所以这里不单独设色：只要 {@link ModMobEffects} 里三个效果的颜色取自各自图标的色阶，
 * 瓶中液体就会自动是"深红 / 金 / 灰紫"，与贴图一致。
 * <p>
 * 获取方式（未加配方时）：
 * {@code /give @s minecraft:potion{Potion:"new_hunger_system:digestive_aid"}}，
 * 名称取自 lang 的 {@code item.minecraft.potion.effect.<id>}。
 *
 * @author THEREDK
 */
public final class ModPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, NewHungerSystem.MOD_ID);

    /** 助消化 3:00 */
    public static final RegistryObject<Potion> DIGESTIVE_AID = POTIONS.register("digestive_aid",
            () -> new Potion("digestive_aid",
                    new MobEffectInstance(ModMobEffects.DIGESTIVE_AID.get(), 3600)));

    /** 瞬间消化 1:00（效果很强，持续时间短） */
    public static final RegistryObject<Potion> INSTANT_DIGESTION = POTIONS.register("instant_digestion",
            () -> new Potion("instant_digestion",
                    new MobEffectInstance(ModMobEffects.INSTANT_DIGESTION.get(), 1200)));

    /** 消化不良 1:30 */
    public static final RegistryObject<Potion> INDIGESTION = POTIONS.register("indigestion",
            () -> new Potion("indigestion",
                    new MobEffectInstance(ModMobEffects.INDIGESTION.get(), 1800)));

    private ModPotions() {
    }

    public static void register(IEventBus modEventBus) {
        POTIONS.register(modEventBus);
    }
}
