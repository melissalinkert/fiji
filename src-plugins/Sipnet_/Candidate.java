
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class Candidate extends Region<Candidate> {

	private static CandidateFactory candidateFactory = new CandidateFactory();

	// closest candidates to this candidate and their assignment probabilities
	private Vector<Candidate>          closestCandidates;
	private HashMap<Candidate, Double> negLogPAssignment;

	private class CandidateComparator implements Comparator<Candidate> {

		private Candidate sourceCandidate;

		public CandidateComparator(Candidate sourceCandidate) {
			this.sourceCandidate = sourceCandidate;
		}

		public int compare(Candidate region1, Candidate region2) {

			double p1 = AssignmentModel.negLogP(sourceCandidate, region1);
			double p2 = AssignmentModel.negLogP(sourceCandidate, region2);

			if (p1 < p2)
				return -1;
			else if (p1 > p2)
				return 1;
			else
				return 0;
		}
	}

	public Candidate(int size, double[] center) {

		super(size, center, candidateFactory);

		this.closestCandidates    = new Vector<Candidate>(AssignmentSearch.MaxTargetCandidates);
		this.negLogPAssignment = new HashMap<Candidate, Double>(AssignmentSearch.MaxTargetCandidates);
	}

	public void cacheClosestCandidates(Set<Candidate> targetCandidates) {

		PriorityQueue<Candidate> sortedCandidates =
		    new PriorityQueue<Candidate>(AssignmentSearch.MaxTargetCandidates, new CandidateComparator(this));

		sortedCandidates.addAll(targetCandidates);

		// cache most likely candidates
		while (closestCandidates.size() < AssignmentSearch.MaxTargetCandidates) {

			double negLogP = AssignmentModel.negLogP(this, sortedCandidates.peek());

			if (negLogP <= AssignmentSearch.MaxNegLogPAssignment) {

				closestCandidates.add(sortedCandidates.peek());
				negLogPAssignment.put(sortedCandidates.poll(), negLogP);
			} else
				break;
		}

		if (closestCandidates.size() < AssignmentSearch.MinTargetCandidates) {
			IJ.log("Oh no! For region " + this + " there are less than " +
			       AssignmentSearch.MinTargetCandidates + " within the threshold of " +
			       AssignmentSearch.MaxNegLogPAssignment);
			IJ.log("Closest non-selected candidate distance: " + AssignmentModel.negLogP(this, sortedCandidates.peek()));
		}
	}

	public void setMinNegLogPAssignment(double minNegLogPAssignment) {
		//this.minNegLogPAssignment = minNegLogPAssignment;
	}

	public double getMinNegLogPAssignment() {
		//return this.minNegLogPAssignment;
		return 0.0;
	}

	public PriorityQueue<Candidate> getClosestCandidates() {
		return new PriorityQueue<Candidate>();
	}

	public String toString() {

		String ret = "Candidate " + getId() + ",";

		for (int d = 0; d < getCenter().length; d++)
			ret += " " + (int)getCenter()[d];

		ret += ", size: " + getSize();

		return ret;
	}
}
