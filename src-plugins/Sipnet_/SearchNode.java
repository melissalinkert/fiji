
import java.util.LinkedList;

public abstract class SearchNode<T> implements Comparable<SearchNode<T>> {

	// the best path to this node
	private LinkedList<T> bestPath;
	// the length of the best path
	private double distanceFromStart;
	// the estimated remaining distance
	private double estimatedDistance;

	public LinkedList<T> getBestPath()
	{
		return this.bestPath;
	}

	public void setBestPath(LinkedList<T> bestPath)
	{
		this.bestPath = bestPath;
	}

	public double getDistanceFromStart()
	{
		return this.distanceFromStart;
	}

	public void setDistanceFromStart(double distanceFromStart)
	{
		this.distanceFromStart = distanceFromStart;
	}

	public final double getEstimatedDistance() {
		return estimatedDistance;
	}

	public final void setEstimatedDistance(double estimatedDistance) {
		this.estimatedDistance = estimatedDistance;
	}

	public final int compareTo(SearchNode<T> other) {

		if (this.getEstimatedDistance() < other.getEstimatedDistance())
			return -1;
		else if (this.getEstimatedDistance() > other.getEstimatedDistance())
			return 1;

		return 0;
	}

}
