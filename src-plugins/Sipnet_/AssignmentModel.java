
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.Properties;

import Jama.Matrix;

import ij.IJ;

public class AssignmentModel {

	/*
	 * MODEL PARAMTETERS
	 */

	// factor weights
	private double weightData;
	private double weightContinuation;
	private double weightBisection;
	private double weightEnd;

	// default values for factor functions
	private double covaPosition  = 10.0;
	private double covaShape     = 0.5;

	private ShapeDissimilarity shapeDissimilarity;

	/*
	 * IMPLEMENTATION
	 */

	private double[][] covaApp =
	    {{covaPosition, 0.0, 0.0},
	     {0.0, covaPosition, 0.0},
	     {0.0, 0.0, covaShape}};

	private Matrix covaAppearance             = new Matrix(covaApp);
	private Matrix invCovaAppearance          = covaAppearance.inverse();

	// this is needed very oftern - therefor, cache the results
	private HashMap<Candidate, HashMap<Candidate, Double>> continuationCache;

	public AssignmentModel(ShapeDissimilarity shapeDissimilarity) {

		this.shapeDissimilarity = shapeDissimilarity;
		this.continuationCache  = new HashMap<Candidate, HashMap<Candidate, Double>>();
	}

	public final double costContinuation(Candidate source, Candidate target) {

		final Candidate smaller = (source.getId() > target.getId() ? target : source);
		final Candidate bigger  = (source.getId() > target.getId() ? source : target);

		HashMap<Candidate, Double> shm = continuationCache.get(smaller);

		if (shm == null) {
			shm = new HashMap<Candidate, Double>();
			continuationCache.put(smaller, shm);
		}

		final Double costs = shm.get(bigger);

		if (costs == null) {

			double dcosts =
					weightContinuation*continuationPrior(source, target) +
					weightData*(dataTerm(source) + dataTerm(target));

			shm.put(bigger, dcosts);

			return dcosts;
		}

		return costs;
	}

	public final double costBisect(Candidate source, Candidate target1, Candidate target2) {

		return
			weightBisection*bisectionPrior(source, target1, target2) +
			weightData*(dataTerm(source) + dataTerm(target1) + dataTerm(target2));
	}

	public final double costEnd(Candidate candidate) {

		return
			weightEnd*endPrior(candidate) +
			weightData*dataTerm(candidate);
	}

	private final double continuationPrior(Candidate source, Candidate target) {

		Matrix diff = new Matrix(3, 1);

		diff.set(0, 0, target.getCenter()[0] - source.getCenter()[0]);
		diff.set(1, 0, target.getCenter()[1] - source.getCenter()[1]);
		diff.set(2, 0, shapeDissimilarity.dissimilarity(source, target));

		return
				(diff.transpose().times(invCovaAppearance).times(diff)).get(0, 0);
	}

	private final double bisectionPrior(Candidate source, Candidate target1, Candidate target2) {

		Matrix diff = new Matrix(3, 1);

		double[] mergedCenter = target1.getCenter();
		mergedCenter[0] =
				(target1.getSize()*mergedCenter[0] +
				 target2.getSize()*target2.getCenter()[0])/
				(target1.getSize() + target2.getSize());
		mergedCenter[1] =
				(target1.getSize()*mergedCenter[1] +
				 target2.getSize()*target2.getCenter()[1])/
				(target1.getSize() + target2.getSize());

		diff.set(0, 0, mergedCenter[0] - source.getCenter()[0]);
		diff.set(1, 0, mergedCenter[1] - source.getCenter()[1]);
		diff.set(2, 0, shapeDissimilarity.dissimilarity(source, target1, target2));

		return
				(diff.transpose().times(invCovaAppearance).times(diff)).get(0, 0);
	}

	private final double endPrior(Candidate candidate) {

		return candidate.getSize();
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

			weightData         = Double.valueOf(parameterFile.getProperty("weight_data"));
			weightContinuation = Double.valueOf(parameterFile.getProperty("weight_continuation"));
			weightBisection    = Double.valueOf(parameterFile.getProperty("weight_bisection"));
			weightEnd          = Double.valueOf(parameterFile.getProperty("weight_end"));

			covaPosition         = Double.valueOf(parameterFile.getProperty("cova_position"));
			covaShape            = Double.valueOf(parameterFile.getProperty("cova_shape"));

			IJ.log("Assignment model read parameters:");
			IJ.log("  weight data term: "         + weightData);
			IJ.log("  weight continuation term: " + weightContinuation);
			IJ.log("  weight bisection term: "    + weightBisection);
			IJ.log("  weight end term: "          + weightEnd);
			IJ.log("  cova position: "            + covaPosition);
			IJ.log("  cova shape dissimilarity: " + covaShape);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	final public static AssignmentModel readFromFile(String filename) {

		AssignmentModel assignmentModel = null;

		Properties parameterFile = new Properties();

		try {

			parameterFile.load(new FileInputStream(new File(filename)));

			if (parameterFile.getProperty("shape_dissimilarity").equals("set_difference"))
				assignmentModel = new AssignmentModel(new SetDifference());
			else if (parameterFile.getProperty("shape_dissimilarity").equals("kl_divergence"))
				assignmentModel = new AssignmentModel(new KLDivergence());
			else {
				IJ.log("No shape dissimilarity defined in " + filename + ", taking set_difference");
				assignmentModel = new AssignmentModel(new SetDifference());
			}

			assignmentModel.readParameters(filename);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return assignmentModel;
	}

}
