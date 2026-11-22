package dev.newhungersystem.api;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * 本模组自己发布的 Tooltip 叠加事件 (结构与 AppleSkin TooltipOverlayEvent 对齐)。
 * <p>
 * {@link Pre} 在收集 Tooltip 组件前 post，可取消整条食物值行；
 * {@link Render} 在实际绘制前 post，可改写 x/y 与 GuiGraphics。
 */
@Cancelable
public class TooltipOverlayEvent extends Event {

    public final FoodValues defaultFood;
    public final FoodValues modifiedFood;
    public final ItemStack itemStack;

    private TooltipOverlayEvent(ItemStack itemStack, FoodValues defaultFood, FoodValues modifiedFood) {
        this.itemStack = itemStack;
        this.defaultFood = defaultFood;
        this.modifiedFood = modifiedFood;
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    public static class Pre extends TooltipOverlayEvent {
        public Pre(ItemStack itemStack, FoodValues defaultFood, FoodValues modifiedFood) {
            super(itemStack, defaultFood, modifiedFood);
        }
    }

    public static class Render extends TooltipOverlayEvent {
        public int x;
        public int y;
        public GuiGraphics guiGraphics;

        public Render(ItemStack itemStack, int x, int y, GuiGraphics guiGraphics,
                      FoodValues defaultFood, FoodValues modifiedFood) {
            super(itemStack, defaultFood, modifiedFood);
            this.x = x;
            this.y = y;
            this.guiGraphics = guiGraphics;
        }
    }
}
