
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.SWIGTYPE_p_double;
import org.gnu.glpk.SWIGTYPE_p_int;
import org.gnu.glpk.glp_iocp;
import org.gnu.glpk.glp_prob;

import ij.IJ;

public class SequenceSearch {

	/*
	 * parameters of the sequence search
	 */

	public static final int MaxTargetCandidates = 5;
	public static final int MinTargetCandidates = 1;

	/*
	 * nodes of the assignment graph
	 */

	// candidates for each slice
	private List<Vector<Candidate>> sliceCandidates;

	// dummy candidates
	private Candidate deathNode;
	private Candidate emergeNode;

	// conversion of pairs of nodes to variable nums
	private HashMap<Candidate, HashMap<Candidate, Long>> nodeNums;
	private long nextNodeId = 1;

	/*
	 * linear program
	 */

	glp_prob problem;

	public SequenceSearch(List<Set<Candidate>> sliceCandidates, Texifyer texifyer) {

		this.sliceCandidates = new ArrayList<Vector<Candidate>>();
		for (Set<Candidate> candidates : sliceCandidates)
			this.sliceCandidates.add(new Vector<Candidate>(candidates));

		this.nodeNums = new HashMap<Candidate, HashMap<Candidate, Long>>();

		this.deathNode = new Candidate(0, 0, new double[]{0.0, 0.0});
		this.emergeNode = new Candidate(0, 0, new double[]{0.0, 0.0});

		// build cache for candidates
		IJ.log("Precaching most likely candidates...");
		for (int s = 0; s < this.sliceCandidates.size() - 1; s++)
			for (Candidate candidate : this.sliceCandidates.get(s))
				candidate.cacheMostSimilarCandidates(this.sliceCandidates.get(s+1));
		IJ.log("Done.");

		setupLp();
	}

	public Sequence getBestAssignmentSequence() {

		IJ.log("Solving ILP...");
		solveLp();
		IJ.log("Done.");

		return readSequence();
	}

	private void setupLp() {

		int numVariables   = computeNumVariables();
		int numConstraints = computeNumConstraints();

		IJ.log("setting up linear program with " + numVariables +
		       " variables and " + numConstraints + " constraints");

		// create problem
		problem = GLPK.glp_create_prob();
		GLPK.glp_set_prob_name(problem, "assignment problem");

		// allocate variables
		GLPK.glp_add_cols(problem, numVariables);

		for (int i = 1; i <= numVariables; i++) {

			GLPK.glp_set_col_name(problem, i, "x" + i);
			GLPK.glp_set_col_kind(problem, i, GLPKConstants.GLP_BV);
		}

		// allocate constraints
		GLPK.glp_add_rows(problem, numConstraints);

		numConstraints = setupConstraints(numConstraints);
		numVariables   = setupObjectiveFunction(numVariables);

		IJ.log("linear program set up with " + numVariables +
		       " variables and " + numConstraints + " constraints");
	}

	private int computeNumVariables() {

		int numVariables = 0;

		// one variable for each possible assignment, death, and emerge
		for (Vector<Candidate> candidates : sliceCandidates)
			for (Candidate candidate : candidates)
				numVariables += candidate.getMostLikelyCandidates().size() + 2;

		return numVariables;
	}

	private int computeNumConstraints() {

		int numConstraints = 0;

		// one explanation consistency constraint for each candidate
		for (Vector<Candidate> candidates : sliceCandidates)
			numConstraints += candidates.size();

		// one hypothesis consistency constraint for each path in the component
		// tree

		for (Vector<Candidate> candidates : sliceCandidates)
			for (Candidate candidate : candidates)
				if (candidate.getChildren().size() == 0)
					numConstraints++;

		return numConstraints;
	}

	private int setupConstraints(int numConstraints) {

		int i = 1;

		// one explanation consistency constraint for each candidate
		for (Vector<Candidate> candidates : sliceCandidates)
			for (Candidate candidate : candidates) {

				// all incoming and outgoing edges plus death and emerge
				int               numEdges = candidate.getMostLikelyCandidates().size() + 
				                             candidate.getMostLikelyOf().size() + 2;
				SWIGTYPE_p_int    varNums  = GLPK.new_intArray(numEdges + 1);
				SWIGTYPE_p_double varCoefs = GLPK.new_doubleArray(numEdges + 1);
				int               index    = 1;

				// sum of all incoming edges...
				for (Candidate from : candidate.getMostLikelyOf()) {

					GLPK.intArray_setitem(varNums, index, (int)getVariableNum(from, candidate));
					GLPK.doubleArray_setitem(varCoefs, index, 1.0);
					index++;
				}
				// ...and emerge node...
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(emergeNode, candidate));
				GLPK.doubleArray_setitem(varCoefs, index, 1.0);
				index++;

				// ...minus sum of all outgoing edges...
				for (Candidate to : candidate.getMostLikelyCandidates()) {

					GLPK.intArray_setitem(varNums, index, (int)getVariableNum(candidate, to));
					GLPK.doubleArray_setitem(varCoefs, index, -1.0);
					index++;
				}
				// ...and death node...
				GLPK.intArray_setitem(varNums, index, (int)getVariableNum(candidate, deathNode));
				GLPK.doubleArray_setitem(varCoefs, index, -1.0);
				index++;

				// ...has to be zero
				GLPK.glp_set_row_name(problem, i, "c" + i);
				GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_FX, 0.0, 0.0);
				GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

				i++;
			}

		// one hypothesis consistency constraint for each path
		for (Vector<Candidate> candidates : sliceCandidates)
			for (Candidate candidate : candidates)
				if (candidate.getChildren().size() == 0) {

					// the path that leads to this child
					Vector<Candidate> path = new Vector<Candidate>();

					Candidate pathMember = candidate;

					while (pathMember != null) {
						path.add(pathMember);
						pathMember = pathMember.getParent();
					}

					// all pairs of nodes, that are connected by an edge where the
					// target is a member of the path and the source is not the
					// death node
					Vector<Candidate[]> pairs = new Vector<Candidate[]>();

					for (Candidate member : path) {

						// get all source nodes that have a connection to member
						for (Candidate source : member.getMostLikelyOf())
							pairs.add(new Candidate[]{source, member});

						// add the emerge node
						pairs.add(new Candidate[]{emergeNode, member});
					}

					// the sum of all incoming edges...
					int               numEdges = pairs.size();
					SWIGTYPE_p_int    varNums  = GLPK.new_intArray(numEdges + 1);
					SWIGTYPE_p_double varCoefs = GLPK.new_doubleArray(numEdges + 1);
					int               index    = 1;

					for (Candidate[] pair : pairs) {

						GLPK.intArray_setitem(varNums, index, (int)getVariableNum(pair[0], pair[1]));
						GLPK.doubleArray_setitem(varCoefs, index, 1.0);
						index++;
					}

					// ...has to be at most one
					GLPK.glp_set_row_name(problem, i, "c" + i);
					GLPK.glp_set_row_bnds(problem, i, GLPKConstants.GLP_DB, 0.0, 1.0);
					GLPK.glp_set_mat_row(problem, i, numEdges, varNums, varCoefs);

					i++;
			}

		return i - 1;
	}

	private int setupObjectiveFunction(int numVariables) {

		int j = 0;

		GLPK.glp_set_obj_name(problem, "min cost");
		GLPK.glp_set_obj_dir(problem, GLPKConstants.GLP_MIN);

		// costs for each edge in the network

		for (Vector<Candidate> candidates : sliceCandidates) {
		
			// for each candidate and its outgoing connections, emerge, and
			// death
			for (Candidate candidate : candidates) {

				for (Candidate to : candidate.getMostLikelyCandidates()) {
					GLPK.glp_set_obj_coef(problem, (int)getVariableNum(candidate, to),
					                      AssignmentModel.negLogPAppearance(candidate, to));
					j++;
				}

				GLPK.glp_set_obj_coef(problem, (int)getVariableNum(candidate, deathNode),
				                      AssignmentModel.negLogPriorDeath);
				j++;

				GLPK.glp_set_obj_coef(problem, (int)getVariableNum(emergeNode, candidate),
				                      AssignmentModel.negLogPriorDeath);
				j++;
			}
		}

		return j;
	}


	private void solveLp() {

		glp_iocp parameters = new glp_iocp();

		GLPK.glp_init_iocp(parameters);
		parameters.setPresolve(GLPKConstants.GLP_ON);

		int result = GLPK.glp_intopt(problem, parameters);

		if (result != 0)
			IJ.log("LP problem could not be solved.");
	}

	private Sequence readSequence() {

		Sequence sequence = new Sequence();

		for (int s = sliceCandidates.size() - 1; s >= 0; s--) {

			Vector<Candidate> candidates = sliceCandidates.get(s);

			Assignment assignment = new Assignment();

			// each continuation
			for (Candidate candidate : candidates) {
				for (Candidate to : candidate.getMostLikelyCandidates()) {

					// values are either 1 or 0
					if (GLPK.glp_mip_col_val(problem, (int)getVariableNum(candidate, to)) > 0.5)
						assignment.add(new SingleAssignment(candidate, to));
				}
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

}
