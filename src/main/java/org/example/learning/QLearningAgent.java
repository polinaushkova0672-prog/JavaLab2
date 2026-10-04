package org.example.learning;
import org.example.model.*;
import java.util.*;
/** Q-learning state includes consumed water to avoid endlessly farming its reward. */
public final class QLearningAgent {
    private static final double ALPHA=.25;
    private static final double GAMMA=.95;
    private record State(Position position,Set<Position> consumed) { }
    private final Map<State,double[]> table=new HashMap<>();
    private final Random random; private final Set<Position> consumed=new HashSet<>();
    public QLearningAgent(Random random) { this.random=random; }
    public void resetEpisode() { consumed.clear(); }
    private State state(Position p) { return new State(p,Set.copyOf(consumed)); }
    private double[] values(State s) { return table.computeIfAbsent(s,k->new double[4]); }
    public Direction choose(Position p,double epsilon) {
        if(random.nextDouble()<epsilon) return Direction.values()[random.nextInt(4)];
        double[] q=values(state(p)); double best=Arrays.stream(q).max().orElse(0);
        int[] ties=new int[4]; int count=0;
        for(int i=0;i<4;i++) if(q[i]==best) ties[count++]=i;
        return Direction.values()[ties[random.nextInt(count)]];
    }
    public void learn(Environment.Transition t,Direction action) {
        double[] previous=values(state(t.from()));
        if(t.drankWater()) consumed.add(t.to());
        double future=t.terminal()?0:Arrays.stream(values(state(t.to()))).max().orElse(0);
        // Slide 9: Qnew(s,a) = Qold(s,a) + alpha * (reward + gamma * max Q(s',a') - Qold(s,a)).
        // Cheese is terminal: its future reward is zero.
        int a=action.ordinal(); previous[a]+=ALPHA*(t.reward()+GAMMA*future-previous[a]);
    }
    /** Current estimate, useful for inspecting the learner's table. */
    public double qValue(Position p,Direction action) {
        double[] q=table.get(state(p));
        return q==null?0:q[action.ordinal()];
    }
    public int stateCount() { return table.size(); }
}
