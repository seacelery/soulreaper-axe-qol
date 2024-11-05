package com.soulreaperaxeqol;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("soulreaperaxeqol")
public interface SoulreaperAxeQoLConfig extends Config {

	public enum themes {
		DEFAULT,
		DARK,
		PINK,
		PURPLE,
		WOODEN,
		CUSTOM
	}

	@ConfigSection(
			name = "Positioning",
			description = "Control the position of the frame relative to the regular special attack frame",
			position = 1,
			closedByDefault = false
	)
	String positioning = "Positioning";

	@ConfigSection(
			name = "Colours",
			description = "Colour settings",
			position = 2,
			closedByDefault = false
	)
	String colours = "Colours";

	@ConfigItem(
			keyName = "showTextOnOrb",
			name = "Show text on orb",
			description ="Either show text on the orb or to the side",
			position = 1,
			section = "Positioning"
	) default boolean showTextOnOrb() {
		return false;
	}

	@ConfigItem(
			keyName = "offsetX",
			name = "Offset in X axis",
			description = "Move left or right relative to the regular special attack frame",
			position = 2,
			section = "Positioning"
	) default int getOffsetX() {
		return 22;
	}

	@ConfigItem(
			keyName = "offsetY",
			name = "Offset in Y axis",
			description = "Move up or down relative to the regular special attack frame",
			position = 3,
			section = "Positioning"
	) default int getOffsetY() {
		return 25;
	}

	@ConfigItem(
			keyName = "theme",
			name = "Theme",
			description = "Select a theme for the base, border, and shadow colour",
			position = 5,
			section = "Colours"
	) default themes theme() {
		return themes.DEFAULT;
	}

	@ConfigItem(
			keyName = "baseColour",
			name = "Base colour (Custom)",
			description = "Select a base colour (requires Custom theme selected)",
			position = 6,
			section = "Colours"
	) default Color getBaseColor() {
		return new Color(0x695a4b);
	}

	@ConfigItem(
			keyName = "borderColour",
			name = "Border colour (Custom)",
			description = "Select a border colour (requires Custom theme selected)",
			position = 7,
			section = "Colours"
	) default Color getBorderColor() {
		return new Color(0x4d4439);
	}

	@ConfigItem(
			keyName = "shadowColour",
			name = "Shadow colour (Custom)",
			description = "Select a shadow colour (requires Custom theme selected)",
			position = 8,
			section = "Colours"
	) default Color getShadowColor() {
		return new Color(0x332c1a);
	}

	@ConfigItem(
			keyName = "specOrbColour",
			name = "Special attack orb colour",
			description = "Select a colour for the special attack orb",
			position = 9,
			section = "Colours"
	) default Color getSpecOrbColor() {
		return new Color(0x27A2CB);
	}

	@ConfigItem(
			keyName = "specRegenColour",
			name = "Special attack regen colour",
			description = "Select a colour for the special attack regen timer",
			position = 10,
			section = "Colours"
	) default Color getSpecRegenColor() {
		return new Color(0x00d0ff);
	}

	@ConfigItem(
			keyName = "soulreaperOrbColour",
			name = "Soulreaper stacks orb colour",
			description = "Select a colour for the Soulreaper Axe orb",
			position = 11,
			section = "Colours"
	) default Color getSoulreaperOrbColor() {
		return new Color(0x8D4BD3);
	}

	@ConfigItem(
			keyName = "soulreaperRegenColour",
			name = "Soulreaper stacks regen colour",
			description = "Select a colour for the Soulreaper Axe regen timer",
			position = 12,
			section = "Colours"
	) default Color getSoulreaperRegenColor() {
		return new Color(0xcb45e6);
	}
}