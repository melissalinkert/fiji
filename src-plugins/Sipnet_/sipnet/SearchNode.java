package sipnet;

public abstract class SearchNode<P, N> implements Comparable<SearchNode<P, N>> {

	// the best path to this node
	private P bestPath;
	// the length of the best path
	private double distanceFromStart;
	// the estimated remaining distance
	private double estimatedDistance;

	public P getBestPath()
	{
		return this.bestPath;
	}

	public void setBestPath(P bestPath)
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

	public final int compareTo(SearchNode<P, N> other) {

		if (this.getEstimatedDistance() < other.getEstimatedDistance())
			return -1;
		else if (this.getEstimatedDistance() > other.getEstimatedDistance())
			return 1;

		return 0;
	}

}
