package dev.newhungersystem.api;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/** Shift / Ctrl 按键查询入口 (对齐 AppleSkin KeyHelper，含 macOS 左右 Command 兼容)。 */
public final class KeyHelper {

    private static final int KEY_LSHIFT = 340;
    private static final int KEY_RSHIFT = 344;
    private static final int KEY_LCTRL = 341;
    private static final int KEY_RCTRL = 345;
    private static final int KEY_LMETA = 343;
    private static final int KEY_RMETA = 347;

    private KeyHelper() {
    }

    public static boolean isShiftKeyDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, KEY_LSHIFT) || InputConstants.isKeyDown(window, KEY_RSHIFT);
    }

    public static boolean isCtrlKeyDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean down = InputConstants.isKeyDown(window, KEY_LCTRL) || InputConstants.isKeyDown(window, KEY_RCTRL);
        if (!down && Minecraft.ON_OSX) {
            down = InputConstants.isKeyDown(window, KEY_LMETA) || InputConstants.isKeyDown(window, KEY_RMETA);
        }
        return down;
    }
}
