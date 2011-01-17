
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.SWIGTYPE_p_double;
import org.gnu.glpk.SWIGTYPE_p_int;
import org.gnu.glpk.glp_prob;
import org.gnu.glpk.glp_smcp;

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
	private final boolean hypothesisConsistency = true;
	
	/*
	 * interna
	 */

	private Vector<Candidate> sourceCandidates;
	private Vector<Candidate> targetCandidates;
	
	private HashMap<Candidate, HashMap<Candidate, Candidate>> mergeNodes;

	private HashMap<Candidate, HashMap<Candidate, Long>> nodeNums;
	private long nextNodeId = 1;

	// dummy candidates
	private Candidate deathNode;
	private Candidate emergeNode;
	private Candidate neglectNode;
	private Candidate neglectCollectNode;

	// linear program
	glp_prob problem;

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

		this.mergeNodes = new HashMap<Candidate, HashMap<Candidate, Candidate>>();

		this.nodeNums = new HashMap<Candidate, HashMap<Candidate, Long>>();

		this.deathNode = new Candidate(0, 0, new double[]{0.0, 0.0});
		this.emergeNode = new Candidate(0, 0, new double[]{0.0, 0.0});
		this.neglectNode = new Candidate(0, 0, new double[]{0.0, 0.0});
		this.neglectCollectNode = new Candidate(0, 0, new double[]{0.0, 0.0});
	}

	public Assignment findBestAssignment() {

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
		int requiredFlow   = computeRequiredFlow();

		IJ.log("setting up problem: " + numVariables + " flow variables, " + numConstraints + " constraints, required flow " + requiredFlow);

		// create problem
		problem = GLPK.glp_create_prob();
		GLPK.glp_set_prob_name(problem, "assignment problem");

		// setup variables
		GLPK.glp_add_cols(problem, numVariables);

		for (int i = 1; i <= numVariables; i++) {

			GLPK.glp_set_col_name(problem, i, "x" + i);
			GLPK.glp_set_col_kind(problem, i, GLPKConstants.GLP_CV);
			GLPK.glp_set_col_bnds(problem, i, GLPKConstants.GLP_DB, 0.0, requiredFlow);
		}

		// setup constraints
		int i = 1;
		GLPK.glp_add_rows(problem, numConstraints);

		/*
		 * MERGES
		 */

		// for each possible merge of two source candidates
		for (Candidate smaller : mergeNodes.keySet())
			for (Candidate bigger : mergeNodes.get(smaller).keySet()) {

				Set<Candidate> jointPartners = new HashSet<Candidate>();

				jointPartners.addAll(smaller.getMostLikelyCandidates());
				jointPartners.addAll(bigger.getMostLikelyCandidates());

				IJ.log("creating merge node for candidates " + smaller.getId() + " and " + bigger.getId());

				// get dummy merge node
				Candidate mergeNode = mergeNodes.get(smaller).get(bigger);

				// sum of input edges to merge node...
				int               numEdges = 3 + jointPartners.size();
				SWIGTYPE_p_int    varNums  = GLPK.new_intArray(numEdges + 1);
				SWIGTYPE_p_double varCoefs = GLPK.new_doubleArray(numEdges + 1);
				int               index    = 1;

				// ...from smaller and bigger...
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(smaller, mergeNode));
				GLPK.doubleArray_setitem(varCoefs, index, 1.0);
				index++;
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(bigger, mergeNode));
				GLPK.doubleArray_setitem(varCoefs, index, 1.0);
				index++;

				// ...minus sum of output edges from merge node...

				// ...to death...
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(mergeNode, deathNode));
				GLPK.doubleArray_setitem(varCoefs, index, -1.0);
				index++;
				// ...and all partners...
				for (Candidate partner : jointPartners) {
					GLPK.intArray_setitem(varNums, index, (int)getVariableNum(mergeNode, partner));
					GLPK.doubleArray_setitem(varCoefs, index, -1.0);
					index++;
				}

				// ...has to be zero
				GLPK.glp_set_row_name(problem, i, "c" + i);
				GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, 0.0, 0.0);
				GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);
				i++;

				numEdges = 2;
				varNums  = GLPK.new_intArray(numEdges + 1);
				varCoefs = GLPK.new_doubleArray(numEdges + 1);
				index    = 1;

				// flow from smaller to merge node...
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(smaller, mergeNode));
				GLPK.doubleArray_setitem(varCoefs, index, 1.0);
				index++;

				// ...minus flow from merge node to delete node...
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(mergeNode, deathNode));
				GLPK.doubleArray_setitem(varCoefs, index, -1.0);
				index++;

				// ...has to be zero
				GLPK.glp_set_row_name(problem, i, "c" + i);
				GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, 0.0, 0.0);
				GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);
				i++;

				numEdges = 2;
				varNums  = GLPK.new_intArray(numEdges + 1);
				varCoefs = GLPK.new_doubleArray(numEdges + 1);
				index    = 1;

				// flow from bigger to merge node...
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(bigger, mergeNode));
				GLPK.doubleArray_setitem(varCoefs, index, 1.0);
				index++;

				// ...minus flow from merge node to delete node...
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(mergeNode, deathNode));
				GLPK.doubleArray_setitem(varCoefs, index, -1.0);
				index++;

				// ...has to be zero
				GLPK.glp_set_row_name(problem, i, "c" + i);
				GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, 0.0, 0.0);
				GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);
				i++;
			}

		/*
		 * OUTGOING FLOW
		 */

		// outgoing flow for each source candidate
		for (Candidate sourceCandidate : sourceCandidates) {

			int numMergeNodes = mergeNodes.get(sourceCandidate).keySet().size();
			for (Candidate mergePartner : mergeNodes.keySet())
				if (mergeNodes.get(mergePartner).keySet().contains(sourceCandidate))
					numMergeNodes++;

			int            numEdges = sourceCandidate.getMostLikelyCandidates().size() + numMergeNodes + 1;
			SWIGTYPE_p_int varNums  = GLPK.new_intArray(numEdges + 1);
			int            index    = 1;

			// to the target candidates
			for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates()) {

				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(sourceCandidate, targetCandidate));
				index++;
			}

			// to the merge nodes
			for (Candidate bigger : mergeNodes.get(sourceCandidate).keySet()) {

				Candidate mergeNode = mergeNodes.get(sourceCandidate).get(bigger);
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(sourceCandidate, mergeNode));
				index++;
			}
			for (Candidate smaller : mergeNodes.keySet())
				if (mergeNodes.get(smaller).keySet().contains(sourceCandidate)) {

					Candidate mergeNode = mergeNodes.get(smaller).get(sourceCandidate);
					GLPK.intArray_setitem(varNums, index, (int)getVariableNum(sourceCandidate, mergeNode));
					index++;
				}

			// to the delete node
			GLPK.intArray_setitem(varNums, index, (int)getVariableNum(sourceCandidate, deathNode));

			GLPK.glp_set_row_name(problem, i, "c" + i);
			GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, 1.0, 1.0);

			SWIGTYPE_p_double varCoefs = GLPK.new_doubleArray(numEdges + 1);
			for (int j = 1; j <= numEdges; j++)
				GLPK.doubleArray_setitem(varCoefs, j, 1.0);

			GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

			i++;
		}

		/*
		 * INCOMING FLOW
		 */

		// incoming flow for each target candidate
		for (Candidate targetCandidate : targetCandidates) {

			// get all source candidates that have targetCandidate as target
			Vector<Candidate> partners = targetCandidate.getMostLikelyOf();

			// get all merge nodes that have target candidate as target
			for (Candidate smaller : mergeNodes.keySet())
				for (Candidate bigger : mergeNodes.get(smaller).keySet())
					if (smaller.getMostLikelyCandidates().contains(targetCandidate) ||
					    bigger.getMostLikelyCandidates().contains(targetCandidate))
						partners.add(mergeNodes.get(smaller).get(bigger));

			int numEdges = partners.size() + 2;
			SWIGTYPE_p_int varNums  = GLPK.new_intArray(numEdges + 1);
			int            index    = 1;

			// from the source candidates
			for (Candidate sourceCandidate : partners) {

				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(sourceCandidate, targetCandidate));
				index++;
			}

			// from the neglect node
			GLPK.intArray_setitem(varNums, index, (int)getVariableNum(targetCandidate, neglectNode));
			index++;

			// from the emerge node
			GLPK.intArray_setitem(varNums, index, (int)getVariableNum(targetCandidate, emergeNode));
			index++;

			GLPK.glp_set_row_name(problem, i, "c" + i);
			GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, 1.0, 1.0);

			SWIGTYPE_p_double varCoefs = GLPK.new_doubleArray(numEdges + 1);
			for (int j = 1; j <= numEdges; j++)
				GLPK.doubleArray_setitem(varCoefs, j, 1.0);

			GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

			i++;
		}

		/*
		 * NEGLECTION
		 */

		// outgoing flow from the neglect node
		int            numEdges = targetCandidates.size() + 1;
		SWIGTYPE_p_int varNums  = GLPK.new_intArray(numEdges + 1);
		int            index    = 1;

		// to the target candidates
		for (Candidate targetCandidate : targetCandidates) {

			GLPK.intArray_setitem(varNums, index, (int)getVariableNum(targetCandidate, neglectNode));
			index++;
		}

		// to the neglect-collect node
		GLPK.intArray_setitem(varNums, index, (int)getVariableNum(neglectNode, neglectCollectNode));
		index++;

		SWIGTYPE_p_double varCoefs = GLPK.new_doubleArray(numEdges + 1);
		for (int j = 1; j <= numEdges; j++)
			GLPK.doubleArray_setitem(varCoefs, j, 1.0);

		// should be number of target candidates
		GLPK.glp_set_row_name(problem, i, "c" + i);
		GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, targetCandidates.size(), targetCandidates.size());

		GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

		i++;

		// outgoing flow from the emerge node
		numEdges = targetCandidates.size() + 2;
		varNums  = GLPK.new_intArray(numEdges + 1);
		index    = 1;

		// to the target candidates
		for (Candidate targetCandidate : targetCandidates) {

			GLPK.intArray_setitem(varNums, index, (int)getVariableNum(targetCandidate, emergeNode));
			index++;
		}

		// to the neglect-collect node
		GLPK.intArray_setitem(varNums, index, (int)getVariableNum(emergeNode, neglectCollectNode));
		index++;

		// to the death node
		GLPK.intArray_setitem(varNums, index, (int)getVariableNum(emergeNode, deathNode));
		index++;

		varCoefs = GLPK.new_doubleArray(numEdges + 1);
		for (int j = 1; j <= numEdges; j++)
			GLPK.doubleArray_setitem(varCoefs, j, 1.0);

		// should be number of target candidates
		GLPK.glp_set_row_name(problem, i, "c" + i);
		GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, targetCandidates.size(), targetCandidates.size());

		GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

		i++;

		// incoming flow to the death node
		int numMergeNodes = 0;
		for (Candidate smaller : mergeNodes.keySet())
			numMergeNodes += mergeNodes.get(smaller).keySet().size();

		numEdges = sourceCandidates.size() + numMergeNodes + 1;
		varNums  = GLPK.new_intArray(numEdges + 1);
		index    = 1;

		// from the source candidates
		for (Candidate sourceCandidate: sourceCandidates) {

			GLPK.intArray_setitem(varNums, index, (int)getVariableNum(sourceCandidate, deathNode));
			index++;
		}

		// from the merge nodes
		for (Candidate smaller : mergeNodes.keySet())
			for (Candidate bigger : mergeNodes.get(smaller).keySet()) {
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(mergeNodes.get(smaller).get(bigger), deathNode));
				index++;
			}

		// from the emerge node
		GLPK.intArray_setitem(varNums, index, (int)getVariableNum(emergeNode, deathNode));
		index++;

		varCoefs = GLPK.new_doubleArray(numEdges + 1);
		for (int j = 1; j <= numEdges; j++)
			GLPK.doubleArray_setitem(varCoefs, j, 1.0);

		// should be number of source candidates
		GLPK.glp_set_row_name(problem, i, "c" + i);
		GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, sourceCandidates.size(), sourceCandidates.size());

		GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

		i++;

		// incoming flow to the neglect-collect node
		numEdges = 2;
		varNums  = GLPK.new_intArray(numEdges + 1);
		index    = 1;

		// from the emerge node
		GLPK.intArray_setitem(varNums, index, (int)getVariableNum(emergeNode, neglectCollectNode));
		index++;

		// from the neglect node
		GLPK.intArray_setitem(varNums, index, (int)getVariableNum(neglectNode, neglectCollectNode));
		index++;

		varCoefs = GLPK.new_doubleArray(numEdges + 1);
		for (int j = 1; j <= numEdges; j++)
			GLPK.doubleArray_setitem(varCoefs, j, 1.0);

		// should be number of target candidates
		GLPK.glp_set_row_name(problem, i, "c" + i);
		GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, targetCandidates.size(), targetCandidates.size());

		GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

		i++;

		/*
		 * HYPOTHESISES
		 */

		// hypothesis consistency - for each path in the component tree of the
		// target candidates, i.e., for each child
		if (hypothesisConsistency) {
			for (Candidate targetCandidate : targetCandidates)
				if (targetCandidate.getChildren().size() == 0) {

					// the path that leads to this child
					Vector<Candidate> path     = new Vector<Candidate>();

					Candidate pathMember = targetCandidate;

					while (pathMember != null) {
						path.add(pathMember);
						pathMember = pathMember.getParent();
					}

					// all pairs of nodes, that are connected by an edge where the
					// target is a member of the path and the source is not the
					// neglect node
					Vector<Candidate[]> pairs = new Vector<Candidate[]>();

					for (Candidate member : path) {

						// get all source nodes that have a connection to member
						for (Candidate sourceCandidate : sourceCandidates)
							if (sourceCandidate.getMostLikelyCandidates().contains(member))
								pairs.add(new Candidate[]{sourceCandidate, member});

						// add the emerge node
						pairs.add(new Candidate[]{emergeNode, member});
					}


					// now we are ready to state the constraint: the sum of all incoming
					// flows to a path has to be one (disregarding the incoming connections
					// from the neglect node)

					numEdges = pairs.size();
					varNums  = GLPK.new_intArray(numEdges + 1);
					varCoefs = GLPK.new_doubleArray(numEdges + 1);
					index    = 1;

					for (Candidate[] pair : pairs) {

						GLPK.intArray_setitem(varNums, index, (int)getVariableNum(pair[0], pair[1]));
						GLPK.doubleArray_setitem(varCoefs, index, 1.0);
						index++;
					}

					// should be smaller or equal to one
					GLPK.glp_set_row_name(problem, i, "c" + i);
					GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_DB, 0.0, 1.0);

					GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

					i++;
				}
		}


		IJ.log("" + (i-1) + " constraints set.");

		/*
		 * OBJECTIVE FUNCTION
		 */

		// setup objective function
		GLPK.glp_set_obj_name(problem, "min cost");
		GLPK.glp_set_obj_dir(problem, GLPKConstants.GLP_MIN);

		// costs for each flow in the network:

		// source candidates to target candidates and death
		for (Candidate sourceCandidate : sourceCandidates) {

			for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates())
				GLPK.glp_set_obj_coef(problem, (int)getVariableNum(sourceCandidate, targetCandidate),
				                      AssignmentModel.negLogPAppearance(sourceCandidate, targetCandidate));

			GLPK.glp_set_obj_coef(problem, (int)getVariableNum(sourceCandidate, deathNode),
			                      AssignmentModel.negLogPriorDeath);
		}

		// merge nodes to target candidates
		for (Candidate smaller : mergeNodes.keySet())
			for (Candidate bigger : mergeNodes.get(smaller).keySet()) {

				Set<Candidate> targets = new HashSet<Candidate>(smaller.getMostLikelyCandidates());
				targets.addAll(bigger.getMostLikelyCandidates());

				for (Candidate target : targets)
					GLPK.glp_set_obj_coef(problem, (int)getVariableNum(mergeNodes.get(smaller).get(bigger), target),
										  AssignmentModel.negLogPriorSplit);
			}

		// neglect to target candidates and neglect-collect
		for (Candidate targetCandidate : targetCandidates)
			GLPK.glp_set_obj_coef(problem, (int)getVariableNum(neglectNode, targetCandidate),
			                      0.0);
		GLPK.glp_set_obj_coef(problem, (int)getVariableNum(neglectNode, neglectCollectNode),
		                      0.0);

		// emerge to target candidates, neglect-collect, and death
		for (Candidate targetCandidate : targetCandidates)
			GLPK.glp_set_obj_coef(problem, (int)getVariableNum(emergeNode, targetCandidate),
			                      AssignmentModel.negLogPriorDeath);
		GLPK.glp_set_obj_coef(problem, (int)getVariableNum(emergeNode, neglectCollectNode),
		                      0.0);
		GLPK.glp_set_obj_coef(problem, (int)getVariableNum(emergeNode, deathNode),
		                      AssignmentModel.negLogPriorContinuation);
	}

	private void findPossibleMergers() {

		mergeNodes = new HashMap<Candidate, HashMap<Candidate, Candidate>>();

		for (Candidate sourceCandidate : sourceCandidates)
			mergeNodes.put(sourceCandidate, new HashMap<Candidate, Candidate>());

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

				mergeNodes.get(smaller).put(bigger, new Candidate(0, 0, new double[]{0.0, 0.0}));
			}
	}

	private int computeNumVariables() {

		// the number of variables is the number of edges for which we would
		// like to calculate the flow
		
		int numVariables = 0;

		// for the connections between the source and target candidates
		for (Candidate sourceCandidate : sourceCandidates)
			numVariables += sourceCandidate.getMostLikelyCandidates().size();

		// for each possible merge of two source candidates
		for (Candidate smaller : mergeNodes.keySet())
			for (Candidate bigger : mergeNodes.get(smaller).keySet()) {

				Set<Candidate> jointPartners = new HashSet<Candidate>();

				jointPartners.addAll(smaller.getMostLikelyCandidates());
				jointPartners.addAll(bigger.getMostLikelyCandidates());

				// two incoming edges, one to delete, and n to the possible
				// merge target
				numVariables += 3 + jointPartners.size();
			}

		// for the connection of each source candidate to the delete node
		numVariables += sourceCandidates.size();

		// for the connections of the emerge node to the target candidates
		numVariables += targetCandidates.size();

		// for the connection of the emerge node to the neglect-collector node
		// and the delete node
		numVariables += 2;

		// for the connections of the neglect node to the target candidates
		numVariables += targetCandidates.size();

		// for the connection of the negelct node to the neglect-collect node
		numVariables += 1;

		// for all other connections, the flow is known already (due to the flow
		// requirement)

		return numVariables;
	}

	private int computeNumConstraints() {

		int numConstraints = 0;

		// outgoing flow for each source candidate
		numConstraints += sourceCandidates.size();

		// incoming flow for each target candidate
		numConstraints += targetCandidates.size();

		// for each possible merge of two source candidates
		for (Candidate smaller : mergeNodes.keySet())
			for (Candidate bigger : mergeNodes.get(smaller).keySet()) {

				// flow conservation, synchronisation of incoming edges with
				// death node
				numConstraints += 3;
			}

		// incoming or outgoing flow for neglect, neglect-collect, emerge, and
		// death node
		numConstraints += 4;

		// hypothesis consistency - one constraint for each path in the
		// component tree of the target candidates, i.e., for each child
		if (hypothesisConsistency)
			for (Candidate targetCandidate : targetCandidates)
				if (targetCandidate.getChildren().size() == 0)
					numConstraints++;

		return numConstraints;
	}

	private int computeRequiredFlow() {

		// the required flow is the sum of all capacities going out of the
		// source node

		int requiredFlow = 0;

		// to the neglect node
		requiredFlow += targetCandidates.size();

		// to each source candidate node
		requiredFlow += sourceCandidates.size();

		// to the emerge node
		requiredFlow += targetCandidates.size();

		return requiredFlow;
	}

	/**
	 * Each pair of source-to-target candidates represents an edge in the graph,
	 * which in turn is modelled as a variable. This function returns the
	 * variable number a given pair is associated to.
	 */
	private long getVariableNum(Candidate candidate1, Candidate candidate2) {

		if (candidate1.getId() > candidate2.getId()) {
			Candidate tmp = candidate1;
			candidate1 = candidate2;
			candidate2 = tmp;
		}

		HashMap<Candidate, Long> m = nodeNums.get(candidate1);

		if (m == null) {
			m = new HashMap<Candidate, Long>();
			nodeNums.put(candidate1, m);
		}

		Long id = m.get(candidate2);

		if (id != null)
			return id;
		else {
			m.put(candidate2, new Long(nextNodeId));
			nextNodeId++;

			return nextNodeId - 1;
		}
	}

	private void solveProblem() {

		glp_smcp parameters = new glp_smcp();

		GLPK.glp_init_smcp(parameters);

		int result = GLPK.glp_simplex(problem, parameters);

		if (result != 0)
			IJ.log("LP problem could not be solved.");
	}

	private Assignment readAssignment() {

		Assignment assignment = new Assignment();

		// each continuation
		for (Candidate sourceCandidate : sourceCandidates)
			for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates())
				if (getFlow(sourceCandidate, targetCandidate) == 1)
					assignment.add(new SingleAssignment(sourceCandidate, targetCandidate));

		// each merge
		for (Candidate smaller : mergeNodes.keySet())
			for (Candidate bigger : mergeNodes.get(smaller).keySet()) {

				Set<Candidate> targets = new HashSet<Candidate>(smaller.getMostLikelyCandidates());
				targets.addAll(bigger.getMostLikelyCandidates());

				for (Candidate target : targets) {
					IJ.log("checking flow from merge node " + smaller.getId() + "+" + bigger.getId() +
					       " to " + target.getId());
					if (getFlow(mergeNodes.get(smaller).get(bigger), target) == 1) {
						IJ.log("Merge detected.");
						assignment.add(new SingleAssignment(smaller, target));
						assignment.add(new SingleAssignment(bigger, target));
					} else
						IJ.log("no merge");
				}
			}

		// each death
		for (Candidate sourceCandidate : sourceCandidates)
			if (getFlow(sourceCandidate, deathNode) == 1)
				IJ.log("candidate " + sourceCandidate.getId() + " died");

		// each emerge
		for (Candidate targetCandidate : targetCandidates)
			if (getFlow(emergeNode, targetCandidate) == 1)
				IJ.log("candidate " + targetCandidate.getId() + " emerged");

		// TODO:
		// • pass emerged nodes to next assignment search

		return assignment;
	}

	private int getFlow(Candidate from, Candidate to) {

		double flow = GLPK.glp_get_col_prim(problem, (int)getVariableNum(from, to));
		
		if (flow > 0.0001 && flow < 0.9999)
			IJ.log("Oh no! One of the flow variables is not integral: " + flow);

		if (flow > 0.5)
			return 1;
		return 0;
	}
}
