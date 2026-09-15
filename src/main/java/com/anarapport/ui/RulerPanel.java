package com.anarapport.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A horizontal or vertical ruler with centimeter tick marks, meant to sit as
 * a {@link javax.swing.JScrollPane} column/row header around {@link ImagePanel}.
 *
 * <p>This component only draws; it has no notion of zoom, pan, calibration or
 * the mouse cursor on its own. {@link ImagePanel} (which owns all of that)
 * pushes updates via {@link #updateView} and {@link #setCursorPosition}
 * whenever they change, so this stays a simple, reusable drawing surface.
 */
final class RulerPanel extends JComponent {

    enum Orientation { HORIZONTAL, VERTICAL }

    private static final int THICKNESS_PX = 22;
    private static final double MIN_LABEL_SPACING_PX = 40;
    private static final double MIN_MINOR_TICK_SPACING_PX = 4;
    private static final int[] MAJOR_TICK_STEPS_CM = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000};

    private final Orientation orientation;

    // How many on-screen pixels correspond to one real centimeter right now
    // (ScreenCalibration.pixelsPerCm() * the view's current zoom), and where
    // the world origin (0 cm) currently falls on screen (i.e. the view's pan).
    private double pixelsPerCm;
    private double originPx;

    private Integer cursorPositionPx;

    RulerPanel(Orientation orientation) {
        this.orientation = orientation;
        setOpaque(true);
        setBackground(new Color(0xF0, 0xF0, 0xF0));
        // A plain JComponent (unlike JPanel) has no UI delegate to supply a
        // default font before it's added to a hierarchy, so getFont() would be
        // null here -- build the font from scratch instead of deriving one.
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        setPreferredSize(orientation == Orientation.HORIZONTAL
                ? new Dimension(10, THICKNESS_PX)
                : new Dimension(THICKNESS_PX, 10));
    }

    /** {@code pixelsPerCm <= 0} (e.g. the screen isn't calibrated yet) simply draws no ticks. */
    void updateView(double pixelsPerCm, double originPx) {
        this.pixelsPerCm = pixelsPerCm;
        this.originPx = originPx;
        repaint();
    }

    /** Position (along this ruler's axis) of the mouse cursor over the viewport, or null if it isn't over it. */
    void setCursorPosition(Integer positionPx) {
        this.cursorPositionPx = positionPx;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (pixelsPerCm <= 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.DARK_GRAY);
            drawTicks(g2);
            if (cursorPositionPx != null) {
                drawCursorIndicator(g2, cursorPositionPx);
            }
        } finally {
            g2.dispose();
        }
    }

    private void drawTicks(Graphics2D g2) {
        int length = orientation == Orientation.HORIZONTAL ? getWidth() : getHeight();
        int majorEveryCm = computeMajorTickStepCm();
        boolean drawMinorTicks = pixelsPerCm >= MIN_MINOR_TICK_SPACING_PX;

        int firstCm = (int) Math.floor(-originPx / pixelsPerCm) - 1;
        int lastCm = (int) Math.ceil((length - originPx) / pixelsPerCm) + 1;

        for (int cm = firstCm; cm <= lastCm; cm++) {
            boolean isMajor = Math.floorMod(cm, majorEveryCm) == 0;
            if (!isMajor && !drawMinorTicks) {
                continue;
            }
            double position = originPx + cm * pixelsPerCm;
            drawTick(g2, position, isMajor, cm);
        }
    }

    private void drawTick(Graphics2D g2, double position, boolean isMajor, int cm) {
        int tickLength = isMajor ? THICKNESS_PX / 2 : THICKNESS_PX / 4;
        int pos = (int) Math.round(position);

        if (orientation == Orientation.HORIZONTAL) {
            g2.drawLine(pos, THICKNESS_PX - tickLength, pos, THICKNESS_PX);
            if (isMajor) {
                g2.drawString(String.valueOf(cm), pos + 2, THICKNESS_PX - tickLength - 1);
            }
        } else {
            g2.drawLine(THICKNESS_PX - tickLength, pos, THICKNESS_PX, pos);
            if (isMajor) {
                g2.drawString(String.valueOf(cm), 2, pos - 2);
            }
        }
    }

    private void drawCursorIndicator(Graphics2D g2, int positionPx) {
        g2.setColor(Color.RED);
        if (orientation == Orientation.HORIZONTAL) {
            g2.drawLine(positionPx, 0, positionPx, THICKNESS_PX);
        } else {
            g2.drawLine(0, positionPx, THICKNESS_PX, positionPx);
        }
    }

    /** Smallest step (1, 2, 5, 10, 20, 50cm, ...) whose labels won't crowd each other at the current scale. */
    private int computeMajorTickStepCm() {
        for (int step : MAJOR_TICK_STEPS_CM) {
            if (step * pixelsPerCm >= MIN_LABEL_SPACING_PX) {
                return step;
            }
        }
        return MAJOR_TICK_STEPS_CM[MAJOR_TICK_STEPS_CM.length - 1];
    }
}
