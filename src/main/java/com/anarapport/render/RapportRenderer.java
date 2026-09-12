package com.anarapport.render;

import com.anarapport.model.RapportType;
import com.anarapport.model.SeamStyle;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders the repeating rapport pattern for a source image onto a graphics context.
 * The layout strategy depends on the given RapportType, so new repeat styles
 * (brick, etc.) can be added later without changing callers.
 *
 * <p>Not thread-safe: each instance caches tile bitmaps pre-scaled to the current
 * tile size, keyed by source image and size, so a given instance must only be
 * driven from one thread at a time. The interactive view and a background export
 * should each use their own instance.
 */
public class RapportRenderer {

    // Seams are kept at a constant on-screen width/dash length regardless of zoom
    private static final double SEAM_WIDTH_PX = 1.0;
    private static final double SEAM_DASH_LENGTH_PX = 4.0;
    private static final double SEAM_DASH_GAP_PX = 4.0;
    private static final int SEAM_ALPHA = 70;
    private static final int SEAM_ALPHA_HIGH_CONTRAST = 160;

    // Cache of tile bitmaps pre-scaled (and, for mirror mode, pre-flipped) to the
    // current tile size, so panning/zooming doesn't re-resample the source image
    // (which may be far larger than the on-screen tile) on every single repaint.
    private BufferedImage cachedSourceImage;
    private int cachedTileWidthPx = -1;
    private int cachedTileHeightPx = -1;
    private final Map<FlipKey, BufferedImage> tileCache = new HashMap<>();

    /**
     * Draws the pattern. The view transform (zoom/pan) is applied to a private copy
     * of the graphics context, so the caller's original Graphics2D is left untouched
     * and can safely be reused afterwards to draw an overlay or UI elements that
     * must not be affected by zoom/pan.
     */
    public void render(Graphics2D g2d, BufferedImage image, int panelWidth, int panelHeight,
                        int gridSize, RapportType type, AffineTransform viewTransform,
                        boolean showSeams, SeamStyle seamStyle) {
        if (image == null || gridSize <= 0 || panelWidth <= 0 || panelHeight <= 0) {
            return;
        }

        Graphics2D contentGraphics = (Graphics2D) g2d.create();
        try {
            contentGraphics.transform(viewTransform);
            contentGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (showSeams) {
                double zoomScale = viewTransform.getScaleX();
                contentGraphics.setColor(seamColor(seamStyle));
                contentGraphics.setStroke(seamStroke(seamStyle, zoomScale));
            }

            // Geometry (and the pre-scaled tile cache) is shared by every repeat
            // strategy below; only the placement/flip of each tile differs.
            TileGeometry geometry = TileGeometry.of(image, panelWidth, panelHeight, gridSize);
            refreshTileCache(image, geometry);

            switch (type) {
                case STRAIGHT -> renderStraight(contentGraphics, panelWidth, panelHeight, geometry, showSeams);
                case HALF_DROP -> renderHalfDrop(contentGraphics, panelWidth, panelHeight, geometry, showSeams);
                case MIRROR -> renderMirror(contentGraphics, panelWidth, panelHeight, geometry, showSeams);
            }
        } finally {
            contentGraphics.dispose();
        }
    }

    /**
     * Straight repeat: the source image is tiled on a plain grid, all rows and
     * columns aligned with no offset between them.
     */
    private void renderStraight(Graphics2D g2d, int panelWidth, int panelHeight,
                                 TileGeometry geometry, boolean showSeams) {
        TileRange range = computeTileRange(g2d, panelWidth, panelHeight, geometry, 1);

        for (int row = range.rowStart(); row <= range.rowEnd(); row++) {
            for (int col = range.colStart(); col <= range.colEnd(); col++) {
                drawTile(g2d, geometry, geometry.tileLeft(col), geometry.tileTop(row), showSeams);
            }
        }
    }

    /**
     * Half-drop repeat: same grid as the straight repeat, but odd columns are
     * shifted down by half the motif's height, following the textile convention.
     */
    private void renderHalfDrop(Graphics2D g2d, int panelWidth, int panelHeight,
                                 TileGeometry geometry, boolean showSeams) {
        // Extra row margin so the vertical offset never leaves a gap at the panel's edges
        TileRange range = computeTileRange(g2d, panelWidth, panelHeight, geometry, 2);
        double halfTileHeight = geometry.tileHeight() / 2;

        for (int col = range.colStart(); col <= range.colEnd(); col++) {
            double columnOffsetY = Math.floorMod(col, 2) == 0 ? 0 : halfTileHeight;
            double x = geometry.tileLeft(col);

            for (int row = range.rowStart(); row <= range.rowEnd(); row++) {
                drawTile(g2d, geometry, x, geometry.tileTop(row) + columnOffsetY, showSeams);
            }
        }
    }

    /**
     * Mirror repeat: cells alternate flip state by row/column parity (no flip,
     * horizontal-only, vertical-only, or both — a checkerboard of orientations),
     * so the motif mirrors continuously across every shared edge between neighbors.
     */
    private void renderMirror(Graphics2D g2d, int panelWidth, int panelHeight,
                               TileGeometry geometry, boolean showSeams) {
        TileRange range = computeTileRange(g2d, panelWidth, panelHeight, geometry, 1);

        for (int row = range.rowStart(); row <= range.rowEnd(); row++) {
            boolean flipVertical = Math.floorMod(row, 2) != 0;
            for (int col = range.colStart(); col <= range.colEnd(); col++) {
                boolean flipHorizontal = Math.floorMod(col, 2) != 0;
                drawTile(g2d, geometry, geometry.tileLeft(col), geometry.tileTop(row),
                        flipHorizontal, flipVertical, showSeams);
            }
        }
    }

    private void drawTile(Graphics2D g2d, TileGeometry geometry, double x, double y, boolean showSeams) {
        drawTile(g2d, geometry, x, y, false, false, showSeams);
    }

    private void drawTile(Graphics2D g2d, TileGeometry geometry, double x, double y,
                           boolean flipHorizontal, boolean flipVertical, boolean showSeams) {
        BufferedImage tile = getCachedTile(flipHorizontal, flipVertical);

        // The cached tile is already ~tileWidth x ~tileHeight, so this is a near
        // 1:1 blit rather than a resample of the (possibly much larger) source image
        AffineTransform tileTransform = new AffineTransform();
        tileTransform.translate(x, y);
        tileTransform.scale(geometry.tileWidth() / tile.getWidth(), geometry.tileHeight() / tile.getHeight());
        g2d.drawImage(tile, tileTransform, null);

        if (showSeams) {
            g2d.draw(new Rectangle2D.Double(x, y, geometry.tileWidth(), geometry.tileHeight()));
        }
    }

    /**
     * Ensures the tile cache matches the current source image and tile size,
     * clearing it whenever either changes (new image, grid size, or panel resize).
     * Zooming/panning alone never changes tile size, since it is applied by the
     * caller's view transform rather than by resizing the tiles themselves.
     */
    private void refreshTileCache(BufferedImage image, TileGeometry geometry) {
        int tileWidthPx = Math.max(1, (int) Math.round(geometry.tileWidth()));
        int tileHeightPx = Math.max(1, (int) Math.round(geometry.tileHeight()));

        if (image != cachedSourceImage || tileWidthPx != cachedTileWidthPx || tileHeightPx != cachedTileHeightPx) {
            tileCache.clear();
            cachedSourceImage = image;
            cachedTileWidthPx = tileWidthPx;
            cachedTileHeightPx = tileHeightPx;
        }
    }

    private BufferedImage getCachedTile(boolean flipHorizontal, boolean flipVertical) {
        return tileCache.computeIfAbsent(new FlipKey(flipHorizontal, flipVertical),
                key -> renderScaledTile(cachedSourceImage, cachedTileWidthPx, cachedTileHeightPx,
                        key.flipHorizontal(), key.flipVertical()));
    }

    // Pre-scales (and, for mirror mode, pre-flips) the source image once per
    // orientation, so repeated tile draws are cheap instead of resampling the
    // full source image on every single one.
    private BufferedImage renderScaledTile(BufferedImage source, int width, int height,
                                            boolean flipHorizontal, boolean flipVertical) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Flipping is a negative scale anchored at the far edge, so the result
            // still exactly fills the [0, width] x [0, height] rectangle
            double originX = flipHorizontal ? width : 0;
            double originY = flipVertical ? height : 0;
            double scaleX = (flipHorizontal ? -1 : 1) * ((double) width / source.getWidth());
            double scaleY = (flipVertical ? -1 : 1) * ((double) height / source.getHeight());

            AffineTransform transform = new AffineTransform();
            transform.translate(originX, originY);
            transform.scale(scaleX, scaleY);
            g2d.drawImage(source, transform, null);
        } finally {
            g2d.dispose();
        }
        return scaled;
    }

    private Color seamColor(SeamStyle seamStyle) {
        return switch (seamStyle) {
            case DARK_GRAY_SOLID -> new Color(0, 0, 0, SEAM_ALPHA);
            case WHITE_SOLID -> new Color(255, 255, 255, SEAM_ALPHA);
            case GRAY_DASHED -> new Color(128, 128, 128, SEAM_ALPHA);
            case RED_HIGH_CONTRAST -> new Color(255, 0, 0, SEAM_ALPHA_HIGH_CONTRAST);
        };
    }

    private Stroke seamStroke(SeamStyle seamStyle, double zoomScale) {
        // Divide by the zoom scale so the seam keeps a constant apparent width/dash length on screen
        float width = (float) (SEAM_WIDTH_PX / zoomScale);
        if (seamStyle == SeamStyle.GRAY_DASHED) {
            float dashLength = (float) (SEAM_DASH_LENGTH_PX / zoomScale);
            float dashGap = (float) (SEAM_DASH_GAP_PX / zoomScale);
            return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10f, new float[] {dashLength, dashGap}, 0f);
        }
        return new BasicStroke(width);
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
    private record TileGeometry(double tileWidth, double tileHeight, double centerX, double centerY) {

        static TileGeometry of(BufferedImage image, int panelWidth, int panelHeight, int gridSize) {
            double tileWidth = (double) panelWidth / gridSize;
            double tileHeight = tileWidth * image.getHeight() / image.getWidth();
            return new TileGeometry(tileWidth, tileHeight, panelWidth / 2.0, panelHeight / 2.0);
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

    private record FlipKey(boolean flipHorizontal, boolean flipVertical) {
    }
}
