package sipnet;

import java.util.List;

import ipopt.IpOpt;

/**
 * Computes marginal probabilities for models that can be expessed as linear
 * programs. If another solver is given on contruction, it is used to infer the
 * initial state of the marginal probability search.
 */
public class IpOptSolver implements LinearProgramSolver {

	static {
		System.loadLibrary("jipopt");
	}

	final int numVariables;

	final private IpOpt ipopt;

	// optional, if not null this solver is used for initialisation
	final private LinearProgramSolver initSolver;

	public IpOptSolver(int numVariables, int numConstraints) {

		this(numVariables, numConstraints, null);
	}

	public IpOptSolver(int numVariables, int numConstraints, LinearProgramSolver initSolver) {

		this.numVariables = numVariables;
		this.ipopt        = new IpOpt(numVariables, numConstraints);
		this.initSolver   = initSolver;
	}

	public void addConstraint(List<Integer> variableNums,
	                          List<Double>  coefficients,
	                          int relation,
	                          double b) {

		int[]    vars  = new int[variableNums.size()];
		double[] coefs = new double[variableNums.size()];

		for (int i = 0; i < variableNums.size(); i++) {
			vars[i]  = variableNums.get(i);
			coefs[i] = coefficients.get(i);
		}

		ipopt.setLinearConstraint(
				variableNums.size(),
				vars, coefs,
				relation, b);

		if (initSolver != null)
			initSolver.addConstraint(variableNums, coefficients, relation, b);
	}

	public void setObjective(List<Integer> variableNums,
	                         List<Double>  coefficients) {

		for (int i = 0; i < variableNums.size(); i++)
			// unary factor contributing <coefficient> if <variablenum> is 1
			ipopt.setSingleSiteFactor(
					variableNums.get(i),
					0, coefficients.get(i));

		if (initSolver != null)
			initSolver.setObjective(variableNums, coefficients);
	}

	public int solve(int numThreads) {

		double[] initialState = new double[numVariables];

		if (initSolver != null) {

			initSolver.solve(numThreads);
			for (int i = 0; i < numVariables; i++)
				initialState[i] = initSolver.getValue(i);
		}

		ipopt.setInitialState(initialState);

		ipopt.inferMarginals(numThreads);
		return 0;
	}

	public double getValue(int variableNum) {

		return ipopt.getState(variableNum);
	}

	public double getMarginal(int variableNum, int value) {

		return ipopt.getMarginal(variableNum, value);
	}
}
