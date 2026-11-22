package dev.newhungersystem.api;

import net.minecraft.resources.ResourceLocation;

/** 图标图集稳定入口 (对齐 AppleSkin TextureHelper)。 */
public final class TextureHelper {

    /** 原版 HUD 图标图集 (心形等) */
    public static final ResourceLocation MC_ICONS = new ResourceLocation("textures/gui/icons.png");
    /** 本模组胃/饱和度/预显/消耗值图集 */
    public static final ResourceLocation MOD_ICONS = DigestionOverlay.TEXTURE;

    private TextureHelper() {
    }
}
