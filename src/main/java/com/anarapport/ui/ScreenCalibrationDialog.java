package com.anarapport.ui;

import com.anarapport.calibration.ScreenCalibration;
import com.anarapport.i18n.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * Manual screen calibration: shows a horizontal bar of adjustable pixel
 * length that the user measures against a physical ruler, then computes and
 * saves pixels-per-centimeter from that measurement. Used both as the
 * fallback when automatic (EDID-based) detection fails and as an explicit
 * "Tools > Calibrate screen" action for the user to fine-tune or redo at any
 * time.
 */
public final class ScreenCalibrationDialog {

    private static final int MIN_BAR_WIDTH_PX = 50;
    private static final int MAX_BAR_WIDTH_PX = 900;
    private static final int DEFAULT_BAR_WIDTH_PX = 400;
    private static final double DEFAULT_MEASURED_CM = 10.0;

    private ScreenCalibrationDialog() {
    }

    /** Shows the modal calibration dialog; returns whether the user saved a new calibration. */
    public static boolean show(JFrame owner) {
        JDialog dialog = new JDialog(owner, Messages.get("dialog.calibration.title"), true);
        dialog.setResizable(false);
        dialog.setLayout(new BorderLayout());

        int initialBarWidth = ScreenCalibration.getPixelsPerCm()
                .map(pixelsPerCm -> clamp((int) Math.round(pixelsPerCm * DEFAULT_MEASURED_CM), MIN_BAR_WIDTH_PX, MAX_BAR_WIDTH_PX))
                .orElse(DEFAULT_BAR_WIDTH_PX);

        JLabel instructions = new JLabel("<html><body style='width: 320px'>"
                + Messages.get("dialog.calibration.instructions") + "</body></html>");
        instructions.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        dialog.add(instructions, BorderLayout.NORTH);

        RulerBarPanel barPanel = new RulerBarPanel(initialBarWidth);
        JPanel barWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        barWrapper.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));
        barWrapper.add(barPanel);
        dialog.add(barWrapper, BorderLayout.CENTER);

        SpinnerNumberModel barWidthModel = new SpinnerNumberModel(initialBarWidth, MIN_BAR_WIDTH_PX, MAX_BAR_WIDTH_PX, 10);
        JSpinner barWidthSpinner = new JSpinner(barWidthModel);
        barWidthSpinner.addChangeListener(event -> barPanel.setBarWidthPx((Integer) barWidthSpinner.getValue()));

        SpinnerNumberModel measuredCmModel = new SpinnerNumberModel(DEFAULT_MEASURED_CM, 0.1, 100.0, 0.1);
        JSpinner measuredCmSpinner = new JSpinner(measuredCmModel);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel(Messages.get("dialog.calibration.barWidth")), gbc);
        gbc.gridx = 1;
        formPanel.add(barWidthSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel(Messages.get("dialog.calibration.measuredLength")), gbc);
        gbc.gridx = 1;
        formPanel.add(measuredCmSpinner, gbc);

        boolean[] confirmed = {false};

        JButton okButton = new JButton(Messages.get("dialog.calibration.ok"));
        okButton.addActionListener(event -> {
            double measuredCm = (Double) measuredCmSpinner.getValue();
            if (measuredCm <= 0) {
                JOptionPane.showMessageDialog(dialog, Messages.get("dialog.calibration.invalidMeasurement.message"),
                        Messages.get("dialog.calibration.invalidMeasurement.title"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            int barWidthPx = (Integer) barWidthSpinner.getValue();
            ScreenCalibration.saveManualCalibration(barWidthPx / measuredCm);
            confirmed[0] = true;
            dialog.dispose();
        });

        JButton cancelButton = new JButton(Messages.get("dialog.calibration.cancel"));
        cancelButton.addActionListener(event -> dialog.dispose());

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonsPanel.add(okButton);
        buttonsPanel.add(cancelButton);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(formPanel, BorderLayout.CENTER);
        southPanel.add(buttonsPanel, BorderLayout.SOUTH);
        southPanel.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        dialog.add(southPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true); // blocks here (modal) until disposed by OK/Cancel

        return confirmed[0];
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Draws a horizontal bar exactly {@code barWidthPx} wide with end brackets, for the user to measure with a ruler. */
    private static final class RulerBarPanel extends JComponent {
        private static final int BRACKET_HEIGHT_PX = 20;
        private static final int LEFT_MARGIN_PX = 10;

        private int barWidthPx;

        RulerBarPanel(int barWidthPx) {
            this.barWidthPx = barWidthPx;
            setOpaque(true);
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            setPreferredSize(new Dimension(LEFT_MARGIN_PX * 2 + MAX_BAR_WIDTH_PX, BRACKET_HEIGHT_PX + 20));
        }

        void setBarWidthPx(int barWidthPx) {
            this.barWidthPx = barWidthPx;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x33, 0x66, 0xA3));
                g2.setStroke(new BasicStroke(2f));

                int left = LEFT_MARGIN_PX;
                int right = left + barWidthPx;
                int centerY = getHeight() / 2;

                g2.drawLine(left, centerY, right, centerY);
                g2.drawLine(left, centerY - BRACKET_HEIGHT_PX / 2, left, centerY + BRACKET_HEIGHT_PX / 2);
                g2.drawLine(right, centerY - BRACKET_HEIGHT_PX / 2, right, centerY + BRACKET_HEIGHT_PX / 2);
            } finally {
                g2.dispose();
            }
        }
    }
}
