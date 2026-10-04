package org.example.ui;

import org.example.model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.function.Consumer;

/** Панель лабиринта в стиле «мышь в лабиринте».
 *  Левый клик — поставить выбранный инструмент (или убрать, если он уже стоит).
 *  Правый клик — очистить клетку до пустой.
 */
public final class MazePanel extends JPanel {
    private Maze maze;
    private Environment environment;
    private int cellSize = 34;

    // === Цвета из задания ===
    private static final Color BG_COLOR     = new Color(30, 22, 60);
    private static final Color WALL_COLOR   = new Color(140, 80, 220);
    private static final Color CHEESE_COLOR = new Color(255, 200, 0);
    private static final Color WATER_COLOR  = new Color(80, 180, 255);
    private static final Color TRAP_COLOR   = new Color(255, 220, 0);
    private static final Color MOUSE_COLOR  = new Color(200, 200, 200);
    private static final Color START_COLOR  = new Color(80, 220, 120);

    public MazePanel(Consumer<Position> leftClick, Consumer<Position> rightClick) {
        setBackground(BG_COLOR);
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (maze == null) return;
                Position p = new Position(e.getY() / cellSize, e.getX() / cellSize);
                if (!maze.contains(p)) return;
                if (SwingUtilities.isRightMouseButton(e)) rightClick.accept(p);
                else                                       leftClick.accept(p);
            }
        });
    }

    public void show(Maze maze, Environment environment) {
        this.maze = maze;
        this.environment = environment;
        revalidate();
        repaint();
    }

    public void zoom(int size) {
        cellSize = size;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return maze == null
                ? new Dimension(600, 500)
                : new Dimension(maze.cols() * cellSize, maze.rows() * cellSize);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (maze == null) return;

        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, getWidth(), getHeight());

        Rectangle clip = g.getClipBounds();
        int rStart = Math.max(0, clip.y / cellSize);
        int rEnd   = Math.min(maze.rows(), (clip.y + clip.height) / cellSize + 1);
        int cStart = Math.max(0, clip.x / cellSize);
        int cEnd   = Math.min(maze.cols(), (clip.x + clip.width) / cellSize + 1);

        for (int r = rStart; r < rEnd; r++) {
            for (int c = cStart; c < cEnd; c++) {
                Position p = new Position(r, c);
                CellType type = maze.at(p);
                int x = c * cellSize;
                int y = r * cellSize;

                switch (type) {
                    case WALL   -> drawWall(g, x, y);
                    case CHEESE -> drawCheese(g, x, y);
                    case WATER  -> drawWater(g, x, y, p);
                    case SHOCK  -> drawTrap(g, x, y);
                    case START  -> drawStart(g, x, y);
                    default     -> { }
                }

                if (environment != null && p.equals(environment.position())) {
                    drawMouse(g, x, y);
                }
            }
        }

        g.dispose();
    }

    private void drawWall(Graphics2D g, int x, int y) {
        g.setColor(WALL_COLOR);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.fillRoundRect(x + 3, y + 3, cellSize - 6, cellSize - 6, 8, 8);
    }

    private void drawCheese(Graphics2D g, int x, int y) {
        int cx = x + cellSize / 2;
        int cy = y + cellSize / 2;
        int r  = cellSize / 3;
        g.setColor(CHEESE_COLOR);
        g.setStroke(new BasicStroke(2f));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            int x1 = cx + (int)(Math.cos(angle) * (r + 3));
            int y1 = cy + (int)(Math.sin(angle) * (r + 3));
            int x2 = cx + (int)(Math.cos(angle) * (r + 8));
            int y2 = cy + (int)(Math.sin(angle) * (r + 8));
            g.drawLine(x1, y1, x2, y2);
        }
        Path2D tri = new Path2D.Double();
        tri.moveTo(cx - r, cy + r);
        tri.lineTo(cx + r, cy + r);
        tri.lineTo(cx, cy - r);
        tri.closePath();
        g.fill(tri);
    }

    private void drawWater(Graphics2D g, int x, int y, Position p) {
        int cx = x + cellSize / 2;
        int cy = y + cellSize / 2;
        int r  = cellSize / 4;
        boolean drunk = environment != null && environment.consumed(p);
        g.setColor(drunk ? new Color(80, 180, 255, 60) : WATER_COLOR);

        Path2D drop = new Path2D.Double();
        drop.moveTo(cx, cy - r);
        drop.curveTo(cx + r, cy, cx + r, cy + r, cx, cy + r);
        drop.curveTo(cx - r, cy + r, cx - r, cy, cx, cy - r);
        drop.closePath();
        g.fill(drop);
    }

    private void drawTrap(Graphics2D g, int x, int y) {
        int cx = x + cellSize / 2;
        int cy = y + cellSize / 2;
        int r  = cellSize / 3;
        g.setColor(TRAP_COLOR);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D bolt = new Path2D.Double();
        bolt.moveTo(cx - r / 2.0, cy - r);
        bolt.lineTo(cx + r / 3.0, cy - r / 4.0);
        bolt.lineTo(cx - r / 3.0, cy);
        bolt.lineTo(cx + r / 2.0, cy + r);
        g.draw(bolt);
    }

    private void drawStart(Graphics2D g, int x, int y) {
        g.setColor(START_COLOR);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(14, cellSize / 2)));
        String s = "S";
        int w = g.getFontMetrics().stringWidth(s);
        g.drawString(s, x + (cellSize - w) / 2, y + cellSize * 3 / 4);
    }

    private void drawMouse(Graphics2D g, int x, int y) {
        int cx = x + cellSize / 2;
        int cy = y + cellSize / 2;
        int r  = cellSize / 3;
        g.setColor(MOUSE_COLOR);
        g.fillOval(cx - r, cy - r - 4, r, r);
        g.fillOval(cx, cy - r - 4, r, r);
        g.fillOval(cx - r, cy - r / 2, r * 2, r * 2);
        g.setColor(Color.BLACK);
        g.fillOval(cx - r / 2, cy, 3, 3);
        g.fillOval(cx + r / 4, cy, 3, 3);
    }
}