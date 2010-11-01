
public class AssignmentModel {

	static double distanceWeight = 1.0;
	static double areaWeight     = 1.0;

	static final double MinPAssignment       = 1e-10;
	static final double MaxNegLogPAssignment = 100*100 + 50*50; //-Math.log(MinPAssignment);

	static final double negLogP(SingleAssignment assignment) {

		return negLogP(assignment.getSource(), assignment.getTarget());
	}

	static final double negLogP(Region source, Region target) {

		double nlDistance = negLogDistance(source.getCenter(),
		                                   target.getCenter());

		double nlArea     = negLogArea(source.getSize(),
		                               target.getSize());

		return distanceWeight*nlDistance + areaWeight*nlArea;
	}

	static final double negLogDistance(double[] center1, double[] center2) {

		return (center1[0] - center2[0])*(center1[0] - center2[0]) +
		       (center1[1] - center2[1])*(center1[1] - center2[1]);
	}

	static final double negLogArea(int size1, int size2) {

		return (size1 - size2)*(size1 - size2);
	}

	static void setDistanceWeight(double distanceWeight) {

		AssignmentModel.distanceWeight = distanceWeight;
	}

	static void setAreaWeight(double areaWeight) {

		AssignmentModel.areaWeight = areaWeight;
	}
}
