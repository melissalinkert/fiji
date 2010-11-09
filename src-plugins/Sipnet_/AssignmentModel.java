
import Jama.Matrix;

public class AssignmentModel {

	static double covaPosition  = 10.0;
	static double covaSize      = 10000.0;
	static double covaNeighbors = 10.0;

	// number of neighbors to consider for mean neighbor distance
	static int    numNeighbors  = 3;

	static double[][] cova = {{covaPosition, 0.0, 0.0, 0.0},
	                          {0.0, covaPosition, 0.0, 0.0},
	                          {0.0, 0.0, covaSize, 0.0},
	                          {0.0, 0.0, 0.0, covaNeighbors}};

	static Matrix covariance       = new Matrix(cova);
	static Matrix invCovariance    = covariance.inverse();
	static double normaliser       = 1.0/(Math.sqrt(covariance.times(2.0*Math.PI).det()));;
	static double negLogNormaliser = -Math.log(normaliser);

	static final double negLogP(SingleAssignment assignment) {

		return negLogP(assignment.getSource(), assignment.getTarget());
	}

	static final double negLogP(Candidate source, Candidate target) {

		Matrix diff = new Matrix(4, 1);

		diff.set(0, 0, target.getCenter()[0]            - source.getCenter()[0]);
		diff.set(1, 0, target.getCenter()[1]            - source.getCenter()[1]);
		diff.set(2, 0, target.getSize()                 - source.getSize());
		diff.set(3, 0, meanNeighborDistance(source, target, assignment) - source.getMeanNeighborDistance());

		return negLogNormaliser + 0.5*(diff.transpose().times(invCovariance).times(diff)).get(0, 0);
	}

	static final void setCovariance(double[][] cova) {

		covariance = new Matrix(cova);
		normaliser = 1.0/(Math.sqrt(covariance.times(2.0*Math.PI).det()));

		invCovariance    = covariance.inverse();
		negLogNormaliser = -Math.log(normaliser);
	}

	static final double meanNeighborDistance(Region region, Set<Region> neighbors, Assignment assignment) {

		double neighborDistance = 0.0;

		for (Region neighbor : neighbors) {

			// get corresponding neighbor in region's slice
			Region correspond = null;
			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSource() == neighbor) {
					correspond = singleAssignment.getTarget();
					break;
				}
			}

			// if not existing (not assigned yet) use closest region in region's
			// slice instead
			if (correspond == null)
				correspond = neighbor.getClosestRegions().peek();

			// contribute to mean
			neighborDistance += Math.sqrt(
			    (region.getCenter()[0] - correspond.getCenter()[0])*(region.getCenter()[0] - correspond.getCenter()[0]) -
			    (region.getCenter()[1] - correspond.getCenter()[1])*(region.getCenter()[1] - correspond.getCenter()[1]));
		}

		return neighborDistance/numNeighbors;
	}
}
