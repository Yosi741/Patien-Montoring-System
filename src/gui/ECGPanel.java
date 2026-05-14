package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.LinkedList;

public class ECGPanel extends JPanel {

    private LinkedList<Integer> points = new LinkedList<>();
    private int tick = 0;
    private boolean connected = false;

    public ECGPanel() {
        setBackground(new Color(8, 18, 24));

        Timer timer = new Timer(35, e -> {
            if (connected) {
                addPoint();
            }
            repaint();
        });

        timer.start();
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
        if (!connected) {
            points.clear();
        }
    }

    private void addPoint() {
        int middle = Math.max(1, getHeight() / 2);
        int phase = tick % 42;
        int value = middle;

        if (phase < 8) {
            value = middle - phase;
        } else if (phase < 12) {
            value = middle + 8;
        } else if (phase == 12) {
            value = middle - 78;
        } else if (phase == 13) {
            value = middle + 54;
        } else if (phase < 20) {
            value = middle - 6;
        } else if (phase < 30) {
            value = middle - (int) (Math.sin((phase - 20) / 10.0 * Math.PI) * 16);
        } else {
            value = middle + (int) (Math.sin(phase) * 4);
        }

        points.add(value);
        tick++;

        int maxPoints = Math.max(80, getWidth());
        while (points.size() > maxPoints) {
            points.removeFirst();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGrid(g2);

        if (connected) {
            g2.setColor(new Color(155, 255, 190, 70));
            g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawWave(g2);

            g2.setColor(new Color(60, 255, 130));
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            drawWave(g2);
        } else {
            int mid = getHeight() / 2;
            g2.setColor(new Color(60, 255, 130, 120));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawLine(0, mid, getWidth(), mid);
            g2.setFont(UITheme.font(Font.BOLD, 18));
            g2.setColor(new Color(208, 255, 222));
            g2.drawString("ECG MONITOR DISCONNECTED", 18, mid - 18);
        }

        g2.setFont(UITheme.font(Font.BOLD, 16));
        g2.setColor(new Color(208, 255, 222));
        g2.drawString(connected ? "LIVE ECG" : "ECG STANDBY", 18, 28);
        g2.setFont(UITheme.font(Font.PLAIN, 12));
        g2.setColor(new Color(128, 178, 150));
        g2.drawString("continuous cardiac waveform", 18, 48);

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(new Color(20, 56, 45));

        for (int x = 0; x < getWidth(); x += 28) {
            g2.drawLine(x, 0, x, getHeight());
        }

        for (int y = 0; y < getHeight(); y += 24) {
            g2.drawLine(0, y, getWidth(), y);
        }
    }

    private void drawWave(Graphics2D g2) {
        if (points.size() < 2) {
            return;
        }

        Path2D path = new Path2D.Double();
        path.moveTo(0, points.get(0));

        for (int i = 1; i < points.size(); i++) {
            path.lineTo(i, points.get(i));
        }

        g2.draw(path);
    }
}
