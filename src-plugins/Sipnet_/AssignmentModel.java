
import Jama.Matrix;

public class AssignmentModel {

	static double covaPosition  = 10.0;
	static double covaSize      = 10000.0;
	static double covaNeighbors = 1.0;

	static double[][] covaAppearance =
	    {{covaPosition, 0.0, 0.0},
	     {0.0, covaPosition, 0.0},
	     {0.0, 0.0, covaSize}};

	static Matrix covariance           = new Matrix(covaAppearance);
	static Matrix invCovariance        = covariance.inverse();
	static double normAppearance       = 1.0/(Math.sqrt(covariance.times(2.0*Math.PI).det()));;
	static double negLogNormAppearance = -Math.log(normAppearance);
	static double normNeighbors        = 1.0/(Math.sqrt(covaNeighbors*2.0*Math.PI));
	static double negLogNormNeighbors  = -Math.log(normNeighbors);

	static final double negLogPAppearance(SingleAssignment assignment) {

		return negLogPAppearance(assignment.getSource(), assignment.getTarget());
	}

	static final double negLogPAppearance(Candidate source, Candidate target) {

		Matrix diff = new Matrix(3, 1);

		diff.set(0, 0, target.getCenter()[0]            - source.getCenter()[0]);
		diff.set(1, 0, target.getCenter()[1]            - source.getCenter()[1]);
		diff.set(2, 0, target.getSize()                 - source.getSize());

		return negLogNormAppearance + 0.5*(diff.transpose().times(invCovariance).times(diff)).get(0, 0);
	}

	/**
	 * @param source The source candidate (that knows its neighbors)
	 * @param target The target candidate
	 * @param currentAssignment A possibly partial assignment
	 * @return The negative log-probability of the change in the mean neighbor
	 * distance. If the assignment does not cover all of the neighbors of the
	 * source node, the result will be an upper bound on that probability (i.e.,
	 * an optimistic guess.
	 */
	static final double negLogPNeighbors(Candidate source, Candidate target, Assignment currentAssignment) {

		double meanNeighborDistance = meanNeighborDistance(source, target, currentAssignment);

		return negLogNormNeighbors + 0.5*((source.getMeanNeighborDistance() - meanNeighborDistance)*
		                                  (source.getMeanNeighborDistance() - meanNeighborDistance)/
		                                  covaNeighbors);
	}

	static final double meanNeighborDistance(Candidate source, Candidate target, Assignment assignment) {

		double neighborDistance = 0.0;

		for (Candidate neighbor : source.getNeighbors()) {

			// get corresponding neighbor in target's slice
			Candidate correspond = null;
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSource() == neighbor) {
					correspond = singleAssignment.getTarget();
					break;
				}
			}

			// if not existing (not assigned yet) use original neighbor instead
			// (this yields a quite optimistic estimate)
			if (correspond == null)
				correspond = neighbor;

			// contribute to mean
			neighborDistance += Math.sqrt(
			    (target.getCenter()[0] - correspond.getCenter()[0])*(target.getCenter()[0] - correspond.getCenter()[0]) -
			    (target.getCenter()[1] - correspond.getCenter()[1])*(target.getCenter()[1] - correspond.getCenter()[1]));
		}

		return neighborDistance/source.getNeighbors().size();
	}
}
