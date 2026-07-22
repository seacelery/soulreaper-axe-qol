package com.soulreaperaxeqol;

import com.google.inject.Provides;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

import static com.soulreaperaxeqol.SoulreaperAxeQoLConstants.*;

@Slf4j
@PluginDescriptor(
		name = "Soulreaper Axe QoL"
)
public class SoulreaperAxeQoLPlugin extends Plugin {
	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	public SpriteManager spriteManager;

	@Inject
	ClientThread clientThread;

	@Inject
	private SoulreaperAxeQoLExtraOrbOverlay extraOrbOverlay;

	@Inject
	private SoulreaperAxeQoLNativeOrbOverlay nativeOrbOverlay;

	@Inject
	private SoulreaperAxeQoLConfig config;

	@Getter
	private int specialAttackPercent = 100;

	@Getter
	private boolean specialAttackEnabled = false;

	@Getter
	private double specRegenProgress;

	@Getter
	private int soulreaperStackCount = 0;

	@Getter
	private double soulreaperRegenProgress;

	@Getter
	private boolean soulreaperAxeEquipped = false;

	private boolean lightbearerEquipped = false;
	private boolean firstGameTick = true;
	private int totalSpecRegenTicks = DEFAULT_TOTAL_SPEC_REGEN_TICKS;
	private int ticksUntilSpecRegen = 0;
	private int ticksUntilStackRemoval = 0;

	@Override
	protected void startUp() throws Exception {
		overlayManager.add(nativeOrbOverlay);
		overlayManager.add(extraOrbOverlay);

		configManager.setConfiguration("regenmeter", "showSpecial", true);
		updateOrbColour();
	}

	@Override
	protected void shutDown() throws Exception {
		overlayManager.remove(nativeOrbOverlay);
		overlayManager.remove(extraOrbOverlay);

		resetOrbColour();
	}

	@Provides
	SoulreaperAxeQoLConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(SoulreaperAxeQoLConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) throws IOException {
		if (event.getGroup().equals("soulreaperaxeqol") && (event.getKey().equals("soulreaperOrbColour") || event.getKey().equals("specOrbColour"))) {
			extraOrbOverlay.refreshOrbImages();
			nativeOrbOverlay.refreshOrbImages();
			updateOrbColour();
		}

		if (event.getKey().equals("alwaysOnTop"))
		{
			extraOrbOverlay.updateLayer(config.alwaysOnTop());
			overlayManager.remove(extraOrbOverlay);
			overlayManager.add(extraOrbOverlay);
		}
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGIN_SCREEN) {
			firstGameTick = true;
		}
	}

	@Subscribe
	private void onGameTick(GameTick event) {
		if (firstGameTick) {
			updateOrbColour();
			startSpecRegenTimer();
			ticksUntilStackRemoval = SOULREAPER_STACK_DECREMENT_TICKS;
			firstGameTick = false;
		}

		getSpecialAttackPercentValue();
		updateSpecRegenTimer();
		updateSoulreaperStackCount();
		updateStackRemovalTimer();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (event.getVarpId() == VarPlayerID.SA_ATTACK) {
			specialAttackEnabled = event.getValue() == 1;
		}

		if (event.getVarpId() == VarPlayerID.SOULREAPER_STACKS) {
			updateSoulreaperStackCount();
		}
	}

	@Subscribe
	private void onItemContainerChanged(ItemContainerChanged event) {
		if (event.getContainerId() != InventoryID.EQUIPMENT.getId()) return;

		ItemContainer equipment = event.getItemContainer();
		boolean lightbearerInEquipment = equipment.contains(ItemID.LIGHTBEARER);
		if (lightbearerEquipped != lightbearerInEquipment) {
			handleLightbeaerSwitch(lightbearerInEquipment);
		};

		boolean soulreaperAxeInEquipment = equipment.contains(ItemID.SOULREAPER_AXE_28338) || equipment.contains(ItemID.SOULREAPER_AXE_O);
		if (soulreaperAxeEquipped != soulreaperAxeInEquipment) {
			handleSoulreaperAxeSwitch(soulreaperAxeInEquipment);
		}
	}

	@Subscribe
	private void onAnimationChanged(AnimationChanged event) {
		Actor actor = event.getActor();

		if (actor != client.getLocalPlayer()) {
			return;
		}

		int animationId = actor.getAnimation();

		if (isSoulreaperAttackAnimation(animationId)) {
			ticksUntilStackRemoval = SOULREAPER_STACK_DECREMENT_TICKS;
		} else if (isSoulreaperSpecAnimation(animationId))
		{
			ticksUntilStackRemoval = 0;
		}
	}

	private void updateSoulreaperStackCount() {
		soulreaperStackCount = client.getVarpValue(VarPlayerID.SOULREAPER_STACKS);

		if (soulreaperStackCount == 0) {
			ticksUntilStackRemoval = 0;
		} else if (ticksUntilStackRemoval <= 0) {
			ticksUntilStackRemoval = SOULREAPER_STACK_DECREMENT_TICKS;
		}
	}

	private void getSpecialAttackPercentValue() {
		specialAttackPercent = client.getVarpValue(VarPlayerID.SA_ENERGY) / 10;
	}

	private void startSpecRegenTimer() {
		ticksUntilSpecRegen = totalSpecRegenTicks;
	}

	private void updateSpecRegenTimer() {
		if (ticksUntilSpecRegen > 0) ticksUntilSpecRegen--;
		if (ticksUntilSpecRegen == 0 || specialAttackPercent == 100) startSpecRegenTimer();

		specRegenProgress = calculateProgressPercentage(totalSpecRegenTicks - ticksUntilSpecRegen, totalSpecRegenTicks);
	}

	private void updateTotalSpecRegenTicks() {
		totalSpecRegenTicks = lightbearerEquipped ? LIGHTBEARER_TOTAL_SPEC_REGEN_TICKS : DEFAULT_TOTAL_SPEC_REGEN_TICKS;
	}

	private double calculateProgressPercentage(int ticksProgress, int totalTicks) {
		return (double) ticksProgress / totalTicks;
	}

	private void handleLightbeaerSwitch(boolean lightbearerInEquipment) {
		ticksUntilSpecRegen = lightbearerInEquipment ? Math.min(ticksUntilSpecRegen, LIGHTBEARER_TOTAL_SPEC_REGEN_TICKS) : DEFAULT_TOTAL_SPEC_REGEN_TICKS;
		lightbearerEquipped = lightbearerInEquipment;
		updateTotalSpecRegenTicks();
	}

	private void handleSoulreaperAxeSwitch(boolean soulreaperAxeInEquipment) {
		soulreaperAxeEquipped = soulreaperAxeInEquipment;
		updateOrbColour();
	}

	private boolean isSoulreaperAttackAnimation(int animationId) {
		return animationId == SOULREAPER_ATTACK_ANIMATION_1 || animationId == SOULREAPER_ATTACK_ANIMATION_2;
	}

	private boolean isSoulreaperSpecAnimation (int animationId) {
		return animationId == SOULREAPER_SPEC_ANIMATION;
	}

	private void updateStackRemovalTimer() {
		if (soulreaperStackCount > 0) {
			ticksUntilStackRemoval--;

			if (ticksUntilStackRemoval <= 0) {
				ticksUntilStackRemoval = SOULREAPER_STACK_DECREMENT_TICKS;
			}

			soulreaperRegenProgress = calculateProgressPercentage(
					SOULREAPER_STACK_DECREMENT_TICKS - ticksUntilStackRemoval,
					SOULREAPER_STACK_DECREMENT_TICKS);
		} else {
			soulreaperRegenProgress = 0;
		}
	}

	private void updateOrbColour() {
		clientThread.invokeLater(() -> {
			client.getSpriteOverrides().remove(SpriteID.MINIMAP_ORB_SPECIAL);
			client.getSpriteOverrides().remove(SpriteID.MINIMAP_ORB_SPECIAL_ACTIVATED);

			try {
				BufferedImage orbImage = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/spec_orb_image.png")));
				BufferedImage orbActivatedImage = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/spec_orb_activated_image.png")));

				if (soulreaperAxeEquipped) {
					orbImage = extraOrbOverlay.recolorImage(orbImage, config.getSoulreaperOrbColor());
					orbActivatedImage = extraOrbOverlay.recolorImage(orbActivatedImage, config.getSoulreaperOrbColor());
				} else {
					orbImage = extraOrbOverlay.recolorImage(orbImage, config.getSpecOrbColor());
					orbActivatedImage = extraOrbOverlay.recolorImage(orbActivatedImage, config.getSpecOrbColor());
				}

				SpritePixels orbSpritePixels = ImageUtil.getImageSpritePixels(orbImage, client);
				client.getSpriteOverrides().put(SpriteID.MINIMAP_ORB_SPECIAL, orbSpritePixels);

				SpritePixels orbActivatedSpritePixels = ImageUtil.getImageSpritePixels(orbActivatedImage, client);
				client.getSpriteOverrides().put(SpriteID.MINIMAP_ORB_SPECIAL_ACTIVATED, orbActivatedSpritePixels);

				client.getWidgetSpriteCache().reset();
			} catch (Exception error) {
				log.debug("Failed to update orb colour", error);
			}
		});
	}

	private void resetOrbColour() {
		clientThread.invokeLater(() -> {
			client.getSpriteOverrides().remove(SpriteID.MINIMAP_ORB_SPECIAL);
			client.getSpriteOverrides().remove(SpriteID.MINIMAP_ORB_SPECIAL_ACTIVATED);
			client.getWidgetSpriteCache().reset();
		});
	}
}
