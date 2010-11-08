
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Set;

public abstract class AStarSearch<P extends LinkedList<N>, N extends SearchNode<P, N>> {

	private PriorityQueue<N> openSet;
	private P currentPath;

	public P findBestPath(P startPath) {
	
		openSet     = new PriorityQueue<N>();
		currentPath = startPath;

		while (true) {

			Set<N> nextNodes = expand(currentPath);

			for (N node : nextNodes) {
				double g = g(currentPath, node);
				node.setDistanceFromStart(g);
				node.setEstimatedDistance(g + h(node));
			}
	
			openSet.addAll(nextNodes);

			N bestNode = openSet.poll();

			// this can only happen if the target nodes are not reachable from
			// the start node
			if (bestNode == null) {
				noMoreOpenNodes(currentPath);
				return null;
			}

			currentPath = bestNode.getBestPath();

			goingTo(bestNode);

			if (reachedTarget(currentPath))
				return currentPath;
		}
	}

	protected abstract double g(P path, N node);
	protected abstract double h(N node);

	protected abstract Set<N> expand(P path);

	// to be overwritten by subclasses
	protected abstract boolean reachedTarget(P path);
	protected void noMoreOpenNodes(P path) {};
	protected void goingTo(N node) {};
}
