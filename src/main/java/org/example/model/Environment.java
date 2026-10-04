package org.example.model;
import java.util.*;
/** Feedback and episode statistics. Water can be consumed once per episode. */
public final class Environment {
    public record Transition(Position from,Position to,double reward,boolean terminal,boolean drankWater) { }
    private final Maze maze; private final Rewards rewards;
    private final Set<Position> consumed=new HashSet<>();
    private Position position; private double total; private int steps,drinks;
    public Environment(Maze maze,Rewards rewards) { this.maze=maze; this.rewards=rewards; reset(); }
    public void reset() { position=maze.start(); total=0; steps=0; drinks=0; consumed.clear(); }
    public Transition step(Direction d) {
        Position from=position;
        if(finished()) return new Transition(from,from,0,true,false);
        Position next=from.move(d); double reward=rewards.step(); boolean drank=false;
        if(!maze.passable(next)) { next=from; reward=rewards.wall(); }
        else switch(maze.at(next)) {
            case CHEESE -> reward=rewards.cheese();
            case SHOCK -> reward=-rewards.shock();
            case WATER -> { if(consumed.add(next)) { reward=rewards.water(); drank=true; drinks++; } }
            default -> { }
        }
        position=next; total+=reward; steps++;
        return new Transition(from,next,reward,finished(),drank);
    }
    public Position position() { return position; }
    public double total() { return total; }
    public int steps() { return steps; }
    public int drinks() { return drinks; }
    public boolean finished() { return position.equals(maze.cheese()); }
    public boolean consumed(Position p) { return consumed.contains(p); }
}
