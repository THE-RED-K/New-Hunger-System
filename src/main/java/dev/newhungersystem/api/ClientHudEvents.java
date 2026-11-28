package dev.newhungersystem.api;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.newhungersystem.compat.ModCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.GuiOverlayManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.text.DecimalFormat;

/**
 * 客户端 HUD 附加事件 (仅客户端注册):
 * 1. 消耗值底栏: 仿 AppleSkin 在 FOOD_LEVEL 的 Pre 事件绘制, 因此被随后绘制的原版饥饿图标盖在下面。
 * 2. 血条回血预显: 在 PLAYER_HEALTH 的 Post 事件叠加"吃下手中食物后预计回复到"的心形
 *    ({@link FoodHealthHelper}, 消化值 → 饥饿值 → 血量链式估算, 替代 AppleSkin 的原版 hunger 估算)。
 * 3. F3 调试屏: 附加消化值/消化饱和度/消耗值行 (仿 AppleSkin DebugInfoHandler)。
 * 4. 每 client tick 推进 popup 闪烁计时。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientHudEvents {

    private static final DecimalFormat DF = new DecimalFormat("0.##");
    private static final ResourceLocation FOOD_LEVEL_ELEMENT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "food_level");
    private static final ResourceLocation PLAYER_HEALTH_ELEMENT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "player_health");

    private ClientHudEvents() {
    }

    @SubscribeEvent
    public static void onRenderFoodOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != GuiOverlayManager.findOverlay(FOOD_LEVEL_ELEMENT)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // AppleSkin HUDOverlayHandler.onRenderGuiOverlayPre(FOOD_LEVEL) 的同款门槛
        if (mc.player == null || mc.options.hideGui || mc.player.getVehicle() instanceof LivingEntity
                || !(mc.gui instanceof ForgeGui gui) || !gui.shouldDrawSurvivalElements()) {
            return;
        }
        if (!ClientConfig.showExhaustionUnderlay()) {
            return;
        }
        float exhaustion = ClientDigestionData.exhaustion;
        float ratio = Math.min(1.0F, Math.max(0.0F, exhaustion / HungerHelper.getMaxExhaustion(mc.player)));
        int width = (int) (ratio * 81.0F);
        if (width <= 0) {
            return;
        }
        int right = mc.getWindow().getGuiScaledWidth() / 2 + 91;
        // AppleSkin renderExhaustion: top = screenHeight - gui.rightHeight。我们的消化条
        // 注册在 FOOD_LEVEL 之前并已把 rightHeight +10，这样底栏才跟饥饿行对齐，而不是硬编码 39。
        int top = event.getWindow().getGuiScaledHeight() - gui.rightHeight;
        GuiGraphics graphics = event.getGuiGraphics();
        HUDOverlayEvent.Exhaustion overlayEvent = new HUDOverlayEvent.Exhaustion(exhaustion, right, top, graphics);
        if (MinecraftForge.EVENT_BUS.post(overlayEvent)) {
            return;
        }
        // AppleSkin drawExhaustionOverlay: 从 81x9 贴图带右端截 width 像素，alpha 0.75。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.75F);
        graphics.blit(DigestionOverlay.TEXTURE, right - width, top,
                DigestionOverlay.U_EXHAUSTION + (DigestionOverlay.EXHAUSTION_WIDTH - width),
                DigestionOverlay.V_EXHAUSTION,
                width, DigestionOverlay.EXHAUSTION_HEIGHT,
                DigestionOverlay.TEXTURE_WIDTH, DigestionOverlay.TEXTURE_HEIGHT);
        // 直接饥饿账本: 仅当饱和度为 0 时, 在上一行叠一条完全同款的消耗条。
        // 饱和度 > 0 时饥饿值只会静默下降, 不显示进度 (避免提示"还没掉饥饿"的假象)。
        float directCarry = ClientDigestionData.directHungerCarry;
        if (directCarry > 0.0F && mc.player.getFoodData().getSaturationLevel() <= 0.0F) {
            int directWidth = (int) (Math.min(1.0F, directCarry) * DigestionOverlay.EXHAUSTION_WIDTH);
            if (directWidth > 0) {
                graphics.blit(DigestionOverlay.TEXTURE, right - directWidth, top - 10,
                        DigestionOverlay.U_EXHAUSTION + (DigestionOverlay.EXHAUSTION_WIDTH - directWidth),
                        DigestionOverlay.V_EXHAUSTION,
                        directWidth, DigestionOverlay.EXHAUSTION_HEIGHT,
                        DigestionOverlay.TEXTURE_WIDTH, DigestionOverlay.TEXTURE_HEIGHT);
            }
        }
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 血条回血预显。必须在 PLAYER_HEALTH 的 Post 绘制：此时原版心形已画完，
     * 用 {@code gui.leftHeight} 反推心形首行的真实 Y 坐标（Forge 1.20.1 心脏走 leftHeight）。
     */
    @SubscribeEvent
    public static void onRenderHealthOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != GuiOverlayManager.findOverlay(PLAYER_HEALTH_ELEMENT)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !(mc.gui instanceof ForgeGui gui) || !gui.shouldDrawSurvivalElements()) {
            return;
        }
        if (!ClientConfig.showFoodHealthHudOverlay() || mc.player == null) {
            FoodHealthHelper.resetFlash();
            return;
        }
        ItemStack held = FoodHealthHelper.getHeldFoodStack(mc.player);
        // 逐帧调用: 同一 tick 内复用上次结果 (避免每帧 post FoodValuesEvent + 跑回血模拟)
        float restored = FoodHealthHelper.estimateHealthRestoredCached(held, mc.player);
        FoodValuesEvent valuesEvent = FoodHealthHelper.cachedValuesEvent();
        if (restored <= 0.0F || valuesEvent == null || !FoodHealthHelper.canShowHealthEstimate(mc.player)) {
            FoodHealthHelper.resetFlash();
            return;
        }
        float modified = Math.min(mc.player.getHealth() + restored, mc.player.getMaxHealth());
        if (modified <= mc.player.getHealth()) {
            FoodHealthHelper.resetFlash();
            return;
        }
        int windowWidth = event.getWindow().getGuiScaledWidth();
        int windowHeight = event.getWindow().getGuiScaledHeight();
        int heartTop = FoodHealthHelper.healthRowTop(windowHeight, gui.leftHeight, mc.player);
        int heartLeft = windowWidth / 2 - 91;
        HUDOverlayEvent.HealthRestored overlayEvent = new HUDOverlayEvent.HealthRestored(
                modified, held, valuesEvent.modifiedFoodValues, heartLeft, heartTop, event.getGuiGraphics());
        if (MinecraftForge.EVENT_BUS.post(overlayEvent)) {
            return;
        }
        FoodHealthHelper.renderHealthOverlay(overlayEvent.guiGraphics, mc.player,
                overlayEvent.x, overlayEvent.y, modified, FoodHealthHelper.flashAlpha(), gui.getGuiTicks());
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
            FoodHealthHelper.clientTick();
        }
    }
}
