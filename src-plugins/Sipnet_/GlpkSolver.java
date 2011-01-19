
import java.util.List;

import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.SWIGTYPE_p_double;
import org.gnu.glpk.SWIGTYPE_p_int;
import org.gnu.glpk.glp_iocp;
import org.gnu.glpk.glp_prob;

class GlpkSolver implements LinearProgramSolver {

	// linear program
	glp_prob problem;

	// the next unassigned constraint
	int nextConstraint;

	public GlpkSolver(int numVariables, int numConstraints) {

		// create problem
		problem = GLPK.glp_create_prob();

		// setup variables
		GLPK.glp_add_cols(problem, numVariables);

		// first, set all variables to be binary
		for (int i = 1; i <= numVariables; i++) {

			GLPK.glp_set_col_name(problem, i, "x" + i);
			GLPK.glp_set_col_kind(problem, i, GLPKConstants.GLP_BV);
		}

		// setup constraints
		GLPK.glp_add_rows(problem, numConstraints);

		nextConstraint = 1;
	}

	public void addConstraint(List<Integer> variableNums,
	                          List<Double>  coefficients,
	                          int relation,
	                          double b) {

		int               numVars  = variableNums.size();
		SWIGTYPE_p_int    varNums  = GLPK.new_intArray(numVars + 1);
		SWIGTYPE_p_double varCoefs = GLPK.new_doubleArray(numVars + 1);

		for (int i = 0; i < numVars; i++) {

			GLPK.intArray_setitem(varNums, i + 1, variableNums.get(i) + 1);
			GLPK.doubleArray_setitem(varCoefs, i + 1, coefficients.get(i));
		}

		if (relation < 0)
			GLPK.glp_set_row_bnds(problem, nextConstraint, GLPKConstants.GLP_UP, b, b);
		else if (relation > 0)
			GLPK.glp_set_row_bnds(problem, nextConstraint, GLPKConstants.GLP_LO, b, b);
		else
			GLPK.glp_set_row_bnds(problem, nextConstraint, GLPKConstants.GLP_FX, b, b);

		GLPK.glp_set_mat_row(problem, nextConstraint, numVars, varNums, varCoefs);

		nextConstraint++;
	}

	public void setObjective(List<Integer> variableNums,
	                         List<Double>  coefficients) {

		GLPK.glp_set_obj_dir(problem, GLPKConstants.GLP_MIN);

		for (int i = 0; i < variableNums.size(); i++)
			GLPK.glp_set_obj_coef(problem, variableNums.get(i) + 1, coefficients.get(i));
	}

	public int solve(int numThreads) {

		glp_iocp parameters = new glp_iocp();

		GLPK.glp_init_iocp(parameters);
		parameters.setPresolve(GLPKConstants.GLP_ON);

		int result = GLPK.glp_intopt(problem, parameters);

		return result;
	}

	public double getValue(int variableNum) {

		double value = GLPK.glp_mip_col_val(problem, variableNum + 1);
		return value;
	}
}
