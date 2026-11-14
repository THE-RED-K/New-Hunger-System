package dev.newhungersystem.api;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.newhungersystem.compat.ModCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.GuiOverlayManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.text.DecimalFormat;

/**
 * 客户端 HUD 附加事件 (仅客户端注册):
 * 1. 消耗值底栏: 仿 AppleSkin 在 FOOD_LEVEL 的 Pre 事件绘制, 因此被随后绘制的原版饥饿图标盖在下面。
 * 2. F3 调试屏: 附加消化值/消化饱和度/消耗值行 (仿 AppleSkin DebugInfoHandler)。
 * 3. 每 client tick 推进 popup 闪烁计时。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientHudEvents {

    private static final DecimalFormat DF = new DecimalFormat("0.##");
    /** 底栏颜色 (半透明暗色, 仿 AppleSkin 的 exhaustion underlay) */
    private static final int COL_EXHAUSTION = 0x80202020;
    private static final ResourceLocation FOOD_LEVEL_ELEMENT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "food_level");

    private ClientHudEvents() {
    }

    @SubscribeEvent
    public static void onRenderFoodOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != GuiOverlayManager.findOverlay(FOOD_LEVEL_ELEMENT)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui instanceof ForgeGui gui) || !gui.shouldDrawSurvivalElements()) {
            return;
        }
        if (!ClientConfig.showExhaustionUnderlay()) {
            return;
        }
        float ratio = Math.min(1.0F, Math.max(0.0F, ClientDigestionData.exhaustion / HungerConfig.maxExhaustion()));
        int width = (int) (ratio * 81.0F);
        if (width <= 0) {
            return;
        }
        int right = mc.getWindow().getGuiScaledWidth() / 2 + 91;
        int top = mc.getWindow().getGuiScaledHeight() - 39;   // 原版饥饿行顶
        GuiGraphics graphics = event.getGuiGraphics();
        RenderSystem.enableBlend();
        graphics.fill(right - width, top, right, top + 9, COL_EXHAUSTION);
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!ClientConfig.showFoodDebugInfo()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.renderDebug) {
            return;
        }
        event.getLeft().add("digestion: " + DF.format(ClientDigestionData.digestion)
                + "/" + DF.format(Digestion.maxDigestion())
                + ", digSat: " + DF.format(ClientDigestionData.digestionSaturation)
                + "/" + DF.format(Digestion.maxDigestionSaturation())
                + ", exh: " + DF.format(ClientDigestionData.exhaustion)
                + "/" + DF.format(HungerConfig.maxExhaustion()));
        event.getLeft().add(ModCompat.statusLine());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DigestionOverlay.clientTick();
        }
    }
}
