package sipnet;

import ilog.concert.IloException;
import ilog.concert.IloLPMatrix;
import ilog.concert.IloNumVar;

import ilog.cplex.IloCplex;
import java.util.List;

class CplexSolver implements LinearProgramSolver {

	private IloCplex    cplex;
	private IloNumVar[] vars;
	private IloLPMatrix matrix;

	private int      numVariables;
	private double[] values;
	private boolean  integer;

	public CplexSolver(int numVariables, int numConstraints, boolean integer) {

		try {

			cplex = new IloCplex();

			matrix = cplex.addLPMatrix();

			// add binary variables

			if (integer)
				vars = cplex.boolVarArray(cplex.columnArray(matrix, numVariables));
			else
				vars = cplex.numVarArray(cplex.columnArray(matrix, numVariables), 0.0, 1.0);

			this.numVariables = numVariables;
			this.integer      = integer;

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

			if (relation < 0)
				matrix.addRow(Double.MIN_VALUE, b, varNums, coeffs);
			else if (relation > 0)
				matrix.addRow(b, Double.MAX_VALUE, varNums, coeffs);
			else
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

		if (values == null)
			try {
				values = cplex.getValues(vars);
			} catch (Exception e) {
				e.printStackTrace();
			}

		return values[variableNum];
	}

	public double getMarginal(int variableNum, int value) {

		throw new RuntimeException("CPLEX linear solver does not provide marginal probabilities");
	}
}
