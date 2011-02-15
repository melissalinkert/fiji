package sipnet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.Properties;

import ij.IJ;

public class AssignmentModel {

	/*
	 * MODEL PARAMTETERS
	 */

	// factor weights
	private double weightData;
	private double weightPositionContinuation;
	private double weightShapeContinuation;
	private double weightPositionBisection;
	private double weightShapeBisection;
	private double weightEnd;

	// size of the margin (in pixels), in which appearence of neurons is more
	// likely
	private double appearanceMargin = 100;

	// a class used to get a measure of shape dissimilarity
	private ShapeDissimilarity shapeDissimilarity;

	/*
	 * IMPLEMENTATION
	 */

	// singleton
	private static AssignmentModel theInstance;

	// size of the slice images to infer distance to border
	private int[] imageDimensions;

	// this is needed very oftern - therefor, cache the results
	private HashMap<Candidate, HashMap<Candidate, Double>> continuationCache;

	final static public AssignmentModel getInstance() {

		if (theInstance == null)
			theInstance = new AssignmentModel();

		return theInstance;
	}

	private AssignmentModel() {

		this.continuationCache  = new HashMap<Candidate, HashMap<Candidate, Double>>();
	}

	final public void setImageDimensions(int[] dimensions) {

		this.imageDimensions = dimensions;
	}

	final public void setShapeDissimilarity(ShapeDissimilarity shapeDissimilarity) {

		this.shapeDissimilarity = shapeDissimilarity;
	}

	public final double costContinuation(Candidate source, Candidate target, boolean dataTerm) {

		final Candidate smaller = (source.getId() > target.getId() ? target : source);
		final Candidate bigger  = (source.getId() > target.getId() ? source : target);

		HashMap<Candidate, Double> shm = continuationCache.get(smaller);

		if (shm == null) {
			shm = new HashMap<Candidate, Double>();
			continuationCache.put(smaller, shm);
		}

		Double prior = shm.get(bigger);

		if (prior == null) {

			prior =
					weightPositionContinuation*centerDistance(source, target) +
					weightShapeContinuation*shapeDistance(source, target);
			shm.put(bigger, prior);
		}

		if (dataTerm)
			return prior + weightData*(dataTerm(source) + dataTerm(target));

		return prior;
	}

	public final double costBisect(Candidate source, Candidate target1, Candidate target2) {

		return
			weightPositionBisection*centerDistance(source, target1, target2) +
			weightShapeBisection*shapeDistance(source, target1, target2) +
			weightData*(dataTerm(source) + dataTerm(target1) + dataTerm(target2));
	}

	public final double costEnd(Candidate candidate) {

		return
			weightEnd*endPrior(candidate) +
			weightData*dataTerm(candidate);
	}

	private final double centerDistance(Candidate source, Candidate target) {

		return
				(target.getCenter(0) - source.getCenter(0))*(target.getCenter(0) - source.getCenter(0)) +
				(target.getCenter(1) - source.getCenter(1))*(target.getCenter(1) - source.getCenter(1));
	}

	private final double shapeDistance(Candidate source, Candidate target) {

		final double diss =
				shapeDissimilarity.dissimilarity(source, target);

		return diss*diss;
	}

	private final double centerDistance(Candidate source, Candidate target1, Candidate target2) {

		double[] mergedCenter = target1.getCenter();

		mergedCenter[0] =
				(target1.getSize()*mergedCenter[0] +
				 target2.getSize()*target2.getCenter(0))/
				(target1.getSize() + target2.getSize());
		mergedCenter[1] =
				(target1.getSize()*mergedCenter[1] +
				 target2.getSize()*target2.getCenter(1))/
				(target1.getSize() + target2.getSize());

		return
				(mergedCenter[0] - source.getCenter(0))*(mergedCenter[0] - source.getCenter(0)) +
				(mergedCenter[1] - source.getCenter(1))*(mergedCenter[1] - source.getCenter(1));
	}

	private final double shapeDistance(Candidate source, Candidate target1, Candidate target2) {

		final double diss =
				shapeDissimilarity.dissimilarity(source, target1, target2);
		return diss*diss;
	}

	private final double endPrior(Candidate candidate) {

		// distance to border
		double distance =
				Math.min(
					Math.min(candidate.getCenter(0), imageDimensions[0] - candidate.getCenter(0)),  // min_x
					Math.min(candidate.getCenter(1), imageDimensions[1] - candidate.getCenter(1))); // min_y

		double weightPosition =
				Math.min(1.0, distance/appearanceMargin);

		return weightPosition*candidate.getSize();
	}

	private final double dataTerm(Candidate candidate) {

		double probMembrane = (double)candidate.getMeanGrayValue()/255.0;

		// ensure numerical stability
		probMembrane = Math.min(Math.max(0.001, probMembrane), 0.999);

		// foreground is neurons
		double negLogPPixelNeuron   = -Math.log(1.0 - probMembrane);
		double negLogPPixelMembrane = -Math.log(probMembrane);

		return candidate.getSize()*(negLogPPixelNeuron - negLogPPixelMembrane);
	}

	final void readParameters(String filename) {

		Properties parameterFile = new Properties();

		try {
			parameterFile.load(new FileInputStream(new File(filename)));

			weightData                 = Double.valueOf(parameterFile.getProperty("weight_data"));
			weightPositionContinuation = Double.valueOf(parameterFile.getProperty("weight_position_continuation"));
			weightShapeContinuation    = Double.valueOf(parameterFile.getProperty("weight_shape_continuation"));
			weightPositionBisection    = Double.valueOf(parameterFile.getProperty("weight_position_bisection"));
			weightShapeBisection       = Double.valueOf(parameterFile.getProperty("weight_shape_bisection"));
			weightEnd                  = Double.valueOf(parameterFile.getProperty("weight_end"));

			appearanceMargin   = Double.valueOf(parameterFile.getProperty("appearance_margin"));

			IJ.log("Assignment model read parameters:");
			IJ.log("  weight data term: "               + weightData);
			IJ.log("  weight pos. continuation term: "  + weightPositionContinuation);
			IJ.log("  weight shape continuation term: " + weightShapeContinuation);
			IJ.log("  weight pos. bisection term: "     + weightPositionBisection);
			IJ.log("  weight shape bisection term: "    + weightShapeBisection);
			IJ.log("  weight end term: "                + weightEnd);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	final void setParameters(double[] w) {

		weightData                 = w[0];
		weightPositionContinuation = w[1];
		weightShapeContinuation    = w[2];
		weightPositionBisection    = w[3];
		weightShapeBisection       = w[4];
		weightEnd                  = w[5];
	}

	final public static AssignmentModel readFromFile(String filename, int[] imageDimensions) {

		AssignmentModel assignmentModel = getInstance();

		assignmentModel.setImageDimensions(imageDimensions);

		Properties parameterFile = new Properties();

		try {

			parameterFile.load(new FileInputStream(new File(filename)));

			if (parameterFile.getProperty("shape_dissimilarity").equals("set_difference"))
				assignmentModel.setShapeDissimilarity(new SetDifference());
			else if (parameterFile.getProperty("shape_dissimilarity").equals("kl_divergence"))
				assignmentModel.setShapeDissimilarity(new KLDivergence());
			else {
				IJ.log("No shape dissimilarity defined in " + filename + ", taking set_difference");
				assignmentModel.setShapeDissimilarity(new SetDifference());
			}

			assignmentModel.readParameters(filename);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return assignmentModel;
	}

}
