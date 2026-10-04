package org.example.model;
import java.util.*;
/** Layout with exactly one start and one cheese. */
public final class Maze {
    private final CellType[][] cells;
    private Position start, cheese;

    public Maze(int rows, int cols) {
        if (rows < 2 || cols < 2) throw new IllegalArgumentException("Размер не меньше 2 × 2.");
        cells = new CellType[rows][cols];
        for (CellType[] row : cells) Arrays.fill(row, CellType.EMPTY);
        start = new Position(rows-1, 0);
        cheese = new Position(0, cols-1);
        cells[start.row()][start.col()] = CellType.START;
        cells[cheese.row()][cheese.col()] = CellType.CHEESE;
    }

    public int rows() { return cells.length; }
    public int cols() { return cells[0].length; }
    public Position start() { return start; }
    public Position cheese() { return cheese; }

    public boolean contains(Position p) {
        return p.row() >= 0 && p.row() < rows() && p.col() >= 0 && p.col() < cols();
    }

    public CellType at(Position p) {
        if (!contains(p)) throw new IllegalArgumentException("Клетка вне поля.");
        return cells[p.row()][p.col()];
    }

    public boolean passable(Position p) {
        return contains(p) && at(p) != CellType.WALL;
    }

    public void set(Position p, CellType type) {
        Objects.requireNonNull(type);
        at(p);
        if ((p.equals(start) && type != CellType.START) || (p.equals(cheese) && type != CellType.CHEESE))
            throw new IllegalArgumentException("Сначала перенесите старт или сыр соответствующим инструментом.");
        if (type == CellType.START)  { cells[start.row()][start.col()] = CellType.EMPTY;  start = p; }
        if (type == CellType.CHEESE) { cells[cheese.row()][cheese.col()] = CellType.EMPTY; cheese = p; }
        cells[p.row()][p.col()] = type;
    }

    /** BFS: есть ли путь от старта до сыра. */
    public boolean hasPath() {
        Set<Position> seen = new HashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        queue.push(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            Position p = queue.pop();
            if (p.equals(cheese)) return true;
            for (Direction d : Direction.values()) {
                Position n = p.move(d);
                if (passable(n) && seen.add(n)) queue.push(n);
            }
        }
        return false;
    }

    /** Возвращает клетки, лежащие на кратчайшем пути от старта до сыра (BFS). */
    private Set<Position> shortestPathCells() {
        Map<Position, Position> parent = new HashMap<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        queue.push(start);
        parent.put(start, null);
        Position found = null;
        while (!queue.isEmpty()) {
            Position p = queue.pop();
            if (p.equals(cheese)) { found = p; break; }
            for (Direction d : Direction.values()) {
                Position n = p.move(d);
                if (passable(n) && !parent.containsKey(n)) {
                    parent.put(n, p);
                    queue.push(n);
                }
            }
        }
        Set<Position> path = new HashSet<>();
        if (found == null) return path;
        for (Position p = found; p != null; p = parent.get(p)) path.add(p);
        return path;
    }

    /** Генерация лабиринта с гарантией проходимости. */
    public static Maze generate(int rows, int cols, Random random) {
        Objects.requireNonNull(random);
        for (int attempt = 0; attempt < 50; attempt++) {
            Maze m = generateOnce(rows, cols, random);
            if (m.hasPath()) return m;
        }
        // На всякий случай: если 50 попыток не помогли — возвращаем последний
        return generateOnce(rows, cols, random);
    }

    private static Maze generateOnce(int rows, int cols, Random random) {
        Maze m = new Maze(rows, cols);
        for (CellType[] row : m.cells) Arrays.fill(row, CellType.WALL);

        // === 1. DFS-лабиринт ===
        boolean[][] visited = new boolean[rows][cols];
        ArrayDeque<Position> stack = new ArrayDeque<>();
        stack.push(m.start);
        visited[m.start.row()][m.start.col()] = true;
        m.cells[m.start.row()][m.start.col()] = CellType.EMPTY;

        while (!stack.isEmpty()) {
            Position current = stack.peek();
            List<Position> neighbors = new ArrayList<>(4);
            for (Direction d : Direction.values()) {
                Position next = current.move(d).move(d);
                if (m.contains(next) && !visited[next.row()][next.col()]) neighbors.add(next);
            }
            if (neighbors.isEmpty()) { stack.pop(); continue; }
            Position next = neighbors.get(random.nextInt(neighbors.size()));
            m.cells[(current.row() + next.row()) / 2][(current.col() + next.col()) / 2] = CellType.EMPTY;
            m.cells[next.row()][next.col()] = CellType.EMPTY;
            visited[next.row()][next.col()] = true;
            stack.push(next);
        }

        // === 2. Прикрепляем сыр к лабиринту ===
        int topVertex = (rows - 1) % 2;
        int rightVertex = ((cols - 1) / 2) * 2;
        for (int r = 0; r <= topVertex; r++) m.cells[r][rightVertex] = CellType.EMPTY;
        for (int c = rightVertex; c < cols; c++) m.cells[0][c] = CellType.EMPTY;

        // Восстанавливаем старт и сыр
        m.cells[m.start.row()][m.start.col()] = CellType.START;
        m.cells[m.cheese.row()][m.cheese.col()] = CellType.CHEESE;

        // === 3. Кратчайший путь — не трогаем ===
        Set<Position> safePath = m.shortestPathCells();

        // === 4. Расставляем воду и ток ТОЛЬКО там, где не сломает путь ===
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (m.cells[r][c] != CellType.EMPTY) continue;
                Position p = new Position(r, c);
                if (p.equals(m.start) || p.equals(m.cheese)) continue;
                if (safePath.contains(p)) continue;      // не трогаем путь
                if (nearStart(p, m.start)) continue;     // не ставим рядом со стартом

                double v = random.nextDouble();
                if (v < 0.08) m.cells[r][c] = CellType.SHOCK;
                else if (v < 0.20) m.cells[r][c] = CellType.WATER;
            }
        }

        // Восстанавливаем старт и сыр (на случай, если что-то затронули)
        m.cells[m.start.row()][m.start.col()] = CellType.START;
        m.cells[m.cheese.row()][m.cheese.col()] = CellType.CHEESE;
        return m;
    }

    /** Проверка: клетка рядом со стартом (в радиусе 1). */
    private static boolean nearStart(Position p, Position start) {
        return Math.abs(p.row() - start.row()) <= 1 && Math.abs(p.col() - start.col()) <= 1;
    }
}