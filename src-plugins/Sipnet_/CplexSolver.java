
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLPMatrix;

import ilog.cplex.IloCplex;
import java.util.List;

class CplexSolver implements LinearProgramSolver {

	private IloCplex    cplex;
	private IloIntVar[] vars;
	private IloLPMatrix matrix;

	private int numVariables;

	public CplexSolver(int numVariables, int numConstraints) {

		try {

			cplex = new IloCplex();

			matrix = cplex.addLPMatrix();

			// add binary variables

			vars = cplex.boolVarArray(cplex.columnArray(matrix, numVariables));

			this.numVariables = numVariables;

		} catch (IloException e) {

			e.printStackTrace();
		}
	}

	public void addConstraint(List<Integer> variableNums,
	                          List<Double> coefficients,
	                          int relation,
	                          double b) {

		int[]    varNums = new int[variableNums.size()];
		double[] coeffs  = new double[variableNums.size()];

		for (int i = 0; i < variableNums.size(); i++) {
			varNums[i] = variableNums.get(i);
			coeffs[i]  = coefficients.get(i);
		}

		try {

			matrix.addRow(b, b, varNums, coeffs);

		} catch (IloException e) {

			e.printStackTrace();
		}
	}

	public void setObjective(List<Integer> variableNums,
	                         List<Double> coefficients) {

		double[] coeffs = new double[numVariables];

		for (int i = 0; i < variableNums.size(); i++)
			coeffs[variableNums.get(i)] = coefficients.get(i);

		try {

			cplex.addMinimize(cplex.scalProd(vars, coeffs));

		} catch (IloException e) {

			e.printStackTrace();
		}
	}

	public int solve(int numThreads) {

		try {

			cplex.solve();

		} catch (IloException e) {

			e.printStackTrace();
		}

		return 0;
	}

	public double getValue(int variableNum) {

		double value = 0;

		try {

			value = cplex.getValues(vars)[variableNum];

		} catch (Exception e) {

			e.printStackTrace();
		}

		return value;
	}

}
