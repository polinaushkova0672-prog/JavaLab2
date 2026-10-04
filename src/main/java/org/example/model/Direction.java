package org.example.model;
public enum Direction {
    UP(-1,0), RIGHT(0,1), DOWN(1,0), LEFT(0,-1);
    public final int dr, dc;
    Direction(int dr, int dc) { this.dr = dr; this.dc = dc; }
}
