package sipnet;

import java.util.List;

import opengm.OpenGM;

/**
 * Encodes the linear program as a graphical model. The objective is modelled as
 * unary factors and constraints as higher order factors with values that are
 * either zero or infinity.
 */
public class GraphicalModelSolver implements LinearProgramSolver {

	final private OpenGM opengm;

	public GraphicalModelSolver(int numVariables, int numConstraints) {

		opengm = new OpenGM(numVariables);
	}

	public void addConstraint(List<Integer> variableNums,
	                          List<Double>  coefficients,
	                          int relation,
	                          double b) {

		long[]  varNums = new long[variableNums.size()];
		double[] values = new double[(int)Math.pow(2, variableNums.size())];
		int[]    config = new int[variableNums.size()];

		for (int i = 0; i < variableNums.size(); i++)
			varNums[i] = variableNums.get(i);

		int c = 0;
		while (true) {

			double sum = 0.0;
			for (int i = 0; i < config.length; i++)
				sum += config[i]*coefficients.get(i);

			boolean valid = false;
			if (relation < 0)
				valid = (sum < b);
			else if (relation == 0)
				valid = (sum == b);
			else if (relation > 0)
				valid = (sum > b);

			if (valid)
				values[c] = 0;
			else
				values[c] = Double.NEGATIVE_INFINITY;

			int num1s = 0;
			for (int i = config.length-1; i <= 0; i--)
				if (config[i] == 1) {
					num1s++;
					config[i] = 0;
				} else {
					config[i] = 1;
					break;
				}
			if (num1s == config.length)
				break;

			c++;
		}

		opengm.setFactor(varNums.length, varNums, values);
	}

	public void setObjective(List<Integer> variableNums,
	                         List<Double>  coefficients) {

		for (int i = 0; i < variableNums.size(); i++)
			// unary factor contributing <coefficient> if <variablenum> is 1
			opengm.setSingleSiteFactor(
					variableNums.get(i),
					0, coefficients.get(i));
	}

	public int solve(int numThreads) {

		opengm.inferMarginals(numThreads);
		return 0;
	}

	public double getValue(int variableNum) {

		return opengm.getState(variableNum);
	}

	public double getMarginal(int variableNum, int value) {

		return opengm.getMarginal(variableNum, value);
	}
}
