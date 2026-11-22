package dev.newhungersystem.api;

import dev.newhungersystem.NewHungerSystem;
import dev.newhungersystem.compat.ModCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.MinecraftForge;

import java.util.Random;
import java.util.Vector;

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

    /** 每帧预生成的逐格胃图标偏移 (供外部 HUD 对齐叠加层；AppleSkin foodBarOffsets 同款) */
    public static final Vector<IntPoint> foodBarOffsets = new Vector<>();

    /** 96x128 图集 (scripts/gen_stomach_icons.py 生成) */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(NewHungerSystem.MOD_ID, "textures/gui/stomach_icons.png");

    private static final int ICON = 9;    // 绘制尺寸 = 原版饥饿图标大小
    private static final int STEP = 8;    // 图标间距 = 原版 (10 格条总宽 81px)
    public static final int TILE = 16;   // 图集源图块尺寸
    private static final int ROW = 10;    // 原版 HUD 行距
    public static final int U_FULL = 0;
    public static final int U_HALF = 16;
    public static final int U_EMPTY = 32;
    /** 消化饱和度四档 (与 AppleSkin 的 1/4、1/2、3/4、1 档位一致)，另加空格 */
    public static final int U_SAT_QUARTER = 0;
    public static final int U_SAT_HALF = 16;
    public static final int U_SAT_THREE_QUARTER = 32;
    public static final int U_SAT_FULL = 48;
    public static final int U_SAT_EMPTY = 64;
    public static final int V_DIGEST = 0;
    public static final int V_SAT = 16;
    /** 腐坏/有害食物: 与正常行同 U 偏移，只是换到暗绿整行 (AppleSkin rotten shank 同款切换) */
    public static final int V_ROTTEN_DIGEST = 80;
    private static final int V_ROTTEN_POPUP_RED = 96;
    /** 消耗值底栏: AppleSkin mod_icons 同款 81x9 深灰条, 绘制时从右端截 width 像素 */
    public static final int U_EXHAUSTION = 0;
    public static final int V_EXHAUSTION = 112;
    public static final int EXHAUSTION_WIDTH = 81;
    public static final int EXHAUSTION_HEIGHT = 9;
    public static final int TEXTURE_WIDTH = 96;
    public static final int TEXTURE_HEIGHT = 128;
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
        // 与 AppleSkin 的 FOOD_LEVEL overlay 门槛一致: hideGui / 骑乘生物 / 非生存元素时不画
        // (骑乘时原版饥饿条也隐藏，本条消化条作为它的附属行必须一起让位)
        if (player == null || mc.options.hideGui || !gui.shouldDrawSurvivalElements()
                || player.getVehicle() instanceof LivingEntity) {
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
        // AppleSkin generateHungerBarOffsets: 每帧按格预生成一次偏移表，底条/值层/popup 共用，
        // 保证预显层与下面的图标严丝合缝地一起抖 (不再各自 nextInt)。
        int icons = barIcons();
        if (foodBarOffsets.size() != icons) {
            foodBarOffsets.setSize(icons);
        }
        for (int i = 0; i < icons; i++) {
            IntPoint point = foodBarOffsets.get(i);
            if (point == null) {
                point = new IntPoint();
                foodBarOffsets.set(i, point);
            }
            point.x = -ICON - i * STEP;
            point.y = animate ? random.nextInt(3) - 1 : 0;
        }

        // ---- 单行: 消化值 (整格胃) + 消化饱和度 (同行黄色图标, 仿原版饱和度叠在饥饿行的画法) ----
        HUDOverlayEvent.Saturation saturationEvent = new HUDOverlayEvent.Saturation(
                digestionSat, xRight, digestY, graphics);
        boolean drawSaturation = ClientConfig.showSaturationOverlay() && digestionSat > 0.0F
                && !MinecraftForge.EVENT_BUS.post(saturationEvent);
        drawBar(graphics, xRight, digestY, digestion, V_DIGEST);
        if (drawSaturation) {
            drawValueIcons(graphics, xRight, digestY, digestionSat, V_SAT);
        }
        gui.rightHeight += ROW;                          // 仅占一行 (氧气条等自动上移)

        // ---- 回复预显 (AppleSkin 完整仿制): 红=消化值增量, 黄=消化饱和度增量, 闪烁 ----
        ItemStack heldFood = ClientConfig.showFoodValuesOverlay() ? FoodHelper.getHeldFoodStack(player) : ItemStack.EMPTY;
        // 客户端显示值事件 (AppleSkin FoodValuesEvent 同款): 客户端模组可按物品改写 HUD 预显值
        FoodValuesEvent valuesEvent = heldFood.isEmpty() ? null : FoodHelper.postFoodValuesEvent(player, heldFood);
        if (valuesEvent == null) {
            resetFlash();
        } else {
            // 腐肉/河豚等带 HARMFUL 效果的食物用整套 rotten 贴图 (仿 AppleSkin FoodHelper.isRotten)
            boolean rotten = FoodHelper.isRotten(heldFood, player);
            int popupV = rotten ? V_ROTTEN_POPUP_RED : V_POPUP_RED;
            float potential = Math.max(0.0F, valuesEvent.modifiedFoodValues.digestion);
            float potentialSat = Math.max(0.0F, valuesEvent.modifiedFoodValues.digestionSaturation);
            float digGain = Math.max(0.0F, Math.min(potential, Digestion.maxDigestion() - digestion));
            float satCap = Math.min(Digestion.maxDigestionSaturation(), digestion + digGain);
            float satGain = Math.max(0.0F, Math.min(potentialSat, satCap - digestionSat));
            if (digGain <= 0.0F && satGain <= 0.0F) {
                resetFlash();
            } else {
                if (digGain > 0.0F) {
                    HUDOverlayEvent.HungerRestored hungerEvent = new HUDOverlayEvent.HungerRestored(
                            digestion, heldFood, valuesEvent.modifiedFoodValues, xRight, digestY, graphics);
                    if (!MinecraftForge.EVENT_BUS.post(hungerEvent)) {
                        float modified = digestion + digGain;
                        // 上限 > 20 时条已画满, 起点会超出可视格数 → clamp 到最后一格,
                        // 让"还能继续加消化值"的信号仍然可见, 而不是整个预显消失
                        int startBar = Math.min(icons - 1, (int) (digestion / 2.0F));
                        int endBar = Math.min(icons, Mth.ceil(modified / 2.0F));
                        int baseV = rotten ? V_ROTTEN_DIGEST : V_DIGEST;
                        enableAlpha(flashAlpha);
                        for (int i = startBar; i < endBar; i++) {
                            IntPoint offset = foodBarOffsets.get(i);
                            int x = xRight + offset.x;
                            int y = digestY + offset.y;
                            int u = modified - i * 2.0F >= 2.0F ? U_FULL : U_HALF;
                            // AppleSkin drawHungerOverlay: 25% 底 + 全透明描边 = 空心轮廓感
                            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, flashAlpha * 0.25F);
                            graphics.blit(TEXTURE, x, y, ICON, ICON, U_EMPTY, baseV, TILE, TILE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
                            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, flashAlpha);
                            graphics.blit(TEXTURE, x, y, ICON, ICON, u, popupV, TILE, TILE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
                        }
                        disableAlpha();
                    }
                }
                if (satGain > 0.0F) {
                    float modified = digestionSat + satGain;
                    int startBar = Math.min(icons - 1, (int) (digestionSat / 2.0F));
                    int endBar = Math.min(icons, Mth.ceil(modified / 2.0F));
                    // 饱和度 popup 按 AppleSkin drawSaturationOverlay 单层绘制，不叠底
                    enableAlpha(flashAlpha);
                    for (int i = startBar; i < endBar; i++) {
                        IntPoint offset = foodBarOffsets.get(i);
                        int x = xRight + offset.x;
                        int y = digestY + offset.y;
                        int u = modified - i * 2.0F >= 2.0F ? U_FULL : U_HALF;
                        graphics.blit(TEXTURE, x, y, ICON, ICON, u, V_POPUP_YELLOW, TILE, TILE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
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

    /** 消化值条: 先画空底, 再按数值画半/满 (每格 2 点); 偏移读公共 foodBarOffsets */
    private void drawBar(GuiGraphics graphics, int xRight, int y, float value, int v) {
        for (int i = 0; i < foodBarOffsets.size(); i++) {
            IntPoint offset = foodBarOffsets.get(i);
            int x = xRight + offset.x;
            int iconY = y + offset.y;
            graphics.blit(TEXTURE, x, iconY, ICON, ICON, U_EMPTY, v, TILE, TILE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
        drawValueIcons(graphics, xRight, y, value, v);
    }

    /** 只画值图标 (无空底), 供消化饱和度同行叠加 (仿 AppleSkin 饱和度 overlay, 不画 empty 底) */
    private void drawValueIcons(GuiGraphics graphics, int xRight, int y, float value, int v) {
        for (int i = 0; i < foodBarOffsets.size(); i++) {
            IntPoint offset = foodBarOffsets.get(i);
            int x = xRight + offset.x;
            int iconY = y + offset.y;
            float remaining = value - i * 2.0F;
            int u;
            if (v == V_SAT) {
                // AppleSkin drawSaturationOverlay 的四档阈值: >=1 / >0.5 / >0.25 / >0（按每格 2 点换算）
                if (remaining >= 2.0F) {
                    u = U_SAT_FULL;
                } else if (remaining > 1.0F) {
                    u = U_SAT_THREE_QUARTER;
                } else if (remaining > 0.5F) {
                    u = U_SAT_HALF;
                } else if (remaining > 0.0F) {
                    u = U_SAT_QUARTER;
                } else {
                    continue;
                }
            } else if (remaining >= 2.0F) {
                u = U_FULL;
            } else if (remaining >= 0.5F) {
                u = U_HALF;
            } else {
                continue;
            }
            graphics.blit(TEXTURE, x, iconY, ICON, ICON, u, v, TILE, TILE, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        }
    }

    /** 左对齐绘制数字 (带阴影), y 支持小数以对齐 AP++ 的 +0.5 亚像素 */
    private void drawNumber(GuiGraphics graphics, Minecraft mc, String text, float x, float y, int color) {
        graphics.drawString(mc.font, text, x, y, color, true);
    }
}