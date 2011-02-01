
import Jama.Matrix;

public class AssignmentModel {

	private static double priorDeath           = 1e-100;
	private static double priorSplit           = 1e-100;

	private static double covaPosition         = 10.0;
	private static double covaKLDivergence     = 0.5;
	private static double covaNeighborPosition = 5.0;

	private static double[][] covaApp =
	    {{covaPosition, 0.0, 0.0},
	     {0.0, covaPosition, 0.0},
	     {0.0, 0.0, covaKLDivergence}};
	private static double[][] covaNeighOff =
	    {{covaNeighborPosition, 0.0},
	     {0.0, covaNeighborPosition}};

	private static double negLogPriorDeath           = -Math.log(priorDeath);
	private static double negLogPriorSplit           = -Math.log(priorSplit);

	private static Matrix covaAppearance             = new Matrix(covaApp);
	private static Matrix invCovaAppearance          = covaAppearance.inverse();
	private static double normAppearance             = 1.0/(Math.sqrt(covaAppearance.times(2.0*Math.PI).det()));
	private static double negLogNormAppearance       = -Math.log(normAppearance);

	private static Matrix covaNeighborOffset         = new Matrix(covaNeighOff);
	private static Matrix invCovaNeighborOffset      = covaNeighborOffset.inverse();
	private static double normNeighborOffset         = 1.0/(Math.sqrt(covaNeighborOffset.times(2.0*Math.PI).det()));
	private static double negLogNormNeighborOffset   = -Math.log(normNeighborOffset);

	static final double negLogPAssignment(SingleAssignment assignment) {

		return negLogPAssignment(assignment.getSource(), assignment.getTarget());
	}

	static final double negLogPAssignment(Candidate source, Candidate target) {


		return
			negLogPAppearance(source, target) +
			0.5*(negLogPSegmentation(source) + negLogPSegmentation(target));
	}

	static final double negLogPAppearance(Candidate source, Candidate target) {

		Matrix diff = new Matrix(3, 1);

		diff.set(0, 0, target.getCenter()[0] - source.getCenter()[0]);
		diff.set(1, 0, target.getCenter()[1] - source.getCenter()[1]);
		diff.set(2, 0, getKLDivergence(source.getCovariance(), target.getCovariance()));

		return
			negLogNormAppearance +
			0.5*(diff.transpose().times(invCovaAppearance).times(diff)).get(0, 0);
	}

	static final double negLogPDeath(Candidate candidate) {

		return
			negLogPriorDeath +
			0.5*negLogPSegmentation(candidate);
	}

	static final double negLogPSplit(Candidate source, Candidate target1, Candidate target2) {

		return
			negLogPriorSplit +
			0.5*(negLogPSegmentation(source) + negLogPSegmentation(target1) + negLogPSegmentation(target2));
	}

	static final double negLogPSegmentation(Candidate candidate) {

		double probMembrane = (double)candidate.getMeanGrayValue()/255.0;

		// ensure numerical stability
		probMembrane = Math.min(Math.max(0.001, probMembrane), 0.999);

		// foreground is neurons
		double negLogPPixelNeuron   = -Math.log(1.0 - probMembrane);
		double negLogPPixelMembrane = -Math.log(probMembrane);

		return candidate.getSize()*(negLogPPixelNeuron - negLogPPixelMembrane);
	}

	static final double negLogPNeighbor(double[] originalOffset, double[] offset) {

		Matrix diff = new Matrix(2, 1);
		
		diff.set(0, 0, originalOffset[0] - offset[0]);
		diff.set(1, 0, originalOffset[1] - offset[1]);

		return negLogNormNeighborOffset + 0.5*(diff.transpose().times(invCovaNeighborOffset).times(diff)).get(0, 0);
	}

	static final private double getKLDivergence(Matrix c1, Matrix c2) {

		final double logDet = Math.log(c1.det()/c2.det());
		final double trace  = c1.inverse().times(c2).trace();

		return 0.5*(logDet + trace + 2);
	}
}
