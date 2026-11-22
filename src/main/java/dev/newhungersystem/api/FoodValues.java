package dev.newhungersystem.api;

/**
 * 客户端显示用的食物数值 (对标 AppleSkin {@code squeek.appleskin.api.food.FoodValues})。
 * <p>
 * 注意：这是<b>显示层</b>数值，不是实际进食结算。实际结算仍走服务端的
 * {@link FoodDigestionEvent}。客户端模组可在 {@link FoodValuesEvent} 里把
 * {@code modifiedFoodValues} 替换成新的实例，从而按物品/标签调整 HUD 与 Tooltip 的预览值。
 */
public final class FoodValues {

    /** 单份食物带来的消化值 */
    public final float digestion;
    /** 单份食物带来的消化饱和度 (已按 saturationModifier × 2 换算) */
    public final float digestionSaturation;

    public FoodValues(float digestion, float digestionSaturation) {
        this.digestion = digestion;
        this.digestionSaturation = digestionSaturation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FoodValues other)) {
            return false;
        }
        return Float.compare(other.digestion, this.digestion) == 0
                && Float.compare(other.digestionSaturation, this.digestionSaturation) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(digestion);
        result = 31 * result + Float.floatToIntBits(digestionSaturation);
        return result;
    }
}
