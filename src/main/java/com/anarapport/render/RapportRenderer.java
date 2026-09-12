package com.anarapport.render;

import com.anarapport.model.RapportType;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Renders the repeating rapport pattern for a source image onto a graphics context.
 * The layout strategy depends on the given RapportType, so new repeat styles
 * (half-drop, brick, mirror, etc.) can be added later without changing callers.
 */
public class RapportRenderer {

    /**
     * Draws the pattern. The view transform (zoom/pan) is applied to a private copy
     * of the graphics context, so the caller's original Graphics2D is left untouched
     * and can safely be reused afterwards to draw an overlay or UI elements that
     * must not be affected by zoom/pan.
     */
    public void render(Graphics2D g2d, BufferedImage image, int panelWidth, int panelHeight,
                        int gridSize, RapportType type, AffineTransform viewTransform) {
        if (image == null || gridSize <= 0 || panelWidth <= 0 || panelHeight <= 0) {
            return;
        }

        Graphics2D contentGraphics = (Graphics2D) g2d.create();
        try {
            contentGraphics.transform(viewTransform);
            contentGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            switch (type) {
                case STRAIGHT -> renderStraight(contentGraphics, image, panelWidth, panelHeight, gridSize);
            }
        } finally {
            contentGraphics.dispose();
        }
    }

    /**
     * Straight repeat: the source image is tiled on a plain grid, all rows and
     * columns aligned with no offset between them. The tile size is derived from
     * gridSize at zoom 1; whatever portion of that infinite grid falls within the
     * current view (after zoom/pan) is drawn, so the panel is always fully covered.
     */
    private void renderStraight(Graphics2D g2d, BufferedImage image, int panelWidth, int panelHeight, int gridSize) {
        double tileWidth = (double) panelWidth / gridSize;
        double tileHeight = tileWidth * image.getHeight() / image.getWidth();

        double centerX = panelWidth / 2.0;
        double centerY = panelHeight / 2.0;

        Rectangle2D visibleWorldBounds = computeVisibleWorldBounds(g2d, panelWidth, panelHeight);

        int colStart = (int) Math.floor((visibleWorldBounds.getMinX() - centerX) / tileWidth) - 1;
        int colEnd = (int) Math.ceil((visibleWorldBounds.getMaxX() - centerX) / tileWidth) + 1;
        int rowStart = (int) Math.floor((visibleWorldBounds.getMinY() - centerY) / tileHeight) - 1;
        int rowEnd = (int) Math.ceil((visibleWorldBounds.getMaxY() - centerY) / tileHeight) + 1;

        double scaleX = tileWidth / image.getWidth();
        double scaleY = tileHeight / image.getHeight();

        for (int row = rowStart; row <= rowEnd; row++) {
            for (int col = colStart; col <= colEnd; col++) {
                double x = centerX - tileWidth / 2 + col * tileWidth;
                double y = centerY - tileHeight / 2 + row * tileHeight;

                AffineTransform tileTransform = new AffineTransform();
                tileTransform.translate(x, y);
                tileTransform.scale(scaleX, scaleY);
                g2d.drawImage(image, tileTransform, null);
            }
        }
    }

    /**
     * Maps the panel's device-space rectangle back through the current graphics
     * transform to find which region of "world" space (i.e. pre-zoom/pan tile
     * coordinates) is actually visible.
     */
    private Rectangle2D computeVisibleWorldBounds(Graphics2D g2d, int panelWidth, int panelHeight) {
        try {
            AffineTransform inverse = g2d.getTransform().createInverse();
            Shape worldShape = inverse.createTransformedShape(new Rectangle2D.Double(0, 0, panelWidth, panelHeight));
            return worldShape.getBounds2D();
        } catch (NoninvertibleTransformException e) {
            return new Rectangle2D.Double(0, 0, panelWidth, panelHeight);
        }
    }
}
