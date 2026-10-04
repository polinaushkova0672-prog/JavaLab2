package org.example.model;
public enum CellType {
    EMPTY("Проход"), WALL("Стена"), WATER("Вода"), SHOCK("Электричество"), START("Старт"), CHEESE("Сыр");
    private final String title;
    CellType(String title) { this.title = title; }
    @Override public String toString() { return title; }
}
