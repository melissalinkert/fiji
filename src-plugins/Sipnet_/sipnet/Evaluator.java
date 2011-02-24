package sipnet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class Evaluator {

	private GroundTruth groundtruth;
	private Sequence    result;

	// mapping of ground-truth regions to found regions
	private HashMap<Candidate, Candidate> correspondences;

	// lists of unexplained ground-truth and result regions for each slice
	private Vector<Set<Candidate>>             unexplainedGroundtruth;
	private Vector<Set<Candidate>>             unexplainedResult;

	// lists of false merges and missed merges for each assignment
	private Vector<HashMap<Candidate, Candidate>> mergeErrors;
	private Vector<HashMap<Candidate, Candidate>> splitErrors;

	// statistics
	private int numMergeErrors;
	private int numSplitErrors;

	public Evaluator(GroundTruth groundtruth, Sequence result) {

		this.groundtruth = groundtruth;
		this.result      = result;

		this.correspondences = new HashMap<Candidate, Candidate>();

		this.unexplainedGroundtruth = new Vector<Set<Candidate>>();
		this.unexplainedResult      = new Vector<Set<Candidate>>();

		this.mergeErrors = new Vector<HashMap<Candidate, Candidate>>();
		this.splitErrors = new Vector<HashMap<Candidate, Candidate>>();

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

	public Vector<HashMap<Candidate,Candidate>> getMergeErrors()
	{
		return this.mergeErrors;
	}

	public Vector<HashMap<Candidate,Candidate>> getSplitErrors()
	{
		return this.splitErrors;
	}

	final public int getNumMergeErrors() {

		return numMergeErrors;
	}

	final public int getNumSplitErrors() {

		return numSplitErrors;
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

		SetDifference setDifference = new SetDifference();
		HashMap<Candidate, HashMap<Candidate, Double>> distanceCache =
				new HashMap<Candidate, HashMap<Candidate, Double>>();

		// for each slice
		for (int s = 0; s <= result.size(); s++) {

			Set<Candidate> groundtruthCandidates =
					groundtruth.getSequence().getCandidates(s);
			Set<Candidate> resultCandidates =
					result.getCandidates(s);

			boolean done = false;
			while (!done) {

				double    minDistance     = -1.0;
				Candidate bestGroundtruth = null;
				Candidate bestResult      = null;

				// find the next best pair of candidates
				for (Candidate groundtruthCandidate : groundtruthCandidates)
					for (Candidate resultCandidate : resultCandidates) {

						HashMap<Candidate, Double> cache =
								distanceCache.get(groundtruthCandidate);

						if (cache == null) {
							cache = new HashMap<Candidate, Double>();
							distanceCache.put(groundtruthCandidate, cache);
						}

						Double distance = cache.get(resultCandidate);

						if (distance == null) {

							distance = setDifference.setDifferenceRatio(
									groundtruthCandidate.getPixels(),
									new int[]{0, 0},
									resultCandidate.getPixels(),
									new int[]{0, 0});

							cache.put(resultCandidate, distance);
						}

						if (distance < minDistance || minDistance < 0) {

							minDistance     = distance;
							bestGroundtruth = groundtruthCandidate;
							bestResult      = resultCandidate;
						}
					}

				// is there any overlap at all?
				if (minDistance >= 0 &&
				    minDistance <
							(bestGroundtruth.getSize() + bestResult.getSize())/
							Math.min(bestGroundtruth.getSize(), bestResult.getSize())) {

					correspondences.put(bestGroundtruth, bestResult);
					groundtruthCandidates.remove(bestGroundtruth);
					resultCandidates.remove(bestResult);

				} else {

					unexplainedGroundtruth.add(groundtruthCandidates);
					unexplainedResult.add(resultCandidates);

					numMergeErrors += groundtruthCandidates.size();
					numSplitErrors += resultCandidates.size();

					IJ.log("" + groundtruthCandidates.size() + " unexplained ground-truth regions in slice " + s);
					IJ.log("" + resultCandidates.size() + " unexplained result regions in slice " + s);

					done = true;
				}
			}
		}
	}

	/**
	 * Find errors in the assignments. For each pair of connected ground-truth regions,
	 * check whether the corresponding result regions are connected as well.
	 * Each missed connection causes a split error. Each extra connection causes
	 * a merge error.
	 */
	final private void checkAssignments() {

		Set<Candidate[]> groundtruthPairs =
				sequenceToPairs(groundtruth.getSequence());
		Set<Candidate[]> resultPairs =
				sequenceToPairs(result);

		int numMatches = 0;
		for (Candidate[] groundtruthPair : groundtruthPairs) {

			Candidate[] correspondingPair =
					new Candidate[]{
							correspondences.get(groundtruthPair[0]),
							correspondences.get(groundtruthPair[1])};

			for (Candidate[] resultPair : resultPairs)
				if (resultPair[0] == correspondingPair[0] &&
				    resultPair[1] == correspondingPair[1]) {
					numMatches++;
					break;
				}
		}


		int numMissed = groundtruthPairs.size() - numMatches;
		int numExtra  = resultPairs.size() - numMatches;
		IJ.log("" + groundtruthPairs.size() + " connections in ground-truth, " + numMissed + " missed");
		IJ.log("" + resultPairs.size() + " connections in result, " + numExtra + " extra");

		numMergeErrors += numExtra;
		numSplitErrors += numMissed;
	}

	final private Set<Candidate[]> sequenceToPairs(Sequence sequence) {

		Set<Candidate[]> pairs = new HashSet<Candidate[]>();

		for (Assignment assignment : sequence)
			for (SingleAssignment singleAssignment : assignment)
				for (Candidate source : singleAssignment.getSources())
					if (source != SequenceSearch.emergeNode)
						for (Candidate target : singleAssignment.getTargets())
							if (target != SequenceSearch.deathNode)
								pairs.add(new Candidate[]{source, target});

		return pairs;
	}
}
