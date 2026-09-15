package com.anarapport.ui;

import com.anarapport.calibration.ScreenCalibration;
import com.anarapport.model.RapportType;
import com.anarapport.model.SeamStyle;
import com.anarapport.render.RapportRenderer;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

/**
 * Panel that draws the rapport pattern (source image tiled according to a
 * RapportType) so it covers the whole visible panel area. Supports zooming
 * with the mouse wheel (centered on the cursor) and panning by dragging
 * with the left mouse button.
 *
 * <p>Implements {@link Scrollable}, always tracking the viewport's width and
 * height: this panel is wrapped in a {@link javax.swing.JScrollPane} only to
 * get its ruler headers (see {@link ImagePanelViewport}), not to actually
 * scroll (pan/zoom are handled manually) -- without this, {@link
 * javax.swing.JViewport} would size the panel to its own tiny default
 * preferred size instead of filling the available area.
 */
public class ImagePanel extends JPanel implements Scrollable {

    private static final double ZOOM_STEP = 1.1;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 8.0;

    private final RapportRenderer renderer = new RapportRenderer();

    private BufferedImage image;
    private int gridSize;
    private RapportType rapportType;
    private boolean showTileSeams;
    private SeamStyle seamStyle;
    private int cellOffsetXPercent;
    private int cellOffsetYPercent;

    private double zoom = 1.0;
    private double panX = 0;
    private double panY = 0;
    private Point lastDragPoint;

    // The cell width in world units at zoom=1: escalaBase (a physical-size
    // calculation from the image's DPI and the screen's calibration) while
    // real-size mode is active, otherwise the usual panelWidth / gridSize.
    // Zoom always multiplies whichever one is currently in effect (see
    // currentMotifWidth()), so mouse-wheel zoom keeps working identically in
    // both modes instead of being a separate, mutually-exclusive control.
    private boolean realSizeActive;
    private double realSizeBaseScale = 1.0;
    private Runnable realSizeDeactivatedListener;

    // Pushed to whenever pan/zoom or the mouse position changes, so the ruler
    // headers (which have no notion of zoom/pan/calibration themselves) stay
    // in sync without polling. Both are optional -- rulers may not be shown.
    private RulerPanel horizontalRuler;
    private RulerPanel verticalRuler;

    private boolean measureModeActive;
    private Point measureStart;
    private Point measureEnd;

    public ImagePanel() {
        installViewControls();
    }

    /** Wires this panel to the ruler headers around it (see Main's JScrollPane setup); either may be null. */
    public void setRulers(RulerPanel horizontalRuler, RulerPanel verticalRuler) {
        this.horizontalRuler = horizontalRuler;
        this.verticalRuler = verticalRuler;
        pushRulerView();
    }

    /**
     * Toggles the click-and-drag measuring tool: while active, dragging with
     * the left mouse button draws a line between the press and release
     * points and labels it with the real distance in centimeters (using the
     * same screenPixelsPerCm * zoom conversion as the rulers), instead of
     * panning the view. The last measurement stays visible as an overlay
     * until the next drag replaces it, or the mode is turned off.
     */
    public void setMeasureModeActive(boolean active) {
        this.measureModeActive = active;
        setCursor(active ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : Cursor.getDefaultCursor());
        if (!active) {
            measureStart = null;
            measureEnd = null;
            repaint();
        }
    }

    public boolean isMeasureModeActive() {
        return measureModeActive;
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        resetView();
    }

    /**
     * Switches to "real size" mode: the grid's cell width becomes {@code
     * baseScale * image pixel width} (i.e. escalaBase, computed by the
     * caller from the image's DPI and the screen's calibration) instead of
     * {@code panelWidth / gridSize}, and zoom/pan reset to their defaults so
     * the image starts out at exactly its physical size (zoom = 100%).
     */
    public void activateRealSize(double baseScale) {
        this.realSizeBaseScale = baseScale;
        this.realSizeActive = true;
        resetZoomAndPan();
        pushRulerView();
        repaint();
    }

    public boolean isRealSizeActive() {
        return realSizeActive;
    }

    /**
     * Returns to "fit to view" mode (cell width back to {@code panelWidth /
     * gridSize}), resetting zoom/pan to that mode's default.
     */
    public void deactivateRealSize() {
        if (!realSizeActive) {
            return;
        }
        realSizeActive = false;
        resetZoomAndPan();
        pushRulerView();
        repaint();
    }

    /**
     * Notified whenever real-size mode turns itself off other than through a
     * direct call to {@link #deactivateRealSize()} -- i.e. a new image being
     * loaded, since the previous escalaBase no longer applies to it -- so the
     * app layer can keep its "real size" toggle button and grid-size control
     * in sync.
     */
    public void setRealSizeDeactivatedListener(Runnable listener) {
        this.realSizeDeactivatedListener = listener;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
        repaint();
    }

    public void setRapportType(RapportType rapportType) {
        this.rapportType = rapportType;
        repaint();
    }

    public void setShowTileSeams(boolean showTileSeams) {
        this.showTileSeams = showTileSeams;
        repaint();
    }

    public void setSeamStyle(SeamStyle seamStyle) {
        this.seamStyle = seamStyle;
        repaint();
    }

    public void setCellOffsetXPercent(int cellOffsetXPercent) {
        this.cellOffsetXPercent = cellOffsetXPercent;
        repaint();
    }

    public void setCellOffsetYPercent(int cellOffsetYPercent) {
        this.cellOffsetYPercent = cellOffsetYPercent;
        repaint();
    }

    private void resetView() {
        resetZoomAndPan();
        if (realSizeActive) {
            // escalaBase was computed for the previous image; it no longer
            // applies, so drop back to fit-to-view rather than silently
            // showing the new image at the wrong physical size.
            realSizeActive = false;
            if (realSizeDeactivatedListener != null) {
                realSizeDeactivatedListener.run();
            }
        }
        pushRulerView();
        repaint();
    }

    private void resetZoomAndPan() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
    }

    /** Recomputes and pushes the current screenPixelsPerCm/origin to whichever ruler headers are set. */
    private void pushRulerView() {
        if (horizontalRuler == null && verticalRuler == null) {
            return;
        }
        double pixelsPerCm = effectivePixelsPerCm();
        if (horizontalRuler != null) {
            horizontalRuler.updateView(pixelsPerCm, panX);
        }
        if (verticalRuler != null) {
            verticalRuler.updateView(pixelsPerCm, panY);
        }
    }

    /**
     * On-screen pixels per real centimeter of the displayed content right now:
     * the screen's own calibration times the current zoom. In "real size" mode
     * this is exactly the screen's calibration at zoom = 100%, so ruler ticks
     * (and the measuring tool) line up with real centimeters on the rendered
     * pattern at any zoom level -- not just physical monitor centimeters.
     * Negative/zero means the screen hasn't been calibrated.
     */
    private double effectivePixelsPerCm() {
        return ScreenCalibration.getPixelsPerCm().map(pixelsPerCm -> pixelsPerCm * zoom).orElse(-1.0);
    }

    private void installViewControls() {
        MouseAdapter panHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (measureModeActive) {
                    measureStart = e.getPoint();
                    measureEnd = e.getPoint();
                    repaint();
                } else {
                    lastDragPoint = e.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                pushCursorPosition(e.getPoint());
                if (measureModeActive) {
                    if (measureStart != null) {
                        measureEnd = e.getPoint();
                        repaint();
                    }
                    return;
                }
                if (lastDragPoint == null) {
                    return;
                }
                // Pan is a plain screen-space offset, unaffected by the current zoom level
                panX += e.getX() - lastDragPoint.x;
                panY += e.getY() - lastDragPoint.y;
                lastDragPoint = e.getPoint();
                pushRulerView();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastDragPoint = null;
                // The finished measurement (line + label) is left as an overlay
                // until the next drag starts a new one -- simpler than timing a
                // fade-out, and lets the user actually read the result.
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                pushCursorPosition(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                pushCursorPosition(null);
            }
        };
        addMouseListener(panHandler);
        addMouseMotionListener(panHandler);

        addMouseWheelListener(e -> {
            if (image == null) {
                return;
            }

            // Zoom multiplies whichever base scale is currently in effect (fit-to-view
            // or real-size); it never exits real-size mode on its own.
            double oldZoom = zoom;
            double factor = Math.pow(ZOOM_STEP, -e.getWheelRotation());
            double newZoom = clamp(oldZoom * factor, MIN_ZOOM, MAX_ZOOM);
            if (newZoom == oldZoom) {
                return;
            }

            // Keep the world point under the cursor fixed on screen while zooming
            double cursorX = e.getX();
            double cursorY = e.getY();
            double worldX = (cursorX - panX) / oldZoom;
            double worldY = (cursorY - panY) / oldZoom;
            panX = cursorX - worldX * newZoom;
            panY = cursorY - worldY * newZoom;
            zoom = newZoom;
            pushRulerView();
            repaint();
        });
    }

    private void pushCursorPosition(Point point) {
        if (horizontalRuler != null) {
            horizontalRuler.setCursorPosition(point != null ? point.x : null);
        }
        if (verticalRuler != null) {
            verticalRuler.setCursorPosition(point != null ? point.y : null);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            return;
        }

        // The renderer applies the view transform to its own copy of g2d, so g stays
        // untransformed here for any overlay/UI drawing added after this call.
        Graphics2D g2d = (Graphics2D) g.create();
        paintContent(g2d);
        g2d.dispose();

        if (measureStart != null && measureEnd != null) {
            drawMeasurementOverlay((Graphics2D) g);
        }
    }

    /**
     * Draws the measuring tool's line and floating distance label directly in
     * screen space (unlike the pattern content, this isn't affected by
     * zoom/pan itself -- it already IS the on-screen distance the user drew).
     * The distance shown converts that on-screen pixel length using the same
     * screenPixelsPerCm * zoom factor as the rulers.
     */
    private void drawMeasurementOverlay(Graphics2D g2d) {
        Graphics2D overlay = (Graphics2D) g2d.create();
        try {
            overlay.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            overlay.setColor(Color.RED);
            overlay.setStroke(new BasicStroke(1.5f));
            overlay.drawLine(measureStart.x, measureStart.y, measureEnd.x, measureEnd.y);
            overlay.fillOval(measureStart.x - 3, measureStart.y - 3, 6, 6);
            overlay.fillOval(measureEnd.x - 3, measureEnd.y - 3, 6, 6);

            double pixelsPerCm = effectivePixelsPerCm();
            double distancePx = measureStart.distance(measureEnd);
            String label = pixelsPerCm > 0
                    ? String.format(Locale.ROOT, "%.1f cm", distancePx / pixelsPerCm)
                    : "--";
            drawFloatingLabel(overlay, label, (measureStart.x + measureEnd.x) / 2, (measureStart.y + measureEnd.y) / 2);
        } finally {
            overlay.dispose();
        }
    }

    private void drawFloatingLabel(Graphics2D g2d, String text, int anchorX, int anchorY) {
        Font font = getFont().deriveFont(Font.BOLD, 12f);
        g2d.setFont(font);
        FontMetrics metrics = g2d.getFontMetrics();
        int padding = 4;
        int width = metrics.stringWidth(text) + padding * 2;
        int height = metrics.getHeight() + padding;
        int x = anchorX - width / 2;
        int y = anchorY - height - 8;

        g2d.setColor(new Color(0xFF, 0xFF, 0xE0));
        g2d.fill(new Rectangle2D.Double(x, y, width, height));
        g2d.setColor(Color.DARK_GRAY);
        g2d.draw(new Rectangle2D.Double(x, y, width, height));
        g2d.drawString(text, x + padding, y + metrics.getAscent() + padding / 2);
    }

    private void paintContent(Graphics2D g2d) {
        renderer.render(g2d, image, getWidth(), getHeight(), currentMotifWidth(), rapportType, currentViewTransform(),
                showTileSeams, seamStyle, cellOffsetXPercent / 100.0, cellOffsetYPercent / 100.0);
    }

    /**
     * The cell width in world (pre-zoom/pan) units: escalaBase while real-size
     * mode is active, otherwise the usual "fit to view" {@code panelWidth /
     * gridSize}. Either way, {@link #currentViewTransform()} applies zoom/pan
     * on top of this identically, and the renderer works out how many
     * repetitions fit the visible area dynamically from the result -- there is
     * no separate fixed row/column count to maintain.
     */
    private double currentMotifWidth() {
        if (realSizeActive && image != null) {
            return realSizeBaseScale * image.getWidth();
        }
        return (double) getWidth() / gridSize;
    }

    private AffineTransform currentViewTransform() {
        AffineTransform viewTransform = new AffineTransform();
        viewTransform.translate(panX, panY);
        viewTransform.scale(zoom, zoom);
        return viewTransform;
    }

    /**
     * Captures exactly what is currently visible on screen (same size, zoom and
     * pan, rapport mode and seam overlay) as an immutable snapshot, which can then
     * be rendered off the Event Dispatch Thread (e.g. for exporting) without
     * touching this panel's fields from another thread. Must be called on the EDT.
     * Returns null if there is nothing to render yet.
     */
    public CompositionSnapshot captureComposition() {
        int width = getWidth();
        int height = getHeight();
        if (image == null || width <= 0 || height <= 0) {
            return null;
        }
        return new CompositionSnapshot(image, width, height, currentMotifWidth(), rapportType,
                currentViewTransform(), showTileSeams, seamStyle,
                cellOffsetXPercent / 100.0, cellOffsetYPercent / 100.0);
    }

    // -- Scrollable: always track the viewport's size (see the class javadoc) --

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 10;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 100;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }

    /**
     * Immutable description of everything needed to reproduce the current view as a
     * standalone image. Safe to hand off to a background thread: rendering it uses
     * its own RapportRenderer instance, never the panel's shared one.
     */
    public record CompositionSnapshot(BufferedImage image, int width, int height, double motifWidth,
                                       RapportType rapportType, AffineTransform viewTransform,
                                       boolean showSeams, SeamStyle seamStyle,
                                       double cellOffsetXFraction, double cellOffsetYFraction) {

        public BufferedImage render() {
            BufferedImage composition = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = composition.createGraphics();
            try {
                new RapportRenderer().render(g2d, image, width, height, motifWidth, rapportType,
                        viewTransform, showSeams, seamStyle, cellOffsetXFraction, cellOffsetYFraction);
            } finally {
                g2d.dispose();
            }
            return composition;
        }
    }
}
