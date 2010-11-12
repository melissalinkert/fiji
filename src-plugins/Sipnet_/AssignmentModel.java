
import Jama.Matrix;

public class AssignmentModel {

	static double covaPosition         = 10.0;
	static double covaSize             = 1000.0;
	static double covaNeighborDistance = 5.0;

	static double[][] covaAppearance =
	    {{covaPosition, 0.0, 0.0},
	     {0.0, covaPosition, 0.0},
	     {0.0, 0.0, covaSize}};

	static Matrix covariance                 = new Matrix(covaAppearance);
	static Matrix invCovariance              = covariance.inverse();
	static double normAppearance             = 1.0/(Math.sqrt(covariance.times(2.0*Math.PI).det()));;
	static double negLogNormAppearance       = -Math.log(normAppearance);
	static double normNeighborDistance       = 1.0/(Math.sqrt(covaNeighborDistance*2.0*Math.PI));
	static double negLogNormNeighborDistance = -Math.log(normNeighborDistance);

	static final double negLogPAppearance(SingleAssignment assignment) {

		return negLogPAppearance(assignment.getSource(), assignment.getTarget());
	}

	static final double negLogPAppearance(Candidate source, Candidate target) {

		Matrix diff = new Matrix(3, 1);

		diff.set(0, 0, target.getCenter()[0] - source.getCenter()[0]);
		diff.set(1, 0, target.getCenter()[1] - source.getCenter()[1]);
		diff.set(2, 0, target.getSize()      - source.getSize());

		return negLogNormAppearance + 0.5*(diff.transpose().times(invCovariance).times(diff)).get(0, 0);
	}

	static final double negLogPNeighbor(double originalDistance, double distance) {

		double diff = originalDistance - distance;

		return negLogNormNeighborDistance +
		    0.5*(diff*diff)/covaNeighborDistance;
	}
}
