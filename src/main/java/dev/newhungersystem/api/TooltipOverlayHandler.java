package dev.newhungersystem.api;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent.GatherComponents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 物品提示栏 (Tooltip) 食物值渲染, 仿 AppleSkin {@code TooltipOverlayHandler}。
 * <p>
 * 布局与 AppleSkin 一致:
 * <ul>
 *   <li>行 1 (高 9): 消化值条, 每格 2 点, 9x9 图标, 间距 9px (贴紧)。超过 10 格时只画 1 格 +
 *       "xN" (0.75 倍缩放文字, 灰色)。</li>
 *   <li>行 2 (高 7, 距行 1 顶 +10): 消化饱和度条, 每格 2 点, 7x7 小图标 (AppleSkin 同款尺寸),
 *       间距 7px; 超过 10 格或等于 0 时画 1 格 + "xN"。</li>
 * </ul>
 * 取值走与进食相同的优先级 (配置覆盖 &gt; 代码登记 &gt; nutrition × digestionPerNutrition),
 * 消化饱和度 = 消化值增益 × saturationModifier × 2 (仿原版 {@code FoodData.eat()}),
 * 因此 Tooltip 提示的数字与实际回復效果保持一致。
 * <p>
 * 图标纹理为本模组自身的 {@link DigestionOverlay#TEXTURE} 图集:
 * 消化值用 V_DIGEST 行的满/半/空格, 消化饱和度用 V_SAT 行的 1/4、1/2、3/4、满、空五档
 * (与 AppleSkin 的 u=0/7/14/21/28 四档 + 空档一一对应)。
 * 行 1 已实现 AppleSkin 同款 default/modified 双层：默认层 25% 透明度打底，
 * {@link FoodValuesEvent#modifiedFoodValues} 全透明度叠在上面；两值相同则视觉等同单层。
 * 行 2 按 AppleSkin 取两者较大值单层绘制。
 * 食物带 HARMFUL 效果时 (腐肉/河豚) 整条消化值切到暗绿 rotten 贴图组，
 * 与 AppleSkin 的 rottenBarTextureOffsets 行为一致；饱和度行保持原色 (AppleSkin 同样不改)。
 */
@OnlyIn(Dist.CLIENT)
public class TooltipOverlayHandler {

    private TooltipOverlayHandler() {
    }

    /** MOD 总线: 注册自定义 Tooltip 组件工厂 (仿 AppleSkin TooltipOverlayHandler#register) */
    public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(FoodTooltip.class, FoodTooltipRenderer::new);
    }

    /** Forge 总线: 收集物品 Tooltip 元素 */
    @SubscribeEvent
    public static void gatherTooltips(GatherComponents event) {
        if (event.isCanceled()) {
            return;
        }
        ItemStack hovered = event.getItemStack();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !shouldShowTooltip(hovered, player)) {
            return;
        }
        // 客户端显示值事件 (AppleSkin FoodValuesEvent 同款入口): 默认同源，客户端模组可改写
        FoodValuesEvent valuesEvent = FoodHelper.postFoodValuesEvent(player, hovered);
        if (valuesEvent == null) {
            return;
        }
        FoodValues defaults = valuesEvent.defaultFoodValues;
        FoodValues modified = valuesEvent.modifiedFoodValues;
        // 自有 TooltipOverlayEvent.Pre (AppleSkin 同款): 第三方可取消整条食物值行
        TooltipOverlayEvent.Pre pre = new TooltipOverlayEvent.Pre(hovered, defaults, modified);
        if (MinecraftForge.EVENT_BUS.post(pre)) {
            return;
        }
        // AppleSkin 同款: 食物效果里含 HARMFUL 即为 rotten，切换到整套 rotten 贴图偏移
        DigestionValues values = new DigestionValues(defaults.digestion, defaults.digestionSaturation,
                modified.digestion, modified.digestionSaturation,
                FoodHelper.isRotten(hovered, player));
        FoodTooltip tooltip = new FoodTooltip(hovered, values);
        if (tooltip.shouldRenderDigestionBars()) {
            event.getTooltipElements().add(Either.right(tooltip));
        }
    }

    private static boolean shouldShowTooltip(ItemStack hoveredStack, Player player) {
        if (hoveredStack.isEmpty()) {
            return false;
        }
        boolean show = (ClientConfig.showFoodValuesInTooltip() && KeyHelper.isShiftKeyDown())
                || ClientConfig.showFoodValuesInTooltipAlways();
        return show && hoveredStack.getItem().getFoodProperties(hoveredStack, player) != null;
    }

    /** Tooltip 展示的消化值/消化饱和度: 默认层 + 事件改写后的显示层 (AppleSkin 双层对比同款) */
    private static class DigestionValues {
        final float defaultDigestion;
        final float defaultSaturation;
        final float digestion;
        final float saturation;
        final boolean rotten;

        DigestionValues(float defaultDigestion, float defaultSaturation,
                        float digestion, float saturation, boolean rotten) {
            this.defaultDigestion = defaultDigestion;
            this.defaultSaturation = defaultSaturation;
            this.digestion = digestion;
            this.saturation = saturation;
            this.rotten = rotten;
        }
    }

    /** 由 {@link TooltipOverlayHandler#register} 绑定的自定义 Tooltip 组件 */
    private static class FoodTooltip implements TooltipComponent {
        private final ItemStack itemStack;
        private final DigestionValues values;
        /** AppleSkin biggest*：默认层与改写层取较大者，避免双层里较大那层被裁掉 */
        private final float biggestDigestion;
        private final float biggestSaturation;
        private final int digestionBars;
        private String digestionBarsText;
        private final int saturationBars;
        private String saturationBarsText;

        FoodTooltip(ItemStack itemStack, DigestionValues values) {
            this.itemStack = itemStack;
            this.values = values;
            this.biggestDigestion = Math.max(values.defaultDigestion, values.digestion);
            this.biggestSaturation = Math.max(values.defaultSaturation, values.saturation);
            int digestionBars = (int) Math.ceil(Math.abs(this.biggestDigestion) / 2.0D);
            if (digestionBars > 10) {
                this.digestionBarsText = "x" + (this.biggestDigestion < 0.0F ? -1 : 1) * digestionBars;
                this.digestionBars = 1;
            } else {
                this.digestionBars = digestionBars;
            }
            int saturationBars = (int) Math.ceil(Math.abs(this.biggestSaturation) / 2.0D);
            if (saturationBars > 10 || saturationBars == 0) {
                this.saturationBarsText = "x" + (this.biggestSaturation < 0.0F ? -1 : 1) * saturationBars;
                this.saturationBars = 1;
            } else {
                this.saturationBars = saturationBars;
            }
        }

        boolean shouldRenderDigestionBars() {
            return this.digestionBars > 0;
        }
    }

    /** 自定义 Tooltip 的绘制器 */
    private static class FoodTooltipRenderer implements ClientTooltipComponent {
        private final FoodTooltip foodTooltip;

        FoodTooltipRenderer(FoodTooltip foodTooltip) {
            this.foodTooltip = foodTooltip;
        }

        @Override
        public int getHeight() {
            return 20;
        }

        @Override
        public int getWidth(Font font) {
            int digestionBarsWidth = this.foodTooltip.digestionBars * 9;
            if (this.foodTooltip.digestionBarsText != null) {
                digestionBarsWidth += font.width(this.foodTooltip.digestionBarsText);
            }
            int saturationBarsWidth = this.foodTooltip.saturationBars * 7;
            if (this.foodTooltip.saturationBarsText != null) {
                saturationBarsWidth += font.width(this.foodTooltip.saturationBarsText);
            }
            return Math.max(digestionBarsWidth, saturationBarsWidth) + 2;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
            ItemStack itemStack = this.foodTooltip.itemStack;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !shouldShowTooltip(itemStack, mc.player) || mc.screen == null) {
                return;
            }
            // 自有 TooltipOverlayEvent.Render (AppleSkin 同款): 可取消、可重定向绘制坐标/画布
            DigestionValues v = this.foodTooltip.values;
            TooltipOverlayEvent.Render renderEvent = new TooltipOverlayEvent.Render(itemStack, x, y, guiGraphics,
                    new FoodValues(v.defaultDigestion, v.defaultSaturation),
                    new FoodValues(v.digestion, v.saturation));
            if (MinecraftForge.EVENT_BUS.post(renderEvent)) {
                return;
            }
            x = renderEvent.x;
            y = renderEvent.y;
            guiGraphics = renderEvent.guiGraphics;
            float digestion = this.foodTooltip.values.digestion;
            float defaultDigestion = this.foodTooltip.values.defaultDigestion;
            // AppleSkin 饱和度行用 biggest 值 (默认/改写取较大者)，不画双层
            float saturation = this.foodTooltip.biggestSaturation;

            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // ---- 行 1: 消化值条 (9x9, 间距 9px, AppleSkin 同款布局) ----
            // 腐坏/有害食物切到暗绿整行，U 偏移不变 (AppleSkin rottenBarTextureOffsets 同款做法)
            int digestV = this.foodTooltip.values.rotten
                    ? DigestionOverlay.V_ROTTEN_DIGEST : DigestionOverlay.V_DIGEST;
            int offsetY = y;
            int offsetX = x + (this.foodTooltip.digestionBars - 1) * 9;
            for (int i = 0; i < this.foodTooltip.digestionBars * 2; i += 2) {
                // 底色 (空胃, 同 HUD drawBar 的 U_EMPTY)
                blit(guiGraphics, offsetX, offsetY, 9, 9, DigestionOverlay.U_EMPTY, digestV);
                // AppleSkin 双层: 默认层 25% 透明度打底，改写层全透明度叠在上面。
                // 两层都没被事件改写时视觉与单层一致；被改写时能看到"原值 vs 新值"的暗色轮廓。
                if (defaultDigestion > i) {
                    float defaultRemaining = defaultDigestion - i;
                    int defaultU = defaultRemaining >= 2.0F
                            ? DigestionOverlay.U_FULL : DigestionOverlay.U_HALF;
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.25F);
                    blit(guiGraphics, offsetX, offsetY, 9, 9, defaultU, digestV);
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                }
                // 值层: 每格 2 点; >=2 画满格, >=0.5 画半格 (与 HUD drawValueIcons 阈值一致)
                if (digestion > i) {
                    float remaining = digestion - i;
                    int u = remaining >= 2.0F ? DigestionOverlay.U_FULL : DigestionOverlay.U_HALF;
                    blit(guiGraphics, offsetX, offsetY, 9, 9, u, digestV);
                }
                offsetX -= 9;
            }
            if (this.foodTooltip.digestionBarsText != null) {
                offsetX += 18;
                PoseStack poseStack = guiGraphics.pose();
                poseStack.pushPose();
                poseStack.translate(offsetX, offsetY, 0.0F);
                poseStack.scale(0.75F, 0.75F, 0.75F);
                guiGraphics.drawString(font, this.foodTooltip.digestionBarsText, 2, 2, -5592406);
                poseStack.popPose();
            }

            // ---- 行 2: 消化饱和度条 (7x7 小图标, 间距 7px, 距行 1 顶 +10) ----
            offsetY += 10;
            float absSaturation = Math.abs(saturation);
            offsetX = x + (this.foodTooltip.saturationBars - 1) * 7;
            for (int i = 0; i < this.foodTooltip.saturationBars * 2; i += 2) {
                float effectiveSaturationOfBar = (absSaturation - i) / 2.0F;
                // AppleSkin TooltipOverlayHandler 的四档 + 空格: >=1 / >0.5 / >0.25 / >0 / else
                int u = effectiveSaturationOfBar >= 1.0F
                        ? DigestionOverlay.U_SAT_FULL
                        : (effectiveSaturationOfBar > 0.5F
                                ? DigestionOverlay.U_SAT_THREE_QUARTER
                                : (effectiveSaturationOfBar > 0.25F
                                        ? DigestionOverlay.U_SAT_HALF
                                        : (effectiveSaturationOfBar > 0.0F
                                                ? DigestionOverlay.U_SAT_QUARTER
                                                : DigestionOverlay.U_SAT_EMPTY)));
                blit(guiGraphics, offsetX, offsetY, 7, 7, u, DigestionOverlay.V_SAT);
                offsetX -= 7;
            }
            if (this.foodTooltip.saturationBarsText != null) {
                offsetX += 14;
                PoseStack poseStack = guiGraphics.pose();
                poseStack.pushPose();
                poseStack.translate(offsetX, offsetY, 0.0F);
                poseStack.scale(0.75F, 0.75F, 0.75F);
                guiGraphics.drawString(font, this.foodTooltip.saturationBarsText, 2, 1, -5592406);
                poseStack.popPose();
            }

            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableDepthTest();
        }

        /**
         * 从 {@link DigestionOverlay#TEXTURE} 图集绘制 16x16 图块到 w×h 的屏幕区域。
         * 与 DigestionOverlay 中 blit 调用的参数签名完全一致。
         */
        private static void blit(GuiGraphics guiGraphics, int x, int y, int w, int h, int u, int v) {
            guiGraphics.blit(DigestionOverlay.TEXTURE, x, y, w, h,
                    (float) u, (float) v, DigestionOverlay.TILE, DigestionOverlay.TILE,
                    DigestionOverlay.TEXTURE_WIDTH, DigestionOverlay.TEXTURE_HEIGHT);
        }
    }
}