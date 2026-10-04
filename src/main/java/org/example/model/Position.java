package org.example.model;
public record Position(int row, int col) {
    public Position move(Direction d) { return new Position(row + d.dr, col + d.dc); }
}
