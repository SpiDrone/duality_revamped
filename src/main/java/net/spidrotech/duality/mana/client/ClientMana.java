package net.spidrotech.duality.mana.client;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.abilities.vampire.VampireRank;
import net.spidrotech.duality.abilities.vampire.BloodDrinking;

import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;

/**
 * The owner's own mana as last sent by ManaNetwork, and the HUD for it.
 *
 * <ul>
 * <li><b>Everyone but vampires</b> gets a mana bar in the bottom-right corner of the screen.
 * <li><b>Vampires</b> have no mana - their powers run on blood (see Mana#usesBlood) - so they get no
 * mana bar. Their hunger row is replaced by a row of blood drops, since blood is their hunger too
 * (see VampireHunger). Each drop is a tenth of their reserve, drawn right to left like hunger.
 * </ul>
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ClientMana {
	private static final ResourceLocation BLOOD_ROW_ID = ResourceLocation.fromNamespaceAndPath("duality", "blood_row");
	private static final ResourceLocation MANA_BAR_ID = ResourceLocation.fromNamespaceAndPath("duality", "mana_bar");
	private static final int BAR_WIDTH = 90, BAR_HEIGHT = 5, CORNER_MARGIN = 6;
	private static float current, max;

	/** A blood drop, 9x9, '#' filled. Drawn in fills so it needs no texture. */
	private static final String[] DROP = {"....#....", "...###...", "...###...", "..#####..", ".#######.", ".#######.", ".#######.", "..#####..", "...###..."};

	private ClientMana() {
	}

	public static void accept(float newCurrent, float newMax) {
		current = newCurrent;
		max = newMax;
	}

	public static float current() {
		return current;
	}

	public static float max() {
		return max;
	}

	private static boolean isVampire(Player player) {
		return player != null && VampireRank.isVampire(player);
	}

	private static boolean hudVisible(Minecraft mc) {
		return !mc.options.hideGui && mc.player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer();
	}

	@SubscribeEvent
	public static void registerLayers(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, BLOOD_ROW_ID, ClientMana::renderBloodRow);
		event.registerAboveAll(MANA_BAR_ID, ClientMana::renderManaBar);
	}

	/** A vampire's hunger row isn't drawn at all - the blood row takes its place. */
	@SubscribeEvent
	public static void onLayer(RenderGuiLayerEvent.Pre event) {
		if (event.getName().equals(VanillaGuiLayers.FOOD_LEVEL) && isVampire(Minecraft.getInstance().player))
			event.setCanceled(true);
	}

	private static void renderBloodRow(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!hudVisible(mc) || !isVampire(mc.player) || mc.player.getVehicle() != null)
			return;
		double blood = mc.player.getData(DualityModVariables.PLAYER_VARIABLES).blood;
		// Where the hunger row would have been - it was cancelled, so its space is still free.
		int right = g.guiWidth() / 2 + 91;
		int y = g.guiHeight() - mc.gui.rightHeight;
		double perDrop = BloodDrinking.RESERVE_MAX / 10.0;
		for (int i = 0; i < 10; i++) {
			int x = right - i * 8 - 9;
			// How full this drop is, 0..1: the first drop (rightmost) fills first, like hunger.
			double fill = Math.max(0, Math.min(1, (blood - i * perDrop) / perDrop));
			drawDrop(g, x, y, fill);
		}
		mc.gui.rightHeight += 10;
	}

	private static void drawDrop(GuiGraphics g, int x, int y, double fill) {
		// Full drops fill completely; a part-full one fills from the bottom.
		int filledRows = (int) Math.round(fill * DROP.length);
		for (int row = 0; row < DROP.length; row++) {
			boolean filled = row >= DROP.length - filledRows;
			String line = DROP[row];
			for (int col = 0; col < line.length(); col++) {
				if (line.charAt(col) != '#')
					continue;
				int color = filled ? (row <= 2 && col == 4 || row == 4 && col == 2 ? 0xFFFF6B6B : 0xFFB00000) : 0xFF3A0A0A;
				g.fill(x + col, y + row, x + col + 1, y + row + 1, color);
			}
		}
	}

	private static void renderManaBar(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!hudVisible(mc) || isVampire(mc.player) || max <= 0)
			return;
		int right = g.guiWidth() - CORNER_MARGIN;
		int left = right - BAR_WIDTH;
		int y = g.guiHeight() - CORNER_MARGIN - BAR_HEIGHT;
		int filled = Math.round(BAR_WIDTH * Math.max(0, Math.min(1, current / max)));
		g.fill(left - 1, y - 1, right + 1, y + BAR_HEIGHT + 1, 0xFF101018);
		g.fill(left, y, right, y + BAR_HEIGHT, 0xFF1B2440);
		if (filled > 0)
			g.fillGradient(left, y, left + filled, y + BAR_HEIGHT, 0xFF7FB2FF, 0xFF2D5BD8);
		String label = Math.round(current) + " / " + Math.round(max);
		g.drawString(mc.font, label, right - mc.font.width(label), y - 10, 0xFF9FC3FF, true);
	}
}
