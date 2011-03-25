package sipnet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

import ij.IJ;

public class SequenceSearch {

	/*
	 * parameters of the sequence search
	 */

	public static int NumDistanceCandidates = 50; // number of closest candidates to consider for targets
	public static int MaxTargetCandidates   = 25;
	public static int MinTargetCandidates   = 10;

	// number of neighbors to consider for neighbor offset
	public static final int NumNeighbors = 3;

	//private static final double MinPAssignment       = 1e-20;
	public static final double MaxNegLogPAppearance = 1e25; //-Math.log(MinPAssignment);

	// enforce explanation of every candidate in the first and last slice?
	public static boolean FullMarginSlices = false;

	/*
	 * nodes of the assignment graph
	 */

	// candidates for each slice
	private List<Vector<Candidate>> sliceCandidates;

	// pairs of nodes that can potentially merge/split
	private HashMap<Candidate, HashMap<Candidate, Candidate>> mergeNodes;
	private HashMap<Candidate, HashMap<Candidate, Candidate>> splitNodes;

	private HashMap<Candidate, HashMap<Candidate, Integer>>   nodeNums;
	private int nextNodeId = 0;

	// dummy candidates
	static public final Candidate deathNode = new Candidate();
	static public final Candidate emergeNode = new Candidate();

	/*
	 * model and inference
	 */

	private AssignmentModel     assignmentModel;
	private LinearProgramSolver lpSolver;

	// instead of finding the best sequence, compute the marginal probabilities
	// of the assignment variables
	private boolean computeMarginals;

	public SequenceSearch(
			List<Vector<Candidate>> sliceCandidates,
			String                  parameterFilename,
			AssignmentModel         assignmentModel,
			boolean                 computeMarginals) {

		this.sliceCandidates = sliceCandidates;

		this.nodeNums = new HashMap<Candidate, HashMap<Candidate, Integer>>();

		this.assignmentModel = assignmentModel;

		this.computeMarginals = computeMarginals;

		readParameters(parameterFilename);
	}

	public Sequence getBestAssignmentSequence() {

		IJ.log("searching for best sequence of assignments");

		setupProblem();

		if (solveProblem())
			return readSequence();

		return new Sequence();
	}

	public final void readParameters(String filename) {

		Properties parameterFile = new Properties();

		try {
			parameterFile.load(new FileInputStream(new File(filename)));

			NumDistanceCandidates = Integer.valueOf(parameterFile.getProperty("num_distance_candidates"));
			MaxTargetCandidates   = Integer.valueOf(parameterFile.getProperty("max_target_candidates"));
			MinTargetCandidates   = Integer.valueOf(parameterFile.getProperty("min_target_candidates"));
			FullMarginSlices      = Boolean.valueOf(parameterFile.getProperty("full_margin_slices"));

			IJ.log("Sequence search read parameters:");
			IJ.log("  NumDistanceCandidates: " + NumDistanceCandidates);
			IJ.log("  MaxTargetCandidates: "   + MaxTargetCandidates);
			IJ.log("  MinTargetCandidates: "   + MinTargetCandidates);
			IJ.log("  FullMarginSlices: "      + FullMarginSlices);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * Check whether source is connected to target in the final sequence. Source
	 * is allowed to be emergeNode, target is allowed to be deathNode.
	 */
	public boolean isConnected(Candidate source, Candidate target) {

		int variable = getVariableNum(source, target);

		if (variable < 0)
			return false;

		return (getVariableValue(variable) == 1);
	}

	/**
	 * Check whether source splits in target1 and target2 in final sequence.
	 */
	public boolean isSplit(Candidate source, Candidate target1, Candidate target2) {

		Candidate smaller = (target1.getId() < target2.getId() ? target1 : target2);
		Candidate bigger  = (target1.getId() < target2.getId() ? target2 : target1);

		Candidate splitNode = splitNodes.get(smaller).get(bigger);

		int variable = getVariableNum(source, splitNode);

		if (variable < 0)
			return false;

		return (getVariableValue(variable) == 1);
	}

	/**
	 * Check whether source1 and source2 merge to target in final sequence.
	 */
	public boolean isMerge(Candidate source1, Candidate source2, Candidate target) {

		Candidate smaller = (source1.getId() < source2.getId() ? source1 : source2);
		Candidate bigger  = (source1.getId() < source2.getId() ? source2 : source1);

		Candidate mergeNode = mergeNodes.get(smaller).get(bigger);

		int variable = getVariableNum(mergeNode, target);

		if (variable < 0)
			return false;

		return (getVariableValue(variable) == 1);
	}

	/**
	 * Marginal probability of the event "source is connected to target". Source
	 * is allowed to be emergeNode, target is allowed to be deathNode.
	 */
	public double marginalConnected(Candidate source, Candidate target) {

		int variable = getVariableNum(source, target);

		if (variable < 0)
			return 0.0;

		return getMarginal(variable, 1);
	}

	/**
	 * Marginal probability of the event "source splits in target1 and target2".
	 */
	public double marginalSplit(Candidate source, Candidate target1, Candidate target2) {

		Candidate smaller = (target1.getId() < target2.getId() ? target1 : target2);
		Candidate bigger  = (target1.getId() < target2.getId() ? target2 : target1);

		Candidate splitNode = splitNodes.get(smaller).get(bigger);

		int variable = getVariableNum(source, splitNode);

		if (variable < 0)
			return 0.0;

		return getMarginal(variable, 1);
	}

	/**
	 * Marginal probability of the event "source1 and source2 merge to target".
	 */
	public double marginalMerge(Candidate source1, Candidate source2, Candidate target) {

		Candidate smaller = (source1.getId() < source2.getId() ? source1 : source2);
		Candidate bigger  = (source1.getId() < source2.getId() ? source2 : source1);

		Candidate mergeNode = mergeNodes.get(smaller).get(bigger);

		int variable = getVariableNum(mergeNode, target);

		if (variable < 0)
			return 0.0;

		return getMarginal(variable, 1);
	}

	private void setupProblem() {

		// build cache for candidates
		IJ.log("Precaching most likely candidates...");

		for (int s = 0; s < this.sliceCandidates.size(); s++)
			for (Candidate candidate : this.sliceCandidates.get(s))
				candidate.clearCaches();

		for (int s = 0; s < this.sliceCandidates.size() - 1; s++) {

			IJ.log("Slice " + (s+1) + "...");
			int numCandidates = this.sliceCandidates.get(s).size();
			int numCached     = 0;

			for (Candidate candidate : this.sliceCandidates.get(s)) {

				candidate.cacheMostSimilarCandidates(this.sliceCandidates.get(s+1), assignmentModel);
				numCached++;
				IJ.showProgress(numCached, numCandidates);
			}
		}

		IJ.log("Precaching neighbors...");
		for (int s = 0; s < this.sliceCandidates.size(); s++)
			for (Candidate candidate : this.sliceCandidates.get(s))
				candidate.findNeighbors(this.sliceCandidates.get(s));

		IJ.log("Done.");
		IJ.log("searching for possible bisections");
		findPossibleBisections();
		IJ.log("Done.");

		int numVariables   = computeNumVariables();
		int numConstraints = computeNumConstraints();

		int numConsUsed = 0;

		IJ.log("setting up problem: " + numVariables + " variables, " + numConstraints + " constraints");

		if (computeMarginals)
			lpSolver = new GraphicalModelSolver(numVariables, numConstraints);
		else
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
				for (Candidate mergeNode : candidate.mergeTargetOf()) {
					variableNums.add(getVariableNum(mergeNode, candidate));
					coefficients.add(1.0);
				}

				// ...and all split edges this target candidate is involved
				// in...
				for (Candidate neighbor : candidate.splitSources().keySet()) {

					Candidate smaller = (neighbor.getId() < candidate.getId() ? neighbor  : candidate);
					Candidate bigger  = (neighbor.getId() < candidate.getId() ? candidate : neighbor);

					Candidate splitNode = splitNodes.get(smaller).get(bigger);

					for (Candidate splitSource : candidate.splitSources().get(neighbor)) {
						variableNums.add(getVariableNum(splitSource, splitNode));
						coefficients.add(1.0);
					}
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

				// ...and all split edges from this candidate...
				for (Candidate splitNode : candidate.splitSourceOf()) {
					variableNums.add(getVariableNum(candidate, splitNode));
					coefficients.add(-1.0);
				}

				// ...and all merge edges this source candidate is involved in...
				for (Candidate neighbor : candidate.mergeTargets().keySet()) {

					Candidate smaller = (neighbor.getId() < candidate.getId() ? neighbor  : candidate);
					Candidate bigger  = (neighbor.getId() < candidate.getId() ? candidate : neighbor);

					Candidate mergeNode = mergeNodes.get(smaller).get(bigger);

					for (Candidate mergeTarget : candidate.mergeTargets().get(neighbor)) {
						variableNums.add(getVariableNum(mergeNode, mergeTarget));
						coefficients.add(-1.0);
					}
				}

				// ...and the edge to the death node...
				variableNums.add(getVariableNum(candidate, deathNode));
				coefficients.add(-1.0);

				// ...has to be exactly zero
				lpSolver.addConstraint(variableNums, coefficients, 0, 0.0);

				numConsUsed++;
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
					for (Candidate neighbor : member.mergeTargets().keySet()) {

						Candidate smaller = (neighbor.getId() < member.getId() ? neighbor : member);
						Candidate bigger  = (neighbor.getId() < member.getId() ? member   : neighbor);

						for (Candidate mergeTarget : member.mergeTargets().get(neighbor)) {
							variableNums.add(getVariableNum(mergeNodes.get(smaller).get(bigger), mergeTarget));
							coefficients.add(1.0);
						}
					}

					// ...and all outgoinh split edges...
					for (Candidate splitNode : member.splitSourceOf()) {

						variableNums.add(getVariableNum(member, splitNode));
						coefficients.add(1.0);
					}
				}

				// ...has to be equal or at most one
				lpSolver.addConstraint(variableNums, coefficients, (FullMarginSlices ? 0 : -1), 1.0);

				numConsUsed++;
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
						for (Candidate mergeNode : member.mergeTargetOf()) {
							variableNums.add(getVariableNum(mergeNode, member));
							coefficients.add(1.0);
						}

						// ...and all incoming split edges...
						for (Candidate neighbor : member.splitSources().keySet()) {

							Candidate smaller = (member.getId() < neighbor.getId() ? member   : neighbor);
							Candidate bigger  = (member.getId() < neighbor.getId() ? neighbor : member);

							Candidate splitNode = splitNodes.get(smaller).get(bigger);

							for (Candidate splitSource : member.splitSources().get(neighbor)) {

								variableNums.add(getVariableNum(splitSource, splitNode));
								coefficients.add(1.0);
							}
						}

						// not for last slice
						if (s < sliceCandidates.size() - 1) {
							// ...and all incoming emerge edges...
							variableNums.add(getVariableNum(emergeNode, member));
							coefficients.add(1.0);
						}
					}

					if (s < sliceCandidates.size() - 1)
						// ...has to be exactly one for intermediate slices
						lpSolver.addConstraint(variableNums, coefficients, 0, 1.0);
					else
						// ...has to be at most one for the last slice
						lpSolver.addConstraint(variableNums, coefficients, (FullMarginSlices ? 0 : -1), 1.0);

					numConsUsed++;
				}

		IJ.log("" + numConsUsed + " constraints set, " + nextNodeId + " variables used");
		IJ.log("setting objective function...");

		/*
		 * OBJECTIVE FUNCTION
		 */

		Vector<Integer> variableNums = new Vector<Integer>();
		Vector<Double>  coefficients = new Vector<Double>();

		// all but last slice
		for (int s = 0; s < sliceCandidates.size() - 1; s++) {

			// for each continuation
			for (Candidate sourceCandidate : sliceCandidates.get(s))
				for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates()) {
					variableNums.add(getVariableNum(sourceCandidate, targetCandidate));
					coefficients.add(assignmentModel.costContinuation(sourceCandidate, targetCandidate, true));
				}

			// for each merge
			for (Candidate candidate : sliceCandidates.get(s))
				for (Candidate neighbor : candidate.mergeTargets().keySet()) {

					// don't count them twice
					if (candidate.getId() > neighbor.getId())
						continue;

					for (Candidate mergeTarget : candidate.mergeTargets().get(neighbor)) {

						variableNums.add(getVariableNum(mergeNodes.get(candidate).get(neighbor), mergeTarget));
						coefficients.add(assignmentModel.costBisect(mergeTarget, candidate, neighbor));
					}
				}
		}

		// all but first slice
		for (int s = 1; s < sliceCandidates.size(); s++) {

			// for each split
			for (Candidate candidate : sliceCandidates.get(s))
				for (Candidate neighbor : candidate.splitSources().keySet()) {

					// don't count them twice
					if (candidate.getId() > neighbor.getId())
						continue;

					if (splitNodes.get(candidate).get(neighbor) != null)
						for (Candidate splitSource : candidate.splitSources().get(neighbor)) {

							variableNums.add(getVariableNum(splitSource, splitNodes.get(candidate).get(neighbor)));
							coefficients.add(assignmentModel.costBisect(splitSource, candidate, neighbor));
						}
				}
		}

		// all but first and last slice
		for (int s = 1; s < sliceCandidates.size() - 1; s++) {
			// for each emerge
			for (Candidate targetCandidate : sliceCandidates.get(s)) {
				variableNums.add(getVariableNum(emergeNode, targetCandidate));
				coefficients.add(assignmentModel.costEnd(targetCandidate));
			}
			// for each death
			for (Candidate sourceCandidate : sliceCandidates.get(s)) {
				variableNums.add(getVariableNum(sourceCandidate, deathNode));
				coefficients.add(assignmentModel.costEnd(sourceCandidate));
			}
		}

		lpSolver.setObjective(variableNums, coefficients);
	}

	private boolean solveProblem() {

		long time = System.currentTimeMillis();
		int result = lpSolver.solve(2);
		time = System.currentTimeMillis() - time;
		IJ.log("sover finished in " + time/1000.0 + " seconds");

		if (result != 0) {
			IJ.log("LP problem could not be solved.");
			return false;
		}

		return true;
	}


	private void findPossibleBisections() {

		mergeNodes   = new HashMap<Candidate, HashMap<Candidate, Candidate>>();
		splitNodes   = new HashMap<Candidate, HashMap<Candidate, Candidate>>();

		// all slices
		for (int s = 0; s < sliceCandidates.size(); s++) {

			// merges (all but last slice)
			if (s < sliceCandidates.size() - 1)
				for (Candidate sourceCandidate : sliceCandidates.get(s))
					mergeNodes.put(sourceCandidate, new HashMap<Candidate, Candidate>());

			// splits (all but first slice)
			if (s > 0)
				for (Candidate targetCandidate : sliceCandidates.get(s))
					splitNodes.put(targetCandidate, new HashMap<Candidate, Candidate>());

			// all pairs of candidates in one slice
			for (Candidate candidate : sliceCandidates.get(s))
				for (Candidate neighbor : candidate.getNeighbors()) {

					Candidate smaller = (candidate.getId() < neighbor.getId() ? candidate : neighbor);
					Candidate bigger  = (candidate.getId() < neighbor.getId() ? neighbor  : candidate);

					// merges (all but last slice)
					if (s < sliceCandidates.size() - 1) {

						// has this pair been considered already?
						if (mergeNodes.get(smaller).get(bigger) == null) {

							Vector<Candidate> targets = new Vector<Candidate>();
							Candidate      mergeNode  = new Candidate();

							for (Candidate mergeTarget : smaller.getMostLikelyCandidates())
								if (bigger.getMostLikelyCandidates().contains(mergeTarget)) {
									targets.add(mergeTarget);
									mergeTarget.addMergeTargetOf(mergeNode);
								}

							if (targets.size() > 0) {

								mergeNodes.get(smaller).put(bigger, mergeNode);
								smaller.addMergePartner(bigger, targets);
								bigger.addMergePartner(smaller, targets);
							}
						}
					}

					// splits (all but first slice)
					if (s > 0) {

						// has this pair been considered already?
						if (splitNodes.get(smaller).get(bigger) != null)
							continue;

						Vector<Candidate> sources = new Vector<Candidate>();
						Candidate      splitNode  = new Candidate();

						for (Candidate splitSource : smaller.getMostLikelyOf())
							if (bigger.getMostLikelyOf().contains(splitSource)) {
								sources.add(splitSource);
								splitSource.addSplitSourceOf(splitNode);
							}

						if (sources.size() > 0) {

							splitNodes.get(smaller).put(bigger, splitNode);
							smaller.addSplitPartner(bigger, sources);
							bigger.addSplitPartner(smaller, sources);
						}
					}
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
				numVariables += smaller.mergeTargets().get(bigger).size();

		// for each possible split of a source candidate to two target
		// candidates
		for (Candidate smaller : splitNodes.keySet())
			for (Candidate bigger : splitNodes.get(smaller).keySet())
				numVariables += smaller.splitSources().get(bigger).size();

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
		for (int s = 0; s < sliceCandidates.size() - 1; s++) {

			Assignment assignment = new Assignment();

				// each continuation
				for (Candidate sourceCandidate : sliceCandidates.get(s))
					for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates())
						if (getVariableValue(sourceCandidate, targetCandidate) == 1) {
							assignment.add(new OneToOneAssignment(sourceCandidate, targetCandidate));
							System.out.println("" + sourceCandidate.getId() + " ⇒ " + targetCandidate.getId());
						}

				// each merge
				for (Candidate candidate : sliceCandidates.get(s))
					for (Candidate neighbor : candidate.mergeTargets().keySet()) {

						// don't count them twice
						if (candidate.getId() > neighbor.getId())
							continue;

						for (Candidate mergeTarget : candidate.mergeTargets().get(neighbor))
							if (getVariableValue(mergeNodes.get(candidate).get(neighbor), mergeTarget) == 1) {

								assignment.add(new MergeAssignment(candidate, neighbor, mergeTarget));
								System.out.println("" + candidate.getId() + ", " + neighbor.getId() + " ⇒ " + mergeTarget.getId());

								// there can only be one merge target
								break;
							}
					}

				// each split
				for (Candidate candidate : sliceCandidates.get(s+1))
					for (Candidate neighbor : candidate.splitSources().keySet()) {

						// don't count them twice
						if (candidate.getId() > neighbor.getId())
							continue;

						for (Candidate splitSource : candidate.splitSources().get(neighbor))
							if (getVariableValue(splitSource, splitNodes.get(candidate).get(neighbor)) == 1) {

								assignment.add(new SplitAssignment(splitSource, candidate, neighbor));
								System.out.println("" + splitSource.getId() + " ⇒ " + candidate.getId() + ", " + neighbor.getId());

								// there can only be one split target
								break;
							}
					}

			// not the first slice
			if (s > 0)
				// each death
				for (Candidate sourceCandidate : sliceCandidates.get(s))
					if (getVariableValue(sourceCandidate, deathNode) == 1) {
						assignment.add(new OneToOneAssignment(sourceCandidate, deathNode));
						System.out.println("" + sourceCandidate.getId() + " ⇒ D");
					}

			// not the last but one slice
			if (s < sliceCandidates.size() - 2)
				// each emerge
				for (Candidate targetCandidate : sliceCandidates.get(s+1))
					if (getVariableValue(emergeNode, targetCandidate) == 1) {
						assignment.add(new OneToOneAssignment(emergeNode, targetCandidate));
						System.out.println("E ⇒ " + targetCandidate.getId());
					}

			sequence.add(assignment);
		}

		assert(sequence.consistent());
		return sequence;
	}

	/**
	 * Each pair of source-to-target candidates represents an edge in the graph,
	 * which in turn is modelled as a variable. This function returns the
	 * variable number a given pair is associated to. If
	 * <code>createOnDemand</code> is set, the variable num will be created if
	 * it does not exist yet. Otherwise, return value of -1 indicates a
	 * non-existing varible.
	 */
	private int getVariableNum(Candidate candidate1, Candidate candidate2, boolean createOnDemand) {

		if (candidate1.getId() > candidate2.getId()) {
			Candidate tmp = candidate1;
			candidate1 = candidate2;
			candidate2 = tmp;
		}

		HashMap<Candidate, Integer> m = nodeNums.get(candidate1);

		if (m == null) {
			if (!createOnDemand)
				return -1;
			m = new HashMap<Candidate, Integer>();
			nodeNums.put(candidate1, m);
		}

		Integer id = m.get(candidate2);

		if (id != null)
			return id;
		else {
			if (!createOnDemand)
				return -1;
			m.put(candidate2, new Integer(nextNodeId));
			nextNodeId++;

			return nextNodeId - 1;
		}
	}
	/**
	 * Default call creates new variable nums.
	 */
	private int getVariableNum(Candidate candidate1, Candidate candidate2) {
		return getVariableNum(candidate1, candidate2, true);
	}

	private int getVariableValue(int variable) {

		double value = lpSolver.getValue(variable);
		
		if (value > 0.5)
			return 1;
		return 0;
	}
	private int getVariableValue(Candidate from, Candidate to) {

		return getVariableValue(getVariableNum(from, to));
	}

	private double getMarginal(int variable, int value) {

		return lpSolver.getMarginal(variable, value);
	}
}
