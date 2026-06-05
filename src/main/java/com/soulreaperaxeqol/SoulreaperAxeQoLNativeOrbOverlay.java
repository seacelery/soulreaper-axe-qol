package com.soulreaperaxeqol;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.image.BufferedImage;
import java.util.Objects;

import static com.soulreaperaxeqol.SoulreaperAxeQoLConstants.*;

@Slf4j
public class SoulreaperAxeQoLNativeOrbOverlay extends Overlay {
    private static final int ANCHOR_OFFSET_X = 12;

    private static final double INNER_CIRCLE_DIAMETER = 26D;
    private static final double REGEN_DIAMETER = 25.5D;

    private static final Color OUTER_ORB_COLOR = new Color(0x151515);

    private final Client client;
    private final SoulreaperAxeQoLPlugin plugin;
    private final SoulreaperAxeQoLConfig config;

    private BufferedImage orbImage;
    private BufferedImage soulreaperOrbImage;
    private BufferedImage soulreaperOrbActivatedImage;

    private BufferedImage soulreaperSprite;

    private boolean previousEquippedState = false;

    private Widget getSpecOrbWidget()
    {
        Widget widget = client.getWidget(InterfaceID.Orbs.ORB_SPECENERGY);

        if (widget == null || widget.isHidden())
        {
            widget = client.getWidget(InterfaceID.OrbsNomap.ORB_SPECENERGY);
        }

        return widget != null && !widget.isHidden() ? widget : null;
    }

    public void refreshOrbImages()
    {
        previousEquippedState = false;
        orbImage = null;
        soulreaperOrbImage = null;
        soulreaperOrbActivatedImage = null;
    }

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

    @Inject
    public SoulreaperAxeQoLNativeOrbOverlay(Client client, SoulreaperAxeQoLPlugin plugin, SoulreaperAxeQoLConfig config, ConfigManager configManager) {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);

        this.client = client;
        this.plugin = plugin;
        this.config = config;

        try {
            soulreaperSprite = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/soulreaper_sprite.png")));
        } catch (Exception error) {
            log.debug("Failed to load sprite", error);
        }
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!plugin.isSoulreaperAxeEquipped()) return null;

        loadSpecOrbImage();
        renderNativeOrbOverlay(g);

        return null;
    }

    private void loadSpecOrbImage() {
        if (previousEquippedState == plugin.isSoulreaperAxeEquipped() && soulreaperOrbImage != null) return;

        try {
            BufferedImage baseOrbImage = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/spec_orb_image.png")));
            BufferedImage baseOrbActivatedImage = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/spec_orb_activated_image.png")));

            soulreaperOrbImage = recolorImage(baseOrbImage, config.getSoulreaperOrbColor());
            soulreaperOrbActivatedImage = recolorImage(baseOrbActivatedImage, config.getSoulreaperOrbColor());

            if (plugin.isSoulreaperAxeEquipped()) {
                orbImage = recolorImage(baseOrbImage, config.getSpecOrbColor());
            } else {
                orbImage = soulreaperOrbImage;
            }

            previousEquippedState = plugin.isSoulreaperAxeEquipped();
        } catch (Exception error) {
            log.debug("Failed to load or recolour image", error);
        }
    }



    private void renderNativeOrbOverlay(Graphics2D g)
    {
        BufferedImage nativeOrbImage = plugin.isSpecialAttackEnabled() ? soulreaperOrbActivatedImage : soulreaperOrbImage;

        Widget widget = getSpecOrbWidget();
        if (widget == null || nativeOrbImage == null) {
            return;
        }

        Rectangle bounds = widget.getBounds();

        double centerX = bounds.x + bounds.width / 2.0 + ANCHOR_OFFSET_X;
        double centerY = bounds.y + bounds.height / 2.0;

        g.setColor(OUTER_ORB_COLOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.fill(new Arc2D.Double(
                centerX - INNER_CIRCLE_DIAMETER / 2,
                centerY - INNER_CIRCLE_DIAMETER / 2,
                INNER_CIRCLE_DIAMETER,
                INNER_CIRCLE_DIAMETER,
                90,
                360,
                Arc2D.PIE
        ));

        double fillPercent = plugin.getSoulreaperStackCount() / (double) SOULREAPER_MAX_STACKS;

        int imageX = (int) Math.round(centerX - nativeOrbImage.getWidth() / 2.0);
        int imageY = (int) Math.round(centerY - nativeOrbImage.getHeight() / 2.0);

        Shape prevClip = g.getClip();

        try
        {
            int visibleHeight = (int) Math.round(nativeOrbImage.getHeight() * fillPercent);
            int clipY = imageY + nativeOrbImage.getHeight() - visibleHeight;

            g.setClip(
                    imageX,
                    clipY,
                    nativeOrbImage.getWidth(),
                    visibleHeight
            );

            g.drawImage(nativeOrbImage, imageX, imageY, null);
        }
        finally
        {
            g.setClip(prevClip);
        }

        renderRegenTimerMask(g);
        renderOrbIcon(g, soulreaperSprite);


        Graphics2D regenG = (Graphics2D) g.create();
        try
        {
            regenG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            regenG.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            renderNativeOrbRegenTimer(
                    regenG,
                    plugin.getSoulreaperRegenProgress(),
                    config.getSoulreaperRegenColor(),
                    false
            );
        }
        finally
        {
            regenG.dispose();
        }
    }

    private void renderOrbIcon(Graphics2D g, BufferedImage image)
    {
        Widget widget = getSpecOrbWidget();
        if (widget == null || image == null) {
            return;
        }

        Rectangle bounds = widget.getBounds();

        int centerX = (int) Math.round(bounds.x + bounds.width / 2.0 + ANCHOR_OFFSET_X);
        int centerY = (int) Math.round(bounds.y + bounds.height / 2.0);

        int x = centerX - image.getWidth() / 2;
        int y = centerY - image.getHeight() / 2;

        g.drawImage(image, x, y, null);
    }

    private void renderRegenTimerMask(Graphics2D g)
    {
        BufferedImage maskImage = plugin.isSpecialAttackEnabled() ? soulreaperOrbActivatedImage : soulreaperOrbImage;

        Widget widget = getSpecOrbWidget();
        if (widget == null || maskImage == null) {
            return;
        }

        Rectangle bounds = widget.getBounds();

        double centerX = bounds.x + bounds.width / 2.0 + ANCHOR_OFFSET_X;
        double centerY = bounds.y + bounds.height / 2.0;

        Arc2D.Double arc = new Arc2D.Double(
                centerX - REGEN_DIAMETER / 2,
                centerY - REGEN_DIAMETER / 2,
                REGEN_DIAMETER,
                REGEN_DIAMETER,
                90,
                -360,
                Arc2D.OPEN
        );

        Stroke prevStroke = g.getStroke();
        Shape prevClip = g.getClip();

        Stroke coverStroke = new BasicStroke(4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
        Stroke imageStroke = new BasicStroke(10f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setStroke(coverStroke);
            g.setColor(OUTER_ORB_COLOR);
            g.draw(arc);

            double fillPercent = plugin.getSoulreaperStackCount() / (double) SOULREAPER_MAX_STACKS;
            if (fillPercent <= 0)
            {
                return;
            }

            int imageX = (int) Math.round(centerX - maskImage.getWidth() / 2.0);
            int imageY = (int) Math.round(centerY - maskImage.getHeight() / 2.0);

            int width = maskImage.getWidth();
            int height = maskImage.getHeight();

            int visibleHeight = (int) Math.round(height * fillPercent);
            int clipY = imageY + height - visibleHeight;

            Shape arcMask = imageStroke.createStrokedShape(arc);

            Rectangle imageFillClip = new Rectangle(
                    imageX - 1,
                    clipY,
                    width + 2,
                    visibleHeight + 2
            );

            Area mask = new Area(arcMask);
            mask.intersect(new Area(imageFillClip));

            g.setClip(mask);

            g.drawImage(maskImage, imageX - 1, imageY,
                    width + 1,
                    height + 1,
                    null);
        }
        finally
        {
            g.setClip(prevClip);
            g.setStroke(prevStroke);
        }
    }

    private void renderNativeOrbRegenTimer(Graphics2D g, double percent, Color color, boolean applyOffset) {
        Widget widget = getSpecOrbWidget();
        if (widget == null) {
            return;
        }
        Rectangle bounds = widget.getBounds();
        double centerX = bounds.x + bounds.width / 2.0 + ANCHOR_OFFSET_X;
        double centerY = bounds.y + bounds.height / 2.0;

        if (applyOffset) {
            centerX += config.getOffsetX();
            centerY += config.getOffsetY();
        }

        Arc2D.Double arc = new Arc2D.Double(centerX - REGEN_DIAMETER / 2 - 1, centerY - REGEN_DIAMETER / 2,
                REGEN_DIAMETER + 1, REGEN_DIAMETER + 1, 90.d, -360.d * percent, Arc2D.OPEN);
        final Stroke STROKE = new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(STROKE);
        g.setColor(color);
        g.draw(arc);
    }
}
