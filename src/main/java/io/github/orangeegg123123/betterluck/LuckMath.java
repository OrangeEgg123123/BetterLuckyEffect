package io.github.orangeegg123123.betterluck;

import net.minecraft.util.RandomSource;

/** 幸运加成的共用算法。 */
public final class LuckMath {
    private LuckMath() {
    }

    /**
     * 把「小数加成」变成整数掉落。
     *
     * <p>做法是<b>逐个物品判定</b>，而不是「先对整堆取整、余数再掷一次」：
     * <ul>
     *   <li>倍率的整数部分：每个物品都固定拿到（{@code whole} 件）</li>
     *   <li>小数部分：每个物品各自按这个概率再多拿 1 件</li>
     * </ul>
     *
     * <p>这样<b>期望值和不拆时完全一样</b>（都是 {@code count × bonus}），
     * 只是把「一堆一次 +4」拆成「一件一件 +1」，观感上频次更高、每次更少。
     *
     * <p>例如 count=3、加成 0.8：
     * <ul>
     *   <li>旧做法：先给 2 件，再 40% 概率给第 3 件 → 结果只有 2 或 3</li>
     *   <li>新做法：每件各自 80% → 结果 1~3，期望同样是 2.4</li>
     * </ul>
     *
     * @param count        原数量
     * @param bonusPerItem 每个物品的额外倍率（0.8 表示 +80%）
     * @param random       随机源
     * @param perStackCap  每一堆的额外数量上限；{@code 0} 表示不限制
     * @return 应该额外增加的数量，可能为 0
     */
    public static int rollExtra(int count, float bonusPerItem, RandomSource random, int perStackCap) {
        if (count <= 0 || bonusPerItem <= 0.0F) return 0;

        int whole = (int) bonusPerItem;
        float frac = bonusPerItem - whole;

        int extra = 0;
        for (int i = 0; i < count; i++) {
            extra += whole;
            if (frac > 0.0F && random.nextFloat() < frac) {
                extra++;
            }
            // 额外数量只增不减，够到上限就能提前退出
            if (perStackCap > 0 && extra >= perStackCap) {
                return perStackCap;
            }
        }
        return extra;
    }

    /**
     * 把「小数期望」变成整数次数（用于「多掷几次战利品表」这类加成）。
     *
     * <p>口径和 {@link #rollExtra} 里的「小数部分按概率补 1」完全一致：
     * 整数部分固定拿到，小数部分按这个概率再补 1 次，
     * 所以<b>期望次数就等于 {@code expectation}</b>。
     *
     * <p>为什么不直接取整：期望 0.5 次时取整会变成 0 次，整个加成被抹掉。
     *
     * @param expectation 期望次数，例如「每级 1 次 × 幸运 0.5」= 0.5
     * @param random      随机源
     * @param cap         次数上限；{@code 0} 表示不限制
     * @return 本次实际次数，可能为 0
     */
    public static int rollCount(float expectation, RandomSource random, int cap) {
        if (expectation <= 0.0F) return 0;

        int whole = (int) expectation;
        float frac = expectation - whole;

        int count = whole;
        if (frac > 0.0F && random.nextFloat() < frac) {
            count++;
        }
        // 只有真的超了才封顶，免得白白吃掉一次随机数
        return cap > 0 && count > cap ? cap : count;
    }
}
