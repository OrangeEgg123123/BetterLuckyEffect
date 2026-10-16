package com.example.betterluck;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * 幸运效果的实际收益。
 *
 * <p>原版的幸运只通过「战利品表的 quality 权重」和「bonus_rolls × luck」生效，
 * 而原版的怪物战利品表几乎不用 quality、方块掉落更是完全不看幸运，
 * 所以喝下去几乎感觉不到。这里补上三块：
 *
 * <ul>
 *   <li><b>挖矿</b>：时运镐挖矿石时，按幸运额外增加掉落数量。</li>
 *   <li><b>战斗</b>：抢夺武器击杀时，按幸运额外增加普通掉落数量；
 *       并额外重掷实体战利品表来提高稀有掉落的概率。</li>
 * </ul>
 *
 * <p>注意「数量」和「概率」刻意分管不同对象，不在同一个物品上叠加：
 * 稀有掉落的<em>概率</em>由额外重掷负责，稀有掉落的<em>数量</em>不再加成，
 * 否则同一个场景两条线叠起来会明显超模。
 *
 * <p>钓鱼那一块在 {@link LuckFishingModifier}，因为 {@code ItemFishedEvent}
 * 拿到的是掉落列表的副本，改不动真正的战利品。
 */
public final class LuckEffects {
    /**
     * 「稀有掉落物」的判定标签，定义在
     * {@code data/betterluck/tags/item/rare_drops.json}，可以自由增删。
     */
    public static final TagKey<Item> RARE_DROPS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(BetterLuckEffect.MODID, "rare_drops"));

    /**
     * 「宝箱里数量会随幸运增加」的物品清单，定义在
     * {@code data/betterluck/tags/item/chest_bonus_items.json}
     * （钻石、附魔书、各种锻造模板）。
     */
    public static final TagKey<Item> CHEST_BONUS_ITEMS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(BetterLuckEffect.MODID, "chest_bonus_items"));

    private static final LuckEffects INSTANCE = new LuckEffects();

    private LuckEffects() {
    }

    /** 挂到游戏事件总线上。由模组构造函数调用。 */
    public static void register() {
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    // ==================================================================
    //  挖矿：时运镐 + 幸运 → 矿物掉得更多
    // ==================================================================

    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        if (event.getDrops().isEmpty()) return;
        if (!(event.getBreaker() instanceof Player player)) return;

        // 只在挖矿石时生效
        if (!event.getState().is(Tags.Blocks.ORES)) return;

        // 需求写的是「附魔了时运的镐子」，所以没时运就没有这份加成
        if (enchantmentLevel(event.getTool(), Enchantments.FORTUNE) <= 0) return;

        // 数量加成走等级表：胸甲 + 主手工具 + 其余幸运（药水等）各自查表后相加
        float bonusPerItem = LuckScale.bonusFrom(player);
        if (bonusPerItem <= 0.0F) return;

        RandomSource random = event.getLevel().getRandom();

        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            // 挖矿/击杀不设每堆上限：矿石和掉落物本来就是 1~2 个一堆，封顶没意义
            int extra = LuckMath.rollExtra(stack.getCount(), bonusPerItem, random, 0);
            if (extra > 0) {
                stack.setCount(stack.getCount() + extra);
            }
        }
    }

    // ==================================================================
    //  战斗：幸运提高稀有掉落概率；抢夺武器再额外提高数量
    // ==================================================================

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (!event.isRecentlyHit()) return;

        float luck = player.getLuck();
        if (luck <= 0.0F) return;

        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;

        Collection<ItemEntity> drops = event.getDrops();

        // (1) 稀有掉落概率：额外重掷实体战利品表，只取稀有物，且已经掉过的不再重复。
        //     用「已掉过就不给」这条规则，可以避免把必掉物（比如凋灵的下界之星）翻倍。
        int rolls = Math.min(
                (int) (LuckTuning.rareDropRollsPerLuck() * luck),
                LuckTuning.rareDropMaxRolls());
        if (rolls > 0) {
            addExtraRareDrops(victim, level, player, event, drops, rolls);
        }

        // (2) 掉落数量：需求限定为「附魔了抢夺的剑或矛」，所以没抢夺就没有这份加成。
        //     稀有掉落刻意不走这条线——它已经被上面的概率线加成过了，
        //     同一个场景同时叠「数量」和「概率」会明显超模。
        if (enchantmentLevel(player.getMainHandItem(), Enchantments.LOOTING) <= 0) return;

        // 数量加成走等级表，同挖矿那一处
        float bonusPerItem = LuckScale.bonusFrom(player);
        if (bonusPerItem <= 0.0F) return;

        RandomSource random = level.getRandom();

        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (stack.is(RARE_DROPS)) continue;
            int extra = LuckMath.rollExtra(stack.getCount(), bonusPerItem, random, 0);
            if (extra > 0) {
                stack.setCount(stack.getCount() + extra);
            }
        }
    }

    private static void addExtraRareDrops(LivingEntity victim,
                                          ServerLevel level,
                                          Player player,
                                          LivingDropsEvent event,
                                          Collection<ItemEntity> drops,
                                          int rolls) {
        ResourceKey<LootTable> tableKey = victim.getLootTable();
        if (tableKey == null) return;

        LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, event.getSource().getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, event.getSource().getDirectEntity())
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withLuck(luck(player))
                .create(LootContextParamSets.ENTITY);

        // 记录已经掉出来的物品，重掷时同类稀有物只补一次
        Set<Item> alreadyDropped = new HashSet<>();
        for (ItemEntity drop : drops) {
            alreadyDropped.add(drop.getItem().getItem());
        }

        for (int i = 0; i < rolls; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (!stack.is(RARE_DROPS)) continue;
                if (!alreadyDropped.add(stack.getItem())) continue;
                drops.add(new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), stack));
            }
        }
    }

    private static float luck(Player player) {
        return player.getLuck();
    }

    // ==================================================================
    //  工具方法
    // ==================================================================

    /** 不查注册表，直接从物品的附魔组件里读等级。 */
    private static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        if (stack.isEmpty()) return 0;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(enchantment)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }
}
