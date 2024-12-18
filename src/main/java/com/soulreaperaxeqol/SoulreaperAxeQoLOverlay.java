package com.soulreaperaxeqol;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Font;
import java.awt.geom.Arc2D;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.FontManager;

import java.awt.image.BufferedImage;
import java.util.Objects;

@Slf4j
public class SoulreaperAxeQoLOverlay extends Overlay {
    private static final int ANCHOR_COMPONENT = ComponentID.MINIMAP_SPEC_ORB;
    private static final int ANCHOR_OFFSET_X = 12;

    private static final double CIRCLE_SHADOW_DIAMETER = 33D;
    private static final double BORDER_DIAMETER = 31D;
    private static final double BASE_CIRCLE_DIAMETER = 29D;
    private static final double INNER_CIRCLE_DIAMETER = 26D;
    private static final double REGEN_DIAMETER = 25.5D;

    private static final Color INNER_CIRCLE_COLOR = new Color(0x0a0a0a);

    private final Client client;
    private final SoulreaperAxeQoLPlugin plugin;
    private final SoulreaperAxeQoLConfig config;

    private BufferedImage orbImage;
    private BufferedImage specialAttackSprite;
    private BufferedImage soulreaperSprite;

    private final Font osrsFont;

    public BufferedImage recolorImage(BufferedImage image, Color newColor) {
        float[] newHSBValues = Color.RGBtoHSB(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), null);
        float brightnessMultiplier = newHSBValues[2];

        BufferedImage recoloredImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                int rgba = image.getRGB(i, j);

                Color originalColor = new Color(rgba, true);

                float[] originalHSBValues = Color.RGBtoHSB(originalColor.getRed(), originalColor.getGreen(), originalColor.getBlue(), null);
                double brightness = Math.min(originalHSBValues[2] * brightnessMultiplier * 1.5, 1.0);
                int alpha = originalColor.getAlpha();

                int newRgba = Color.HSBtoRGB(newHSBValues[0], newHSBValues[1], (float) brightness);
                newRgba = (alpha << 24) | (newRgba & 0x00FFFFFF);

                recoloredImage.setRGB(i, j, newRgba);
            }
        }
        return recoloredImage;
    }

    private Color getChosenBaseColor() {
        switch (config.theme()) {
            case DEFAULT:
                return new Color(0x695a4b);
            case DARK:
                return new Color(0x2B2B2B);
            case PINK:
                return new Color(0x996b72);
            case PURPLE:
                return new Color(0x473157);
            case WOODEN:
                return new Color(0x4A3638);
            case CUSTOM:
            default:
                return config.getBaseColor();
        }
    }

    private Color getChosenBorderColor() {
        switch (config.theme()) {
            case DEFAULT:
                return new Color(0x4d4439);
            case DARK:
                return new Color(0x1F1F1F);
            case PINK:
                return new Color(0x8c5b63);
            case PURPLE:
                return new Color(0x382745);
            case WOODEN:
                return new Color(0x614243);
            case CUSTOM:
            default:
                return config.getBorderColor();
        }
    }

    private Color getChosenShadowColor() {
        switch (config.theme()) {
            case DEFAULT:
                return new Color(0x332c1a);
            case DARK:
                return new Color(0x121212);
            case PINK:
                return new Color(0x332c1a);
            case PURPLE:
                return new Color(0x23192b);
            case WOODEN:
                return new Color(0x362222);
            case CUSTOM:
            default:
                return config.getShadowColor();
        }
    }

    @Inject
    public SoulreaperAxeQoLOverlay(Client client, SoulreaperAxeQoLPlugin plugin, SoulreaperAxeQoLConfig config, ConfigManager configManager) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        this.client = client;
        this.plugin = plugin;
        this.config = config;

        this.osrsFont = FontManager.getRunescapeSmallFont();

        try {
            specialAttackSprite = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/special_attack_sprite.png")));
            soulreaperSprite = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/soulreaper_sprite.png")));
        } catch (Exception error) {
            log.debug("Failed to load sprite", error);
        }
    }

    @Override
    public Dimension render(Graphics2D g) {
        // render spec regen at all times
        if (!plugin.isSoulreaperAxeEquipped()) {
            renderRegen(g, plugin.getSpecRegenProgress(), config.getSpecRegenColor(), false);
        }

        // hide minimap orb when Soulreaper Axe not equipped and no stacks are remaining
        if (!plugin.isSoulreaperAxeEquipped() && plugin.getSoulreaperStackCount() == 0) return null;

        Color baseColor = getChosenBaseColor();
        Color borderColor = getChosenBorderColor();
        Color shadowColor = getChosenShadowColor();
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // hide text box when option selected
        if (!config.showTextOnOrb()) {
            renderTextBox(g, shadowColor, 33, 0.6 * CIRCLE_SHADOW_DIAMETER + 2, -2);
            renderTextBox(g, borderColor, 32, 0.6 * CIRCLE_SHADOW_DIAMETER, -1);
            renderTextBox(g, baseColor, 30, 15, 2);
        }

        renderInnerCircle(g, INNER_CIRCLE_COLOR);

        // load, recolour, and render spec orb
        try {
            orbImage = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/spec_orb_image.png")));
            if (plugin.isSoulreaperAxeEquipped()) {
                orbImage = recolorImage(orbImage, config.getSpecOrbColor());
            } else {
                orbImage = recolorImage(orbImage, config.getSoulreaperOrbColor());
            }
        } catch (Exception error) {
            log.debug("Failed to load or recolour image", error);
        }

        if (plugin.isSoulreaperAxeEquipped()) {
            renderSpecOrbSprite(g,(double) plugin.getSpecialAttackPercent() / 100, orbImage);
        } else {
            renderSpecOrbSprite(g,(double) plugin.getSoulreaperStackCount() / 5, orbImage);
        }

        renderOuterCircle(g, borderColor);

        // render borders
        if (config.showTextOnOrb()) {
            renderCircleBorder(g, baseColor, 90, -360, 1f);
            renderCircleShadow(g, shadowColor, 90, -360);
        } else {
            // partial border arc
            renderCircleBorder(g, baseColor, 90, -210, 1f);
            renderCircleBorder(g, baseColor, 90, 60, 1f);

            // textbox arc
            renderCircleBorder(g, baseColor, 162, 56, 3f);

            // partial shadow arc
            renderCircleShadow(g, shadowColor, 90, -210);
            renderCircleShadow(g, shadowColor, 90, 60);
        }

        // render text & regen
        if (plugin.isSoulreaperAxeEquipped()) {
            if (!config.showTextOnOrb()) {
                renderOrbIcon(g, specialAttackSprite);
                renderText(g, String.valueOf(plugin.getSpecialAttackPercent()), true, (double) plugin.getSpecialAttackPercent() / 100, 0, 5);
            } else {
                renderText(g, String.valueOf(plugin.getSpecialAttackPercent()), true, (double) plugin.getSpecialAttackPercent() / 100, 24, 3);
            }
            renderRegen(g, plugin.getSpecRegenProgress(), config.getSpecRegenColor(), true);
            renderRegen(g, plugin.getSoulreaperRegenProgress(), config.getSoulreaperRegenColor(), false);
        } else {
            if (!config.showTextOnOrb()) {
                renderOrbIcon(g, soulreaperSprite);
                renderText(g, String.valueOf(plugin.getSoulreaperStackCount()), true, (double) plugin.getSoulreaperStackCount() / 5, 0, 5);
            } else {
                renderText(g, String.valueOf(plugin.getSoulreaperStackCount()), true, (double) plugin.getSoulreaperStackCount() / 5, 24, 3);
            }
            renderRegen(g, plugin.getSoulreaperRegenProgress(), config.getSoulreaperRegenColor(), true);
        }

        return null;
    }

    private void renderRegen(Graphics2D g, double percent, Color color, boolean applyOffset) {
        Widget widget = client.getWidget(ANCHOR_COMPONENT);
        if (widget == null || widget.isHidden()) {
            return;
        }
        Rectangle bounds = widget.getBounds();
        double centerX = bounds.x + bounds.width / 2.0 + ANCHOR_OFFSET_X;
        double centerY = bounds.y + bounds.height / 2.0;

        if (applyOffset) {
            centerX += config.getOffsetX();
            centerY += config.getOffsetY();
        }

        Arc2D.Double arc = new Arc2D.Double(centerX - REGEN_DIAMETER / 2, centerY - REGEN_DIAMETER / 2,
                REGEN_DIAMETER, REGEN_DIAMETER, 90.d, -360.d * percent, Arc2D.OPEN);
        final Stroke STROKE = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(STROKE);
        g.setColor(color);
        g.draw(arc);
    }

    private void renderOuterCircle(Graphics2D g, Color color) {
        Widget widget = client.getWidget(ANCHOR_COMPONENT);
        if (widget == null || widget.isHidden()) {
            return;
        }
        Rectangle bounds = widget.getBounds();
        double centerX = bounds.x + bounds.width / 2.0 + config.getOffsetX() + ANCHOR_OFFSET_X;
        double centerY = bounds.y + bounds.height / 2.0 + config.getOffsetY();

        final Stroke OUTER_STROKE = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        g.setStroke(OUTER_STROKE);
        g.setColor(color);
        g.draw(new Arc2D.Double(centerX - BASE_CIRCLE_DIAMETER / 2, centerY - BASE_CIRCLE_DIAMETER / 2,
                BASE_CIRCLE_DIAMETER, BASE_CIRCLE_DIAMETER, 0, 360, Arc2D.OPEN));
    }

    private void renderCircleBorder(Graphics2D g, Color color, int startAngle, int sweep, float thickness) {
        Widget widget = client.getWidget(ANCHOR_COMPONENT);
        if (widget == null || widget.isHidden()) {
            return;
        }
        Rectangle bounds = widget.getBounds();
        double centerX = bounds.x + bounds.width / 2.0 + config.getOffsetX() + ANCHOR_OFFSET_X;
        double centerY = bounds.y + bounds.height / 2.0 + config.getOffsetY();

        final Stroke OUTER_STROKE = new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setStroke(OUTER_STROKE);
        g.setColor(color);
        g.draw(new Arc2D.Double(centerX - BORDER_DIAMETER / 2, centerY - BORDER_DIAMETER / 2, BORDER_DIAMETER,
                BORDER_DIAMETER, startAngle, sweep, Arc2D.OPEN));
    }

    private void renderCircleShadow(Graphics2D g, Color color, int startAngle, int sweep) {
        Widget widget = client.getWidget(ANCHOR_COMPONENT);
        if (widget == null || widget.isHidden()) {
            return;
        }
        Rectangle bounds = widget.getBounds();
        double centerX = bounds.x + bounds.width / 2.0 + config.getOffsetX() + ANCHOR_OFFSET_X;
        double centerY = bounds.y + bounds.height / 2.0 + config.getOffsetY();

        final Stroke OUTER_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setStroke(OUTER_STROKE);
        g.setColor(color);
        g.draw(new Arc2D.Double(centerX - CIRCLE_SHADOW_DIAMETER / 2, centerY - CIRCLE_SHADOW_DIAMETER / 2,
                CIRCLE_SHADOW_DIAMETER, CIRCLE_SHADOW_DIAMETER, startAngle, sweep, Arc2D.OPEN));
    }

    private void renderInnerCircle(Graphics2D g, Color color) {
        Widget widget = client.getWidget(ANCHOR_COMPONENT);
        if (widget == null || widget.isHidden()) {
            return;
        }
        Rectangle bounds = widget.getBounds();
        double centerX = bounds.x + bounds.width / 2.0 + config.getOffsetX() + ANCHOR_OFFSET_X;
        double centerY = bounds.y + bounds.height / 2.0 + config.getOffsetY();

        g.setColor(color);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(new Arc2D.Double(centerX - INNER_CIRCLE_DIAMETER / 2, centerY - INNER_CIRCLE_DIAMETER / 2,
                INNER_CIRCLE_DIAMETER, INNER_CIRCLE_DIAMETER, 90, 360, Arc2D.PIE));
    }

    private void renderTextBox(Graphics2D g, Color color, double width, double height, int addedOffsetY) {
        Widget widget = client.getWidget(ANCHOR_COMPONENT);
        if (widget == null || widget.isHidden()) {
            return;
        }

        Rectangle bounds = widget.getBounds();
        double centerX = bounds.x + bounds.width / 2.0 + config.getOffsetX() + ANCHOR_OFFSET_X;
        double centerY = bounds.y + bounds.height / 2.0 + config.getOffsetY() + addedOffsetY;

        double startY = centerY - (CIRCLE_SHADOW_DIAMETER / 2) + (0.3 * CIRCLE_SHADOW_DIAMETER);
        double offsetX = centerX - CIRCLE_SHADOW_DIAMETER / 2 - width + 10;

        g.setColor(color);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        double arcWidth = 5;
        double arcHeight = 5;
        g.fill(new RoundRectangle2D.Double(offsetX, startY, width, height, arcWidth, arcHeight));
    }

    private void renderSpecOrbSprite(Graphics2D g, double fillPercent, BufferedImage image) {
        Widget widget = client.getWidget(ANCHOR_COMPONENT);
        if (widget == null || widget.isHidden()) {
            return;
        }

        Rectangle bounds = widget.getBounds();
        int centerX = (int) (bounds.x + bounds.width / 2.0 + ANCHOR_OFFSET_X);
        int centerY = (int) (bounds.y + bounds.height / 2.0) - 1;

        centerX += config.getOffsetX() - ANCHOR_OFFSET_X;
        centerY += config.getOffsetY() - ANCHOR_OFFSET_X;

        int width = image.getWidth();
        int height = image.getHeight();

        int visibleHeight = (int) (height * fillPercent);
        int clipY = centerY + height - visibleHeight;

        Shape previousClip = g.getClip();
        try {
            g.setClip(centerX, clipY, width, visibleHeight);
            g.drawImage(image, centerX, centerY, null);
        } finally {
            g.setClip(previousClip);
        }
    }

    private void renderOrbIcon(Graphics2D g, BufferedImage image) {
        Widget widget = client.getWidget(ANCHOR_COMPONENT);
        if (widget == null || widget.isHidden()) {
            return;
        }

        Rectangle bounds = widget.getBounds();
        int centerX = (int) (bounds.x + bounds.width / 2.0 + ANCHOR_OFFSET_X + 1);
        int centerY = (int) (bounds.y + bounds.height / 2.0);

        centerX += config.getOffsetX();
        centerY += config.getOffsetY();

        int x = centerX - image.getWidth() / 2;
        int y = centerY - image.getHeight() / 2;

        g.drawImage(image, x, y, null);
    }

    private void renderText(Graphics2D g, String text, boolean applyShadow, double progressPercent, int textOffsetX, int textOffsetY) {
        Widget widget = client.getWidget(ANCHOR_COMPONENT);
        if (widget == null || widget.isHidden()) {
            return;
        }

        Rectangle bounds = widget.getBounds();
        int centerX = (int) (bounds.x + bounds.width / 2.0 - ANCHOR_OFFSET_X + textOffsetX);
        int centerY = (int) (bounds.y + bounds.height / 2.0) + textOffsetY;

        centerX += config.getOffsetX();
        centerY += config.getOffsetY();

        g.setFont(osrsFont);

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int textWidth = g.getFontMetrics().stringWidth(text);
        int textHeight = g.getFontMetrics().getHeight();

        if (applyShadow) {
            g.setColor(Color.BLACK);
            g.drawString(text, centerX - textWidth / 2 + 1, centerY + textHeight / 4 + 1);
        }

        float hue = (float) (120 * progressPercent / 360);
        Color progressColor = Color.getHSBColor(hue, 1.0f, 1.0f);
        g.setColor(progressColor);
        g.drawString(text, centerX - textWidth / 2, centerY + textHeight / 4);
    }
}
