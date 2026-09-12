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
 * (brick, mirror, etc.) can be added later without changing callers.
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
                case HALF_DROP -> renderHalfDrop(contentGraphics, image, panelWidth, panelHeight, gridSize);
            }
        } finally {
            contentGraphics.dispose();
        }
    }

    /**
     * Straight repeat: the source image is tiled on a plain grid, all rows and
     * columns aligned with no offset between them.
     */
    private void renderStraight(Graphics2D g2d, BufferedImage image, int panelWidth, int panelHeight, int gridSize) {
        TileGeometry geometry = TileGeometry.of(image, panelWidth, panelHeight, gridSize);
        TileRange range = computeTileRange(g2d, panelWidth, panelHeight, geometry, 1);

        for (int row = range.rowStart(); row <= range.rowEnd(); row++) {
            for (int col = range.colStart(); col <= range.colEnd(); col++) {
                double x = geometry.tileLeft(col);
                double y = geometry.tileTop(row);
                drawTile(g2d, image, x, y, geometry);
            }
        }
    }

    /**
     * Half-drop repeat: same grid as the straight repeat, but odd columns are
     * shifted down by half the motif's height, following the textile convention.
     */
    private void renderHalfDrop(Graphics2D g2d, BufferedImage image, int panelWidth, int panelHeight, int gridSize) {
        TileGeometry geometry = TileGeometry.of(image, panelWidth, panelHeight, gridSize);
        // Extra row margin so the vertical offset never leaves a gap at the panel's edges
        TileRange range = computeTileRange(g2d, panelWidth, panelHeight, geometry, 2);

        double halfTileHeight = geometry.tileHeight() / 2;

        for (int col = range.colStart(); col <= range.colEnd(); col++) {
            double columnOffsetY = Math.floorMod(col, 2) == 0 ? 0 : halfTileHeight;
            double x = geometry.tileLeft(col);

            for (int row = range.rowStart(); row <= range.rowEnd(); row++) {
                double y = geometry.tileTop(row) + columnOffsetY;
                drawTile(g2d, image, x, y, geometry);
            }
        }
    }

    private void drawTile(Graphics2D g2d, BufferedImage image, double x, double y, TileGeometry geometry) {
        AffineTransform tileTransform = new AffineTransform();
        tileTransform.translate(x, y);
        tileTransform.scale(geometry.scaleX(), geometry.scaleY());
        g2d.drawImage(image, tileTransform, null);
    }

    /**
     * Works out which tile row/column indices need to be drawn to cover the panel,
     * by mapping the panel's device-space rectangle back into tile-grid coordinates
     * through the current graphics transform (i.e. the inverse of zoom/pan).
     */
    private TileRange computeTileRange(Graphics2D g2d, int panelWidth, int panelHeight,
                                        TileGeometry geometry, int marginTiles) {
        Rectangle2D visibleWorldBounds = computeVisibleWorldBounds(g2d, panelWidth, panelHeight);

        int colStart = (int) Math.floor((visibleWorldBounds.getMinX() - geometry.centerX()) / geometry.tileWidth()) - marginTiles;
        int colEnd = (int) Math.ceil((visibleWorldBounds.getMaxX() - geometry.centerX()) / geometry.tileWidth()) + marginTiles;
        int rowStart = (int) Math.floor((visibleWorldBounds.getMinY() - geometry.centerY()) / geometry.tileHeight()) - marginTiles;
        int rowEnd = (int) Math.ceil((visibleWorldBounds.getMaxY() - geometry.centerY()) / geometry.tileHeight()) + marginTiles;

        return new TileRange(colStart, colEnd, rowStart, rowEnd);
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

    /**
     * Tile size and panel-center reference shared by every repeat strategy.
     * Tile width comes from gridSize; tile height follows the image's aspect ratio.
     */
    private record TileGeometry(double tileWidth, double tileHeight, double centerX, double centerY,
                                 double scaleX, double scaleY) {

        static TileGeometry of(BufferedImage image, int panelWidth, int panelHeight, int gridSize) {
            double tileWidth = (double) panelWidth / gridSize;
            double tileHeight = tileWidth * image.getHeight() / image.getWidth();
            return new TileGeometry(
                    tileWidth, tileHeight,
                    panelWidth / 2.0, panelHeight / 2.0,
                    tileWidth / image.getWidth(), tileHeight / image.getHeight());
        }

        double tileLeft(int col) {
            return centerX - tileWidth / 2 + col * tileWidth;
        }

        double tileTop(int row) {
            return centerY - tileHeight / 2 + row * tileHeight;
        }
    }

    private record TileRange(int colStart, int colEnd, int rowStart, int rowEnd) {
    }
}
