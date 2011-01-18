
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class AssignmentSearch {

	/*
	 * parameters
	 */

	public static final int MaxTargetCandidates = 5;
	public static final int MinTargetCandidates = 1;

	// number of neighbors to consider for neighbor offset
	public static final int NumNeighbors = 3;

	//private static final double MinPAssignment       = 1e-20;
	public static final double MaxNegLogPAssignment = 1e25; //-Math.log(MinPAssignment);

	// add constraints for hypothesis consistency?
	private final boolean hypothesisConsistency = false;
	
	/*
	 * interna
	 */

	private Vector<Candidate> sourceCandidates;
	private Vector<Candidate> targetCandidates;
	
	// pairs of nodes that can potentially merge
	private HashMap<Candidate, HashMap<Candidate, Candidate>> mergeNodes;
	private HashMap<Candidate, HashMap<Candidate, Set<Candidate>>> mergePartners;

	private HashMap<Candidate, HashMap<Candidate, Integer>> nodeNums;
	private int nextNodeId = 0;

	// dummy candidates
	static public final Candidate deathNode = new Candidate(0, 0, new double[]{0.0, 0.0});
	static public final Candidate emergeNode = new Candidate(0, 0, new double[]{0.0, 0.0});

	private LinearProgramSolver lpSolver;

	public AssignmentSearch(Set<Candidate> sourceCandidates, Set<Candidate> targetCandidates) {

		this.sourceCandidates = new Vector<Candidate>();
		this.targetCandidates = new Vector<Candidate>();

		this.sourceCandidates.addAll(sourceCandidates);
		this.targetCandidates.addAll(targetCandidates);

		// build cache and find neighbors in source candidates
		for (Candidate sourceCandidate : this.sourceCandidates) {
			sourceCandidate.cacheMostSimilarCandidates(this.targetCandidates);
			sourceCandidate.findNeighbors(this.sourceCandidates);
		}

		this.nodeNums = new HashMap<Candidate, HashMap<Candidate, Integer>>();
	}

	public Assignment findBestAssignment() {

		IJ.log("searching for best assignment of " + sourceCandidates.size() +
		       " candidates to " + targetCandidates.size() + " candidates");

		setupProblem();

		solveProblem();

		return readAssignment();
	}

	public Assignment findNextBestAssignment() {

		// TODO:
		// • find the next best assignment
		return readAssignment();
	}

	private void setupProblem() {

		findPossibleMergers();

		int numVariables   = computeNumVariables();
		int numConstraints = computeNumConstraints();

		IJ.log("setting up problem: " + numVariables + " variables, " + numConstraints + " constraints");

		lpSolver = new CplexSolver(numVariables, numConstraints);

		/*
		 * OUTGOING EDGES
		 */

		// for each source candidate
		for (Candidate sourceCandidate : sourceCandidates) {

			Vector<Integer> variableNums = new Vector<Integer>();
			Vector<Double>  coefficients = new Vector<Double>();

			// the sum of all continuation edges...
			for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates()) {
				variableNums.add(getVariableNum(sourceCandidate, targetCandidate));
				coefficients.add(1.0);
			}

			// ...and all merge edges this source candidate is involved in...
			for (Candidate bigger : mergePartners.get(sourceCandidate).keySet())
				for (Candidate partner : mergePartners.get(sourceCandidate).get(bigger)) {
					variableNums.add(getVariableNum(mergeNodes.get(sourceCandidate).get(bigger), partner));
					coefficients.add(1.0);
				}
			for (Candidate smaller : sourceCandidates)
				if (mergePartners.get(smaller).keySet().contains(sourceCandidate))
					for (Candidate partner : mergePartners.get(smaller).get(sourceCandidate)) {
						variableNums.add(getVariableNum(mergeNodes.get(smaller).get(sourceCandidate), partner));
						coefficients.add(1.0);
					}

			// ...and the edge to the delete node...
			variableNums.add(getVariableNum(sourceCandidate, deathNode));
			coefficients.add(1.0);

			// ...has to be exactly one
			lpSolver.addConstraint(variableNums, coefficients, 0, 1.0);
		}

		/*
		 * INCOMING EDGES
		 */

		// for each target candidate
		for (Candidate targetCandidate : targetCandidates) {

			Vector<Integer> variableNums = new Vector<Integer>();
			Vector<Double>  coefficients = new Vector<Double>();

			// the sum of all continuation edges
			for (Candidate sourceCandidate : targetCandidate.getMostLikelyOf()) {
				variableNums.add(getVariableNum(sourceCandidate, targetCandidate));
				coefficients.add(1.0);
			}

			// ...and all merge edges this target candidate is involved in...
			for (Candidate smaller : sourceCandidates)
				for (Candidate bigger : mergePartners.get(smaller).keySet())
					if (mergePartners.get(smaller).get(bigger).contains(targetCandidate)) {
						variableNums.add(getVariableNum(mergeNodes.get(smaller).get(bigger), targetCandidate));
						coefficients.add(1.0);
					}

			// ...and the edge from the emerge node...
			variableNums.add(getVariableNum(emergeNode, targetCandidate));
			coefficients.add(1.0);

			// ...has to be exactly one
			lpSolver.addConstraint(variableNums, coefficients, 0, 1.0);
		}

		/*
		 * HYPOTHESISES
		 */

		// hypothesis consistency - for each path in the component tree of the
		// target candidates, i.e., for each child
		if (hypothesisConsistency) {

			IJ.log("HYPOTHESIS CONSISTENCY NOT IMPLEMENTED YET");
		}

		/*
		 * OBJECTIVE FUNCTION
		 */

		Vector<Integer> variableNums = new Vector<Integer>();
		Vector<Double>  coefficients = new Vector<Double>();

		// for each continuation
		for (Candidate sourceCandidate : sourceCandidates)
			for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates()) {
				variableNums.add(getVariableNum(sourceCandidate, targetCandidate));
				coefficients.add(AssignmentModel.negLogPAppearance(sourceCandidate, targetCandidate));
			}

		// for each merge
		for (Candidate smaller : mergePartners.keySet())
			for (Candidate bigger : mergePartners.get(smaller).keySet())
				for (Candidate partner : mergePartners.get(smaller).get(bigger)) {
					variableNums.add(getVariableNum(mergeNodes.get(smaller).get(bigger), partner));
					coefficients.add(AssignmentModel.negLogPriorSplit);
				}

		// for each emerge
		for (Candidate targetCandidate : targetCandidates) {
			variableNums.add(getVariableNum(emergeNode, targetCandidate));
			coefficients.add(AssignmentModel.negLogPriorDeath);
		}

		// for each death
		for (Candidate sourceCandidate : sourceCandidates) {
			variableNums.add(getVariableNum(sourceCandidate, deathNode));
			coefficients.add(AssignmentModel.negLogPriorDeath);
		}

		lpSolver.setObjective(variableNums, coefficients);
	}

	private void findPossibleMergers() {

		mergeNodes    = new HashMap<Candidate, HashMap<Candidate, Candidate>>();
		mergePartners = new HashMap<Candidate, HashMap<Candidate, Set<Candidate>>>();

		for (Candidate sourceCandidate : sourceCandidates) {
			mergeNodes.put(sourceCandidate, new HashMap<Candidate, Candidate>());
			mergePartners.put(sourceCandidate, new HashMap<Candidate, Set<Candidate>>());
		}

		for (Candidate sourceCandidate : sourceCandidates)
			for (Candidate neighbor : sourceCandidate.getNeighbors()) {

				Candidate smaller;
				Candidate bigger;

				if (sourceCandidate.getId() < neighbor.getId()) {
					smaller = sourceCandidate;
					bigger  = neighbor;
				} else {
					smaller = neighbor;
					bigger  = sourceCandidate;
				}

				Set<Candidate> partners = new HashSet<Candidate>();

				for (Candidate mergePartner : smaller.getMostLikelyCandidates())
					if (bigger.getMostLikelyCandidates().contains(mergePartner))
						partners.add(mergePartner);

				mergeNodes.get(smaller).put(bigger, new Candidate(0, 0, new double[]{0.0, 0.0}));
				mergePartners.get(smaller).put(bigger, partners);
			}
	}

	private int computeNumVariables() {

		// the number of variables is the number of possible connection cases
		
		int numVariables = 0;

		// for the continue-connections between the source and target candidates
		for (Candidate sourceCandidate : sourceCandidates)
			numVariables += sourceCandidate.getMostLikelyCandidates().size();

		// for each possible merge of two source candidates to a target
		// candidate
		for (Candidate smaller : mergeNodes.keySet())
			for (Candidate bigger : mergeNodes.get(smaller).keySet())
				numVariables += mergePartners.get(smaller).get(bigger).size();

		// for the connection of each source candidate to the delete node
		numVariables += sourceCandidates.size();

		// for the connections of the emerge node to the target candidates
		numVariables += targetCandidates.size();

		if (hypothesisConsistency) {

			// for the connections of the neglect node to the target candidates
			numVariables += targetCandidates.size();
		}

		return numVariables;
	}

	private int computeNumConstraints() {

		int numConstraints = 0;

		// outgoing edges for each source candidate
		numConstraints += sourceCandidates.size();

		// incoming edges for each target candidate
		numConstraints += targetCandidates.size();

		// hypothesis consistency - one constraint for each path in the
		// component tree of the target candidates, i.e., for each child
		if (hypothesisConsistency)
			for (Candidate targetCandidate : targetCandidates)
				if (targetCandidate.getChildren().size() == 0)
					numConstraints++;

		return numConstraints;
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
			m = new HashMap<Candidate, Integer>();
			nodeNums.put(candidate1, m);
		}

		Integer id = m.get(candidate2);

		if (id != null)
			return id;
		else {
			m.put(candidate2, new Integer(nextNodeId));
			nextNodeId++;

			return nextNodeId - 1;
		}
	}

	private void solveProblem() {

		int result = lpSolver.solve(2);

		if (result != 0)
			IJ.log("LP problem could not be solved.");
	}

	private Assignment readAssignment() {

		Assignment assignment = new Assignment();

		// each continuation
		for (Candidate sourceCandidate : sourceCandidates)
			for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates())
				if (getVariableValue(sourceCandidate, targetCandidate) == 1)
					assignment.add(new SingleAssignment(sourceCandidate, targetCandidate));

		// each merge
		for (Candidate smaller : mergeNodes.keySet())
			for (Candidate bigger : mergeNodes.get(smaller).keySet())
				for (Candidate partner : mergePartners.get(smaller).get(bigger))
					if (getVariableValue(mergeNodes.get(smaller).get(bigger), partner) == 1) {
						assignment.add(new SingleAssignment(smaller, partner));
						assignment.add(new SingleAssignment(bigger, partner));
					}

		// each death
		for (Candidate sourceCandidate : sourceCandidates)
			if (getVariableValue(sourceCandidate, deathNode) == 1)
				assignment.add(new SingleAssignment(sourceCandidate, deathNode));

		// each emerge
		for (Candidate targetCandidate : targetCandidates)
			if (getVariableValue(emergeNode, targetCandidate) == 1)
				assignment.add(new SingleAssignment(emergeNode, targetCandidate));

		return assignment;
	}

	private int getVariableValue(Candidate from, Candidate to) {

		double value = lpSolver.getValue((int)getVariableNum(from, to));
		
		if (value > 0.5)
			return 1;
		return 0;
	}
}
