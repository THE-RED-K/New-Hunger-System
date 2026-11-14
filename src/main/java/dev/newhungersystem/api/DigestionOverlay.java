package dev.newhungersystem.api;

import dev.newhungersystem.NewHungerSystem;
import dev.newhungersystem.compat.ModCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Random;

/**
 * 消化值 HUD: 在原版饥饿条正上方一行 (行距 10px, 与护甲相对生命一致) 绘制消化条。
 * 图标 9x9、间距 8px, 与原版饥饿图标完全一致 (条总宽 81px)。
 * <p>
 * 布局仿原版/苹果皮 (反编译 appleskin-forge-mc1.20.1-2.5.1):
 * - 消化值与消化饱和度在【同一行】: 消化值画整格胃图标, 消化饱和度画同行黄色缩小图标
 *   (原版饱和度就是叠在饥饿条同一行, 不占第二行)。
 * - 手持食物预显 (AppleSkin 完整仿制): 红=消化值增量(空心轮廓, 叠同行), 黄=消化饱和度增量(同行),
 *   闪烁 alpha = 每 tick ±0.125, 在 [-0.5,1.5] 间往返并 clamp 到 [0,1] (HUDOverlayHandler.onClientTick);
 *   不持食物/无增量时 resetFlash。
 * - 行锚点: y = screenHeight - ForgeGui.rightHeight (rightHeight 是距屏幕底部的行偏移, 不是 Y 坐标!)。
 * - 数字仿 Armor Points ++ 右侧规则: 左对齐锚点 = 条右缘 + 2px, y = 行顶 + 0.5, 格式 "值+饱和度"。
 */
public class DigestionOverlay implements IGuiOverlay {

    public static final DigestionOverlay INSTANCE = new DigestionOverlay();

    /** 64x128 图集 (scripts/gen_stomach_icons.py 生成) */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(NewHungerSystem.MOD_ID, "textures/gui/stomach_icons.png");

    private static final int ICON = 9;    // 绘制尺寸 = 原版饥饿图标大小
    private static final int STEP = 8;    // 图标间距 = 原版 (10 格条总宽 81px)
    private static final int TILE = 16;   // 图集源图块尺寸
    private static final int ROW = 10;    // 原版 HUD 行距
    private static final int U_FULL = 0;
    private static final int U_HALF = 16;
    private static final int U_EMPTY = 32;
    private static final int V_DIGEST = 0;
    private static final int V_SAT = 16;
    private static final int V_POPUP_RED = 32;
    private static final int V_POPUP_YELLOW = 48;

    /** 数值颜色 (仿 Armor Points ++ 风格) */
    private static final int COL_HUNGER = 0xFFA0522D;        // 棕色 (饥饿值贴图)
    private static final int COL_SAT = 0xFFE6C200;           // 金色 (饱和度贴图)
    private static final int COL_DIGEST = 0xFFCD9B7A;        // 肉色 (消化值贴图)
    private static final int COL_DIGEST_SAT = 0xFFE8C9A0;    // 浅肉色 (消化饱和度)

    /** 闪烁状态 (AppleSkin HUDOverlayHandler 同款参数) */
    private static float unclampedFlashAlpha = 0.0F;
    private static float flashAlpha = 0.0F;
    private static byte alphaDir = 1;

    /** 图标抖动随机源 (每 tick 按 ticks*312871 播种, 同 AppleSkin) */
    private static final Random random = new Random();

    private DigestionOverlay() {
    }

    /** 每 client tick 推进闪烁 alpha (AppleSkin: ±0.125/tick, [-0.5,1.5] 往返, clamp [0,1]) */
    public static void clientTick() {
        unclampedFlashAlpha = unclampedFlashAlpha + alphaDir * 0.125F;
        if (unclampedFlashAlpha >= 1.5F) {
            alphaDir = -1;
        } else if (unclampedFlashAlpha <= -0.5F) {
            alphaDir = 1;
        }
        flashAlpha = Mth.clamp(unclampedFlashAlpha, 0.0F, 1.0F) * ClientConfig.maxFlashAlpha();
    }

    public static void resetFlash() {
        flashAlpha = 0.0F;
        unclampedFlashAlpha = 0.0F;
        alphaDir = 1;
    }

    private static void enableAlpha(float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.blendFunc(770, 771); // GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA (AppleSkin 同款)
    }

    private static void disableAlpha() {
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = gui.getMinecraft();
        Player player = mc.player;
        if (player == null || !gui.shouldDrawSurvivalElements()) {
            return;
        }
        gui.setupOverlayRenderState(true, false);

        float digestion = ClientDigestionData.digestion;
        float digestionSat = ClientDigestionData.digestionSaturation;

        int xRight = screenWidth / 2 + 91;              // 原版饥饿条右缘
        int digestY = screenHeight - gui.rightHeight;   // 饥饿条正上方一行 (rightHeight 为距底行偏移)
        int foodTop = screenHeight - 39;                // 原版饥饿行顶 (数字用)

        // 原版抖动动画条件 (仿 AppleSkin generateHungerBarOffsets): 饱和度=0 且 ticks % (foodLevel*3+1) == 0
        int ticks = gui.getGuiTicks();
        boolean animate = ClientConfig.vanillaAnimation()
                && player.getFoodData().getSaturationLevel() <= 0.0F
                && ticks % (player.getFoodData().getFoodLevel() * 3 + 1) == 0;
        if (animate) {
            random.setSeed(ticks * 312871L);
        }

        // ---- 单行: 消化值 (整格胃) + 消化饱和度 (同行黄色图标, 仿原版饱和度叠在饥饿行的画法) ----
        drawBar(graphics, xRight, digestY, digestion, V_DIGEST, animate);
        if (ClientConfig.showSaturationOverlay() && digestionSat > 0.0F) {
            drawValueIcons(graphics, xRight, digestY, digestionSat, V_SAT, animate);
        }
        gui.rightHeight += ROW;                          // 仅占一行 (氧气条等自动上移)

        // ---- 回复预显 (AppleSkin 完整仿制): 红=消化值增量, 黄=消化饱和度增量, 闪烁 ----
        ItemStack heldFood = ClientConfig.showFoodValuesOverlay() ? getHeldFoodStack(player) : ItemStack.EMPTY;
        FoodProperties food = heldFood.isEmpty() ? null : heldFood.getItem().getFoodProperties(heldFood, player);
        if (food == null) {
            resetFlash();
        } else {
            // 与 onUseItemFinish 的取值优先级保持一致: 配置覆盖 > 代码登记 > nutrition * digestionPerNutrition,
            // 否则改倍率/加覆盖后 HUD 预显会和实际增益对不上
            Float override = FoodDigestionValues.resolve(heldFood);
            float potential = override != null
                    ? override
                    : Math.max(0.0F, food.getNutrition() * HungerConfig.digestionPerNutrition());
            // 消化饱和度部分与 onUseItemFinish 一致: gain × saturationModifier × 2
            float potentialSat = Math.max(0.0F, potential * food.getSaturationModifier() * 2.0F);
            float digGain = Math.max(0.0F, Math.min(potential, Digestion.maxDigestion() - digestion));
            float satCap = Math.min(Digestion.maxDigestionSaturation(), digestion + digGain);
            float satGain = Math.max(0.0F, Math.min(potentialSat, satCap - digestionSat));
            if (digGain <= 0.0F && satGain <= 0.0F) {
                resetFlash();
            } else {
                if (digGain > 0.0F) {
                    float modified = digestion + digGain;
                    // 上限 > 20 时条已画满, 起点会超出可视格数 → clamp 到最后一格,
                    // 让"还能继续加消化值"的信号仍然可见, 而不是整个预显消失
                    int icons = barIcons();
                    int startBar = Math.min(icons - 1, (int) (digestion / 2.0F));
                    int endBar = Math.min(icons, Mth.ceil(modified / 2.0F));
                    enableAlpha(flashAlpha);
                    for (int i = startBar; i < endBar; i++) {
                        int x = xRight - ICON - i * STEP;
                        int y = digestY + (animate ? random.nextInt(3) - 1 : 0);
                        int u = modified - i * 2.0F >= 2.0F ? U_FULL : U_HALF;
                        graphics.blit(TEXTURE, x, y, ICON, ICON, u, V_POPUP_RED, TILE, TILE, 64, 128);
                    }
                    disableAlpha();
                }
                if (satGain > 0.0F) {
                    float modified = digestionSat + satGain;
                    int icons = barIcons();
                    int startBar = Math.min(icons - 1, (int) (digestionSat / 2.0F));
                    int endBar = Math.min(icons, Mth.ceil(modified / 2.0F));
                    enableAlpha(flashAlpha);
                    for (int i = startBar; i < endBar; i++) {
                        int x = xRight - ICON - i * STEP;
                        int y = digestY + (animate ? random.nextInt(3) - 1 : 0);
                        int u = modified - i * 2.0F >= 2.0F ? U_FULL : U_HALF;
                        graphics.blit(TEXTURE, x, y, ICON, ICON, u, V_POPUP_YELLOW, TILE, TILE, 64, 128);
                    }
                    disableAlpha();
                }
            }
        }

        // ---- 数值: "值+饱和度" 组合格式 (仿 AP++), 锚点 = 条右缘 + 2, y = 行顶 + 0.5 ----
        if (ClientConfig.showNumbers() && !ModCompat.hideNumbers()) {
            int numX = xRight + 2;

            // 饥饿行: "20+10" (饥饿棕, +饱和度金)
            String hungerStr = String.valueOf(player.getFoodData().getFoodLevel());
            String satStr = "+" + Math.round(player.getFoodData().getSaturationLevel());
            drawNumber(graphics, mc, hungerStr, numX, foodTop + 0.5F, COL_HUNGER);
            drawNumber(graphics, mc, satStr, numX + mc.font.width(hungerStr), foodTop + 0.5F, COL_SAT);

            // 消化行: "20+0" (消化肉色, +消化饱和度浅肉)
            String digStr = String.valueOf(Math.round(digestion));
            String digSatStr = "+" + Math.round(digestionSat);
            drawNumber(graphics, mc, digStr, numX, digestY + 0.5F, COL_DIGEST);
            drawNumber(graphics, mc, digSatStr, numX + mc.font.width(digStr), digestY + 0.5F, COL_DIGEST_SAT);
        }
    }

    /**
     * 条上实际绘制的格数: 每格 2 点, 最多 10 格。
     * <p>
     * 上限 &gt; 20 时仍为 10（超出 20 的部分只由右侧数字表达，与原版饥饿条在提升上限后的表现一致）；
     * 上限 &lt; 20 时按比例缩短，否则满值会画成"半条"，视觉上等于谎报容量。
     */
    private static int barIcons() {
        return Mth.clamp(Mth.ceil(Digestion.maxDigestion() / 2.0F), 1, 10);
    }

    /** 消化值条: 先画空底, 再按数值画半/满 (每格 2 点); animate 时每格随机上下 1px (原版抖动) */
    private void drawBar(GuiGraphics graphics, int xRight, int y, float value, int v, boolean animate) {
        for (int i = 0; i < barIcons(); i++) {
            int x = xRight - ICON - i * STEP;
            int iconY = y + (animate ? random.nextInt(3) - 1 : 0);
            graphics.blit(TEXTURE, x, iconY, ICON, ICON, U_EMPTY, v, TILE, TILE, 64, 128);
        }
        drawValueIcons(graphics, xRight, y, value, v, animate);
    }

    /** 只画值图标 (无空底), 供消化饱和度同行叠加 (仿 AppleSkin 饱和度 overlay, 不画 empty 底) */
    private void drawValueIcons(GuiGraphics graphics, int xRight, int y, float value, int v, boolean animate) {
        for (int i = 0; i < barIcons(); i++) {
            int x = xRight - ICON - i * STEP;
            int iconY = y + (animate ? random.nextInt(3) - 1 : 0);
            float remaining = value - i * 2.0F;
            if (remaining >= 2.0F) {
                graphics.blit(TEXTURE, x, iconY, ICON, ICON, U_FULL, v, TILE, TILE, 64, 128);
            } else if (remaining >= 0.5F) {
                graphics.blit(TEXTURE, x, iconY, ICON, ICON, U_HALF, v, TILE, TILE, 64, 128);
            }
        }
    }

    /** 左对齐绘制数字 (带阴影), y 支持小数以对齐 AP++ 的 +0.5 亚像素 */
    private void drawNumber(GuiGraphics graphics, Minecraft mc, String text, float x, float y, int color) {
        graphics.drawString(mc.font, text, x, y, color, true);
    }

    /** 手持(主手优先)的食物物品栈; 两手都不是食物时返回 {@link ItemStack#EMPTY} */
    private ItemStack getHeldFoodStack(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem().getFoodProperties(mainHand, player) != null) {
            return mainHand;
        }
        if (ClientConfig.offhand()) {
            ItemStack offHand = player.getOffhandItem();
            if (offHand.getItem().getFoodProperties(offHand, player) != null) {
                return offHand;
            }
        }
        return ItemStack.EMPTY;
    }
}
