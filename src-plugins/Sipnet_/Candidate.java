
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

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
	private HashMap<Candidate, Double> negLogPAppearances;
	// all candidates of which this one is a most likely candidate
	private Vector<Candidate>          mostSimilarOf;

	// vector of merge nodes that point to this candidate
	private Vector<Candidate>          mergePartnerOf;

	// closest candidates in x-y of same slice
	private Vector<Candidate> neighbors;
	private Vector<Double>    neighborDistances;
	private Vector<double[]>  neighborOffsets;
	private Vector<Integer>   neighborIndices;

	// the pixels belonging to this candidate
	private int[][] pixels;

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

			double d1 = sourceCandidate.distanceTo(region1);
			double d2 = sourceCandidate.distanceTo(region2);

			if (d1 < d2)
				return -1;
			else if (d1 > d2)
				return 1;
			else
				return 0;
		}
	}

	public Candidate() {
	
		super(0, 0, new double[]{0.0, 0.0}, candidateFactory);

		this.mostSimilarCandidates = new Vector<Candidate>(AssignmentSearch.MaxTargetCandidates);
		this.negLogPAppearances    = new HashMap<Candidate, Double>(AssignmentSearch.MaxTargetCandidates);
		this.mostSimilarOf         = new Vector<Candidate>(AssignmentSearch.MaxTargetCandidates);
		this.mergePartnerOf        = new Vector<Candidate>(AssignmentSearch.MaxTargetCandidates);

		this.pixels = new int[0][0];
	}

	public Candidate(int size, int perimeter, double[] center, int[][] pixels) {

		super(size, perimeter, center, candidateFactory);

		this.mostSimilarCandidates = new Vector<Candidate>(AssignmentSearch.MaxTargetCandidates);
		this.negLogPAppearances    = new HashMap<Candidate, Double>(AssignmentSearch.MaxTargetCandidates);
		this.mostSimilarOf         = new Vector<Candidate>(AssignmentSearch.MaxTargetCandidates);
		this.mergePartnerOf        = new Vector<Candidate>(AssignmentSearch.MaxTargetCandidates);

		this.pixels = pixels;
	}

	public void cacheMostSimilarCandidates(Vector<Candidate> targetCandidates) {

		// sort all candidates according to appearance likelihood
		PriorityQueue<Candidate> sortedCandidates =
			new PriorityQueue<Candidate>(AssignmentSearch.MaxTargetCandidates, new LikelihoodComparator(this));
		sortedCandidates.addAll(targetCandidates);

		// cache most likely candidates
		while (mostSimilarCandidates.size() < AssignmentSearch.MaxTargetCandidates &&
		       sortedCandidates.peek() != null) {

			double negLogP = AssignmentModel.negLogPAppearance(this, sortedCandidates.peek());

			if (negLogP <= AssignmentSearch.MaxNegLogPAssignment) {

				mostSimilarCandidates.add(sortedCandidates.peek());
				// tell this candidate about us
				sortedCandidates.peek().addMostLikelyOf(this);
				negLogPAppearances.put(sortedCandidates.poll(), negLogP);
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

	public void addMostLikelyOf(Candidate candidate) {

		mostSimilarOf.add(candidate);
	}

	public void addMergeParnerOf(Candidate candidate) {

		mergePartnerOf.add(candidate);
	}

	public Vector<Candidate> mergePartnerOf() {

		return mergePartnerOf;
	}

	public void findNeighbors(Vector<Candidate> candidates) {

		neighbors         = new Vector<Candidate>(AssignmentSearch.NumNeighbors);
		neighborDistances = new Vector<Double>(AssignmentSearch.NumNeighbors);
		neighborOffsets   = new Vector<double[]>(AssignmentSearch.NumNeighbors);
		neighborIndices   = new Vector<Integer>(AssignmentSearch.NumNeighbors);

		PriorityQueue<Candidate> sortedNeighbors =
			new PriorityQueue<Candidate>(AssignmentSearch.NumNeighbors, new DistanceComparator(this));
		sortedNeighbors.addAll(candidates);

		while (neighbors.size() < AssignmentSearch.NumNeighbors && sortedNeighbors.peek() != null) {

			Candidate neighbor = sortedNeighbors.poll();

			// don't consider yourself as a neighbor
			if (neighbor == this)
				continue;

			double   distance = distanceTo(neighbor);
			double[] offset   = offsetTo(neighbor);

			neighbors.add(neighbor);
			neighborDistances.add(distance);
			neighborOffsets.add(offset);
		}

		// store indices of closest neighbors (used to decide whether all
		// neighbors have been assigned already during the search)

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

	public Vector<Double> getNeighborDistances() {

		return neighborDistances;
	}

	public Vector<double[]> getNeighborOffsets() {

		return neighborOffsets;
	}

	public Vector<Candidate> getMostLikelyCandidates() {

		return mostSimilarCandidates;
	}

	public Vector<Candidate> getMostLikelyOf() {

		return mostSimilarOf;
	}

	public double getNegLogPAppearance(Candidate candidate) {

		return negLogPAppearances.get(candidate);
	}

	public double distanceTo(Candidate candidate) {

		double diffx = getCenter()[0] - candidate.getCenter()[0];
		double diffy = getCenter()[1] - candidate.getCenter()[1];

		return Math.sqrt(diffx*diffx + diffy*diffy);
	}

	public double[] offsetTo(Candidate candidate) {

		double[] offset = new double[2];

		offset[0] = candidate.getCenter()[0] - getCenter()[0];
		offset[1] = candidate.getCenter()[1] - getCenter()[1];

		return offset;
	}

	public int[][] getPixels() {

		return pixels;
	}

	public void writeExternal(ObjectOutput out) throws IOException {

		out.writeInt(pixels.length);
		if (pixels.length > 0)
			out.writeInt(pixels[0].length);

		for (int i = 0; i < pixels.length; i++)
			for (int d = 0; d < pixels[i].length; d++)
				out.writeInt(pixels[i][d]);

		super.writeExternal(out);
	}

	public void readExternal(ObjectInput in) throws IOException {

		int numPixels = in.readInt();

		if (numPixels > 0) {
			int numDimensions = in.readInt();

			pixels = new int[numPixels][numDimensions];

			for (int i = 0; i < numPixels; i++)
				for (int d = 0; d < numDimensions; d++)
					pixels[i][d] = in.readInt();
		} else 
			pixels = new int[0][0];

		super.readExternal(in);
	}

	public String toString() {

		String ret = "Candidate " + getId() + ",";

		for (int d = 0; d < getCenter().length; d++)
			ret += " " + (int)getCenter()[d];

		ret += ", size: " + getSize();
		ret += ", perimeter: " + getPerimeter();

		return ret;
	}
}
