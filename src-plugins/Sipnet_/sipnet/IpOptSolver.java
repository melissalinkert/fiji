package sipnet;

import java.util.List;

import ij.IJ;

import ipopt.IpOpt;

/**
 * Computes marginal probabilities for models that can be expessed as linear
 * programs.
 */
public class IpOptSolver implements LinearProgramSolver {

	static {
		System.loadLibrary("jipopt");
	}

	final private IpOpt ipopt;

	public IpOptSolver(int numVariables, int numConstraints) {

		ipopt = new IpOpt(numVariables, numConstraints);
	}

	public void addConstraint(List<Integer> variableNums,
	                          List<Double>  coefficients,
	                          int relation,
	                          double b) {

		long[]   vars  = new long[variableNums.size()];
		double[] coefs = new double[variableNums.size()];

		for (int i = 0; i < variableNums.size(); i++) {
			vars[i]  = variableNums.get(i);
			coefs[i] = coefficients.get(i);
		}

		ipopt.setLinearConstraint(
				variableNums.size(),
				vars, coefs,
				relation, b);
	}

	public void setObjective(List<Integer> variableNums,
	                         List<Double>  coefficients) {

		for (int i = 0; i < variableNums.size(); i++)
			// unary factor contributing <coefficient> if <variablenum> is 1
			ipopt.setSingleSiteFactor(
					variableNums.get(i),
					0, coefficients.get(i));
	}

	public int solve(int numThreads) {

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
