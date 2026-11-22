package dev.newhungersystem.api;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 本模组自己发布的 HUD 叠加事件 (结构与 AppleSkin HUDOverlayEvent 对齐)。
 * <p>
 * 四个子类都在对应绘制之前 post 到 Forge EVENT_BUS；监听器可
 * {@link #setCanceled(boolean)} 跳过该层绘制。注意本模组的数值语义：
 * Saturation/HungerRestored 指消化饱和度/消化值，不是原版饥饿值。
 */
@Cancelable
public class HUDOverlayEvent extends Event {

    public int x;
    public int y;
    public GuiGraphics guiGraphics;

    private HUDOverlayEvent(int x, int y, GuiGraphics guiGraphics) {
        this.x = x;
        this.y = y;
        this.guiGraphics = guiGraphics;
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    /** 消耗值底栏 (原版饥饿行下方 81px) */
    public static class Exhaustion extends HUDOverlayEvent {
        public final float exhaustion;

        public Exhaustion(float exhaustion, int x, int y, GuiGraphics guiGraphics) {
            super(x, y, guiGraphics);
            this.exhaustion = exhaustion;
        }
    }

    /** 消化饱和度叠加 (与消化值同一行) */
    public static class Saturation extends HUDOverlayEvent {
        /** 当前消化饱和度 (对应 AppleSkin 的 saturationLevel 字段) */
        public final float saturationLevel;

        public Saturation(float saturationLevel, int x, int y, GuiGraphics guiGraphics) {
            super(x, y, guiGraphics);
            this.saturationLevel = saturationLevel;
        }
    }

    /** 手持食物的消化值 popup (对应 AppleSkin 的 HungerRestored) */
    public static class HungerRestored extends HUDOverlayEvent {
        public final FoodValues foodValues;
        public final ItemStack itemStack;
        /** 当前消化值 (对应 AppleSkin 的 currentFoodLevel) */
        public final float currentDigestion;

        public HungerRestored(float currentDigestion, ItemStack itemStack, FoodValues foodValues,
                              int x, int y, GuiGraphics guiGraphics) {
            super(x, y, guiGraphics);
            this.currentDigestion = currentDigestion;
            this.itemStack = itemStack;
            this.foodValues = foodValues;
        }
    }

    /** 血条上的消化链回血预显 */
    public static class HealthRestored extends HUDOverlayEvent {
        public final FoodValues foodValues;
        public final ItemStack itemStack;
        public final float modifiedHealth;

        public HealthRestored(float modifiedHealth, ItemStack itemStack, FoodValues foodValues,
                              int x, int y, GuiGraphics guiGraphics) {
            super(x, y, guiGraphics);
            this.modifiedHealth = modifiedHealth;
            this.itemStack = itemStack;
            this.foodValues = foodValues;
        }
    }
}
