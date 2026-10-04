package io.github.autyism.easysuppressions;

import io.github.autyism.easysuppressions.block.LightSuppressorBlock;
import io.github.autyism.easysuppressions.block.LightSuppressorBlockEntity;
import io.github.autyism.easysuppressions.block.PortalCutterBlock;
import io.github.autyism.easysuppressions.block.PortalCutterBlockEntity;
import io.github.autyism.easysuppressions.item.BoundZone;
import io.github.autyism.easysuppressions.item.PortalPickaxeItem;
import io.github.autyism.easysuppressions.item.Selection;
import io.github.autyism.easysuppressions.item.SelectorItem;
import io.github.autyism.easysuppressions.item.SuppressionBlockItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

import java.util.function.Function;

/** Every block, item, block entity type and item component of the mod. */
public final class ModContent {
	public static final ComponentType<Selection> SELECTION = Registry.register(Registries.DATA_COMPONENT_TYPE,
			EasySuppressions.id("selection"),
			ComponentType.<Selection>builder().codec(Selection.CODEC).packetCodec(Selection.PACKET_CODEC).build());
	public static final ComponentType<BoundZone> BOUND_ZONE = Registry.register(Registries.DATA_COMPONENT_TYPE,
			EasySuppressions.id("bound_zone"),
			ComponentType.<BoundZone>builder().codec(BoundZone.CODEC).packetCodec(BoundZone.PACKET_CODEC).build());

	public static final Block PORTAL_CUTTER_BLOCK = block("portal_cutter_block", MapColor.PURPLE, PortalCutterBlock::new);
	public static final Block LIGHT_SUPPRESSOR_BLOCK = block("light_suppressor_block", MapColor.ORANGE, LightSuppressorBlock::new);

	public static final BlockEntityType<PortalCutterBlockEntity> PORTAL_CUTTER_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, EasySuppressions.id("portal_cutter_block"),
			FabricBlockEntityTypeBuilder.create(PortalCutterBlockEntity::new, PORTAL_CUTTER_BLOCK).build());
	public static final BlockEntityType<LightSuppressorBlockEntity> LIGHT_SUPPRESSOR_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, EasySuppressions.id("light_suppressor_block"),
			FabricBlockEntityTypeBuilder.create(LightSuppressorBlockEntity::new, LIGHT_SUPPRESSOR_BLOCK).build());

	public static final Item PORTAL_CUTTER_BLOCK_ITEM = item("portal_cutter_block",
			s -> new SuppressionBlockItem(PORTAL_CUTTER_BLOCK, SuppressorKind.PORTAL_CUTTER, s.useBlockPrefixedTranslationKey()));
	public static final Item LIGHT_SUPPRESSOR_BLOCK_ITEM = item("light_suppressor_block",
			s -> new SuppressionBlockItem(LIGHT_SUPPRESSOR_BLOCK, SuppressorKind.LIGHT_SUPPRESSOR, s.useBlockPrefixedTranslationKey()));
	public static final Item PORTAL_CUTTER_SELECTOR = item("portal_cutter_selector",
			s -> new SelectorItem(SuppressorKind.PORTAL_CUTTER, s.maxCount(1).fireproof().rarity(Rarity.UNCOMMON)));
	public static final Item LIGHT_SUPPRESSOR_SELECTOR = item("light_suppressor_selector",
			s -> new SelectorItem(SuppressorKind.LIGHT_SUPPRESSOR, s.maxCount(1).fireproof().rarity(Rarity.UNCOMMON)));
	public static final Item PORTAL_PICKAXE = item("portal_pickaxe",
			s -> new PortalPickaxeItem(s.maxCount(1).fireproof().rarity(Rarity.UNCOMMON)));

	public static final ItemGroup ITEM_GROUP = Registry.register(Registries.ITEM_GROUP, EasySuppressions.id("main"),
			FabricItemGroup.builder()
					.displayName(Text.translatable("itemGroup.easysuppressions"))
					.icon(() -> new ItemStack(PORTAL_CUTTER_BLOCK_ITEM))
					.entries((context, entries) -> {
						entries.add(PORTAL_CUTTER_BLOCK_ITEM);
						entries.add(PORTAL_CUTTER_SELECTOR);
						entries.add(PORTAL_PICKAXE);
						entries.add(LIGHT_SUPPRESSOR_BLOCK_ITEM);
						entries.add(LIGHT_SUPPRESSOR_SELECTOR);
					})
					.build());

	private ModContent() {
	}

	public static void init() {
		// Class loading registers everything above.
	}

	public static Item blockItem(SuppressorKind kind) {
		return kind == SuppressorKind.PORTAL_CUTTER ? PORTAL_CUTTER_BLOCK_ITEM : LIGHT_SUPPRESSOR_BLOCK_ITEM;
	}

	public static Item selector(SuppressorKind kind) {
		return kind == SuppressorKind.PORTAL_CUTTER ? PORTAL_CUTTER_SELECTOR : LIGHT_SUPPRESSOR_SELECTOR;
	}

	/** Items that may not be used as a crafting ingredient, enchanted or put into a grindstone. */
	public static boolean isRestrictedTool(ItemStack stack) {
		return stack.isOf(PORTAL_CUTTER_SELECTOR) || stack.isOf(LIGHT_SUPPRESSOR_SELECTOR) || stack.isOf(PORTAL_PICKAXE);
	}

	private static Block block(String name, MapColor color, Function<AbstractBlock.Settings, Block> factory) {
		RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, EasySuppressions.id(name));
		AbstractBlock.Settings settings = AbstractBlock.Settings.create()
				.registryKey(key)
				.mapColor(color)
				// The real survival break time is fixed by the config (see SuppressionBlock); the
				// resistance makes every explosion (TNT, creepers, wither skulls, end crystals) harmless.
				.strength(50.0F, 3600000.0F)
				.dropsNothing()
				.pistonBehavior(PistonBehavior.BLOCK)
				// Not a redstone conductor: the block takes power but never passes it on.
				.solidBlock((state, world, pos) -> false)
				.sounds(BlockSoundGroup.AMETHYST_BLOCK);
		return Registry.register(Registries.BLOCK, key, factory.apply(settings));
	}

	private static Item item(String name, Function<Item.Settings, Item> factory) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, EasySuppressions.id(name));
		return Registry.register(Registries.ITEM, key, factory.apply(new Item.Settings().registryKey(key)));
	}
}
