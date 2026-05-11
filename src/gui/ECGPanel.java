package gui;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;
import java.util.Random;

public class ECGPanel extends JPanel {

    private LinkedList<Integer> points = new LinkedList<>();
    private Random random = new Random();

    public ECGPanel() {
        setBackground(Color.BLACK);

        Timer timer = new Timer(60, e -> {
            addPoint();
            repaint();
        });

        timer.start();
    }

    private void addPoint() {
        int middle = getHeight() / 2;

        int value;

        if (random.nextInt(20) == 0) {
            value = middle - 70;
        } else {
            value = middle + random.nextInt(20) - 10;
        }

        points.add(value);

        if (points.size() > getWidth()) {
            points.removeFirst();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(new Color(0, 255, 80));
        g.drawString("Live ECG Monitor", 15, 20);

        for (int i = 1; i < points.size(); i++) {
            g.drawLine(
                    i - 1,
                    points.get(i - 1),
                    i,
                    points.get(i)
            );
        }
    }
}