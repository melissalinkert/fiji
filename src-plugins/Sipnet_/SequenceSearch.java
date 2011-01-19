
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class SequenceSearch {

	/*
	 * parameters of the sequence search
	 */

	public static final int MaxTargetCandidates = 5;
	public static final int MinTargetCandidates = 1;

	// number of neighbors to consider for neighbor offset
	public static final int NumNeighbors = 3;

	//private static final double MinPAssignment       = 1e-20;
	public static final double MaxNegLogPAssignment = 1e25; //-Math.log(MinPAssignment);

	/*
	 * nodes of the assignment graph
	 */

	// candidates for each slice
	private List<Vector<Candidate>> sliceCandidates;

	// pairs of nodes that can potentially merge
	private HashMap<Candidate, HashMap<Candidate, Candidate>>      mergeNodes;
	private HashMap<Candidate, HashMap<Candidate, Set<Candidate>>> mergePartners;

	private HashMap<Candidate, HashMap<Candidate, Integer>> nodeNums;
	private int nextNodeId = 0;

	// dummy candidates
	static public final Candidate deathNode = new Candidate(0, 0, new double[]{0.0, 0.0});
	static public final Candidate emergeNode = new Candidate(0, 0, new double[]{0.0, 0.0});

	private LinearProgramSolver lpSolver;

	private boolean noMoreNewVariables = false;

	public SequenceSearch(List<Set<Candidate>> sliceCandidates, Texifyer texifyer) {

		this.sliceCandidates = new ArrayList<Vector<Candidate>>();
		for (Set<Candidate> candidates : sliceCandidates)
			this.sliceCandidates.add(new Vector<Candidate>(candidates));

		this.nodeNums = new HashMap<Candidate, HashMap<Candidate, Integer>>();

		// build cache for candidates
		IJ.log("Precaching most likely candidates...");
		for (int s = 0; s < this.sliceCandidates.size() - 1; s++)
			for (Candidate candidate : this.sliceCandidates.get(s))
				candidate.cacheMostSimilarCandidates(this.sliceCandidates.get(s+1));

		IJ.log("Precaching neighbors...");
		for (int s = 0; s < this.sliceCandidates.size(); s++)
			for (Candidate candidate : this.sliceCandidates.get(s))
				candidate.findNeighbors(this.sliceCandidates.get(s));

		IJ.log("Done.");
	}

	public Sequence getBestAssignmentSequence() {

		IJ.log("searching for best sequence of assignments");

		setupProblem();

		if (solveProblem())
			return readSequence();

		return new Sequence();
	}

	private void setupProblem() {

		IJ.log("searching for possible merge candidates");
		findPossibleMergers();
		IJ.log("Done.");

		int numVariables   = computeNumVariables();
		int numConstraints = computeNumConstraints();

		int numConsUsed = 0;
		int numVarsUsed = 0;

		IJ.log("setting up problem: " + numVariables + " variables, " + numConstraints + " constraints");

		lpSolver = new CplexSolver(numVariables, numConstraints);

		/*
		 * INCOMING AND OUTGOING EDGES
		 */

		// for each but the first and last slice
		for (int s = 1; s < sliceCandidates.size() - 1; s++)

			// for each candidate of this slice
			for (Candidate candidate : sliceCandidates.get(s)) {

				Vector<Integer> variableNums = new Vector<Integer>();
				Vector<Double>  coefficients = new Vector<Double>();

				// the sum of all continuation edges...
				for (Candidate sourceCandidate : candidate.getMostLikelyOf()) {
					variableNums.add(getVariableNum(sourceCandidate, candidate));
					coefficients.add(1.0);
				}

				// ...and all merge edges pointing to this candidate...
				for (Candidate mergeNode : candidate.mergePartnerOf()) {
					variableNums.add(getVariableNum(mergeNode, candidate));
					coefficients.add(1.0);
				}

				// ...and the edge from the emerge node...
				variableNums.add(getVariableNum(emergeNode, candidate));
				coefficients.add(1.0);

				// ...minus...

				// ...the sum of all continuation edges...
				for (Candidate targetCandidate : candidate.getMostLikelyCandidates()) {
					variableNums.add(getVariableNum(candidate, targetCandidate));
					coefficients.add(-1.0);
				}

				// ...and all merge edges this source candidate is involved in...
				for (Candidate neighbor : candidate.getNeighbors()) {

					Candidate smaller = (neighbor.getId() < candidate.getId() ? neighbor  : candidate);
					Candidate bigger  = (neighbor.getId() < candidate.getId() ? candidate : neighbor);

					Candidate mergeNode = mergeNodes.get(smaller).get(bigger);

					for (Candidate mergePartner : mergePartners.get(smaller).get(bigger)) {
						variableNums.add(getVariableNum(mergeNode, mergePartner));
						coefficients.add(-1.0);
					}
				}

				// ...and the edge to the death node...
				variableNums.add(getVariableNum(candidate, deathNode));
				coefficients.add(-1.0);

				// ...has to be exactly zero
				lpSolver.addConstraint(variableNums, coefficients, 0, 0.0);

				numConsUsed++;
				for (Integer n : variableNums)
					if (n > numVarsUsed)
						numVarsUsed = n;
			}

		/*
		 * HYPOTHESISES
		 */

		// first slice
		for (Candidate candidate : sliceCandidates.get(0))

			// for each path in the component tree
			if (candidate.getChildren().size() == 0) {

				Vector<Candidate> path = new Vector<Candidate>();
				Candidate tmp = candidate;

				while (tmp != null) {

					path.add(tmp);
					tmp = tmp.getParent();
				}

				Vector<Integer> variableNums = new Vector<Integer>();
				Vector<Double>  coefficients = new Vector<Double>();

				for (Candidate member : path) {

					// the sum of all outgoing continuation edges...
					for (Candidate targetCandidate : member.getMostLikelyCandidates()) {
						variableNums.add(getVariableNum(member, targetCandidate));
						coefficients.add(1.0);
					}

					// ...and all outgoing merge edges...
					for (Candidate neighbor : member.getNeighbors()) {

						Candidate smaller = (neighbor.getId() < member.getId() ? neighbor : member);
						Candidate bigger  = (neighbor.getId() < member.getId() ? member   : neighbor);

						System.out.println("considering merge node of " + smaller.getId() + " + " + bigger.getId());
						for (Candidate mergePartner : mergePartners.get(smaller).get(bigger)) {
							variableNums.add(getVariableNum(mergeNodes.get(smaller).get(bigger), mergePartner));
							coefficients.add(1.0);
						}
					}
				}

				// ...has to be exactly one
				lpSolver.addConstraint(variableNums, coefficients, 0, 1.0);

				numConsUsed++;
				for (Integer n : variableNums)
					if (n > numVarsUsed)
						numVarsUsed = n;
			}

		// intermediate and last slices
		for (int s = 1; s < sliceCandidates.size(); s++)

			for (Candidate candidate : sliceCandidates.get(s))

				if (candidate.getChildren().size() == 0) {

					// for each path in the component tree
					Vector<Candidate> path = new Vector<Candidate>();
					Candidate tmp = candidate;

					while (tmp != null) {

						path.add(tmp);
						tmp = tmp.getParent();
					}

					Vector<Integer> variableNums = new Vector<Integer>();
					Vector<Double>  coefficients = new Vector<Double>();

					for (Candidate member : path) {

						// the sum of all incoming continuation edges...
						for (Candidate sourceCandidate : member.getMostLikelyOf()) {
							variableNums.add(getVariableNum(sourceCandidate, member));
							coefficients.add(1.0);
						}

						// ...and all incoming merge edges...
						for (Candidate mergeNode : member.mergePartnerOf()) {
							variableNums.add(getVariableNum(mergeNode, member));
							coefficients.add(1.0);
						}

						// not for last slice
						if (s != sliceCandidates.size() - 1) {
							// ...and all incoming emerge edges...
							variableNums.add(getVariableNum(emergeNode, member));
							coefficients.add(1.0);
						}
					}

					// ...has to be exactly one
					lpSolver.addConstraint(variableNums, coefficients, 0, 1.0);

					numConsUsed++;
					for (Integer n : variableNums)
						if (n > numVarsUsed)
							numVarsUsed = n;
				}

		IJ.log("" + numConsUsed + " constraints set, up to " + (numVarsUsed+1) + " variables used");

		/*
		 * OBJECTIVE FUNCTION
		 */

		noMoreNewVariables = true;

		Vector<Integer> variableNums = new Vector<Integer>();
		Vector<Double>  coefficients = new Vector<Double>();

		// all but last slice
		for (int s = 0; s < sliceCandidates.size() - 1; s++) {

			// for each continuation
			for (Candidate sourceCandidate : sliceCandidates.get(s))
				for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates()) {
					variableNums.add(getVariableNum(sourceCandidate, targetCandidate));
					coefficients.add(AssignmentModel.negLogPAppearance(sourceCandidate, targetCandidate));
				}

			// for each merge
			for (Candidate candidate : sliceCandidates.get(s))
				for (Candidate neighbor : candidate.getNeighbors()) {

					Candidate smaller = (candidate.getId() < neighbor.getId() ? candidate : neighbor);
					Candidate bigger  = (candidate.getId() < neighbor.getId() ? neighbor  : candidate);

					for (Candidate mergePartner : mergePartners.get(smaller).get(bigger)) {
						
						variableNums.add(getVariableNum(mergeNodes.get(smaller).get(bigger), mergePartner));
						coefficients.add(AssignmentModel.negLogPriorSplit);
					}
				}
		}

		// all but first and last slice
		for (int s = 1; s < sliceCandidates.size() - 1; s++) {
			// for each emerge
			for (Candidate targetCandidate : sliceCandidates.get(s)) {
				variableNums.add(getVariableNum(emergeNode, targetCandidate));
				coefficients.add(AssignmentModel.negLogPriorDeath);
			}
			// for each death
			for (Candidate sourceCandidate : sliceCandidates.get(s)) {
				variableNums.add(getVariableNum(sourceCandidate, deathNode));
				coefficients.add(AssignmentModel.negLogPriorDeath);
			}
		}

		lpSolver.setObjective(variableNums, coefficients);
	}

	private boolean solveProblem() {

		int result = lpSolver.solve(2);

		if (result != 0) {
			IJ.log("LP problem could not be solved.");
			return false;
		}

		return true;
	}


	private void findPossibleMergers() {

		mergeNodes    = new HashMap<Candidate, HashMap<Candidate, Candidate>>();
		mergePartners = new HashMap<Candidate, HashMap<Candidate, Set<Candidate>>>();

		// all but the last slice
		for (int s = 0; s < sliceCandidates.size() - 1; s++) {

			for (Candidate sourceCandidate : sliceCandidates.get(s)) {

				mergeNodes.put(sourceCandidate, new HashMap<Candidate, Candidate>());
				mergePartners.put(sourceCandidate, new HashMap<Candidate, Set<Candidate>>());
			}

			for (Candidate candidate : sliceCandidates.get(s))
				for (Candidate neighbor : candidate.getNeighbors()) {

					Candidate smaller = (candidate.getId() < neighbor.getId() ? candidate : neighbor);
					Candidate bigger  = (candidate.getId() < neighbor.getId() ? neighbor  : candidate);

					// has this pair been considered already?
					if (mergeNodes.get(smaller).get(bigger) != null)
						continue;

					Set<Candidate> partners  = new HashSet<Candidate>();
					Candidate      mergeNode = new Candidate(0, 0, new double[]{0.0, 0.0});

					for (Candidate mergePartner : smaller.getMostLikelyCandidates())
						if (bigger.getMostLikelyCandidates().contains(mergePartner)) {
							partners.add(mergePartner);
							mergePartner.addMergeParnerOf(mergeNode);
						}

					mergeNodes.get(smaller).put(bigger, mergeNode);
					mergePartners.get(smaller).put(bigger, partners);

					System.out.println("possible merge: " + smaller.getId() + " + " + bigger.getId());
				}
		}
	}

	private int computeNumVariables() {

		// the number of variables is the number of possible connection cases
		
		int numVariables = 0;

		// for each possible merge of two source candidates to a target
		// candidate
		for (Candidate smaller : mergeNodes.keySet())
			for (Candidate bigger : mergeNodes.get(smaller).keySet())
				numVariables += mergePartners.get(smaller).get(bigger).size();

		// all but the last slice
		for (int s = 0; s < sliceCandidates.size() - 1; s++)
			// for the continue-connections between the source and target candidates
			for (Candidate sourceCandidate : sliceCandidates.get(s))
				numVariables += sourceCandidate.getMostLikelyCandidates().size();

		// all but the first and the last slice
		for (int s = 1; s < sliceCandidates.size() - 1; s++)
			// for the emerge and death edges
			numVariables += 2*sliceCandidates.get(s).size();

		return numVariables;
	}

	private int computeNumConstraints() {

		int numConstraints = 0;

		// all but the first and last slice
		for (int s = 1; s < sliceCandidates.size() - 1; s++)
			// incoming and outgoing edges for each candidate
			numConstraints += sliceCandidates.get(s).size();

		// slices
		for (int s = 0; s < sliceCandidates.size(); s++)
			// hypothesis consistency
			for (Candidate targetCandidate : sliceCandidates.get(s))
				if (targetCandidate.getChildren().size() == 0)
					numConstraints++;

		return numConstraints;
	}

	private Sequence readSequence() {

		Sequence sequence = new Sequence();

		// all but the last slice
		for (int s = sliceCandidates.size() - 2; s >= 0; s--) {

			Assignment assignment = new Assignment();

				// each continuation
				for (Candidate sourceCandidate : sliceCandidates.get(s))
					for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates())
						if (getVariableValue(sourceCandidate, targetCandidate) == 1)
							assignment.add(new SingleAssignment(sourceCandidate, targetCandidate));

				// each merge
				for (Candidate candidate : sliceCandidates.get(s))
					for (Candidate neighbor : candidate.getNeighbors()) {

						Candidate smaller = (candidate.getId() < neighbor.getId() ? candidate : neighbor);
						Candidate bigger  = (candidate.getId() < neighbor.getId() ? neighbor  : candidate);

						// was that pair handled already?
						if (mergeNodes.get(smaller).get(bigger) == null)
							continue;

						for (Candidate mergePartner : mergePartners.get(smaller).get(bigger))
							if (getVariableValue(mergeNodes.get(smaller).get(bigger), mergePartner) == 1) {
								assignment.add(new SingleAssignment(smaller, mergePartner));
								assignment.add(new SingleAssignment(bigger, mergePartner));

								// remember that this pair was handled already
								mergeNodes.get(smaller).put(bigger, null);

								// there can only be one merge partner
								break;
							}
					}

			// not the first slice
			if (s > 0) {
				// each death
				for (Candidate sourceCandidate : sliceCandidates.get(s))
					if (getVariableValue(sourceCandidate, deathNode) == 1)
						assignment.add(new SingleAssignment(sourceCandidate, deathNode));

				// each emerge
				for (Candidate targetCandidate : sliceCandidates.get(s))
					if (getVariableValue(emergeNode, targetCandidate) == 1)
						assignment.add(new SingleAssignment(emergeNode, targetCandidate));
			}

			sequence.add(new SequenceNode(assignment));
		}

		return sequence;
	}

	/**
	 * Each pair of source-to-target candidates represents an edge in the graph,
	 * which in turn is modelled as a variable. This function returns the
	 * variable number a given pair is associated to.
	 */
	private int getVariableNum(Candidate candidate1, Candidate candidate2) {

		if (candidate1.getId() > candidate2.getId()) {
			Candidate tmp = candidate1;
			candidate1 = candidate2;
			candidate2 = tmp;
		}

		HashMap<Candidate, Integer> m = nodeNums.get(candidate1);

		if (m == null) {
			if (noMoreNewVariables)
				throw new RuntimeException("new variable " + candidate1.getId() + " + " + candidate2.getId());
			m = new HashMap<Candidate, Integer>();
			nodeNums.put(candidate1, m);
		}

		Integer id = m.get(candidate2);

		if (id != null)
			return id;
		else {
			if (noMoreNewVariables)
				throw new RuntimeException("new variable " + candidate1.getId() + " + " + candidate2.getId());
			m.put(candidate2, new Integer(nextNodeId));
			nextNodeId++;

			return nextNodeId - 1;
		}
	}

	private int getVariableValue(Candidate from, Candidate to) {

		double value = lpSolver.getValue((int)getVariableNum(from, to));
		
		if (value > 0.5)
			return 1;
		return 0;
	}
}
