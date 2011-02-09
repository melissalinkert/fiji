package sipnet;

import java.util.List;

interface LinearProgramSolver {

	public void addConstraint(List<Integer> variableNums,
	                          List<Double>  coefficients,
	                          int relation,
	                          double b);

	public void setObjective(List<Integer> variableNums,
	                         List<Double>  coefficients);

	public int solve(int numThreads);

	public double getValue(int variableNum);
}
