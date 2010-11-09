
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Vector;

import ij.IJ;

public class Candidate extends Region<Candidate> {

	private static CandidateFactory candidateFactory = new CandidateFactory();

	// most similar candidates (to position and appearance) to this candidate
	// in the next slice and their assignment probabilities
	private Vector<Candidate>          mostSimilarCandidates;
	private HashMap<Candidate, Double> negLogPAppearance;

	// closest candidates in x-y of same slice
	private Vector<Candidate>          neighbors;
	private HashMap<Candidate, Double> neighborDistances;
	private Vector<Integer>            neighborIndices;
	private double                     meanNeighborDistance;

	private class LikelihoodComparator implements Comparator<Candidate> {

		private Candidate sourceCandidate;

		public LikelihoodComparator(Candidate sourceCandidate) {
			this.sourceCandidate = sourceCandidate;
		}

		public int compare(Candidate region1, Candidate region2) {

			double p1 = AssignmentModel.negLogPAppearance(sourceCandidate, region1);
			double p2 = AssignmentModel.negLogPAppearance(sourceCandidate, region2);

			if (p1 < p2)
				return -1;
			else if (p1 > p2)
				return 1;
			else
				return 0;
		}
	}

	private class DistanceComparator implements Comparator<Candidate> {

		private Candidate sourceCandidate;

		public DistanceComparator(Candidate sourceCandidate) {
			this.sourceCandidate = sourceCandidate;
		}

		public int compare(Candidate region1, Candidate region2) {

			double d1 = sourceCandidate.distance2To(region1);
			double d2 = sourceCandidate.distance2To(region2);

			if (d1 < d2)
				return -1;
			else if (d1 > d2)
				return 1;
			else
				return 0;
		}
	}
	
	public Candidate(int size, double[] center) {

		super(size, center, candidateFactory);

		this.mostSimilarCandidates = new Vector<Candidate>(AssignmentSearch.MaxTargetCandidates);
		this.negLogPAppearance     = new HashMap<Candidate, Double>(AssignmentSearch.MaxTargetCandidates);
		this.neighbors             = new Vector<Candidate>(AssignmentSearch.NumNeighbors);
		this.neighborDistances     = new HashMap<Candidate, Double>(AssignmentSearch.NumNeighbors);
		this.neighborIndices       = new Vector<Integer>(AssignmentSearch.NumNeighbors);
	}

	public void cacheMostSimilarCandidates(Vector<Candidate> targetCandidates) {

		// sort all candidates according to appearance likelihood
		PriorityQueue<Candidate> sortedCandidates =
		    new PriorityQueue<Candidate>(AssignmentSearch.MaxTargetCandidates, new LikelihoodComparator(this));
		sortedCandidates.addAll(targetCandidates);

		// cache most likely candidates
		while (mostSimilarCandidates.size() < AssignmentSearch.MaxTargetCandidates) {

			double negLogP = AssignmentModel.negLogPAppearance(this, sortedCandidates.peek());

			if (negLogP <= AssignmentSearch.MaxNegLogPAssignment) {

				mostSimilarCandidates.add(sortedCandidates.peek());
				negLogPAppearance.put(sortedCandidates.poll(), negLogP);
			} else
				break;
		}

		if (mostSimilarCandidates.size() < AssignmentSearch.MinTargetCandidates) {
			IJ.log("Oh no! For region " + this + " there are less than " +
			       AssignmentSearch.MinTargetCandidates + " within the threshold of " +
			       AssignmentSearch.MaxNegLogPAssignment);
			IJ.log("Closest non-selected candidate distance: " + AssignmentModel.negLogPAppearance(this, sortedCandidates.peek()));
		}
	}

	public void findNeighbors(Vector<Candidate> candidates) {

		meanNeighborDistance = 0.0;

		PriorityQueue<Candidate> sortedNeighbors =
		    new PriorityQueue<Candidate>(AssignmentSearch.NumNeighbors, new DistanceComparator(this));
		sortedNeighbors.addAll(candidates);

		while (neighbors.size() < AssignmentSearch.NumNeighbors && sortedNeighbors.peek() != null) {

			double distance = distance2To(sortedNeighbors.peek());

			// don't consider yourself as a neighbor
			if (distance == 0) {
				sortedNeighbors.poll();
				continue;
			}

			neighbors.add(sortedNeighbors.peek());
			neighborDistances.put(sortedNeighbors.poll(), distance);

			meanNeighborDistance += distance;
		}

		meanNeighborDistance /= neighbors.size();

		// store indices of closest neighbors (used to decide whether all
		// neighbors have been assigned already during the search)

		neighborIndices = new Vector<Integer>(neighbors.size());

		for (Candidate neighbor : neighbors)
			for (int i = 0; i < candidates.size(); i++)
				if (neighbor == candidates.get(i)) {
					neighborIndices.add(i);
					break;
				}
	}

	public Vector<Candidate> getNeighbors() {

		return neighbors;
	}

	/**
	 * @return The indices of this candidate's neighbors in the vector that was
	 * used to find the neighbors.
	 *
	 * Used for optimization that relies on the order of candidates in the
	 * source slice.
	 */
	public Vector<Integer> getNeighborIndices() {

		return neighborIndices;
	}

	public Vector<Candidate> getMostLikelyCandidates() {

		return mostSimilarCandidates;
	}

	public double getNegLogPAppearance(Candidate candidate) {

		return negLogPAppearance.get(candidate);
	}

	public double getMeanNeighborDistance() {

		return meanNeighborDistance;
	}

	public double distance2To(Candidate candidate) {

		return (getCenter()[0] - candidate.getCenter()[0])*
		       (getCenter()[0] - candidate.getCenter()[0]) +
		       (getCenter()[1] - candidate.getCenter()[1])*
		       (getCenter()[1] - candidate.getCenter()[1]);
	}

	public String toString() {

		String ret = "Candidate " + getId() + ",";

		for (int d = 0; d < getCenter().length; d++)
			ret += " " + (int)getCenter()[d];

		ret += ", size: " + getSize();

		return ret;
	}
}
