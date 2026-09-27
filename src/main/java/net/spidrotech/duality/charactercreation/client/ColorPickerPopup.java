package net.spidrotech.duality.charactercreation.client;

import net.spidrotech.duality.skin.PupilColors;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.Color;
import java.util.function.IntConsumer;

/**
 * The creator's tint picker: a saturation/brightness square beside a hue bar, the usual colour
 * wheel laid flat. Drawn and hit-tested by hand at absolute screen coordinates, like the stat rows
 * on page four.
 *
 * <p>A pupil picker passes {@code pupil = true}: every colour {@link PupilColors} refuses is
 * shaded out of the square and can't be picked, so the picker never offers what the server would
 * then throw away.
 *
 * <p>The colour is applied when the mouse is released, not while dragging, so a drag across the
 * square costs one packet instead of one per frame.
 */
public final class ColorPickerPopup {
	public static final int WIDTH = 90, HEIGHT = 108;
	private static final int PAD = 4, SQUARE = 70, HUE_W = 8, HUE_GAP = 4, TITLE_H = 12, CELL = 2;
	private static final ResourceLocation FONT = ResourceLocation.parse("spis_ui_api:boldpixels");

	private boolean open;
	private int x, y;
	private String title = "";
	private boolean pupil;
	private IntConsumer onPick;
	private float hue, saturation, brightness;
	private int defaultColor;
	/** 0 = nothing, 1 = dragging in the square, 2 = dragging on the hue bar. */
	private int dragging;

	public boolean isOpen() {
		return open;
	}

	/**
	 * @param current      the tint worn now, or -1 for the texture's own colour
	 * @param defaultColor what -1 looks like, so the picker can start on it
	 * @param onPick       receives the chosen ARGB colour, or -1 for "Default"
	 */
	public void open(int x, int y, String title, boolean pupil, int current, int defaultColor, IntConsumer onPick) {
		this.open = true;
		this.x = x;
		this.y = y;
		this.title = title;
		this.pupil = pupil;
		this.onPick = onPick;
		this.defaultColor = defaultColor;
		int shown = current == -1 ? defaultColor : current;
		float[] hsb = Color.RGBtoHSB((shown >> 16) & 0xFF, (shown >> 8) & 0xFF, shown & 0xFF, null);
		hue = hsb[0];
		saturation = hsb[1];
		brightness = hsb[2];
		dragging = 0;
	}

	public void close() {
		open = false;
		dragging = 0;
	}

	private int squareX() {
		return x + PAD;
	}

	private int squareY() {
		return y + TITLE_H;
	}

	private int hueX() {
		return squareX() + SQUARE + HUE_GAP;
	}

	private int buttonsY() {
		return squareY() + SQUARE + 6;
	}

	private int color() {
		return 0xFF000000 | Color.HSBtoRGB(hue, saturation, brightness);
	}

	private boolean allowed(float h, float s, float b) {
		return !pupil || PupilColors.isAllowed(h, s, b);
	}

	public void render(GuiGraphics g, int mouseX, int mouseY) {
		if (!open)
			return;
		g.pose().pushPose();
		g.pose().translate(0, 0, 300);
		g.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xFF1A1A1A);
		g.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF3C3C3C);
		net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(g, FONT, title, x + WIDTH / 2, y + 3, 0xFFFFFFFF, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.CENTER, 0.7f, 0.7f);

		// Square: each column is a vertical gradient from full brightness to black, which is exactly
		// how brightness scales an RGB colour - so a gradient per column draws the square losslessly.
		int sx = squareX(), sy = squareY();
		for (int col = 0; col < SQUARE; col++) {
			float s = col / (float) (SQUARE - 1);
			g.fillGradient(sx + col, sy, sx + col + 1, sy + SQUARE, 0xFF000000 | Color.HSBtoRGB(hue, s, 1f), 0xFF000000);
		}
		if (pupil) {
			for (int cy = 0; cy < SQUARE; cy += CELL) {
				for (int cx = 0; cx < SQUARE; cx += CELL) {
					float s = (cx + CELL / 2f) / SQUARE;
					float b = 1f - (cy + CELL / 2f) / SQUARE;
					if (!allowed(hue, s, b))
						g.fill(sx + cx, sy + cy, sx + Math.min(SQUARE, cx + CELL), sy + Math.min(SQUARE, cy + CELL), 0xC0282828);
				}
			}
		}
		int markX = sx + Math.round(saturation * (SQUARE - 1));
		int markY = sy + Math.round((1f - brightness) * (SQUARE - 1));
		g.fill(markX - 2, markY, markX + 3, markY + 1, 0xFFFFFFFF);
		g.fill(markX, markY - 2, markX + 1, markY + 3, 0xFFFFFFFF);

		int hx = hueX();
		for (int row = 0; row < SQUARE; row++) {
			g.fill(hx, sy + row, hx + HUE_W, sy + row + 1, 0xFF000000 | Color.HSBtoRGB(row / (float) SQUARE, 1f, 1f));
		}
		int hueMark = sy + Math.round(hue * (SQUARE - 1));
		g.fill(hx - 1, hueMark, hx + HUE_W + 1, hueMark + 1, 0xFFFFFFFF);

		// Bottom row: the chosen colour, then Default, then Done.
		int by = buttonsY();
		g.fill(sx, by, sx + 14, by + 12, 0xFF000000);
		g.fill(sx + 1, by + 1, sx + 13, by + 11, color());
		drawButton(g, "Default", sx + 18, by, 36, mouseX, mouseY);
		drawButton(g, "Done", sx + 58, by, 24, mouseX, mouseY);
		g.pose().popPose();
	}

	private void drawButton(GuiGraphics g, String label, int bx, int by, int w, int mouseX, int mouseY) {
		boolean hover = mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + 12;
		g.fill(bx, by, bx + w, by + 12, hover ? 0xFF6A6A6A : 0xFF555555);
		net.spidrone.uiapi.UIGraphicsHelper.drawCustomFont(g, FONT, label, bx + w / 2, by + 3, 0xFFFFFFFF, false, net.spidrone.uiapi.UIGraphicsHelper.TextAlign.CENTER, 0.6f, 0.6f);
	}

	/** Swallows every click while open - one outside the popup closes it. */
	public boolean mouseClicked(double mouseX, double mouseY) {
		if (!open)
			return false;
		if (mouseX < x || mouseX >= x + WIDTH || mouseY < y || mouseY >= y + HEIGHT) {
			close();
			return true;
		}
		int sx = squareX(), sy = squareY(), by = buttonsY();
		if (mouseX >= sx && mouseX < sx + SQUARE && mouseY >= sy && mouseY < sy + SQUARE) {
			dragging = 1;
			dragTo(mouseX, mouseY);
		} else if (mouseX >= hueX() && mouseX < hueX() + HUE_W && mouseY >= sy && mouseY < sy + SQUARE) {
			dragging = 2;
			dragTo(mouseX, mouseY);
		} else if (mouseY >= by && mouseY < by + 12) {
			if (mouseX >= sx + 18 && mouseX < sx + 54) {
				float[] hsb = Color.RGBtoHSB((defaultColor >> 16) & 0xFF, (defaultColor >> 8) & 0xFF, defaultColor & 0xFF, null);
				hue = hsb[0];
				saturation = hsb[1];
				brightness = hsb[2];
				onPick.accept(-1);
			} else if (mouseX >= sx + 58 && mouseX < sx + 82) {
				close();
			}
		}
		return true;
	}

	public boolean mouseDragged(double mouseX, double mouseY) {
		if (!open || dragging == 0)
			return false;
		dragTo(mouseX, mouseY);
		return true;
	}

	public boolean mouseReleased() {
		if (!open || dragging == 0)
			return false;
		dragging = 0;
		if (allowed(hue, saturation, brightness))
			onPick.accept(color());
		return true;
	}

	/** Moves the marker - but a pupil picker never lands it on a refused colour, it just stays put. */
	private void dragTo(double mouseX, double mouseY) {
		float t = (float) Math.max(0, Math.min(1, (mouseY - squareY()) / (SQUARE - 1)));
		if (dragging == 1) {
			float s = (float) Math.max(0, Math.min(1, (mouseX - squareX()) / (SQUARE - 1)));
			float b = 1f - t;
			if (allowed(hue, s, b)) {
				saturation = s;
				brightness = b;
			}
		} else if (dragging == 2) {
			// Sliding onto red with a strong colour picked: wash it out to the palest pink the rule
			// allows rather than refusing the hue outright, so the bar never feels stuck.
			if (allowed(t, saturation, brightness)) {
				hue = t;
			} else if (allowed(t, 0.35f, brightness)) {
				hue = t;
				saturation = 0.35f;
			}
		}
	}
}
