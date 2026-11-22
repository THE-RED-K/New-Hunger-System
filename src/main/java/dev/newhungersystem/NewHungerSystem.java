package dev.newhungersystem;

import dev.newhungersystem.api.Digestion;
import dev.newhungersystem.api.DigestionOverlay;
import dev.newhungersystem.api.HungerConfig;
import dev.newhungersystem.api.ClientConfig;
import dev.newhungersystem.api.ClientHudEvents;
import dev.newhungersystem.api.DigestionCommands;
import dev.newhungersystem.api.DigestionEvents;
import dev.newhungersystem.api.FoodDigestionValues;
import dev.newhungersystem.api.HungerBonusStorage;
import dev.newhungersystem.api.ModAttributes;
import dev.newhungersystem.api.ModMobEffects;
import dev.newhungersystem.api.ModNetwork;
import dev.newhungersystem.api.ModPotions;
import dev.newhungersystem.api.OverflowCacheHandler;
import dev.newhungersystem.api.TooltipOverlayHandler;
import dev.newhungersystem.compat.AppleSkinCompat;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// 此值须与 gradle.properties 中的 mod_id 及 META-INF/mods.toml 中的 modId 一致
@Mod(NewHungerSystem.MOD_ID)
public class NewHungerSystem {

    public static final String MOD_ID = "new_hunger_system";

    public NewHungerSystem(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // 服务端配置 (baseDigestionRate) + 客户端 HUD 配置
        HungerConfig.register();
        ClientConfig.register();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::registerOverlays);
        // 配置加载/重载: 重建 per-item 覆盖表 + 把在线玩家收敛到新上限
        modEventBus.register(FoodDigestionValues.class);
        // 自定义属性 (hunger_efficiency / digestion_efficiency) + 挂到玩家
        ModAttributes.register(modEventBus);
        modEventBus.addListener(ModAttributes::onEntityAttributeModification);
        // 药水效果: digestive_aid / instant_digestion / indigestion
        ModMobEffects.register(modEventBus);
        // 药水本体 (不含酿造配方)
        ModPotions.register(modEventBus);

        // 游戏事件监听 (能力附加/进食拦截/转化 tick/同步) + /digestion 命令 + 溢出缓存
        MinecraftForge.EVENT_BUS.register(DigestionEvents.class);
        MinecraftForge.EVENT_BUS.register(DigestionCommands.class);
        MinecraftForge.EVENT_BUS.register(OverflowCacheHandler.class);
        // 上限加成的 SavedData 绑定/解绑 (服务器启停)
        MinecraftForge.EVENT_BUS.register(HungerBonusStorage.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    /** 客户端专属事件 (消耗值底栏 / F3 调试行 / 闪烁计时 / 物品 Tooltip) 只在此注册, 避免服务端加载客户端类 */
    private void clientSetup(final FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.register(ClientHudEvents.class);
        // 物品 Tooltip 食物值: MOD 总线注册组件工厂 + Forge 总线收集 GatherComponents 事件
        FMLJavaModLoadingContext.get().getModEventBus().addListener(TooltipOverlayHandler::register);
        MinecraftForge.EVENT_BUS.register(TooltipOverlayHandler.class);
        // 软兼容: 装了 AppleSkin 时按配置取消它的 HUD overlay 事件
        AppleSkinCompat.register();
    }

    private void registerCapabilities(final RegisterCapabilitiesEvent event) {
        event.register(Digestion.class);
    }

    private void registerOverlays(final RegisterGuiOverlaysEvent event) {
        // 注册在原版饥饿条之上 (渲染顺序在其后, 位置在其上方)
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "digestion_bar", DigestionOverlay.INSTANCE);
    }
}