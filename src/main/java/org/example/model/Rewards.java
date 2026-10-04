package org.example.model;

/**
 * Награды (подкрепление) для мыши.
 * Соответствует условию задачи:
 *   +Z — сыр (самый большой наградной сыр, Z > x),
 *   +x — вода (меньшая награда),
 *   −y — электротравма (штраф).
 * Дополнительно: −1 за шаг и −3 за удар в стену — стандартные приёмы Q-learning.
 */
public record Rewards(double cheese, double water, double shock, double step, double wall) {

    public Rewards {
        // Все значения должны быть конечными числами
        if (!Double.isFinite(cheese)) throw new IllegalArgumentException("Сыр +Z должен быть конечным числом.");
        if (!Double.isFinite(water))  throw new IllegalArgumentException("Вода +x должна быть конечным числом.");
        if (!Double.isFinite(shock))  throw new IllegalArgumentException("Электротравма −y должна быть конечным числом.");
        if (!Double.isFinite(step))   throw new IllegalArgumentException("Шаг должен быть конечным числом.");
        if (!Double.isFinite(wall))   throw new IllegalArgumentException("Удар в стену должен быть конечным числом.");

        if (cheese <= 0) throw new IllegalArgumentException("Сыр +Z должен быть > 0.");
        if (water  <= 0) throw new IllegalArgumentException("Вода +x должна быть > 0.");
        if (shock  <= 0) throw new IllegalArgumentException("Электротравма −y должна быть > 0.");

        // «Самый большой наградной сыр» — значит, Z > x и Z > y
        if (cheese <= water) throw new IllegalArgumentException("Сыр +Z должен быть больше воды +x.");
        if (cheese <= shock) throw new IllegalArgumentException("Сыр +Z должен быть больше электротравмы −y.");
    }

    /** Награды по умолчанию — используются в тестах и в MazeFrame. */
    public static Rewards defaults() {
        return new Rewards(100, 10, 20, -1, -3);
    }

    /** Награда за шаг (штраф −1 — стимулирует искать короткий путь). */
    public double step() { return step; }

    /** Штраф за удар в стену (мышь остаётся на месте). */
    public double wall() { return wall; }
}