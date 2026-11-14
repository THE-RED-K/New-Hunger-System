package dev.newhungersystem.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 每个存档的「上限加成」持久化数据（{@link SavedData}，落在世界存档
 * {@code data/new_hunger_system_bonus.dat}）。
 * <p>
 * 只存 {@link HungerConfig#addMaxHunger} 等 API 在<b>运行期</b>累加的加成：
 * 启动阶段（尚无服务器）调用产生的 {@code baseBonus} 不写入这里 —— 那类加成由模组每次启动
 * 自己重新注册，重复持久化反而会随世界重复叠加。
 * <p>
 * 变更通过 {@link #setDirty()} 交给原版在下次存档时落盘；服务器关闭时 {@link HungerBonusStorage} 会再标脏一次。
 *
 * @author THEREDK
 */
public class HungerBonusData extends SavedData {

    public static final String DATA_NAME = "new_hunger_system_bonus";

    private static final String KEY_HUNGER = "BonusMaxHunger";
    private static final String KEY_SATURATION = "BonusMaxSaturation";
    private static final String KEY_DIGESTION = "BonusMaxDigestion";
    private static final String KEY_DIGESTION_SATURATION = "BonusMaxDigestionSaturation";

    private double bonusMaxHunger;
    private double bonusMaxSaturation;
    private double bonusMaxDigestion;
    private double bonusMaxDigestionSaturation;

    /** 反序列化入口（computeIfAbsent 用），缺省键按 0 处理，兼容旧存档 */
    public static HungerBonusData load(CompoundTag tag) {
        HungerBonusData data = new HungerBonusData();
        data.bonusMaxHunger = tag.getDouble(KEY_HUNGER);
        data.bonusMaxSaturation = tag.getDouble(KEY_SATURATION);
        data.bonusMaxDigestion = tag.getDouble(KEY_DIGESTION);
        data.bonusMaxDigestionSaturation = tag.getDouble(KEY_DIGESTION_SATURATION);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putDouble(KEY_HUNGER, bonusMaxHunger);
        tag.putDouble(KEY_SATURATION, bonusMaxSaturation);
        tag.putDouble(KEY_DIGESTION, bonusMaxDigestion);
        tag.putDouble(KEY_DIGESTION_SATURATION, bonusMaxDigestionSaturation);
        return tag;
    }

    public double getBonusMaxHunger() {
        return bonusMaxHunger;
    }

    public void addBonusMaxHunger(double amount) {
        this.bonusMaxHunger += amount;
        setDirty();
    }

    public double getBonusMaxSaturation() {
        return bonusMaxSaturation;
    }

    public void addBonusMaxSaturation(double amount) {
        this.bonusMaxSaturation += amount;
        setDirty();
    }

    public double getBonusMaxDigestion() {
        return bonusMaxDigestion;
    }

    public void addBonusMaxDigestion(double amount) {
        this.bonusMaxDigestion += amount;
        setDirty();
    }

    public double getBonusMaxDigestionSaturation() {
        return bonusMaxDigestionSaturation;
    }

    public void addBonusMaxDigestionSaturation(double amount) {
        this.bonusMaxDigestionSaturation += amount;
        setDirty();
    }
}
