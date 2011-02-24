package sipnet;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import org.apache.commons.math.FunctionEvaluationException;
import org.apache.commons.math.analysis.DifferentiableMultivariateRealFunction;
import org.apache.commons.math.analysis.MultivariateRealFunction;
import org.apache.commons.math.analysis.MultivariateVectorialFunction;

import org.apache.commons.math.optimization.DifferentiableMultivariateRealOptimizer;
import org.apache.commons.math.optimization.GoalType;
import org.apache.commons.math.optimization.RealPointValuePair;

import org.apache.commons.math.optimization.general.ConjugateGradientFormula;
import org.apache.commons.math.optimization.general.NonLinearConjugateGradientOptimizer;

import ij.IJ;

public class ParameterEstimator {

	// the assignemt model to estimate paramters for
	private AssignmentModel assignmentModel;

	// the training input as a sequence of candidates and according msers
	private Sequence                trainingSequence;
	private List<Vector<Candidate>> msers;

	// the function to minimize
	private Objective objective;

	// the per-component standart deviation of the parameters used for
	// regularization
	private double parameterStdDeviation;

	/**
	 * Interface class for the apache.math optimizer. Delegates computation of
	 * the gradient to the ParameterEstimator.
	 */
	final private class Gradient implements MultivariateVectorialFunction {

		final public double[] value(double[] w) {

			double[] gradient = new double[6];

			assignmentModel.setParameters(w);

			gradient[0] = gradientData()                 + regularizer(w[0]);
			gradient[1] = gradientPositionContinuation() + regularizer(w[1]);
			gradient[2] = gradientShapeContinuation()    + regularizer(w[2]);
			gradient[3] = gradientPositionBisection()    + regularizer(w[3]);
			gradient[4] = gradientShapeBisection()       + regularizer(w[4]);
			gradient[5] = gradientEnd()                  + regularizer(w[5]);

			try {
				objective.value(w);
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("gradient at " + Arrays.toString(w) + ":\n" + Arrays.toString(gradient));

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
					return gradientData() + regularizer(w[0]);
				case 1:
					return gradientPositionContinuation() + regularizer(w[1]);
				case 2:
					return gradientShapeContinuation() + regularizer(w[2]);
				case 3:
					return gradientPositionBisection() + regularizer(w[3]);
				case 4:
					return gradientShapeBisection() + regularizer(w[4]);
				case 5:
					return gradientEnd() + regularizer(w[5]);
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

			for (Assignment assignment : trainingSequence)
				sumCosts += assignment.getCosts(assignmentModel);

			return -sumCosts;
		}

		public MultivariateVectorialFunction gradient() {

			return gradient;
		}

		public MultivariateRealFunction partialDerivative(int k) {

			return partialDerivatives[k];
		}

	}

	public ParameterEstimator(
			Sequence trainingSequence,
			List<Vector<Candidate>> msers,
			double parameterStdDeviation) {

		this.assignmentModel       = new AssignmentModel();
		this.trainingSequence      = trainingSequence;
		this.msers                 = msers;
		this.parameterStdDeviation = parameterStdDeviation;
	}

	final public void estimate() {

		DifferentiableMultivariateRealOptimizer optimizer =
				new NonLinearConjugateGradientOptimizer(
						ConjugateGradientFormula.FLETCHER_REEVES);

		objective = new Objective();

		RealPointValuePair result = null;

		try {
			IJ.log("starting optimizer...");
			/*
			 * NOTE:
			 *
			 * Here, we are MAXIMIZING, since we compute the values of the
			 * LIKELIHOOD of the training samples.
			 */
			result = optimizer.optimize(
					objective,
					GoalType.MAXIMIZE,
					new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0});
			IJ.log("done.");

		} catch (Exception e) {

			e.printStackTrace();
		}

		assignmentModel.setParameters(result.getPointRef());
	}

	final private double gradientData() {

		double sumTermsTraining = 0.0;
		double sumTermsExpected = 0.0;

		// every true continuation
		for (Assignment assignment : trainingSequence)
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSources().size() == 1 &&
				    singleAssignment.getTargets().size() == 1 &&
				    singleAssignment.getSources().get(0) != SequenceSearch.emergeNode &&
				    singleAssignment.getTargets().get(0) != SequenceSearch.deathNode)

					sumTermsTraining +=
							assignmentModel.dataTerm(singleAssignment.getSources().get(0)) +
							assignmentModel.dataTerm(singleAssignment.getTargets().get(0));
			}

		// every true bisection
		for (Assignment assignment : trainingSequence)
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSources().size() == 1 &&
				    singleAssignment.getTargets().size() == 2)

					sumTermsTraining +=
							assignmentModel.dataTerm(singleAssignment.getSources().get(0)) +
							assignmentModel.dataTerm(singleAssignment.getTargets().get(0)) +
							assignmentModel.dataTerm(singleAssignment.getTargets().get(1));

				if (singleAssignment.getSources().size() == 2 &&
				    singleAssignment.getTargets().size() == 1)

					sumTermsTraining +=
							assignmentModel.dataTerm(singleAssignment.getTargets().get(0)) +
							assignmentModel.dataTerm(singleAssignment.getSources().get(0)) +
							assignmentModel.dataTerm(singleAssignment.getSources().get(1));
			}

		// every true end in the training data
		for (Assignment assignment : trainingSequence)
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSources().get(0) == SequenceSearch.emergeNode)
					sumTermsTraining += assignmentModel.dataTerm(singleAssignment.getTargets().get(0));

				if (singleAssignment.getTargets().get(0) == SequenceSearch.deathNode)
					sumTermsTraining += assignmentModel.dataTerm(singleAssignment.getSources().get(0));
			}

		// for each possible continuation
		for (Vector<Candidate> sliceCandidates : msers)
			for (Candidate source : sliceCandidates)
				for (Candidate target : source.getMostLikelyCandidates()) {

					double p = Math.exp(-assignmentModel.costContinuation(source, target, true));

					if (p == Double.POSITIVE_INFINITY)
						p = 1.0;
					else
						p = p/(p + 1.0);

					sumTermsExpected +=
						(assignmentModel.dataTerm(source) +
						 assignmentModel.dataTerm(target))*p;
				}

		// for each possible bisection
		for (Vector<Candidate> sliceCandidates : msers)
			for (Candidate candidate : sliceCandidates) {

				for (Candidate neighbor : candidate.mergeTargets().keySet())
					for (Candidate target : candidate.mergeTargets().get(neighbor)) {

						double p = Math.exp(-assignmentModel.costBisect(target, candidate, neighbor));

						if (p == Double.POSITIVE_INFINITY)
							p = 1.0;
						else
							p = p/(p + 1.0);

						sumTermsExpected +=
								(assignmentModel.dataTerm(target) +
								 assignmentModel.dataTerm(candidate) +
								 assignmentModel.dataTerm(neighbor))*p;
				}

				for (Candidate neighbor : candidate.splitSources().keySet())
					for (Candidate source : candidate.splitSources().get(neighbor)) {

						double p = Math.exp(-assignmentModel.costBisect(source, candidate, neighbor));

						if (p == Double.POSITIVE_INFINITY)
							p = 1.0;
						else
							p = p/(p + 1.0);

						sumTermsExpected +=
								(assignmentModel.dataTerm(source) +
								 assignmentModel.dataTerm(candidate) +
								 assignmentModel.dataTerm(neighbor))*p;
				}
			}

		// every possible end
		int s = 0;
		for (Vector<Candidate> sliceCandidates : msers) {

			// all but last slice
			if (s < msers.size() - 1)
				for (Candidate target : sliceCandidates) {

					double p = Math.exp(-assignmentModel.costEnd(target));

					if (p == Double.POSITIVE_INFINITY)
						p = 1.0;
					else
						p = p/(p + 1.0);

					sumTermsExpected += assignmentModel.dataTerm(target)*p;
				}

			// all but first slice
			if (s > 0)
				for (Candidate source : sliceCandidates) {

					double p = Math.exp(-assignmentModel.costEnd(source));

					if (p == Double.POSITIVE_INFINITY)
						p = 1.0;
					else
						p = p/(p + 1.0);


					sumTermsExpected += assignmentModel.dataTerm(source)*p;
				}

			s++;
		}

		return sumTermsExpected - sumTermsTraining;
	}

	final private double gradientPositionContinuation() {

		double sumTermsTraining = 0.0;
		double sumTermsExpected = 0.0;

		// every true continuation
		for (Assignment assignment : trainingSequence)
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSources().size() == 1 &&
				    singleAssignment.getTargets().size() == 1 &&
				    singleAssignment.getSources().get(0) != SequenceSearch.emergeNode &&
				    singleAssignment.getTargets().get(0) != SequenceSearch.deathNode)

					sumTermsTraining +=
							assignmentModel.centerTerm(
									singleAssignment.getSources().get(0),
									singleAssignment.getTargets().get(0));
			}

		// for each possible continuation
		for (Vector<Candidate> sliceCandidates : msers)
			for (Candidate source : sliceCandidates)
				for (Candidate target : source.getMostLikelyCandidates()) {

					double p = Math.exp(-assignmentModel.costContinuation(source, target, true));

					if (p == Double.POSITIVE_INFINITY)
						p = 1.0;
					else
						p = p/(p + 1.0);

					sumTermsExpected += assignmentModel.centerTerm(source, target)*p;
				}

		return sumTermsExpected - sumTermsTraining;
	}

	final private double gradientShapeContinuation() {

		double sumTermsTraining = 0.0;
		double sumTermsExpected = 0.0;

		// every true continuation
		for (Assignment assignment : trainingSequence)
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSources().size() == 1 &&
				    singleAssignment.getTargets().size() == 1 &&
				    singleAssignment.getSources().get(0) != SequenceSearch.emergeNode &&
				    singleAssignment.getTargets().get(0) != SequenceSearch.deathNode)

					sumTermsTraining +=
							assignmentModel.shapeTerm(
									singleAssignment.getSources().get(0),
									singleAssignment.getTargets().get(0));
			}

		// for each possible continuation
		for (Vector<Candidate> sliceCandidates : msers)
			for (Candidate source : sliceCandidates)
				for (Candidate target : source.getMostLikelyCandidates()) {

					double p = Math.exp(-assignmentModel.costContinuation(source, target, true));

					if (p == Double.POSITIVE_INFINITY)
						p = 1.0;
					else
						p = p/(p + 1.0);

					sumTermsExpected += assignmentModel.shapeTerm(source, target)*p;
				}

		return sumTermsExpected - sumTermsTraining;
	}

	final private double gradientPositionBisection() {

		double sumTermsTraining = 0.0;
		double sumTermsExpected = 0.0;

		// every true bisection
		for (Assignment assignment : trainingSequence)
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSources().size() == 1 &&
				    singleAssignment.getTargets().size() == 2)

					sumTermsTraining +=
							assignmentModel.centerTerm(
									singleAssignment.getSources().get(0),
									singleAssignment.getTargets().get(0),
									singleAssignment.getTargets().get(1));

				if (singleAssignment.getSources().size() == 2 &&
				    singleAssignment.getTargets().size() == 1)

					sumTermsTraining +=
							assignmentModel.centerTerm(
									singleAssignment.getTargets().get(0),
									singleAssignment.getSources().get(0),
									singleAssignment.getSources().get(1));
			}

		// for each possible bisection
		for (Vector<Candidate> sliceCandidates : msers)
			for (Candidate candidate : sliceCandidates) {

				for (Candidate neighbor : candidate.mergeTargets().keySet())
					for (Candidate target : candidate.mergeTargets().get(neighbor)) {

						double p = Math.exp(-assignmentModel.costBisect(target, candidate, neighbor));

						if (p == Double.POSITIVE_INFINITY)
							p = 1.0;
						else
							p = p/(p + 1.0);

						sumTermsExpected += assignmentModel.centerTerm(target, candidate, neighbor)*p;
				}

				for (Candidate neighbor : candidate.splitSources().keySet())
					for (Candidate source : candidate.splitSources().get(neighbor)) {

						double p = Math.exp(-assignmentModel.costBisect(source, candidate, neighbor));

						if (p == Double.POSITIVE_INFINITY)
							p = 1.0;
						else
							p = p/(p + 1.0);

						sumTermsExpected += assignmentModel.centerTerm(source, candidate, neighbor)*p;
				}
			}

		return sumTermsExpected - sumTermsTraining;
	}

	final private double gradientShapeBisection() {

		double sumTermsTraining = 0.0;
		double sumTermsExpected = 0.0;

		// every true bisection
		for (Assignment assignment : trainingSequence)
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSources().size() == 1 &&
				    singleAssignment.getTargets().size() == 2)

					sumTermsTraining +=
							assignmentModel.shapeTerm(
									singleAssignment.getSources().get(0),
									singleAssignment.getTargets().get(0),
									singleAssignment.getTargets().get(1));

				if (singleAssignment.getSources().size() == 2 &&
				    singleAssignment.getTargets().size() == 1)

					sumTermsTraining +=
							assignmentModel.shapeTerm(
									singleAssignment.getTargets().get(0),
									singleAssignment.getSources().get(0),
									singleAssignment.getSources().get(1));
			}

		// for each possible bisection
		for (Vector<Candidate> sliceCandidates : msers)
			for (Candidate candidate : sliceCandidates) {

				for (Candidate neighbor : candidate.mergeTargets().keySet())
					for (Candidate target : candidate.mergeTargets().get(neighbor)) {

						double p = Math.exp(-assignmentModel.costBisect(target, candidate, neighbor));

						if (p == Double.POSITIVE_INFINITY)
							p = 1.0;
						else
							p = p/(p + 1.0);

						sumTermsExpected += assignmentModel.shapeTerm(target, candidate, neighbor)*p;
				}

				for (Candidate neighbor : candidate.splitSources().keySet())
					for (Candidate source : candidate.splitSources().get(neighbor)) {

						double p = Math.exp(-assignmentModel.costBisect(source, candidate, neighbor));

						if (p == Double.POSITIVE_INFINITY)
							p = 1.0;
						else
							p = p/(p + 1.0);

						sumTermsExpected += assignmentModel.shapeTerm(source, candidate, neighbor)*p;
				}
			}

		return sumTermsExpected - sumTermsTraining;
	}

	final private double gradientEnd() {

		double sumTermsTraining = 0.0;
		double sumTermsExpected = 0.0;

		// every true end in the training data
		for (Assignment assignment : trainingSequence)
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSources().get(0) == SequenceSearch.emergeNode)
					sumTermsTraining += assignmentModel.endTerm(singleAssignment.getTargets().get(0));

				if (singleAssignment.getTargets().get(0) == SequenceSearch.deathNode)
					sumTermsTraining += assignmentModel.endTerm(singleAssignment.getSources().get(0));
			}

		// every possible end
		int s = 0;
		for (Vector<Candidate> sliceCandidates : msers) {

			// all but last slice
			if (s < msers.size() - 1)
				for (Candidate target : sliceCandidates) {

					double p = Math.exp(-assignmentModel.costEnd(target));

					if (p == Double.POSITIVE_INFINITY)
						p = 1.0;
					else
						p = p/(p + 1.0);

					sumTermsExpected += assignmentModel.endTerm(target)*p;
				}

			// all but first slice
			if (s > 0)
				for (Candidate source : sliceCandidates) {

					double p = Math.exp(-assignmentModel.costEnd(source));

					if (p == Double.POSITIVE_INFINITY)
						p = 1.0;
					else
						p = p/(p + 1.0);

					sumTermsExpected += assignmentModel.endTerm(source)*p;
				}

			s++;
		}

		return sumTermsExpected - sumTermsTraining;
	}

	final private double regularizer(double value) {

		if (parameterStdDeviation <= 0.0)
			return 0;

		return -value/parameterStdDeviation;
	}
}
