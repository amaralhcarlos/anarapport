package com.anarapport.ui;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * Wraps an {@link ImagePanel} in a {@link JScrollPane} whose column/row
 * headers are ruler components (see {@link RulerPanel}), so the rulers sit
 * flush around the viewport for free via Swing's own layout. The pane never
 * actually scrolls -- {@link ImagePanel} manages its own pan/zoom -- the
 * scrollbars are simply disabled and this is used purely for the header
 * layout.
 */
public final class ImagePanelViewport {

    private ImagePanelViewport() {
    }

    /**
     * Builds the scroll pane and wires the rulers to {@code imagePanel}.
     * Callers that want to toggle ruler visibility later should capture
     * {@code scrollPane.getColumnHeader().getView()} /
     * {@code getRowHeader().getView()} right after this returns, then swap
     * them for {@code null} and back via {@code setColumnHeaderView}/
     * {@code setRowHeaderView} -- no need to reference {@link RulerPanel}
     * directly (it's package-private).
     */
    public static JScrollPane wrap(ImagePanel imagePanel) {
        RulerPanel horizontalRuler = new RulerPanel(RulerPanel.Orientation.HORIZONTAL);
        RulerPanel verticalRuler = new RulerPanel(RulerPanel.Orientation.VERTICAL);
        imagePanel.setRulers(horizontalRuler, verticalRuler);

        JScrollPane scrollPane = new JScrollPane(imagePanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setColumnHeaderView(horizontalRuler);
        scrollPane.setRowHeaderView(verticalRuler);
        scrollPane.setCorner(JScrollPane.UPPER_LEFT_CORNER, new JPanel());
        scrollPane.setBorder(null);
        return scrollPane;
    }
}
