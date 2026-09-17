package net.spidrotech.duality.client.screens;

import org.checkerframework.checker.units.qual.h;

import net.spidrotech.duality.procedures.ShouldJumpbarProcedure;
import net.spidrotech.duality.procedures.ReturnJumpPowerProcedure;
import net.spidrotech.duality.procedures.ReturnJumpMaxPowerProcedure;

import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;

@EventBusSubscriber(Dist.CLIENT)
public class ProgressbarsOverlay {
	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void eventHandler(RenderGuiEvent.Pre event) {
		int w = event.getGuiGraphics().guiWidth();
		int h = event.getGuiGraphics().guiHeight();
		Level world = null;
		double x = 0;
		double y = 0;
		double z = 0;
		Player entity = Minecraft.getInstance().player;
		if (entity != null) {
			world = entity.level();
			x = entity.getX();
			y = entity.getY();
			z = entity.getZ();
		}
		if (true) {
			if (ShouldJumpbarProcedure.execute(entity)) {
				net.spidrone.uiapi.UIProgressBarRenderer.drawProgressBar(event.getGuiGraphics(), w / 2 + -90, h - 48, 182, 8, 1, -16777216, -13421773, net.spidrone.uiapi.UIColorEffects.gradient(-26215, -10027162),
						ReturnJumpPowerProcedure.execute(entity), ReturnJumpMaxPowerProcedure.execute(entity), net.spidrone.uiapi.UIProgressBarRenderer.FillDirection.LEFT_TO_RIGHT);
			}
		}
	}
}