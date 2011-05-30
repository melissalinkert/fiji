package sipnet;

import java.util.ArrayList;
import java.util.List;

import ij.IJ;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;

public class Evaluator {

	private GroundTruth groundtruth;
	private Sequence    result;

	private List<Vector<Candidate>>     groundtruthSliceCandidates;
	private List<Vector<Candidate>>     resultSliceCandidates;

	// mapping of ground-truth regions to found regions
	private HashMap<Candidate, Vector<Candidate>> correspondences;

	// lists of unexplained ground-truth and result regions for each slice
	private Vector<Set<Candidate>>      unexplainedGroundtruth;
	private Vector<Set<Candidate>>      unexplainedResult;
	// unexplained result regions, for which no ground truth data is available
	private Vector<Set<Candidate>>      unknownResult;

	// lists of false merges and missed merges for each assignment
	private Vector<Vector<Candidate[]>> mergeErrors;
	private Vector<Vector<Candidate[]>> splitErrors;

	// statistics
	private int numMergeErrors;
	private int numSplitErrors;

	private class CandidatePair {

		public Candidate candidate1;
		public Candidate candidate2;
		public double difference;
		public int    overlap;

		private SetDifference setDifference = new SetDifference();

		public CandidatePair(Candidate candidate1, Candidate candidate2) {

			this.candidate1 = candidate1;
			this.candidate2 = candidate2;
			this.overlap    =
					setDifference.numMatches(
							candidate1.getPixels(),
							new int[]{0, 0},
							candidate2.getPixels(),
							new int[]{0, 0});
			this.difference =
					setDifference.setDifferenceRatio(
							candidate1.getPixels(),
							new int[]{0, 0},
							candidate2.getPixels(),
							new int[]{0, 0});
		}
	}

	private class OverlapComparator implements Comparator<CandidatePair> {

		/**
		 * Sort in descending order.
		 */
		public int compare(CandidatePair pair1, CandidatePair pair2) {

			double m1 = pair1.overlap;
			double m2 = pair2.overlap;

			if (m1 < m2)
				return 1;
			else if (m1 > m2)
				return -1;
			else
				return 0;
		}
	}

	private class DifferenceComparator implements Comparator<CandidatePair> {

		/**
		 * Sort in ascending order.
		 */
		public int compare(CandidatePair pair1, CandidatePair pair2) {

			double m1 = pair1.difference;
			double m2 = pair2.difference;

			if (m1 < m2)
				return -1;
			else if (m1 > m2)
				return 1;
			else
				return 0;
		}
	}

	public Evaluator() {}

	public Evaluator(GroundTruth groundtruth, Sequence result) {

		this.groundtruth = groundtruth;
		this.result      = result;

		this.correspondences = new HashMap<Candidate, Vector<Candidate>>();

		this.unexplainedGroundtruth = new Vector<Set<Candidate>>();
		this.unexplainedResult      = new Vector<Set<Candidate>>();
		this.unknownResult          = new Vector<Set<Candidate>>();

		this.mergeErrors = new Vector<Vector<Candidate[]>>();
		this.splitErrors = new Vector<Vector<Candidate[]>>();

		this.numMergeErrors = 0;
		this.numSplitErrors = 0;

		findCorrespondences();
		checkAssignments();
	}

	public Vector<Set<Candidate>> getUnexplainedGroundtruth()
	{
		return this.unexplainedGroundtruth;
	}

	public Vector<Set<Candidate>> getUnexplainedResult()
	{
		return this.unexplainedResult;
	}

	public Vector<Vector<Candidate[]>> getMergeErrors()
	{
		return this.mergeErrors;
	}

	public Vector<Vector<Candidate[]>> getSplitErrors()
	{
		return this.splitErrors;
	}

	final public int getNumMergeErrors() {

		return numMergeErrors;
	}

	final public int getNumSplitErrors() {

		return numSplitErrors;
	}

	final public List<Vector<Candidate>> getGroundtruthCandidates() {

		return groundtruthSliceCandidates;
	}

	final public List<Vector<Candidate>> getResultCandidates() {

		return resultSliceCandidates;
	}

	final public HashMap<Candidate, Vector<Candidate>> getCorrespondences() {

		return correspondences;
	}

	/**
	 * Find a corresponding result region for each ground-truth region. The
	 * corresponding region is the one with the smallest set difference. Each
	 * ground-truth region that can not be assigned causes a merge error. Each
	 * result region that is unexplained causes a split error.
	 */
	final private void findCorrespondences() {

		assert(groundtruth.getSequence().consistent());
		assert(result.consistent());

		groundtruthSliceCandidates = new ArrayList<Vector<Candidate>>();
		resultSliceCandidates      = new ArrayList<Vector<Candidate>>();

		// for each slice
		for (int s = 0; s <= result.size(); s++) {

			Vector<Candidate> groundtruthCandidates =
					new Vector<Candidate>();
			groundtruthCandidates.addAll(
					groundtruth.getSequence().getCandidates(s));
			Vector<Candidate> resultCandidates =
					new Vector<Candidate>();
			resultCandidates.addAll(
					result.getCandidates(s));

			groundtruthSliceCandidates.add(groundtruthCandidates);
			resultSliceCandidates.add(resultCandidates);

			findCorrespondences(groundtruthCandidates, resultCandidates, correspondences, false, true);

			// remaining regions are additional or missed
			Set<Candidate> additional = new HashSet<Candidate>();
			Set<Candidate> missed     = new HashSet<Candidate>();

			// each division of one groundtruth region is a split error
			for (Candidate groundtruthCandidate : groundtruthCandidates) {

				if (correspondences.get(groundtruthCandidate) == null)
					// we missed a region - this is a split error
					missed.add(groundtruthCandidate);
				else
					// every additional region is a split error
					numSplitErrors += (correspondences.get(groundtruthCandidate).size() - 1);
			}

			// each merge of one groundtruth region is a merge error
			for (Candidate resultCandidate : resultCandidates) {

				if (correspondences.get(resultCandidate) == null)
					// we fantasized a region - this is a merge error
					additional.add(resultCandidate);
				else
					// each additional region is a merge error
					numMergeErrors += (correspondences.get(resultCandidate).size() - 1);
			}

			unexplainedGroundtruth.add(missed);
			unexplainedResult.add(additional);

			// find result regions that have no groundtruth
			Set<Candidate> unknown = new HashSet<Candidate>();

			for (Candidate resultCandidate : additional) {

				// regions that have no groundtruth region in the quadrants of
				// their relative coordinates are outside the groundtruth
				// segmentation
				boolean upperLeft  = false;
				boolean lowerLeft  = false;
				boolean lowerRight = false;
				boolean upperRight = false;

				for (Candidate groundtruthCandidate : groundtruthCandidates) {

					double offsetX =
							groundtruthCandidate.getCenter(0) - resultCandidate.getCenter(0);
					double offsetY =
							groundtruthCandidate.getCenter(1) - resultCandidate.getCenter(1);

					upperLeft  = upperLeft  || (offsetX <= 0 && offsetY >  0);
					lowerLeft  = lowerLeft  || (offsetX <= 0 && offsetY <= 0);
					lowerRight = lowerRight || (offsetX >  0 && offsetY >  0);
					upperRight = upperRight || (offsetX >  0 && offsetY <= 0);
				}

				if (!(upperLeft && lowerLeft && lowerRight && upperRight))
					unknown.add(resultCandidate);
			}

			unexplainedResult.get(s).removeAll(unknown);
			unknownResult.add(unknown);

			IJ.log("missed " + missed.size() + " regions in slice " + (s+1));
			IJ.log("fantasized " + additional.size() + " regions in slice " + (s+1));
			IJ.log("splits of ground-truth regions: " + numSplitErrors + " (all slices so far)");
			IJ.log("merges of ground-truth regions: " + numMergeErrors + " (all slices so far)");
		}
	}

	/**
	 * Finds corresponding candidates between the two given sets. If
	 * 'oneToOne' is set, candidates appear in at most one
	 * correspondence list. Otherwise, all candidates are diveded into to types:
	 * superregions and subregions. Superregions can only be associated to
	 * subregions and vice versa. The determination of the type is done
	 * greedily: Whenever two candidates correspond to one other the two
	 * candidates are subregions and the other is a superregion. Subsequent
	 * pairs involving the subregions are disregarded.
	 * If 'overlap' is set, the only criterion for pairing regions is the
	 * number of overlapping pixels. If not set, the symmetric set difference -
	 * normalized by the size of the regions - is used.
	 */
	final public void findCorrespondences(
			Collection<Candidate> set1,
			Collection<Candidate> set2,
			HashMap<Candidate, Vector<Candidate>> correspondences,
			boolean oneToOne,
			boolean overlap) {

		// create all possible pairs
		PriorityQueue<CandidatePair> pairs;
		
		if (overlap)
			pairs =
				new PriorityQueue<CandidatePair>(
						set1.size()*set2.size() + 1,
						new OverlapComparator());
		else
			pairs =
				new PriorityQueue<CandidatePair>(
						set1.size()*set2.size() + 1,
						new DifferenceComparator());

		for (Candidate candidate1 : set1)
			for (Candidate candidate2 : set2)
				pairs.add(new CandidatePair(candidate1, candidate2));

		// overlapping regions correspond
A:		while (pairs.peek() != null) {

			CandidatePair pair = pairs.poll();

			// don't accept pairs that don't overlap
			if (pair.overlap == 0)
				continue;

			if (oneToOne) {
				// don't accept correspondences to already found candidates
				if (correspondences.get(pair.candidate1) != null ||
				    correspondences.get(pair.candidate2) != null)
					continue;

				// don't accept correspondences to conflicting candidates of
				// already found ones, either
				for (Candidate anchestor : pair.candidate1.getAnchestors())
					if (correspondences.get(anchestor) != null)
						continue A;
				for (Candidate descendant : pair.candidate1.getDescendants())
					if (correspondences.get(descendant) != null)
						continue A;
				for (Candidate anchestor : pair.candidate2.getAnchestors())
					if (correspondences.get(anchestor) != null)
						continue A;
				for (Candidate descendant : pair.candidate2.getDescendants())
					if (correspondences.get(descendant) != null)
						continue A;
			} else {

				// don't accecpt correspondences involving subregions
				if (isSubRegion(pair.candidate1, correspondences) ||
				    isSubRegion(pair.candidate2, correspondences))
					continue;
			}

			if (correspondences.get(pair.candidate1) == null)
				correspondences.put(pair.candidate1, new Vector<Candidate>());
			if (correspondences.get(pair.candidate2) == null)
				correspondences.put(pair.candidate2, new Vector<Candidate>());

			correspondences.get(pair.candidate1).add(pair.candidate2);
			correspondences.get(pair.candidate2).add(pair.candidate1);
		}
	}

	/**
	 * Check whether a candidate is a superregion. A candidate is a superregion
	 * if it was assigned to more than one other candidates. Returns false if
	 * the type is not determined yet.
	 */
	final private boolean isSuperRegion(
			Candidate candidate,
			HashMap<Candidate, Vector<Candidate>> correspondences) {

		if (correspondences.get(candidate) != null &&
		    correspondences.get(candidate).size() > 1)
			return true;

		return false;
	}

	/**
	 * Check whether a candidate is a subregion. A candidate is a subregion, if
	 * it is assigned to exactly one candidate, which is also a superregion.
	 * Returns false if the type is not determined yet.
	 */
	final private boolean isSubRegion(
			Candidate candidate,
			HashMap<Candidate, Vector<Candidate>> correspondences) {
	
		if (correspondences.get(candidate) != null &&
		    correspondences.get(candidate).size() == 1 &&
		    isSuperRegion(
					correspondences.get(candidate).get(0),
					correspondences))
			return true;

		return false;
	}

	/**
	 * Find errors in the assignments. For each pair of connected ground-truth regions,
	 * check whether the corresponding result regions are connected as well.
	 * Each missed connection causes a split error. Each extra connection causes
	 * a merge error.
	 */
	final private void checkAssignments() {

		Vector<Set<Candidate[]>> groundtruthPairs =
				sequenceToPairs(groundtruth.getSequence());
		Vector<Set<Candidate[]>> resultPairs =
				sequenceToPairs(result);

		Vector<Set<Candidate[]>> remainingGroundtruthPairs =
				new Vector<Set<Candidate[]>>();
		Vector<Set<Candidate[]>> remainingResultPairs =
				new Vector<Set<Candidate[]>>();

		for (int s = 0; s < groundtruthPairs.size(); s++) {

			remainingGroundtruthPairs.add(new HashSet<Candidate[]>(groundtruthPairs.get(s)));
			remainingResultPairs.add(new HashSet<Candidate[]>(resultPairs.get(s)));

			// remove all result pairs, for which no groundtruth is available
			for (Candidate[] resultPair : resultPairs.get(s)) {

				if (unknownResult.get(s).contains(resultPair[0]) ||
					unknownResult.get(s+1).contains(resultPair[1]))
					if (!remainingResultPairs.get(s).remove(resultPair))
						IJ.log(
								"couldn't remove pair " + resultPair[0].getId() +
								" -> " + resultPair[1].getId() + " - it didn't exist");
			}

			// for each ground-truth pair, remove all result pairs that are
			// explained by that
			for (Candidate[] groundtruthPair : groundtruthPairs.get(s)) {

				if (correspondences.get(groundtruthPair[0]) == null ||
				    correspondences.get(groundtruthPair[1]) == null)
					continue;

				// all possible corresponding result pairs
				for (Candidate correspond1 : correspondences.get(groundtruthPair[0]))
					for (Candidate correspond2 : correspondences.get(groundtruthPair[1]))

						// get the reference to the corresponding result pair
						for (Candidate[] resultPair : resultPairs.get(s))

							if (resultPair[0] == correspond1 &&
								resultPair[1] == correspond2) {

								// remove this pair from remaining result pairs
								remainingResultPairs.get(s).remove(resultPair);
								break;
							}
			}

			// for each result pair, remove all groundtruth pairs that are explained
			// by that
			for (Candidate[] resultPair : resultPairs.get(s)) {

				if (correspondences.get(resultPair[0]) == null ||
				    correspondences.get(resultPair[1]) == null)
					continue;

				// all possible corresponding groundtruth pairs
				for (Candidate correspond1 : correspondences.get(resultPair[0]))
					for (Candidate correspond2 : correspondences.get(resultPair[1]))

						// get the reference to the corresponding groundtruth pair
						for (Candidate[] groundtruthPair : groundtruthPairs.get(s))

							if (groundtruthPair[0] == correspond1 &&
								groundtruthPair[1] == correspond2) {

								// remove this pair from remaining groundtruth pairs
								remainingGroundtruthPairs.get(s).remove(groundtruthPair);
								break;
							}
			}

			// remaining result pairs are merge errors
			numMergeErrors += remainingResultPairs.get(s).size();
			mergeErrors.add(new Vector<Candidate[]>(remainingResultPairs.get(s)));

			// remaining groundtruth pairs are split errors
			numSplitErrors += remainingGroundtruthPairs.get(s).size();
			splitErrors.add(new Vector<Candidate[]>(remainingGroundtruthPairs.get(s)));
		}
	}

	final private Vector<Set<Candidate[]>> sequenceToPairs(Sequence sequence) {

		Vector<Set<Candidate[]>> pairs = new Vector<Set<Candidate[]>>();

		for (Assignment assignment : sequence) {

			Set<Candidate[]> apairs = new HashSet<Candidate[]>();

			for (SingleAssignment singleAssignment : assignment)
				for (Candidate source : singleAssignment.getSources())
					if (source != SequenceSearch.emergeNode)
						for (Candidate target : singleAssignment.getTargets())
							if (target != SequenceSearch.deathNode)
								apairs.add(new Candidate[]{source, target});

			pairs.add(apairs);
		}

		return pairs;
	}
}
