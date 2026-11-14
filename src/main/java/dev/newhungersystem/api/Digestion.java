package dev.newhungersystem.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 玩家消化系统数据:
 * digestion            消化值      (0..maxDigestion(), 上限见 HungerConfig)
 * digestionSaturation  消化饱和度  (0..maxDigestionSaturation(), 上限见 HungerConfig, 且不能超过消化值)
 * foodCarry            消化值->饥饿值的进位缓存 (0..1, 内部用)
 * lastAttrFactor/lastSwimFactor  上一次应用的属性因子缓存, 仅在变化时才重设修饰器
 */
public class Digestion implements INBTSerializable<CompoundTag> {

    /** 最大消化值 = 配置 baseMaxDigestion + API 加成 (不写死, 默认 30) */
    public static float maxDigestion() {
        return HungerConfig.maxDigestion();
    }

    /** 最大消化饱和度 = 配置 baseMaxDigestionSaturation + API 加成 (不写死, 默认 30) */
    public static float maxDigestionSaturation() {
        return HungerConfig.maxDigestionSaturation();
    }

    private float digestion;
    private float digestionSaturation;
    private float foodCarry;
    private float lastAttrFactor = 1.0F;
    private float lastSwimFactor = 1.0F;
    /** 溢出缓存 (默认关闭, 由 DigestionOverflowEvent 填充, 每 0.5s 回填) */
    private float overflowDigestionCache;
    private float overflowSaturationCache;

    /** Capability 所属玩家; 为 null (手工 new) 时不广播事件, 但 setter 行为完全一致 */
    private final Player owner;

    /** 事件重入保护: 监听器在事件里同步回写 Capability 时不再二次广播, 避免无限递归 */
    private boolean posting;

    /** 无 owner 构造: 供测试/第三方手工实例化, 不广播事件 */
    public Digestion() {
        this(null);
    }

    /** 由 {@link DigestionCapability.Provider} 在附加到玩家时调用 */
    public Digestion(Player owner) {
        this.owner = owner;
        // 开局默认: 消化值/消化饱和度取配置起始值, 并 clamp 到各自上限 (直接赋值, 不触发溢出事件)
        float startD = HungerConfig.startDigestion();
        float startDS = HungerConfig.startDigestionSaturation();
        this.digestion = Mth.clamp(startD, 0.0F, maxDigestion());
        this.digestionSaturation = Mth.clamp(startDS, 0.0F,
                Math.min(maxDigestionSaturation(), this.digestion));
    }

    public float getDigestion() {
        return digestion;
    }

    public void setDigestion(float value) {
        if (!Float.isFinite(value)) {
            return;   // NaN / Infinity 会污染 HUD、属性与所有比较, 直接丢弃
        }
        float before = this.digestion;
        float requested = value - before;
        this.digestion = Mth.clamp(value, 0.0F, maxDigestion());
        // 消化值下降后, 消化饱和度不得大于消化值
        this.digestionSaturation = Mth.clamp(this.digestionSaturation, 0.0F,
                Math.min(maxDigestionSaturation(), this.digestion));
        postOverflow(DigestionOverflowEvent.Type.DIGESTION, requested, this.digestion - before);
    }

    public void addDigestion(float value) {
        setDigestion(digestion + value);
    }

    public float getDigestionSaturation() {
        return digestionSaturation;
    }

    public void setDigestionSaturation(float value) {
        if (!Float.isFinite(value)) {
            return;   // NaN / Infinity 会污染 HUD、属性与所有比较, 直接丢弃
        }
        // 消化饱和度不能超过消化值, 也不能超过自身上限
        float before = this.digestionSaturation;
        float requested = value - before;
        this.digestionSaturation = Mth.clamp(value, 0.0F, Math.min(maxDigestionSaturation(), this.digestion));
        postOverflow(DigestionOverflowEvent.Type.DIGESTION_SATURATION, requested,
                this.digestionSaturation - before);
    }

    /** 统一的溢出广播口: 只有确实被 clamp 掉的量才发事件；手工 new 的实例 (owner=null) 不发 */
    private void postOverflow(DigestionOverflowEvent.Type type, float requested, float accepted) {
        float overflow = Math.max(0.0F, requested - accepted);
        if (owner == null || posting || overflow <= 0.0F) {
            return;
        }
        posting = true;
        try {
            MinecraftForge.EVENT_BUS.post(new DigestionOverflowEvent(owner, this, type,
                    requested, accepted, overflow));
        } finally {
            posting = false;
        }
    }

    public void addDigestionSaturation(float value) {
        setDigestionSaturation(digestionSaturation + value);
    }

    public float getFoodCarry() {
        return foodCarry;
    }

    public void setFoodCarry(float value) {
        this.foodCarry = Mth.clamp(value, 0.0F, 1.0F);
    }

    public float getLastAttrFactor() {
        return lastAttrFactor;
    }

    public void setLastAttrFactor(float value) {
        this.lastAttrFactor = value;
    }

    public float getLastSwimFactor() {
        return lastSwimFactor;
    }

    public void setLastSwimFactor(float value) {
        this.lastSwimFactor = value;
    }

    public float getOverflowDigestionCache() {
        return overflowDigestionCache;
    }

    /** 缓存上限由配置决定, 随取随 clamp, 因此调小容量也会立即收敛 */
    public void setOverflowDigestionCache(float value) {
        this.overflowDigestionCache = Mth.clamp(value, 0.0F, HungerConfig.overflowCacheCapacity());
    }

    public void addOverflowDigestionCache(float value) {
        setOverflowDigestionCache(overflowDigestionCache + value);
    }

    public float getOverflowSaturationCache() {
        return overflowSaturationCache;
    }

    public void setOverflowSaturationCache(float value) {
        this.overflowSaturationCache = Mth.clamp(value, 0.0F, HungerConfig.overflowCacheCapacity());
    }

    public void addOverflowSaturationCache(float value) {
        setOverflowSaturationCache(overflowSaturationCache + value);
    }

    /**
     * 完全重置本 Capability 的全部状态: 消化值/消化饱和度/进位缓存归零,
     * 属性因子缓存复位为 1 (不触碰原版 hunger/saturation)。
     * <p>
     * 只改数据; 若要同时清属性修饰器、重算惩罚并同步客户端, 请用 {@link DigestionHelper#reset}.
     */
    public void reset() {
        applyReset(0.0F, 0.0F);
    }

    /** 重置为配置里的开局数值 (等同新玩家), 同样把进位缓存与因子缓存复位 */
    public void resetToStart() {
        float targetD = Mth.clamp(HungerConfig.startDigestion(), 0.0F, maxDigestion());
        float targetDS = Mth.clamp(HungerConfig.startDigestionSaturation(), 0.0F,
                Math.min(maxDigestionSaturation(), targetD));
        applyReset(targetD, targetDS);
    }

    /** reset / resetToStart 共用: 直接赋字段 (不发事件) */
    private void applyReset(float targetDigestion, float targetSaturation) {
        this.digestion = targetDigestion;
        this.digestionSaturation = targetSaturation;
        this.foodCarry = 0.0F;
        this.lastAttrFactor = 1.0F;
        this.lastSwimFactor = 1.0F;
        this.overflowDigestionCache = 0.0F;
        this.overflowSaturationCache = 0.0F;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Digestion", digestion);
        tag.putFloat("DigestionSaturation", digestionSaturation);
        tag.putFloat("FoodCarry", foodCarry);
        tag.putFloat("OverflowDigestionCache", overflowDigestionCache);
        tag.putFloat("OverflowSaturationCache", overflowSaturationCache);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        // 读档时按"当前"上限 clamp: 管理员把 maxDigestion/maxDigestionSaturation 调低后,
        // 老存档里的超额数值不应继续存在 (否则 HUD 会出现 30/20 这种值, 要等下次写入才收敛)
        this.digestion = Mth.clamp(tag.getFloat("Digestion"), 0.0F, maxDigestion());
        this.digestionSaturation = Mth.clamp(tag.getFloat("DigestionSaturation"), 0.0F,
                Math.min(maxDigestionSaturation(), this.digestion));
        this.foodCarry = Mth.clamp(tag.getFloat("FoodCarry"), 0.0F, 1.0F);
        setOverflowDigestionCache(tag.getFloat("OverflowDigestionCache"));   // 读回时按当前容量 clamp
        setOverflowSaturationCache(tag.getFloat("OverflowSaturationCache"));
        // lastAttrFactor / lastSwimFactor 为瞬时缓存, 不持久化, 重置为 1 以便下次重新应用
        this.lastAttrFactor = 1.0F;
        this.lastSwimFactor = 1.0F;
    }
}
