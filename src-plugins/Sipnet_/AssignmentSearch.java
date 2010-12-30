
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.Vector;

import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.GlpkException;
import org.gnu.glpk.SWIGTYPE_p_double;
import org.gnu.glpk.SWIGTYPE_p_int;
import org.gnu.glpk.glp_prob;

import ij.IJ;

public class AssignmentSearch {

	public static final int MaxTargetCandidates = 5;
	public static final int MinTargetCandidates = 1;

	// number of neighbors to consider for neighbor offset
	public static final int NumNeighbors = 3;

	//private static final double MinPAssignment       = 1e-20;
	public static final double MaxNegLogPAssignment = 1e25; //-Math.log(MinPAssignment);

	private Vector<Candidate> sourceCandidates;
	private Vector<Candidate> targetCandidates;

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

		this.deathNode = new Candidate(0, 0, new double[]{0.0, 0.0});
		this.emergeNode = new Candidate(0, 0, new double[]{0.0, 0.0});
		this.neglectNode = new Candidate(0, 0, new double[]{0.0, 0.0});
		this.neglectCollectNode = new Candidate(0, 0, new double[]{0.0, 0.0});
	}

	public Assignment findBestAssignment() {

		try {

			setupProblem();

		} catch (GlpkException e) {

			e.printStackTrace();

		}

		return new Assignment();
	}

	public Assignment findNextBestPath() {

		return new Assignment();
	}

	private void setupProblem() {

		int numVariables   = computeNumVariables();
		int numConstraints = computeNumConstraints();
		int requiredFlow   = computeRequiredFlow();

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
		GLPK.glp_add_rows(problem, numConstraints);

		// outgoing flow for each source candidate
		int i = 1;
		for (Candidate sourceCandidate : sourceCandidates) {

			int            numEdges = sourceCandidate.getMostLikelyCandidates().size() + 1;
			SWIGTYPE_p_int varNums  = GLPK.new_intArray(numEdges + 1);
			int            index    = 1;

			// to the target candidates
			for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates()) {

				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(sourceCandidate, targetCandidate));
				index++;
			}

			// to the delete node
			GLPK.intArray_setitem(varNums, index, (int)getVariableNum(sourceCandidate, deathNode));

			GLPK.glp_set_row_name(problem, i, "c" + i);
			GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_DB, 1.0, 1.0);

			SWIGTYPE_p_double varCoefs = GLPK.new_doubleArray(numEdges + 1);
			for (int j = 1; j <= numEdges; j++)
				GLPK.doubleArray_setitem(varCoefs, j, 1.0);

			GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

			i++;
		}

		// incoming flow for each target candidate
		for (Candidate targetCandidate : targetCandidates) {

			// get all source candidates that have targetCandidate as target
			Vector<Candidate> partners = new Vector<Candidate>();
			for (Candidate sourceCandidate : sourceCandidates)
				if (sourceCandidate.getMostLikelyCandidates().contains(targetCandidate))
					partners.add(sourceCandidate);

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
			GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_DB, 1.0, 1.0);

			SWIGTYPE_p_double varCoefs = GLPK.new_doubleArray(numEdges + 1);
			for (int j = 1; j <= numEdges; j++)
				GLPK.doubleArray_setitem(varCoefs, j, 1.0);

			GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

			i++;
		}

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
		GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_DB, targetCandidates.size(), targetCandidates.size());

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
		GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_DB, targetCandidates.size(), targetCandidates.size());

		GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

		i++;

		// incoming flow to the death node
		numEdges = sourceCandidates.size() + 1;
		varNums  = GLPK.new_intArray(numEdges + 1);
		index    = 1;

		// from the target candidates
		for (Candidate sourceCandidate: sourceCandidates) {

			GLPK.intArray_setitem(varNums, index, (int)getVariableNum(sourceCandidate, deathNode));
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
		GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_DB, sourceCandidates.size(), sourceCandidates.size());

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
		GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_DB, targetCandidates.size(), targetCandidates.size());

		GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

		i++;

		IJ.log("" + i + " constraints set.");

		// setup objective function
		GLPK.glp_set_ojb_name(problem, "min cost");
		GLPK.glp_set_obj_dir(problem, GLPKConstants.GLP_MIN);
	}

	private int computeNumVariables() {

		// the number of variables is the number of edges for which we would
		// like to calculate the flow
		
		int numVariables = 0;

		// for the connections between the source and target candidates
		for (Candidate sourceCandidate : sourceCandidates)
			numVariables += sourceCandidate.getMostLikelyCandidates().size();

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

		// incoming or outgoing flow for neglect, neglect-collect, emerge, and
		// death node
		numConstraints += 4;

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

	private boolean conflicts(Candidate candidate1, Candidate candidate2) {

		// two candidates are in conflict, if one is the ancestor of the other
		return (candidate1.isAncestorOf(candidate2) || candidate2.isAncestorOf(candidate1));
	}
}
