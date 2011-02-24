package sipnet;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;

import mser.Region;

import Jama.Matrix;

import ij.IJ;

public class Candidate extends Region<Candidate> {

	private static CandidateFactory candidateFactory = new CandidateFactory();

	// most similar candidates (to position and appearance) to this candidate
	// in the next slice and their assignment probabilities
	private Vector<Candidate>          mostSimilarCandidates;
	private HashMap<Candidate, Double> negLogPAppearances;
	// all candidates of which this one is a most likely candidate
	private Vector<Candidate>          mostSimilarOf;

	// vector of merge/split nodes that point to this candidate
	private Vector<Candidate>          mergeTargetOf;
	private Vector<Candidate>          splitSourceOf;

	// closest candidates in x-y of same slice
	private Vector<Candidate> neighbors;
	private Vector<Double>    neighborDistances;
	private Vector<double[]>  neighborOffsets;
	private Vector<Integer>   neighborIndices;

	// all potential merge/split partners and the corresponding targets/sources
	private HashMap<Candidate, Vector<Candidate>> mergeTargets;
	private HashMap<Candidate, Vector<Candidate>> splitSources;

	// the pixels belonging to this candidate
	private List<int[]> pixels;
	// the covariance of the pixel positions
	private Matrix  covariance;
	// the mean gray value of the pixels belonging to this candidate
	private double  meanGrayValue;

	private class LikelihoodComparator implements Comparator<Candidate> {

		private Candidate       sourceCandidate;
		private AssignmentModel assignmentModel;

		public LikelihoodComparator(Candidate sourceCandidate, AssignmentModel assignmentModel) {

			this.sourceCandidate = sourceCandidate;
			this.assignmentModel = assignmentModel;
		}

		public int compare(Candidate region1, Candidate region2) {

			double p1 = assignmentModel.costContinuation(sourceCandidate, region1, false);
			double p2 = assignmentModel.costContinuation(sourceCandidate, region2, false);

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

		clearCaches();

		this.pixels = new ArrayList<int[]>();
	}

	public Candidate(int size, int perimeter, double[] center, Vector<int[]> pixels, double meanGrayValue) {

		super(size, perimeter, center, candidateFactory);

		clearCaches();

		this.pixels        = new ArrayList<int[]>(pixels);
		this.meanGrayValue = meanGrayValue;

		Collections.sort(this.pixels, new PixelComparator());

		computePixelCovariance();
	}

	public void cacheMostSimilarCandidates(Vector<Candidate> targetCandidates, AssignmentModel assignmentModel) {

		// get closest candidates in next slice
		PriorityQueue<Candidate> closestCandidates =
			new PriorityQueue<Candidate>(SequenceSearch.MaxTargetCandidates, new DistanceComparator(this));
		closestCandidates.addAll(targetCandidates);

		// sort all candidates according to appearance likelihood
		PriorityQueue<Candidate> sortedCandidates =
			new PriorityQueue<Candidate>(SequenceSearch.MaxTargetCandidates, new LikelihoodComparator(this, assignmentModel));

		for (int i = 0; i < SequenceSearch.NumDistanceCandidates && closestCandidates.peek() != null; i++)
			sortedCandidates.add(closestCandidates.poll());

		// cache most likely candidates
		while (mostSimilarCandidates.size() < SequenceSearch.MaxTargetCandidates &&
		       sortedCandidates.peek() != null) {

			double negLogP = assignmentModel.costContinuation(this, sortedCandidates.peek(), false);

			if (negLogP <= SequenceSearch.MaxNegLogPAppearance) {

				mostSimilarCandidates.add(sortedCandidates.peek());
				// tell this candidate about us
				sortedCandidates.peek().addMostLikelyOf(this);
				negLogPAppearances.put(sortedCandidates.poll(), negLogP);
			} else
				break;
		}

		if (mostSimilarCandidates.size() < SequenceSearch.MinTargetCandidates) {
			IJ.log("Oh no! For region " + this + " there are less than " +
				   SequenceSearch.MinTargetCandidates + " within the threshold of " +
				   SequenceSearch.MaxNegLogPAppearance);
			IJ.log("Closest non-selected candidate distance: " + assignmentModel.costContinuation(this, sortedCandidates.peek(), false));
		}
	}

	public void addMostLikelyOf(Candidate candidate) {

		mostSimilarOf.add(candidate);
	}

	public void addMergePartner(Candidate partner, Vector<Candidate> targets) {

		mergeTargets.put(partner, targets);
	}

	public void addSplitPartner(Candidate partner, Vector<Candidate> sources) {

		splitSources.put(partner, sources);
	}

	public HashMap<Candidate, Vector<Candidate>> mergeTargets() {

		return mergeTargets;
	}

	public HashMap<Candidate, Vector<Candidate>> splitSources() {

		return splitSources;
	}

	public void addMergeTargetOf(Candidate candidate) {

		mergeTargetOf.add(candidate);
	}

	public void addSplitSourceOf(Candidate candidate) {

		splitSourceOf.add(candidate);
	}

	public Vector<Candidate> mergeTargetOf() {

		return mergeTargetOf;
	}

	public Vector<Candidate> splitSourceOf() {

		return splitSourceOf;
	}

	public void findNeighbors(Vector<Candidate> candidates) {

		neighbors         = new Vector<Candidate>(SequenceSearch.NumNeighbors);
		neighborDistances = new Vector<Double>(SequenceSearch.NumNeighbors);
		neighborOffsets   = new Vector<double[]>(SequenceSearch.NumNeighbors);
		neighborIndices   = new Vector<Integer>(SequenceSearch.NumNeighbors);

		PriorityQueue<Candidate> sortedNeighbors =
			new PriorityQueue<Candidate>(SequenceSearch.NumNeighbors, new DistanceComparator(this));
		sortedNeighbors.addAll(candidates);

		while (neighbors.size() < SequenceSearch.NumNeighbors && sortedNeighbors.peek() != null) {

			Candidate neighbor = sortedNeighbors.poll();

			// don't consider neighbors that represent a concurring hypothesis
			boolean validNeighbor = true;
			for (Candidate tmp = neighbor; tmp != null; tmp = tmp.getParent())
				if (tmp == this) {
					validNeighbor = false;
					break;
				}
			if (!validNeighbor)
				continue;
			for (Candidate tmp = this; tmp != null; tmp = tmp.getParent())
				if (tmp == neighbor) {
					validNeighbor = false;
					break;
				}
			if (!validNeighbor)
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

		double diffx = getCenter(0) - candidate.getCenter(0);
		double diffy = getCenter(1) - candidate.getCenter(1);

		return Math.sqrt(diffx*diffx + diffy*diffy);
	}

	public double[] offsetTo(Candidate candidate) {

		double[] offset = new double[2];

		offset[0] = candidate.getCenter(0) - getCenter(0);
		offset[1] = candidate.getCenter(1) - getCenter(1);

		return offset;
	}

	public List<int[]> getPixels() {

		return pixels;
	}

	public Matrix getCovariance() {

		return covariance;
	}

	public void setMeanGrayValue(double value) {

		meanGrayValue = value;
	}

	public double getMeanGrayValue() {

		return meanGrayValue;
	}

	public void writeExternal(ObjectOutput out) throws IOException {

		out.writeInt(pixels.size());
		if (pixels.size() > 0)
			out.writeInt(pixels.get(0).length);

		for (int i = 0; i < pixels.size(); i++)
			for (int d = 0; d < pixels.get(i).length; d++)
				out.writeInt(pixels.get(i)[d]);

		out.writeDouble(meanGrayValue);

		super.writeExternal(out);
	}

	public void readExternal(ObjectInput in) throws IOException {

		int numPixels = in.readInt();

		if (numPixels > 0) {
			int numDimensions = in.readInt();

			pixels = new ArrayList<int[]>(numPixels);

			for (int i = 0; i < numPixels; i++) {
				pixels.add(new int[numDimensions]);
				for (int d = 0; d < numDimensions; d++)
					pixels.get(i)[d] = in.readInt();
			}

		} else 
			pixels = new ArrayList<int[]>();

		meanGrayValue = in.readDouble();

		super.readExternal(in);

		computePixelCovariance();
	}

	public void clearCaches() {

		mostSimilarCandidates = new Vector<Candidate>(SequenceSearch.MaxTargetCandidates);
		negLogPAppearances    = new HashMap<Candidate, Double>(SequenceSearch.MaxTargetCandidates);
		mostSimilarOf         = new Vector<Candidate>(SequenceSearch.MaxTargetCandidates);
		mergeTargetOf         = new Vector<Candidate>(SequenceSearch.MaxTargetCandidates);
		mergeTargets          = new HashMap<Candidate, Vector<Candidate>>();
		splitSourceOf         = new Vector<Candidate>(SequenceSearch.MaxTargetCandidates);
		splitSources          = new HashMap<Candidate, Vector<Candidate>>();
	}

	public String toString() {

		String ret = "Candidate " + getId() + ",";

		for (int d = 0; d < getCenter().length; d++)
			ret += " " + (int)getCenter(d);

		ret += ", size: " + getSize();
		ret += ", perimeter: " + getPerimeter();

		return ret;
	}

	private void computePixelCovariance() {

		covariance = new Matrix(2, 2);
		covariance.set(0, 0, 1.0e-10);
		covariance.set(1, 1, 1.0e-10);

		if (pixels.size() <= 1)
			return;

		for (int[] pixel : pixels) {

			double[] p = {
					(double)pixel[0] - getCenter(0),
					(double)pixel[1] - getCenter(1)};

			Matrix  mp = new Matrix(p, 1);

			covariance = covariance.plus(mp.transpose().times(mp));
		}

		covariance = covariance.times(1.0/pixels.size());
	}
}
