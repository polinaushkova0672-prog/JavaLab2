package org.example.ui;

import org.example.model.*;
import org.example.learning.QLearningAgent;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

/**
 * Главное окно. Две вкладки:
 *   «Главная»  — лабиринт, редактирование, обучение, статистика.
 *   «Награды»  — подкрепление (+Z, +x, −y).
 * Поле занимает всё свободное место. Слайдер «Масштаб» меняет размер клеток.
 * Лог — в отдельном окне (кнопка «Открыть лог»).
 */
public final class MazeFrame extends JFrame {

    private Maze maze;
    private Environment environment;
    private QLearningAgent agent;

    private final MazePanel board = new MazePanel(this::editLeft, this::editRight);
    private final JTabbedPane tabs = new JTabbedPane();

    // === Параметры ===
    private final JSpinner rows = number(12, 2, 200);
    private final JSpinner cols = number(16, 2, 200);
    private final JSpinner cheese = number(100, 2, 100000);
    private final JSpinner water  = number(10, 1, 99999);
    private final JSpinner shock  = number(20, 1, 100000);
    private final JSpinner episodes = number(1000, 1, 20000);

    private final JComboBox<CellType> tool = new JComboBox<>(CellType.values());

    // === Статистика (внутри вкладки) ===
    private final JTextArea stats = new JTextArea(2, 40);
    private final JLabel message = new JLabel(" ");

    // === Лог (в отдельном окне) ===
    private final JTextArea log = new JTextArea(20, 60);
    private JFrame logWindow;

    // === Таймеры ===
    private final Timer playback = new Timer(120, e -> autoStep());
    private final Timer training = new Timer(1, e -> trainBatch());
    private int trained, target, success, episodeSteps;
    private Environment trainingEnvironment;
    private boolean episodeActive;

    // === Масштаб ===
    private static final int BASE_CELL = 34;
    private final JSlider zoom = new JSlider(20, 200, 100);

    public MazeFrame() {
        super("Мышь в лабиринте — ООП / Q-learning");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                stop();
                if (logWindow != null) logWindow.dispose();
            }
        });

        tabs.addTab("Главная",  buildMainTab());
        tabs.addTab("Награды",  buildRewardsTab());

        // === Верх: вкладки + масштаб + лог ===
        JPanel top = new JPanel(new BorderLayout());
        top.add(tabs, BorderLayout.CENTER);

        JPanel zoomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 3));
        zoomBar.add(new JLabel("Масштаб:"));
        zoomBar.add(zoom);
        JLabel zoomValue = new JLabel("100%");
        zoomValue.setPreferredSize(new Dimension(48, 20));
        zoom.addChangeListener(e -> {
            int percent = zoom.getValue();
            zoomValue.setText(percent + "%");
            board.zoom(BASE_CELL * percent / 100);
        });
        zoomBar.add(zoomValue);
        zoomBar.add(button("Открыть лог", this::openLog));
        top.add(zoomBar, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);

        // === Центр — поле ===
        JScrollPane boardScroll = new JScrollPane(board);
        boardScroll.getVerticalScrollBar().setUnitIncrement(16);
        boardScroll.getHorizontalScrollBar().setUnitIncrement(16);
        add(boardScroll, BorderLayout.CENTER);

        // === Настройка stats (для вкладки «Главная») ===
        stats.setEditable(false);
        stats.setLineWrap(true);
        stats.setWrapStyleWord(true);
        stats.setOpaque(false);
        stats.setFocusable(false);
        stats.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        stats.setBorder(BorderFactory.createEmptyBorder(6, 12, 2, 12));

        // === Низ — только сообщение ===
        message.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));
        JPanel south = new JPanel(new BorderLayout());
        south.add(message, BorderLayout.CENTER);
        south.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 210)));
        add(south, BorderLayout.SOUTH);

        // === Горячие клавиши ===
        for (Direction d : Direction.values()) {
            String key = switch (d) {
                case UP -> "UP"; case DOWN -> "DOWN"; case LEFT -> "LEFT"; case RIGHT -> "RIGHT";
            };
            board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
            board.getActionMap().put(key, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) { manual(d); }
            });
        }

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        newMaze(true);
        setSize(1300, 900);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
    }

    // === Вкладки ===

    private JPanel buildMainTab() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel topRow = new JPanel(new GridLayout(1, 2, 12, 0));

        // --- Левый столбец ---
        JPanel leftCol = new JPanel(new GridBagLayout());
        leftCol.setBorder(BorderFactory.createTitledBorder("Редактирование схемы"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.gridx = 0; g.gridy = 0;
        g.weightx = 1.0;

        g.gridy++;
        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        toolRow.add(new JLabel("Инструмент:"));
        toolRow.add(tool);
        leftCol.add(toolRow, g);

        g.gridy++;
        leftCol.add(hint("Повторный клик по объекту убирает его."), g);

        // --- Правый столбец ---
        JPanel rightCol = new JPanel(new GridBagLayout());
        rightCol.setBorder(BorderFactory.createTitledBorder("Размер поля"));
        GridBagConstraints g2 = new GridBagConstraints();
        g2.insets = new Insets(4, 6, 4, 6);
        g2.anchor = GridBagConstraints.WEST;
        g2.fill = GridBagConstraints.HORIZONTAL;
        g2.gridx = 0; g2.gridy = 0;
        g2.weightx = 1.0;

        g2.gridy++;
        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sizeRow.add(new JLabel("Высота:"));
        sizeRow.add(rows);
        sizeRow.add(new JLabel("Ширина:"));
        sizeRow.add(cols);
        rightCol.add(sizeRow, g2);

        g2.gridy++;
        JPanel sizeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sizeButtons.add(button("Создать лабиринт", () -> newMaze(true)));
        sizeButtons.add(button("Чистое поле",      () -> newMaze(false)));
        rightCol.add(sizeButtons, g2);

        topRow.add(leftCol);
        topRow.add(rightCol);

        // === Обучение ===
        JPanel trainPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        trainPanel.setBorder(BorderFactory.createTitledBorder("Обучение мыши (Q-learning)"));
        trainPanel.add(new JLabel("Число попыток:"));
        trainPanel.add(episodes);
        trainPanel.add(button("Запустить обучение", this::startTraining));
        trainPanel.add(button("Прервать",           this::stop));

        // === Демонстрация ===
        JPanel demoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        demoPanel.setBorder(BorderFactory.createTitledBorder("Демонстрация"));
        demoPanel.add(button("Один ход мыши", this::oneStep));
        demoPanel.add(button("Вернуть мышь",  this::reset));
        demoPanel.add(button("Пройти по выученному пути", this::startPlayback));

        // === Статистика ===
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBorder(BorderFactory.createTitledBorder("Статистика"));
        statsPanel.add(stats, BorderLayout.CENTER);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.add(topRow);
        center.add(trainPanel);
        center.add(demoPanel);
        center.add(statsPanel);

        root.add(center, BorderLayout.NORTH);
        return root;
    }

    private JPanel buildRewardsTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; g.gridy = 0;

        g.gridy++;
        p.add(boldLabel("Подкрепление (по условию задачи)"), g);
        g.gridy++;
        JPanel r1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        r1.add(new JLabel("Сыр (+Z):")); r1.add(cheese);
        p.add(r1, g);
        g.gridy++;
        JPanel r2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        r2.add(new JLabel("Вода (+x):")); r2.add(water);
        p.add(r2, g);
        g.gridy++;
        JPanel r3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        r3.add(new JLabel("Электротравма (−y):")); r3.add(shock);
        p.add(r3, g);
        g.gridy++;
        p.add(button("Задать подкрепление", this::applyRewards), g);
        g.gridy++;
        p.add(hint("Изменение наград сбрасывает обучение мыши."), g);

        g.gridy++;
        p.add(boldLabel("Легенда"), g);
        g.gridy++;
        p.add(legend(), g);

        return p;
    }

    // === Лог ===

    private void openLog() {
        if (logWindow == null) {
            logWindow = new JFrame("Лог шагов");
            logWindow.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            logWindow.add(new JScrollPane(log), BorderLayout.CENTER);

            JButton copyLog = new JButton("Копировать лог");
            copyLog.addActionListener(e -> {
                java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(log.getText());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            });
            JButton clearLog = new JButton("Очистить лог");
            clearLog.addActionListener(e -> log.setText(""));

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            buttons.add(copyLog);
            buttons.add(clearLog);

            logWindow.add(buttons, BorderLayout.SOUTH);
            logWindow.setSize(800, 500);
            logWindow.setLocationRelativeTo(this);
        }
        logWindow.setVisible(true);
    }

    // === UI ===

    private static JLabel boldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        return l;
    }

    private static JLabel hint(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.ITALIC, 11f));
        l.setForeground(new Color(90, 90, 110));
        return l;
    }

    private JPanel legend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 3));
        p.add(legendItem(new Color(80, 220, 120),   "старт"));
        p.add(legendItem(new Color(255, 200, 0),   "сыр (+Z)"));
        p.add(legendItem(new Color(80, 180, 255),  "вода (+x)"));
        p.add(legendItem(new Color(255, 220, 0),   "ток (−y)"));
        p.add(legendItem(new Color(200, 200, 200), "мышь"));
        p.add(legendItem(new Color(140, 80, 220),  "стена"));
        return p;
    }

    private JPanel legendItem(Color c, String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel box = new JLabel("  ");
        box.setOpaque(true);
        box.setBackground(c);
        box.setPreferredSize(new Dimension(14, 14));
        box.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        p.add(box);
        p.add(new JLabel(text));
        return p;
    }

    private static JSpinner number(int value, int min, int max) {
        return new JSpinner(new SpinnerNumberModel(value, min, max, 1));
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> {
            try { action.run(); }
            catch (IllegalArgumentException ex) { message.setText(ex.getMessage()); }
        });
        return b;
    }

    private int value(JSpinner spinner) {
        try { spinner.commitEdit(); }
        catch (java.text.ParseException e) { throw new IllegalArgumentException("Введите целое число в допустимом диапазоне."); }
        return ((Number) spinner.getValue()).intValue();
    }

    // === Логика ===

    private Rewards rewards() {
        return new Rewards(value(cheese), value(water), value(shock), -1, -3);
    }

    private void newMaze(boolean generate) {
        int r = value(rows), c = value(cols);
        Rewards reward = rewards();
        stop();
        maze = generate ? Maze.generate(r, c, new Random()) : new Maze(r, c);
        environment = new Environment(maze, reward);
        agent = new QLearningAgent(new Random());
        trained = 0;
        log.setText("");
        refresh();
        message.setText("Лабиринт создан. Выберите инструмент и кликните по клетке.");
    }

    private void applyRewards() {
        Rewards reward = rewards();
        stop();
        environment = new Environment(maze, reward);
        agent = new QLearningAgent(new Random());
        trained = 0;
        log.setText("");
        refresh();
        message.setText("Подкрепление задано, обучение сброшено.");
    }

    private void editLeft(Position p) {
        if (training.isRunning() || playback.isRunning()) return;
        try {
            CellType selected = (CellType) tool.getSelectedItem();
            CellType current  = maze.at(p);

            if (current == CellType.START || current == CellType.CHEESE) {
                if (selected == CellType.START || selected == CellType.CHEESE) {
                    maze.set(p, selected);
                    afterEdit();
                }
                return;
            }
            if (selected == CellType.START || selected == CellType.CHEESE) {
                maze.set(p, selected);
            } else if (current == selected) {
                maze.set(p, CellType.EMPTY);
            } else {
                maze.set(p, selected);
            }
            afterEdit();
        } catch (IllegalArgumentException e) {
            message.setText(e.getMessage());
        }
    }

    private void editRight(Position p) {
        if (training.isRunning() || playback.isRunning()) return;
        try {
            CellType current = maze.at(p);
            if (current == CellType.START || current == CellType.CHEESE) return;
            maze.set(p, CellType.EMPTY);
            afterEdit();
        } catch (IllegalArgumentException e) {
            message.setText(e.getMessage());
        }
    }

    private void afterEdit() {
        environment.reset();
        agent = new QLearningAgent(new Random());
        trained = 0;
        log.setText("");
        refresh();
        message.setText(maze.hasPath()
                ? "Схема изменена. Обучение сброшено."
                : "Сыр недостижим: откройте проход.");
    }

    private void refresh() {
        stats.setText(String.format(
                "Шаги: %d   |   Сумма: %.1f   |   Вода: %d   |   Эпизоды: %d   |   Q: %d",
                environment.steps(), environment.total(), environment.drinks(),
                trained, agent.stateCount()));
        board.show(maze, environment);
    }

    private void stop() {
        playback.stop();
        if (training.isRunning() && agent != null) agent.resetEpisode();
        training.stop();
    }

    private void reset() {
        stop();
        environment.reset();
        agent.resetEpisode();
        refresh();
        message.setText("Мышь возвращена на старт. Обучение сохранено.");
    }

    private boolean reachable() {
        if (maze.hasPath()) return true;
        message.setText("Сыр недостижим: исправьте схему лабиринта.");
        return false;
    }

    private void manual(Direction direction) {
        if (training.isRunning()) {
            message.setText("Остановите обучение для ручного движения.");
            return;
        }
        playback.stop();
        move(direction);
    }

    private void move(Direction direction) {
        if (environment.finished()) return;
        Environment.Transition t = environment.step(direction);
        agent.learn(t, direction);
        log.append(String.format("%s: (%d,%d) → (%d,%d), подкрепление %+.1f, сумма %.1f%n",
                direction, t.from().row() + 1, t.from().col() + 1,
                t.to().row() + 1, t.to().col() + 1,
                t.reward(), environment.total()));
        if (log.getLineCount() > 500) {
            try { log.replaceRange("", 0, log.getLineEndOffset(0)); }
            catch (javax.swing.text.BadLocationException ignored) { }
        }
        log.setCaretPosition(log.getDocument().getLength());
        refresh();
        if (t.terminal()) {
            playback.stop();
            message.setText("Сыр найден! Итоговый выигрыш: " + environment.total());
        }
    }

    private void startPlayback() {
        if (!reachable()) return;
        reset();
        playback.start();
        message.setText("Мышь идёт по выученной стратегии. «Прервать» останавливает показ.");
    }

    private void oneStep() {
        if (!training.isRunning()) autoStep();
    }

    private void autoStep() {
        if (training.isRunning() || environment.finished()) return;
        move(agent.choose(environment.position(), 0));
        if (environment.steps() >= stepLimit() && !environment.finished()) {
            playback.stop();
            message.setText("Достигнут лимит шагов. Продолжите обучение или измените лабиринт.");
        }
    }

    private int stepLimit() {
        return Math.min(10000, maze.rows() * maze.cols() * 10);
    }

    private void startTraining() {
        if (!reachable()) return;
        int count = value(episodes);
        Rewards reward = rewards();
        stop();
        target = count;
        success = 0;
        episodeActive = false;
        trainingEnvironment = new Environment(maze, reward);
        environment = new Environment(maze, reward);
        agent = new QLearningAgent(new Random());
        trained = 0;
        log.setText("");
        training.start();
        refresh();
    }

    private void trainBatch() {
        long deadline = System.nanoTime() + 12_000_000;
        while (trained < target && System.nanoTime() < deadline) {
            if (!episodeActive) {
                trainingEnvironment.reset();
                agent.resetEpisode();
                episodeSteps = 0;
                episodeActive = true;
            }
            double epsilon = Math.max(.05, .9 * (1.0 - (double) trained / target));
            Direction d = agent.choose(trainingEnvironment.position(), epsilon);
            agent.learn(trainingEnvironment.step(d), d);
            episodeSteps++;
            if (trainingEnvironment.finished() || episodeSteps >= stepLimit()) {
                if (trainingEnvironment.finished()) success++;
                trained++;
                episodeActive = false;
            }
            if (agent.stateCount() > 100000) {
                training.stop();
                agent.resetEpisode();
                message.setText("Лимит 100 000 состояний: уменьшите число клеток воды или размер поля.");
                refresh();
                return;
            }
        }
        refresh();
        message.setText("Обучение: " + trained + " / " + target + ", успешных попыток: " + success);
        if (trained >= target) {
            training.stop();
            agent.resetEpisode();
            message.setText("Обучение завершено. Успешных попыток: " + success + " / " + target
                    + ". Нажмите «Пройти по выученному пути».");
        }
    }
}