
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.Properties;

import Jama.Matrix;

import ij.IJ;

public class AssignmentModel {

	/*
	 * MODEL PARAMTETERS
	 */

	private double priorDeath           = 1e-100;
	private double priorSplit           = 1e-100;

	private double covaPosition         = 10.0;
	private double covaKLDivergence     = 0.5;
	private double covaNeighborPosition = 5.0;

	private ShapeDissimilarity shapeDissimilarity;

	/*
	 * IMPLEMENTATION
	 */

	private double[][] covaApp =
	    {{covaPosition, 0.0, 0.0},
	     {0.0, covaPosition, 0.0},
	     {0.0, 0.0, covaKLDivergence}};
	private double[][] covaNeighOff =
	    {{covaNeighborPosition, 0.0},
	     {0.0, covaNeighborPosition}};

	private double negLogPriorDeath           = -Math.log(priorDeath);
	private double negLogPriorSplit           = -Math.log(priorSplit);

	private Matrix covaAppearance             = new Matrix(covaApp);
	private Matrix invCovaAppearance          = covaAppearance.inverse();
	private double normAppearance             = 1.0/(Math.sqrt(covaAppearance.times(2.0*Math.PI).det()));
	private double negLogNormAppearance       = -Math.log(normAppearance);

	private Matrix covaNeighborOffset         = new Matrix(covaNeighOff);
	private Matrix invCovaNeighborOffset      = covaNeighborOffset.inverse();
	private double normNeighborOffset         = 1.0/(Math.sqrt(covaNeighborOffset.times(2.0*Math.PI).det()));
	private double negLogNormNeighborOffset   = -Math.log(normNeighborOffset);

	public AssignmentModel(ShapeDissimilarity shapeDissimilarity) {

		this.shapeDissimilarity = shapeDissimilarity;
	}

	final double negLogPAssignment(SingleAssignment assignment) {

		return negLogPAssignment(assignment.getSource(), assignment.getTarget());
	}

	final double negLogPAssignment(Candidate source, Candidate target) {


		return
			negLogPAppearance(source, target) +
			0.5*(negLogPSegmentation(source) + negLogPSegmentation(target));
	}

	final double negLogPAppearance(Candidate source, Candidate target) {

		Matrix diff = new Matrix(3, 1);

		diff.set(0, 0, target.getCenter()[0] - source.getCenter()[0]);
		diff.set(1, 0, target.getCenter()[1] - source.getCenter()[1]);
		diff.set(2, 0, shapeDissimilarity.dissimilarity(source, target));

		return
			negLogNormAppearance +
			0.5*(diff.transpose().times(invCovaAppearance).times(diff)).get(0, 0);
	}

	final double negLogPDeath(Candidate candidate) {

		return
			negLogPriorDeath +
			0.5*negLogPSegmentation(candidate);
	}

	final double negLogPSplit(Candidate source, Candidate target1, Candidate target2) {

		return
			negLogPriorSplit +
			0.5*(negLogPSegmentation(source) + negLogPSegmentation(target1) + negLogPSegmentation(target2));
	}

	final double negLogPSegmentation(Candidate candidate) {

		double probMembrane = (double)candidate.getMeanGrayValue()/255.0;

		// ensure numerical stability
		probMembrane = Math.min(Math.max(0.001, probMembrane), 0.999);

		// foreground is neurons
		double negLogPPixelNeuron   = -Math.log(1.0 - probMembrane);
		double negLogPPixelMembrane = -Math.log(probMembrane);

		return candidate.getSize()*(negLogPPixelNeuron - negLogPPixelMembrane);
	}

	final double negLogPNeighbor(double[] originalOffset, double[] offset) {

		Matrix diff = new Matrix(2, 1);
		
		diff.set(0, 0, originalOffset[0] - offset[0]);
		diff.set(1, 0, originalOffset[1] - offset[1]);

		return negLogNormNeighborOffset + 0.5*(diff.transpose().times(invCovaNeighborOffset).times(diff)).get(0, 0);
	}

	final void readParameters(String filename) {

		Properties parameterFile = new Properties();

		try {
			parameterFile.load(new FileInputStream(new File(filename)));

			priorDeath           = Double.valueOf(parameterFile.getProperty("prior_death"));
			priorSplit           = Double.valueOf(parameterFile.getProperty("prior_split"));

			covaPosition         = Double.valueOf(parameterFile.getProperty("cova_position"));
			covaKLDivergence     = Double.valueOf(parameterFile.getProperty("cova_kl_divergence"));

			IJ.log("Assignment model read parameters:");
			IJ.log("  prior death/emerge: " + priorDeath);
			IJ.log("  prior split: " + priorSplit);
			IJ.log("  cova position: " + covaPosition);
			IJ.log("  cova shape dissimilarity: " + covaKLDivergence);
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
