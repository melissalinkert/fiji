package sipnet;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.DifferentiableMultivariateRealFunction;
import org.apache.commons.math.analysis.MultivariateRealFunction;
import org.apache.commons.math.analysis.MultivariateVectorialFunction;

import org.apache.commons.math.optimization.DifferentiableMultivariateRealOptimizer;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.RealPointValuePair;

import org.apache.commons.math.optimization.general.ConjugateGradientFormula;
import org.apache.commons.math.optimization.general.NonLinearConjugateGradientOptimizer;

public class ParameterEstimator {

	// the assignemt model to estimate paramters for
	private AssignmentModel assignmentModel;

	// the training input as a sequence of candidates
	private Sequence        groundTruth;

	/**
	 * Interface class for the apache.math optimizer. Delegates computation of
	 * the gradient to the ParameterEstimator.
	 */
	final private class Gradient implements MultivariateVectorialFunction {

		final public double[] value(double[] w) {

			double[] gradient = new double[6];

			assignmentModel.setParameters(w);

			gradient[0] = gradientData();
			gradient[1] = gradientPositionContinuation();
			gradient[2] = gradientShapeContinuation();
			gradient[3] = gradientPositionBisection();
			gradient[4] = gradientShapeBisection();
			gradient[5] = gradientEnd();

			return gradient;
		}
	}

	final private class PartialGradient implements MultivariateRealFunction {

		private int part;

		public PartialGradient(int part) {
			this.part = part;
		}

		final public double value(double[] w) {

			assignmentModel.setParameters(w);

			switch (part) {
				case 0:
					return gradientData();
				case 1:
					return gradientPositionContinuation();
				case 2:
					return gradientShapeContinuation();
				case 3:
					return gradientPositionBisection();
				case 4:
					return gradientShapeBisection();
				case 5:
					return gradientEnd();
				default:
					throw new RuntimeException("parameter with number " + part + " does not exist");
			}
		}
	}

	/**
	 * Interface class for the apache.math optimizer. Provides objective values
	 * and gradients for sets of parameters.
	 */
	final private class Objective implements DifferentiableMultivariateRealFunction {

		final private Gradient          gradient;
		final private PartialGradient[] partialDerivatives;

		public Objective() {

			this.gradient           = new Gradient();
			this.partialDerivatives = new PartialGradient[6];

			for (int i = 0; i < 6; i++)
				partialDerivatives[i] = new PartialGradient(i);
		}

		public double value(double[] w)
		throws
				FunctionEvaluationException,
				IllegalArgumentException {

			assignmentModel.setParameters(w);

			double sumCosts = 0.0;

			for (SequenceNode node : groundTruth)
				sumCosts += node.getAssignment().getCosts();

			return sumCosts;
		}

		public MultivariateVectorialFunction gradient() {

			return gradient;
		}

		public MultivariateRealFunction partialDerivative(int k) {

			return partialDerivatives[k];
		}

	}

	public ParameterEstimator(Sequence groundTruth) {

		this.assignmentModel = AssignmentModel.getInstance();
		this.groundTruth     = groundTruth;
	}

	final public void estimate() {

		DifferentiableMultivariateRealOptimizer optimizer =
				new NonLinearConjugateGradientOptimizer(
						ConjugateGradientFormula.FLETCHER_REEVES);

		Objective objective = new Objective();

		RealPointValuePair result = null;

		try {
			result = optimizer.optimize(
					objective,
					GoalType.MINIMIZE,
					new double[]{0.0, 0.0, 0.0, 0.0, 0.0});

		} catch (Exception e) {

			e.printStackTrace();
		}

		assignmentModel.setParameters(result.getPointRef());
	}

	final private double gradientData() {

		return 0.0;
	}

	final private double gradientPositionContinuation() {

		return 0.0;
	}

	final private double gradientShapeContinuation() {

		return 0.0;
	}

	final private double gradientPositionBisection() {

		return 0.0;
	}

	final private double gradientShapeBisection() {

		return 0.0;
	}

	final private double gradientEnd() {

		return 0.0;
	}
}
