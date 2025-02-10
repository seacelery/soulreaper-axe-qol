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


@Slf4j
@PluginDescriptor(
		name = "Soulreaper Axe QoL"
)
public class SoulreaperAxeQoLPlugin extends Plugin {
	private static final int DEFAULT_TOTAL_SPEC_REGEN_TICKS = 50;
	private static final int LIGHTBEARER_TOTAL_SPEC_REGEN_TICKS = DEFAULT_TOTAL_SPEC_REGEN_TICKS / 2;
	private static final int SOULREAPER_MAX_STACKS = 5;
	private static final int SOULREAPER_STACK_DECREMENT_TICKS = 20;

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
	private SoulreaperAxeQoLOverlay overlay;

	@Inject
	private SoulreaperAxeQoLConfig config;

	@Getter
	private int specialAttackPercent = 100;

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
	private boolean originalShowSpecialSetting;
	private int totalSpecRegenTicks = DEFAULT_TOTAL_SPEC_REGEN_TICKS;
	private int ticksUntilSpecRegen = 0;
	private int ticksUntilStackRemoval = 0;

	@Override
	protected void startUp() throws Exception {
		overlayManager.add(overlay);

		originalShowSpecialSetting = configManager.getConfiguration("regenmeter", "showSpecial", Boolean.class);
		configManager.setConfiguration("regenmeter", "showSpecial", false);

		updateOrbColour();
	}

	@Override
	protected void shutDown() throws Exception {
		overlayManager.remove(overlay);

		configManager.setConfiguration("regenmeter", "showSpecial", originalShowSpecialSetting);
	}

	@Provides
	SoulreaperAxeQoLConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(SoulreaperAxeQoLConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) throws IOException {
		if (event.getGroup().equals("soulreaperaxeqol") && (event.getKey().equals("soulreaperOrbColour") || event.getKey().equals("specOrbColour"))) {
			updateOrbColour();
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
		updateStackRemovalTimer();
	}

	@Subscribe
	private void onItemContainerChanged(ItemContainerChanged event) {
		if (event.getContainerId() != InventoryID.EQUIPMENT.getId()) return;

		ItemContainer equipment = event.getItemContainer();
		boolean lightbearerInEquipment = equipment.contains(ItemID.LIGHTBEARER);
		if (lightbearerEquipped != lightbearerInEquipment) {
			handleLightbeaerSwitch(lightbearerInEquipment);
		};

		boolean soulreaperAxeInEquipment = equipment.contains(ItemID.SOULREAPER_AXE_28338);
		if (soulreaperAxeEquipped != soulreaperAxeInEquipment) {
			handleSoulreaperAxeSwitch(soulreaperAxeInEquipment);
		}
	}

	@Subscribe
	private void onAnimationChanged(AnimationChanged event) {
		Actor actor = event.getActor();
		int animationId = actor.getAnimation();

		if (actor == client.getLocalPlayer()) {
			if (isSoulreaperAttackAnimation(animationId)) {
				soulreaperStackCount = Math.min(SOULREAPER_MAX_STACKS, soulreaperStackCount + 1);
				ticksUntilStackRemoval = SOULREAPER_STACK_DECREMENT_TICKS;
			} else if (isSoulreaperSpecAnimation(animationId)) {
				soulreaperStackCount = 0;
				ticksUntilStackRemoval = SOULREAPER_STACK_DECREMENT_TICKS;
			}
		}
	}

	private void getSpecialAttackPercentValue() {
		specialAttackPercent = client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10;
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
		ticksUntilSpecRegen = lightbearerInEquipment ? Math.min(ticksUntilSpecRegen, 25) : DEFAULT_TOTAL_SPEC_REGEN_TICKS;
		lightbearerEquipped = lightbearerInEquipment;
		updateTotalSpecRegenTicks();
	}

	private void handleSoulreaperAxeSwitch(boolean soulreaperAxeInEquipment) {
		soulreaperAxeEquipped = soulreaperAxeInEquipment;
		updateOrbColour();
	}

	private boolean isSoulreaperAttackAnimation(int animationId) {
		return animationId == 10171 || animationId == 10172;
	}

	private boolean isSoulreaperSpecAnimation (int animationId) {
		return animationId == 10173;
	}

	private void updateStackRemovalTimer() {
		if (soulreaperStackCount > 0) {
			ticksUntilStackRemoval--;

			if (ticksUntilStackRemoval == 0) {
				soulreaperStackCount--;
				ticksUntilStackRemoval = SOULREAPER_STACK_DECREMENT_TICKS;
			}
		}

		soulreaperRegenProgress = calculateProgressPercentage(SOULREAPER_STACK_DECREMENT_TICKS - ticksUntilStackRemoval, SOULREAPER_STACK_DECREMENT_TICKS);
	}

	private void updateOrbColour() {
		clientThread.invokeLater(() -> {
			client.getSpriteOverrides().remove(SpriteID.MINIMAP_ORB_SPECIAL);
			client.getSpriteOverrides().remove(SpriteID.MINIMAP_ORB_SPECIAL_ACTIVATED);

			try {
				BufferedImage orbImage = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/spec_orb_image.png")));
				BufferedImage orbActivatedImage = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/spec_orb_activated_image.png")));

				if (soulreaperAxeEquipped) {
					orbImage = overlay.recolorImage(orbImage, config.getSoulreaperOrbColor());
					orbActivatedImage = overlay.recolorImage(orbActivatedImage, config.getSoulreaperOrbColor());
				} else {
					orbImage = overlay.recolorImage(orbImage, config.getSpecOrbColor());
					orbActivatedImage = overlay.recolorImage(orbActivatedImage, config.getSpecOrbColor());
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
}