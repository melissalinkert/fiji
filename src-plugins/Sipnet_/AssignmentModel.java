
public class AssignmentModel {

	static final double MinPAssignment       = 1e-10;
	static final double MaxNegLogPAssignment = 100*100; //-Math.log(MinPAssignment);

	static double distanceWeight = 1.0;

	static final double negLogP(SingleAssignment assignment) {

		return negLogP(assignment.getSource(), assignment.getTarget());
	}

	static final double negLogP(Region source, Region target) {

		double nlDistance = negLogDistance(source.getCenter(),
		                                   target.getCenter());

		return distanceWeight*nlDistance;
	}

	static final double negLogDistance(double[] center1, double[] center2) {

		return (center1[0] - center2[0])*(center1[0] - center2[0]) +
		       (center1[1] - center2[1])*(center1[1] - center2[1]);
	}

	static void setDistanceWeight(double distanceWeight) {

		AssignmentModel.distanceWeight = distanceWeight;
	}
}
