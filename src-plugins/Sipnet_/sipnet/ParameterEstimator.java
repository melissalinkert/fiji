package sipnet;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.DifferentiableMultivariateRealFunction;
import org.apache.commons.math.analysis.MultivariateRealFunction;
import org.apache.commons.math.analysis.MultivariateVectorialFunction;

public class ParameterEstimator {

	// the assignemt model to estimate paramters for
	private AssignmentModel assignmentModel;

	// the training input as a sequence of candidates
	private Sequence        groundTruth;

	private class Objective implements DifferentiableMultivariateRealFunction {

		public double value(double[] w)
		throws
				FunctionEvaluationException,
				IllegalArgumentException {

			double sumCosts = 0.0;

			for (SequenceNode node : groundTruth)
				sumCosts += node.getAssignment().getCosts();

			return sumCosts;
		}

		public MultivariateVectorialFunction gradient() {
			return null;
		}

		public MultivariateRealFunction partialDerivative(int arg0) {
			return null;
		}

	}

	public ParameterEstimator(AssignmentModel assignmentModel, Sequence groundTruth) {

		this.assignmentModel = assignmentModel;
		this.groundTruth     = groundTruth;
	}

	final public void estimate() {

		// iterate:
		//  updateExpectedValues();
		//  use gradient method to refire paramters
	}

	final private void updateExpectedValues() {

	}

	final private double expectedData() {

		return 0.0;
	}

	final private double expectedPositionContinuation() {

		return 0.0;
	}

	final private double expectedShapeContinuation() {

		return 0.0;
	}

	final private double expectedPositionBisection() {

		return 0.0;
	}

	final private double expectedShapeBisection() {

		return 0.0;
	}
}
