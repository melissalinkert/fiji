
public class AssignmentModel {

	static double distanceVariance = 100;
	static double distanceLogZ     = Math.log(Math.sqrt(2*Math.PI*distanceVariance));
	static double areaVariance     = 1;
	static double areaLogZ         = Math.log(Math.sqrt(2*Math.PI*areaVariance));

	static final double negLogP(SingleAssignment assignment) {

		return negLogP(assignment.getSource(), assignment.getTarget());
	}

	static final double negLogP(Region source, Region target) {

		double nlDistance = negLogDistance(source.getCenter(),
		                                   target.getCenter());

		double nlArea     = negLogArea(source.getSize(),
		                               target.getSize());

		return nlDistance + nlArea;
	}

	static final double negLogDistance(double[] center1, double[] center2) {

		return ((center1[0] - center2[0])*(center1[0] - center2[0]) +
		        (center1[1] - center2[1])*(center1[1] - center2[1]))/(2*distanceVariance)
		       - distanceLogZ;
	}

	static final double negLogArea(int size1, int size2) {

		double sizeChange = 1.0 - size2/size1;
		return (sizeChange*sizeChange)/(2*areaVariance) - areaLogZ;
	}

	static void setDistanceVariance(double distanceVariance) {

		AssignmentModel.distanceVariance = distanceVariance;
	}

	static void setAreaVariance(double areaVariance) {

		AssignmentModel.areaVariance = areaVariance;
	}
}
