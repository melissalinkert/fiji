
import Jama.Matrix;

public class AssignmentModel {

	static double priorDeath           = 1e-10;
	static double priorContinuation    = 1.0;
	static double priorSplit           = 1e-10;
	static double priorImpossible      = 1e-300;

	static double covaPosition         = 10.0;
	static double covaSize             = 1.0;
	static double covaCircularity      = 0.5;
	static double covaNeighborPosition = 5.0;

	static double[][] covaApp =
	    {{covaPosition, 0.0, 0.0, 0.0},
	     {0.0, covaPosition, 0.0, 0.0},
	     {0.0, 0.0, covaSize, 0.0},
		 {0.0, 0.0, 0.0, covaCircularity}};
	static double[][] covaNeighOff =
	    {{covaNeighborPosition, 0.0},
	     {0.0, covaNeighborPosition}};

	static double negLogPriorDeath           = -Math.log(priorDeath);
	static double negLogPriorContinuation    = -Math.log(priorContinuation);
	static double negLogPriorSplit           = -Math.log(priorSplit);
	static double negLogPriorImpossible      = -Math.log(priorImpossible);

	static Matrix covaAppearance             = new Matrix(covaApp);
	static Matrix invCovaAppearance          = covaAppearance.inverse();
	static double normAppearance             = 1.0/(Math.sqrt(covaAppearance.times(2.0*Math.PI).det()));
	static double negLogNormAppearance       = -Math.log(normAppearance);

	static Matrix covaNeighborOffset         = new Matrix(covaNeighOff);
	static Matrix invCovaNeighborOffset      = covaNeighborOffset.inverse();
	static double normNeighborOffset         = 1.0/(Math.sqrt(covaNeighborOffset.times(2.0*Math.PI).det()));
	static double negLogNormNeighborOffset   = -Math.log(normNeighborOffset);

	static final double negLogPAppearance(SingleAssignment assignment) {

		return negLogPAppearance(assignment.getSource(), assignment.getTarget());
	}

	static final double negLogPAppearance(Candidate source, Candidate target) {

		double ciruclaritySource = 4*Math.PI*source.getSize()/(source.getPerimeter()*source.getPerimeter());
		double ciruclarityTarget = 4*Math.PI*target.getSize()/(target.getPerimeter()*target.getPerimeter());

		Matrix diff = new Matrix(4, 1);

		diff.set(0, 0, target.getCenter()[0] - source.getCenter()[0]);
		diff.set(1, 0, target.getCenter()[1] - source.getCenter()[1]);
		diff.set(2, 0, (Math.sqrt(target.getSize()) - Math.sqrt(source.getSize()))/Math.sqrt(source.getSize()));
		diff.set(3, 0, ciruclaritySource - ciruclarityTarget);

		return negLogNormAppearance + 0.5*(diff.transpose().times(invCovaAppearance).times(diff)).get(0, 0);
	}

	static final double negLogPNeighbor(double[] originalOffset, double[] offset) {

		Matrix diff = new Matrix(2, 1);
		
		diff.set(0, 0, originalOffset[0] - offset[0]);
		diff.set(1, 0, originalOffset[1] - offset[1]);

		return negLogNormNeighborOffset + 0.5*(diff.transpose().times(invCovaNeighborOffset).times(diff)).get(0, 0);
	}
}
