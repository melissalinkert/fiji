
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Set;

import ij.IJ;

public abstract class AStarSearch<P extends LinkedList<N>, N extends SearchNode<P, N>> {

	private PriorityQueue<N> openSet;
	private P currentPath;

	public P findBestPath(P startPath) {
	
		openSet     = new PriorityQueue<N>();
		currentPath = startPath;

		while (true) {

			IJ.log("processing node " + currentPath.peek());

			Set<N> nextNodes = expand(currentPath);

			IJ.log("" + nextNodes.size() + " next possible");
	
			for (N node : nextNodes) {
				double g = g(currentPath, node);
				node.setDistanceFromStart(g);
				node.setEstimatedDistance(g + h(node));
			}
	
			openSet.addAll(nextNodes);

			IJ.log("in total, " + openSet.size() + " open nodes");
	
			N bestNode = openSet.poll();

			// this can only happen if the target nodes are not reachable from
			// the start node
			if (bestNode == null)
				return null;
	
			currentPath = bestNode.getBestPath();

			if (reachedTarget(currentPath)) {
				IJ.log("Found best path.");
				return currentPath;
			}
		}
	}

	protected abstract double g(P path, N node);
	protected abstract double h(N node);

	protected abstract Set<N> expand(P path);

	protected abstract boolean reachedTarget(P path);
}
