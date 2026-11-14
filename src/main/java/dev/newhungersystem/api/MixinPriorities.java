package dev.newhungersystem.api;

/**
 * 本模组 Mixin 的优先级常量（单一事实来源）。
 *
 * <p><b>为什么"接管型" mixin 要用低优先级</b>：
 * <ol>
 *   <li>Mixin 按优先级<b>升序</b>应用 —— {@code MixinInfo#compareTo} 的字节码为
 *       {@code return this.priority - other.priority}（同级再按注册顺序 {@code order}），
 *       故优先级越低越早写入目标方法。</li>
 *   <li>一个方法被某个 mixin {@code @Overwrite} 之后，其它模组的注入
 *       （{@code @Inject} / {@code @ModifyVariable} / MixinExtras 的 {@code @WrapOperation} 等）
 *       能否落入该方法体，取决于 {@code InjectionPoint#checkPriority(mergedPriority, myPriority)}：
 *       其字节码等价于 {@code mergedPriority < myPriority} —— <b>注入方的优先级必须严格高于接管方</b>，
 *       否则在 PREINJECT 阶段抛出 {@code InvalidInjectionException: ... cannot inject into ... merged by ...}；
 *       由于 {@code injectors.defaultRequire = 1}，这会直接导致游戏启动崩溃。</li>
 *   <li>反过来，{@code @Overwrite} 之间的冲突是"<b>先写入者胜出</b>"：后写入者被
 *       {@code Method overwrite conflict ... Skipping method} 跳过。因此低优先级同时保证
 *       本模组的接管不会被别的 {@code @Overwrite} 取代 —— 两头都稳。</li>
 * </ol>
 *
 * <p><b>本模组的使用范围</b>：按用户要求，所有 {@code @Overwrite} 一律取最低档
 * {@link #TAKE_OVER_FINAL}（{@code -Integer.MAX_VALUE}），保证任何模组的注入优先级都严格高于它、
 * 且没有任何模组能比它更早写入。当前唯一的接管型 mixin 是
 * {@code FoodDataMixin}（{@code FoodData#tick} / {@code eat} / {@code needsFood}）。
 * <ul>
 *   <li>{@code FoodDataInitMixin}（仅构造 {@code @Inject}）与 {@code MobEffectMixin}（仅
 *       {@code @Inject}）<b>保持默认 1000</b>：注入器若也压到最低档，反而可能被后写入的
 *       {@code @Overwrite} 冲掉。这也是把构造注入从 FoodDataMixin 拆出来的原因。</li>
 * </ul>
 *
 * <p>Mixin 默认优先级是 1000，生态中绝大多数 mixin 配置都用默认值。若某个整合包存在优先级
 * ≤ {@value #TAKE_OVER_FINAL} 的模组仍冲突，按 {@code TAKE_OVER_OVER_*} 阶梯下调即可
 * （常量阶梯照搬 New Armor System 的 {@code dev.newarmorsystem.api.MixinPriorities}，便于两个项目对照）。
 *
 * @author THEREDK
 */
public final class MixinPriorities {

    /** 接管型（{@code @Overwrite}）mixin 的基准优先级：低于默认值 1000。 */
    public static final int TAKE_OVER = 500;

    public static final int TAKE_OVER_OVER = 250;

    public static final int TAKE_OVER_OVER_OVER = 0;

    public static final int TAKE_OVER_OVER_OVER_OVER = -250;

    public static final int TAKE_OVER_OVER_OVER_OVER_OVER = -500;

    public static final int TAKE_OVER_OVER_OVER_OVER_OVER_OVER = -750;

    public static final int TAKE_OVER_OVER_OVER_OVER_OVER_OVER_OVER = -1000;

    /**
     * 最低档（{@code -Max}）：必须最先写入、任何模组都不能取代或早于它。
     * 本模组全部 {@code @Overwrite} 使用此值。
     */
    public static final int TAKE_OVER_FINAL = -Integer.MAX_VALUE;

    /** 包装型（wrapper）mixin：低于默认值但高于接管档。 */
    public static final int WRAPPER = 750;

    /** 最高优先级（最后应用）：供"必须最后定案"的守卫类使用。 */
    public static final int FINAL = Integer.MAX_VALUE;

    private MixinPriorities() {
    }
}
