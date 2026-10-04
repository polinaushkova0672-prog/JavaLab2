package org.example;
import org.example.model.*;
import org.example.learning.QLearningAgent;
import org.junit.Test;
import java.util.Random;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.Set;
import static org.junit.Assert.*;

public class MazeTest {
    @Test public void dfsProducesConnectedTreeOfCorridorsOnOddEvenAndLargeBoards() {
        for(int[] size:new int[][]{{2,2},{2,9},{9,2},{12,16},{13,17},{200,200}}) {
            Maze m=Maze.generate(size[0],size[1],new Random(123));
            Set<Position> reached=new HashSet<>(); ArrayDeque<Position> stack=new ArrayDeque<>();
            reached.add(m.start()); stack.push(m.start());
            while(!stack.isEmpty()) {
                Position p=stack.pop();
                for(Direction d:Direction.values()) { Position n=p.move(d); if(m.passable(n)&&reached.add(n)) stack.push(n); }
            }
            int vertices=0,edges=0;
            for(int r=0;r<m.rows();r++) for(int c=0;c<m.cols();c++) {
                Position p=new Position(r,c); if(!m.passable(p)) continue;
                vertices++;
                if(m.passable(p.move(Direction.RIGHT))) edges++;
                if(m.passable(p.move(Direction.DOWN))) edges++;
            }
            assertEquals("All carved cells must be connected",vertices,reached.size());
            assertEquals("DFS must carve a tree without cycles",vertices-1,edges);
        }
    }
    @Test public void qLearningUsesBellmanUpdateAndZeroFutureForTerminalStates() {
        QLearningAgent agent=new QLearningAgent(new Random(123));
        Position s=new Position(1,0),next=new Position(0,0),goal=new Position(0,1);
        agent.learn(new Environment.Transition(next,goal,100,true,false),Direction.RIGHT);
        assertEquals(25,agent.qValue(next,Direction.RIGHT),1e-10);
        agent.learn(new Environment.Transition(s,next,-1,false,false),Direction.UP);
        double expected=.25*(-1+.95*25);
        assertEquals(expected,agent.qValue(s,Direction.UP),1e-10);
        agent.learn(new Environment.Transition(s,next,-1,false,false),Direction.UP);
        assertEquals(expected+.25*(-1+.95*25-expected),agent.qValue(s,Direction.UP),1e-10);
        agent.learn(new Environment.Transition(goal,s,100,true,false),Direction.DOWN);
        assertEquals(25,agent.qValue(goal,Direction.DOWN),1e-10);
    }
    @Test public void emptyBoardAlsoStartsAtBottomWithCheeseAtTop() {
        Maze m=new Maze(12,16); Environment e=new Environment(m,Rewards.defaults());
        assertEquals(new Position(11,0),e.position()); assertEquals(new Position(0,15),m.cheese());
        e.step(Direction.UP); e.reset(); assertEquals(m.start(),e.position());
    }
    @Test public void generatedLayoutsAlwaysHaveRoute() {
        for(int rows:new int[]{2,3,12,31,200}) for(int cols:new int[]{2,4,16,41}) for(int seed=0;seed<5;seed++) {
            Maze m=Maze.generate(rows,cols,new Random(seed));
            assertEquals(rows,m.rows()); assertEquals(cols,m.cols()); assertTrue(m.hasPath());
            assertEquals(CellType.START,m.at(m.start())); assertEquals(CellType.CHEESE,m.at(m.cheese()));
            assertEquals(new Position(rows-1,0),m.start()); assertEquals(new Position(0,cols-1),m.cheese());
        }
    }
    @Test public void editMovesUniqueMarkersAndDetectsBlockedRoute() {
        Maze m=new Maze(3,3); m.set(new Position(1,0),CellType.START);
        assertEquals(CellType.EMPTY,m.at(new Position(2,0)));
        m.set(new Position(2,2),CellType.CHEESE); assertEquals(CellType.EMPTY,m.at(new Position(0,2)));
        for(int r=0;r<3;r++) m.set(new Position(r,1),CellType.WALL);
        assertFalse(m.hasPath()); m.set(new Position(1,1),CellType.EMPTY); assertTrue(m.hasPath());
    }
    @Test public void waterIsConsumedOnceAndResetRestoresIt() {
        Maze m=new Maze(2,3); m.set(new Position(1,1),CellType.WATER);
        Environment e=new Environment(m,Rewards.defaults());
        assertEquals(10,e.step(Direction.RIGHT).reward(),0); e.step(Direction.LEFT);
        assertEquals(-1,e.step(Direction.RIGHT).reward(),0); assertEquals(1,e.drinks()); assertEquals(8,e.total(),0);
        e.reset(); assertEquals(0,e.steps()); assertFalse(e.consumed(new Position(1,1)));
        assertEquals(10,e.step(Direction.RIGHT).reward(),0);
    }
    @Test public void wallsBoundariesShockCheeseAndTerminalAccounting() {
        Maze m=new Maze(2,3); m.set(new Position(1,1),CellType.WALL); m.set(new Position(0,0),CellType.SHOCK);
        Environment e=new Environment(m,Rewards.defaults());
        assertEquals(-3,e.step(Direction.DOWN).reward(),0); assertEquals(m.start(),e.position());
        assertEquals(-3,e.step(Direction.RIGHT).reward(),0);
        assertEquals(-20,e.step(Direction.UP).reward(),0); e.step(Direction.RIGHT);
        assertTrue(e.step(Direction.RIGHT).terminal()); assertEquals(73,e.total(),0);
        int steps=e.steps(); assertEquals(0,e.step(Direction.LEFT).reward(),0); assertEquals(steps,e.steps());
    }
    @Test(expected=IllegalArgumentException.class) public void cannotEraseStart() { Maze m=new Maze(2,2); m.set(m.start(),CellType.WALL); }
    @Test(expected=IllegalArgumentException.class) public void cannotMergeStartAndCheese() { Maze m=new Maze(2,2); m.set(m.cheese(),CellType.START); }
    @Test(expected=IllegalArgumentException.class) public void rejectSmallDimensions() { new Maze(1,5); }
    @Test(expected=IllegalArgumentException.class) public void rejectInvalidRewards() { new Rewards(10,20,5,-1,-3); }
    @Test(expected=IllegalArgumentException.class) public void rejectNonFiniteRewards() { new Rewards(Double.NaN,2,5,-1,-3); }
    @Test public void learnerFindsCheeseAndAvoidsShock() {
        Maze m=new Maze(2,3); m.set(new Position(0,0),CellType.SHOCK); m.set(new Position(1,1),CellType.WATER);
        Environment e=new Environment(m,Rewards.defaults()); QLearningAgent agent=new QLearningAgent(new Random(42));
        for(int episode=0;episode<1500;episode++) {
            e.reset(); agent.resetEpisode();
            for(int step=0;step<60&&!e.finished();step++) { Direction d=agent.choose(e.position(),.25); agent.learn(e.step(d),d); }
        }
        e.reset(); agent.resetEpisode();
        for(int step=0;step<10&&!e.finished();step++) { Direction d=agent.choose(e.position(),0); agent.learn(e.step(d),d); }
        assertTrue(e.finished()); assertEquals(1,e.drinks()); assertEquals(109,e.total(),0);
    }
}
