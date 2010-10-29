
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Set;

public abstract class AStarSearch<T extends SearchNode<T>> {

	private PriorityQueue<T> openSet;
	private LinkedList<T>    currentPath;

	public LinkedList<T> findBestPath() {
	
		openSet           = new PriorityQueue<T>();
		currentPath       = new LinkedList<T>();

		while (true) {

			Set<T> nextNodes = expand(currentPath);
	
			for (T node : nextNodes) {
				double g = g(currentPath, node);
				node.setDistanceFromStart(g);
				node.setEstimatedDistance(g + h(node));
			}
	
			openSet.addAll(nextNodes);
	
			T bestNode = openSet.poll();

			// this can only happen if the target nodes are not reachable from
			// the start node
			if (bestNode == null)
				return null;
	
			currentPath = bestNode.getBestPath();
	
			if (reachedTarget(currentPath))
				return currentPath;
		}
	}

	protected abstract double g(LinkedList<T> path, T node);
	protected abstract double h(T node);

	protected abstract Set<T> expand(LinkedList<T> node);

	protected abstract boolean reachedTarget(LinkedList<T> path);
}
