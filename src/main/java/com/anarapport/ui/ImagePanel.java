package com.anarapport.ui;

import com.anarapport.model.RapportType;
import com.anarapport.model.SeamStyle;
import com.anarapport.render.RapportRenderer;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Panel that draws the rapport pattern (source image tiled according to a
 * RapportType) so it covers the whole visible panel area. Supports zooming
 * with the mouse wheel (centered on the cursor) and panning by dragging
 * with the left mouse button.
 */
public class ImagePanel extends JPanel {

    private static final double ZOOM_STEP = 1.1;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 8.0;

    private final RapportRenderer renderer = new RapportRenderer();

    private BufferedImage image;
    private int gridSize;
    private RapportType rapportType;
    private boolean showTileSeams;
    private SeamStyle seamStyle;

    private double zoom = 1.0;
    private double panX = 0;
    private double panY = 0;
    private Point lastDragPoint;

    public ImagePanel() {
        installViewControls();
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        resetView();
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

    private void resetView() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
        repaint();
    }

    private void installViewControls() {
        MouseAdapter panHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    lastDragPoint = e.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastDragPoint == null) {
                    return;
                }
                // Pan is a plain screen-space offset, unaffected by the current zoom level
                panX += e.getX() - lastDragPoint.x;
                panY += e.getY() - lastDragPoint.y;
                lastDragPoint = e.getPoint();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastDragPoint = null;
            }
        };
        addMouseListener(panHandler);
        addMouseMotionListener(panHandler);

        addMouseWheelListener(e -> {
            if (image == null) {
                return;
            }

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
            repaint();
        });
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

        AffineTransform viewTransform = new AffineTransform();
        viewTransform.translate(panX, panY);
        viewTransform.scale(zoom, zoom);

        // The renderer applies viewTransform to its own copy of g2d, so g stays
        // untransformed here for any overlay/UI drawing added after this call.
        Graphics2D g2d = (Graphics2D) g.create();
        renderer.render(g2d, image, getWidth(), getHeight(), gridSize, rapportType, viewTransform,
                showTileSeams, seamStyle);
        g2d.dispose();
    }
}
