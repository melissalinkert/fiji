
import Jama.Matrix;

public class AssignmentModel {

	static double covaPosition = 10.0;
	static double covaSize     = 10000.0;

	static double[][] cova = {{covaPosition, 0.0, 0.0},
	                          {0.0, covaPosition, 0.0},
	                          {0.0, 0.0, covaSize}};

	static Matrix covariance       = new Matrix(cova);
	static Matrix invCovariance    = covariance.inverse();
	static double normaliser       = 1.0/(Math.sqrt(covariance.times(2.0*Math.PI).det()));;
	static double negLogNormaliser = -Math.log(normaliser);

	static final double negLogP(SingleAssignment assignment) {

		return negLogP(assignment.getSource(), assignment.getTarget());
	}

	static final double negLogP(Region source, Region target) {

		Matrix diff = new Matrix(3, 1);

		diff.set(0, 0, target.getCenter()[0] - source.getCenter()[0]);
		diff.set(1, 0, target.getCenter()[1] - source.getCenter()[1]);
		diff.set(2, 0, target.getSize()      - source.getSize());

		return negLogNormaliser + 0.5*(diff.transpose().times(invCovariance).times(diff)).get(0, 0);
	}

	static final void setCovariance(double[][] cova) {

		covariance = new Matrix(cova);
		normaliser = 1.0/(Math.sqrt(covariance.times(2.0*Math.PI).det()));

		invCovariance    = covariance.inverse();
		negLogNormaliser = -Math.log(normaliser);
	}
}
