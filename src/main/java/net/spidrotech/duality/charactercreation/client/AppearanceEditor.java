package net.spidrotech.duality.charactercreation.client;

import net.spidrotech.duality.skin.client.ClientSkinState;
import net.spidrotech.duality.charactercreation.RaceDefinition;
import net.spidrotech.duality.charactercreation.DraftView;
import net.spidrotech.duality.charactercreation.CharacterDraft;
import net.spidrotech.duality.charactercreation.AppearanceRequirements;
import net.spidrotech.duality.skin.SkinPartTarget;
import net.spidrotech.duality.skin.SkinPartCatalog;
import net.spidrotech.duality.skin.SkinPart;
import net.spidrotech.duality.skin.SkinNetwork;
import net.spidrotech.duality.skin.SkinLoadout;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

import net.spidrone.uiapi.UIScrollRegion;
import net.spidrone.uiapi.UIGraphicsHelper;
import net.spidrone.uiapi.UIClipRegion;
import net.spidrone.uiapi.UIEntityDisplay;

/**
 * Screen five's body-part editor.
 *
 * <p>Two scroll columns on the left, a category column and then that category's options, plus a
 * live preview of the player in the codex panel with a colour swatch for each tintable layer of
 * what's worn. Everything is drawn and hit-tested by hand at absolute screen coordinates, and it
 * reads the draft view and the player's loadout fresh every frame. That's why page five is never
 * rebuilt on a view update (see ClientCharacterCreation#refreshOpenScreen), so the name box keeps
 * focus while typing.
 *
 * <p>Picks go straight out as SkinNetwork.EquipPartPayload. The server re-checks ownership (during
 * creation, the player's creator unlocks) and pairs each eye with its pupil.
 */
public final class AppearanceEditor {
	public static final String CATEGORY_REGION = "duality:cc_categories";
	public static final String OPTION_REGION = "duality:cc_options";
	private static final int ROW_H = 14, GAP = 2, SCROLL_W = 5, REGION_H = 130;
	private static final int CATEGORY_X = -61, CATEGORY_W = 58, OPTION_X = 5, OPTION_W = 82, REGIONS_Y = 22;
	private static final int SWATCH = 11, SWATCH_GAP = 4;
	/** Shown first, in this order; any other category follows in catalog order. */
	private static final List<String> CATEGORY_ORDER = List.of("skins", "eyes", "eyebrows", "lips", "hair", "shirts", "pants", "shoes");
	/** Pupils are picked through their eye (and tinted from the eyes category). */
	private static final Set<String> HIDDEN_CATEGORIES = Set.of("pupils");
	private static final ResourceLocation BUTTON = ResourceLocation.parse("duality:textures/screens/button0.png");
	private static final ResourceLocation BUTTON_HOVER = ResourceLocation.parse("duality:textures/screens/button1.png");
	private static final ResourceLocation BUTTON_SELECTED = ResourceLocation.parse("duality:textures/screens/button2.png");
	private static final ResourceLocation FONT = ResourceLocation.parse("spis_ui_api:boldpixels");
	private static final int TEXT = -12829636;

	/** Static so the chosen tab survives the screen being re-inited (a resize, coming back from page four). */
	private static String selectedCategory;

	private final ColorPickerPopup picker = new ColorPickerPopup();
	private int left, top;

	/** One swatch: a tintable layer of a worn part. */
	private record TintSlot(SkinPart part, int index, int current, int defaultColor, boolean pupil, String label) {
	}

	public void init(int leftPos, int topPos) {
		this.left = leftPos;
		this.top = topPos;
		UIScrollRegion.defineRegion(CATEGORY_REGION, left + CATEGORY_X, top + REGIONS_Y, CATEGORY_W, REGION_H);
		UIScrollRegion.setContentStart(CATEGORY_REGION, 0, 0);
		UIScrollRegion.setSmoothScroll(CATEGORY_REGION, true);
		UIScrollRegion.setItemLayout(CATEGORY_REGION, ROW_H, GAP, 0);
		UIScrollRegion.defineRegion(OPTION_REGION, left + OPTION_X, top + REGIONS_Y, OPTION_W, REGION_H);
		UIScrollRegion.setContentStart(OPTION_REGION, 0, 0);
		UIScrollRegion.setSmoothScroll(OPTION_REGION, true);
		UIScrollRegion.setItemLayout(OPTION_REGION, ROW_H, GAP, 0);
		picker.close();
	}

	// ------------------------------------------------------------------------------------ data
	/** Categories holding at least one part this player may pick, in display order. */
	private List<String> categories() {
		List<String> found = new ArrayList<>();
		for (Map.Entry<String, List<SkinPart>> entry : SkinPartCatalog.byCategory().entrySet()) {
			if (HIDDEN_CATEGORIES.contains(entry.getKey()))
				continue;
			for (SkinPart part : entry.getValue()) {
				if (ClientCharacterCreation.isCosmeticAvailable(part)) {
					found.add(entry.getKey());
					break;
				}
			}
		}
		List<String> ordered = new ArrayList<>();
		for (String preferred : CATEGORY_ORDER) {
			if (found.remove(preferred))
				ordered.add(preferred);
		}
		ordered.addAll(found);
		return ordered;
	}

	private String currentCategory(List<String> categories) {
		if (selectedCategory == null || !categories.contains(selectedCategory))
			selectedCategory = categories.isEmpty() ? null : categories.get(0);
		return selectedCategory;
	}

	/** The options column: index 0 is "None", then every available part in the category. */
	private List<SkinPart> options(String category) {
		List<SkinPart> out = new ArrayList<>();
		if (category == null)
			return out;
		for (SkinPart part : SkinPartCatalog.byCategory().getOrDefault(category, List.of())) {
			if (ClientCharacterCreation.isCosmeticAvailable(part))
				out.add(part);
		}
		return out;
	}

	private static SkinLoadout loadout() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? SkinLoadout.EMPTY : ClientSkinState.loadoutOf(mc.player.getUUID());
	}

	private static SkinLoadout.Equipped equippedWhere(SkinLoadout loadout, java.util.function.Predicate<SkinPart> test) {
		for (SkinLoadout.Equipped equipped : loadout.parts()) {
			SkinPart part = SkinPartCatalog.get(equipped.partId());
			if (part != null && test.test(part))
				return equipped;
		}
		return null;
	}

	private static SkinLoadout.Equipped equippedIn(SkinLoadout loadout, String category) {
		return equippedWhere(loadout, part -> part.category().equals(category));
	}

	/** Every tintable layer worn in this category. The eyes category also carries the pupil's,
	 *  since pupils have no tab of their own. */
	private List<TintSlot> tintSlots(String category) {
		List<TintSlot> slots = new ArrayList<>();
		if (category == null)
			return slots;
		SkinLoadout loadout = loadout();
		addSlots(slots, equippedIn(loadout, category), false, "Colour");
		if ("eyes".equals(category))
			addSlots(slots, equippedWhere(loadout, part -> part.target() == SkinPartTarget.PUPIL), true, "Pupil colour");
		return slots;
	}

	private static void addSlots(List<TintSlot> slots, SkinLoadout.Equipped equipped, boolean pupil, String label) {
		if (equipped == null)
			return;
		SkinPart part = SkinPartCatalog.get(equipped.partId());
		if (part == null)
			return;
		int index = 0;
		for (SkinPart.SubPart sub : part.subParts()) {
			if (!sub.tintable())
				continue;
			int current = index < equipped.tints().size() ? equipped.tints().get(index) : -1;
			slots.add(new TintSlot(part, index, current, sub.defaultColor(), pupil, part.tintableCount() > 1 ? label + " " + (index + 1) : label));
			index++;
		}
	}

	/** "hair_male0_greyscale" in category "hair" reads as "Male0". */
	private static String displayName(SkinPart part) {
		String name = part.id();
		if (name.startsWith(part.category() + "_"))
			name = name.substring(part.category().length() + 1);
		for (String suffix : new String[]{"_greyscale", "_tintable"}) {
			if (name.endsWith(suffix))
				name = name.substring(0, name.length() - suffix.length());
		}
		if (name.startsWith(part.category() + "_") && name.length() > part.category().length() + 1)
			name = name.substring(part.category().length() + 1);
		return capitalise(name.replace('_', ' '));
	}

	/** 1 if the options column opens with "None", 0 for a slot every character must fill (skin,
	 *  eyes, pants - see AppearanceRequirements). */
	private static int noneRows(String category) {
		return category == null || AppearanceRequirements.isRequiredCategory(category) ? 0 : 1;
	}

	private static String categoryName(String category) {
		return "skins".equals(category) ? "Skin" : capitalise(category);
	}

	/**
	 * What's still stopping the character from being confirmed, as short phrases for the confirm
	 * button's tooltip - empty when it's ready. Read live, so it follows the name box and the
	 * loadout the moment either changes. The server re-checks all of it on commit.
	 *
	 * @param typedName what's in the name box right now - the draft's copy can lag by a packet
	 */
	public static List<String> confirmBlockers(String typedName) {
		DraftView view = ClientCharacterCreation.view();
		List<String> blockers = new ArrayList<>();
		RaceDefinition race = view.race();
		if (race == null)
			blockers.add("a race");
		else if (!race.subraces().isEmpty() && view.subrace() == null)
			blockers.add("a lineage");
		String trimmed = typedName == null ? "" : typedName.trim();
		if (!CharacterDraft.isNameUsable(trimmed))
			blockers.add("a name (1-" + CharacterDraft.NAME_MAX + " letters)");
		else if (trimmed.equals(view.name()) && !view.nameUsable())
			blockers.add("a name nobody living already has");
		else if (!trimmed.equals(view.name()))
			blockers.add("a moment - checking the name");
		blockers.addAll(AppearanceRequirements.missing(loadout()));
		return blockers;
	}

	private static String capitalise(String text) {
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	/** The tints a part should go on with: whatever it's already wearing, else all defaults. */
	private static List<Integer> tintsFor(SkinPart part, SkinLoadout.Equipped worn) {
		List<Integer> tints = new ArrayList<>();
		for (int i = 0; i < part.tintableCount(); i++) {
			tints.add(worn != null && worn.partId().equals(part.id()) && i < worn.tints().size() ? worn.tints().get(i) : -1);
		}
		return tints;
	}

	// ---------------------------------------------------------------------------------- render
	public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
		List<String> categories = categories();
		String category = currentCategory(categories);
		List<SkinPart> options = options(category);
		UIScrollRegion.setItemCount(CATEGORY_REGION, categories.size());
		int first = noneRows(category);
		UIScrollRegion.setItemCount(OPTION_REGION, options.size() + first);
		SkinLoadout.Equipped worn = category == null ? null : equippedIn(loadout(), category);
		boolean blockHover = picker.isOpen();

		UIClipRegion.beginClip(g, left + CATEGORY_X, top + REGIONS_Y, CATEGORY_W, REGION_H);
		for (int i = 0; i < categories.size(); i++) {
			drawRow(g, CATEGORY_REGION, i, CATEGORY_W, categoryName(categories.get(i)), categories.get(i).equals(category), !blockHover && overRow(CATEGORY_REGION, i, CATEGORY_W, mouseX, mouseY));
		}
		UIClipRegion.endClip(g);

		UIClipRegion.beginClip(g, left + OPTION_X, top + REGIONS_Y, OPTION_W, REGION_H);
		if (first == 1)
			drawRow(g, OPTION_REGION, 0, OPTION_W, "None", worn == null, !blockHover && overRow(OPTION_REGION, 0, OPTION_W, mouseX, mouseY));
		for (int i = 0; i < options.size(); i++) {
			SkinPart part = options.get(i);
			drawRow(g, OPTION_REGION, i + first, OPTION_W, displayName(part), worn != null && worn.partId().equals(part.id()), !blockHover && overRow(OPTION_REGION, i + first, OPTION_W, mouseX, mouseY));
		}
		UIClipRegion.endClip(g);

		UIScrollRegion.drawScrollbar(g, CATEGORY_REGION, left + CATEGORY_X + CATEGORY_W + 1, top + REGIONS_Y, SCROLL_W, REGION_H, -16777216, -16737895);
		UIScrollRegion.drawScrollbar(g, OPTION_REGION, left + OPTION_X + OPTION_W + 1, top + REGIONS_Y, SCROLL_W, REGION_H, -16777216, -16737895);

		renderPreview(g, font, category, mouseX, mouseY);
		picker.render(g, mouseX, mouseY);
	}

	private void drawRow(GuiGraphics g, String region, int index, int width, String label, boolean selected, boolean hover) {
		if (!UIScrollRegion.isItemVisible(region, index))
			return;
		int x = UIScrollRegion.getItemX(region);
		int y = UIScrollRegion.getItemY(region, index);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		g.blit(selected ? BUTTON_SELECTED : hover ? BUTTON_HOVER : BUTTON, x, y, width, ROW_H, 0, 0, 163, 32, 163, 32);
		RenderSystem.disableBlend();
		UIGraphicsHelper.drawCustomFont(g, FONT, label, x + width / 2, y + 4, TEXT, false, UIGraphicsHelper.TextAlign.CENTER, 0.6f, 0.6f);
	}

	private boolean overRow(String region, int index, int width, double mouseX, double mouseY) {
		if (!UIScrollRegion.isItemVisible(region, index) || !UIScrollRegion.isPointInRegion(region, (int) mouseX, (int) mouseY))
			return false;
		int x = UIScrollRegion.getItemX(region);
		int y = UIScrollRegion.getItemY(region, index);
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ROW_H;
	}

	/** The codex panel (126,8 to 242,122): category title, the player turned toward the mouse,
	 *  and the tint swatches along the bottom. */
	private void renderPreview(GuiGraphics g, Font font, String category, int mouseX, int mouseY) {
		UIGraphicsHelper.drawCustomFont(g, FONT, category == null ? "Appearance" : categoryName(category), left + 184, top + 12, TEXT, false, UIGraphicsHelper.TextAlign.CENTER, 0.8f, 0.8f);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			int cx = left + 184, cy = top + 60;
			float angleX = (float) Math.atan((cx - mouseX) / 40.0);
			float angleY = (float) Math.atan((cy - 20 - mouseY) / 40.0);
			UIEntityDisplay.renderCentered(g, cx, cy, 34, angleX, angleY, mc.player);
		}
		List<TintSlot> slots = tintSlots(category);
		for (int i = 0; i < slots.size(); i++) {
			TintSlot slot = slots.get(i);
			int sx = swatchX(i), sy = swatchY();
			int shown = slot.current() == -1 ? slot.defaultColor() : slot.current();
			boolean hover = !picker.isOpen() && overSwatch(i, mouseX, mouseY);
			g.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, hover ? 0xFFFFFFFF : 0xFF1A1A1A);
			g.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | shown);
			if (hover)
				g.renderTooltip(font, Component.literal(slot.label()), mouseX, mouseY);
		}
	}

	private int swatchX(int index) {
		return left + 132 + index * (SWATCH + SWATCH_GAP);
	}

	private int swatchY() {
		return top + 106;
	}

	private boolean overSwatch(int index, double mouseX, double mouseY) {
		int sx = swatchX(index), sy = swatchY();
		return mouseX >= sx && mouseX < sx + SWATCH && mouseY >= sy && mouseY < sy + SWATCH;
	}

	// ---------------------------------------------------------------------------------- input
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (picker.mouseClicked(mouseX, mouseY))
			return true;
		if (button != 0)
			return false;
		if (clickScrollbar(CATEGORY_REGION, left + CATEGORY_X + CATEGORY_W + 1, mouseX, mouseY) || clickScrollbar(OPTION_REGION, left + OPTION_X + OPTION_W + 1, mouseX, mouseY))
			return true;
		List<String> categories = categories();
		String category = currentCategory(categories);
		for (int i = 0; i < categories.size(); i++) {
			if (overRow(CATEGORY_REGION, i, CATEGORY_W, mouseX, mouseY)) {
				if (!categories.get(i).equals(category)) {
					selectedCategory = categories.get(i);
					UIScrollRegion.setScrollOffset(OPTION_REGION, 0);
				}
				click();
				return true;
			}
		}
		if (category == null)
			return false;
		List<SkinPart> options = options(category);
		SkinLoadout.Equipped worn = equippedIn(loadout(), category);
		int first = noneRows(category);
		if (first == 1 && overRow(OPTION_REGION, 0, OPTION_W, mouseX, mouseY)) {
			if (worn != null)
				PacketDistributor.sendToServer(new SkinNetwork.EquipPartPayload(worn.partId(), false, List.of()));
			click();
			return true;
		}
		for (int i = 0; i < options.size(); i++) {
			if (overRow(OPTION_REGION, i + first, OPTION_W, mouseX, mouseY)) {
				SkinPart part = options.get(i);
				if (worn == null || !worn.partId().equals(part.id()))
					PacketDistributor.sendToServer(new SkinNetwork.EquipPartPayload(part.id(), true, tintsFor(part, worn)));
				click();
				return true;
			}
		}
		List<TintSlot> slots = tintSlots(category);
		for (int i = 0; i < slots.size(); i++) {
			if (overSwatch(i, mouseX, mouseY)) {
				TintSlot slot = slots.get(i);
				picker.open(left + OPTION_X, top + REGIONS_Y, slot.label(), slot.pupil(), slot.current(), slot.defaultColor(), color -> applyTint(slot, color));
				click();
				return true;
			}
		}
		return false;
	}

	/** Re-equips the part with one tint changed, reading the loadout fresh so a second swatch on the
	 *  same part doesn't undo the first. */
	private static void applyTint(TintSlot slot, int color) {
		SkinLoadout.Equipped worn = equippedWhere(loadout(), part -> part.id().equals(slot.part().id()));
		List<Integer> tints = tintsFor(slot.part(), worn);
		if (slot.index() >= tints.size())
			return;
		tints.set(slot.index(), color);
		PacketDistributor.sendToServer(new SkinNetwork.EquipPartPayload(slot.part().id(), true, tints));
	}

	private boolean clickScrollbar(String region, int trackX, double mouseX, double mouseY) {
		if (!UIScrollRegion.isThumbNeeded(region))
			return false;
		int trackY = top + REGIONS_Y;
		return UIScrollRegion.beginThumbDrag(region, (int) mouseX, (int) mouseY, trackX, trackY, SCROLL_W, REGION_H)
				|| UIScrollRegion.handleTrackClick(region, (int) mouseX, (int) mouseY, trackX, trackY, SCROLL_W, REGION_H);
	}

	public boolean mouseDragged(double mouseX, double mouseY) {
		if (picker.mouseDragged(mouseX, mouseY))
			return true;
		for (String region : new String[]{CATEGORY_REGION, OPTION_REGION}) {
			if (UIScrollRegion.isDraggingThumb(region)) {
				UIScrollRegion.dragThumb(region, (int) mouseY, top + REGIONS_Y, REGION_H);
				return true;
			}
		}
		return false;
	}

	public boolean mouseReleased() {
		UIScrollRegion.endThumbDrag(CATEGORY_REGION);
		UIScrollRegion.endThumbDrag(OPTION_REGION);
		return picker.mouseReleased();
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (picker.isOpen())
			return true;
		return UIScrollRegion.handleMouseScroll(CATEGORY_REGION, (int) mouseX, (int) mouseY, scrollY, 14)
				|| UIScrollRegion.handleMouseScroll(OPTION_REGION, (int) mouseX, (int) mouseY, scrollY, 14);
	}

	/** Escape closes an open picker before it closes the screen. */
	public boolean isPickerOpen() {
		return picker.isOpen();
	}

	public boolean closePicker() {
		if (!picker.isOpen())
			return false;
		picker.close();
		return true;
	}

	private static void click() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}
}
