
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

import java.util.Arrays;
import java.util.Properties;

import mpicbg.imglib.cursor.Cursor;

import mser.MSER;

import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;

import ij.gui.GenericDialog;

import ij.plugin.PlugIn;

import ij.process.ImageProcessor;

import sipnet.Assignment;
import sipnet.Evaluator;
import sipnet.GroundTruth;
import sipnet.SingleAssignment;
import sipnet.io.IO;
import sipnet.io.ResultCacher;

import sipnet.AssignmentModel;
import sipnet.Candidate;
import sipnet.CandidateFactory;
import sipnet.Sequence;
import sipnet.Sipnet;
import sipnet.Texifyer;
import sipnet.Visualiser;

import mpicbg.imglib.cursor.LocalizableByDimCursor;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;

import mpicbg.imglib.type.numeric.RealType;

public class Sipnet_<T extends RealType<T>> implements PlugIn {

	// the stack to process
	private ImagePlus    membraneImp;
	private ImagePlus    groundtruthImp;
	private ImagePlus    msersImp;
	private ImagePlus    visualisationImp;
	private int          numSlices;

	private int          firstSlice;
	private int          lastSlice;

	private Sipnet       sipnet;

	// parameters
	private int    delta        = 1;
	private int    minArea      = 10;
	private int    maxArea      = 100000;
	private double maxVariation = 10.0;
	private double minDiversity = 0.1;

	// grip search parameters of assignment model
	private boolean  performGridSearch;
	private double[] startParameters = null;
	private double[] endParameters = null;
	private double[] stepParameters = null;

	// don't perform inference, visualise only
	private boolean visualisationOnly    = false;
	private boolean compareToGroundtruth = true;

	private GroundTruth groundtruth = null;

	private class ProcessingThread extends Thread {

		public void run() {

			// read image
			membraneImp = WindowManager.getCurrentImage();
			if (membraneImp == null) {
				IJ.showMessage("Please open an image first.");
				return;
			}
			numSlices = membraneImp.getStack().getSize();

			// create result cacher
			ResultCacher resultCacher = new ResultCacher("./.cache", new IO());

			// read assignment model paramters
			AssignmentModel.readFromFile(
					"./assignment_model.conf",
					new int[]{membraneImp.getWidth(), membraneImp.getHeight()});

			// setup mser algorithm
			MSER<T, Candidate> mser =
				new MSER<T, Candidate>(new int[]{membraneImp.getWidth(), membraneImp.getHeight()},
				                       delta,
				                       minArea,
				                       maxArea,
				                       maxVariation,
				                       minDiversity,
				                       new CandidateFactory());

			// read candidate msers
			msersImp = resultCacher.readMserImages(membraneImp.getOriginalFileInfo().fileName, mser.getParameters());
			Vector<Vector<Candidate>> sliceCandidates =
				resultCacher.readMsers(
						membraneImp.getOriginalFileInfo().fileName,
						mser.getParameters(),
						firstSlice - 1,
						lastSlice  - 1);

			if (msersImp == null || sliceCandidates == null) {

				// prepare segmentation image
				msersImp = membraneImp.createImagePlus();
				ImageStack regStack = new ImageStack(membraneImp.getWidth(), membraneImp.getHeight());
				for (int s = 1; s <= numSlices; s++) {
					ImageProcessor duplProcessor = membraneImp.getStack().getProcessor(s).duplicate();
					regStack.addSlice("", duplProcessor);
				}
				msersImp.setStack(regStack);
				msersImp.setDimensions(1, numSlices, 1);
				if (numSlices > 1)
					msersImp.setOpenAsHyperStack(true);
				IJ.run(msersImp, "Fire", "");

				msersImp.setTitle("msers of " + membraneImp.getTitle());

				Texifyer texifyer = new Texifyer(msersImp, null, "./sipnet-tex/");

				// prepare slice candidates
				sliceCandidates = new Vector<Vector<Candidate>>();
				sliceCandidates.setSize(numSlices);
				Vector<Vector<Candidate>> sliceTopMsers = new Vector<Vector<Candidate>>();
				sliceTopMsers.setSize(numSlices);

				// extract msers from membrane image
				for (int s = 0; s < numSlices; s++) {
				
					IJ.log("Extracing MSERs from slice " + s + "...");

					HashSet<Candidate> topMsers = null;
					HashSet<Candidate> msers    = null;

					// create slice images
					ImagePlus sliceMembraneImp = new ImagePlus("slice " + s+1, membraneImp.getStack().getProcessor(s+1));
					Image<T> sliceMembrane     = ImagePlusAdapter.wrap(sliceMembraneImp);
					ImagePlus sliceMsersImp    = new ImagePlus("slice " + s+1, msersImp.getStack().getProcessor(s+1));
					Image<T> sliceMsers        = ImagePlusAdapter.wrap(sliceMsersImp);

					// black out msers image
					LocalizableByDimCursor<T> msersCursor = sliceMsers.createLocalizableByDimCursor();
					while (msersCursor.hasNext()) {
						msersCursor.fwd();
						msersCursor.getType().setReal(0.0);
					}

					// process slice image
					mser.process(sliceMembrane, true, false, sliceMsers);
					topMsers = mser.getTopMsers();
					msers    = mser.getMsers();
					IJ.log("Found " + msers.size() + " candidates in slice " + s);

					// store slice candidates
					sliceCandidates.set(s, new Vector<Candidate>(msers));
					sliceTopMsers.set(s, new Vector<Candidate>(topMsers));

					// visualise result
					msersCursor.reset();
					double maxValue = 0.0;
					while (msersCursor.hasNext()) {
						msersCursor.fwd();
						if (msersCursor.getType().getRealFloat() > maxValue)
							maxValue = msersCursor.getType().getRealFloat();
					}
					msersCursor.reset();
					while (msersCursor.hasNext()) {
						msersCursor.fwd();
						msersCursor.getType().setReal(
							128.0 * msersCursor.getType().getRealFloat()/maxValue);
					}
					texifyer.texifyMserTree(mser, s);
				}

				resultCacher.writeMserImages(msersImp, membraneImp.getOriginalFileInfo().fileName, mser.getParameters());
				resultCacher.writeMsers(sliceTopMsers, membraneImp.getOriginalFileInfo().fileName, mser.getParameters());
			}

			List<Vector<Candidate>> selectedSliceCandidates =
					sliceCandidates.subList(firstSlice - 1, lastSlice);

			if (performGridSearch)
				gridSearch(selectedSliceCandidates);
			else
				singleSearch(selectedSliceCandidates);

		}
	}
	
	private void singleSearch(List<Vector<Candidate>> selectedSliceCandidates) {

		AssignmentModel assignmentModel
				= AssignmentModel.readFromFile(
						"./assignment_model.conf",
						new int[]{membraneImp.getWidth(), membraneImp.getHeight()});

		// perform search
		Texifyer texifyer = new Texifyer(msersImp, assignmentModel, "./sipnet-tex/");
		sipnet   = new Sipnet(
				selectedSliceCandidates,
				"./sequence_search.conf",
				texifyer,
				assignmentModel);

		Visualiser visualiser = new Visualiser(assignmentModel);

		if (!visualisationOnly) {

			IJ.log("Searching for the best path");
			Sequence bestSequence = sipnet.bestSearch();
		
			if (bestSequence == null) {
				IJ.log("No sequence could be found.");
				return;
			}

			visualiser.drawSequence(visualisationImp, bestSequence, false, false, false, 0.5);
			visualiser.drawSequence(visualisationImp, bestSequence, false, true, true, 0.5);
			//visualiser3d.showAssignments(bestSequence);
			//visualiser3d.showSlices(msersImp);

			// print statistics
			if (compareToGroundtruth) {

				groundtruth = readGroundTruth(groundtruthImp);

				Evaluator evaluator = new Evaluator(groundtruth, bestSequence);

				IJ.log("num splits: " + evaluator.getNumSplitErrors());
				IJ.log("num merges: " + evaluator.getNumMergeErrors());

				visualiser.drawUnexplainedErrors(visualisationImp, bestSequence, groundtruth, evaluator, 0.5);
			}

		} else {

			IJ.log("visualising most likely candidates...");
			visualiser.drawMostLikelyCandidates(membraneImp, selectedSliceCandidates, "./mlcs");
			IJ.log("done.");
		}
	}

	private void gridSearch(List<Vector<Candidate>> selectedSliceCandidates) {

		AssignmentModel assignmentModel = new AssignmentModel(new int[]{membraneImp.getWidth(), membraneImp.getHeight()});
		GroundTruth     groundtruth     = readGroundTruth(groundtruthImp);

		double[] parameters = new double[startParameters.length];
		System.arraycopy(startParameters, 0, parameters, 0, startParameters.length);

		Texifyer       texifyer     = new Texifyer(msersImp, assignmentModel, "./sipnet-tex/");
		BufferedWriter resultWriter = null;
		try {

			resultWriter  = new BufferedWriter(new FileWriter("./grid_search.dat"));

			boolean done = false;
			while (!done) {

				assignmentModel.setParameters(parameters);

				sipnet = new Sipnet(
						selectedSliceCandidates,
						"./sequence_search.conf",
						texifyer,
						assignmentModel);

				IJ.log("searching for best sequence with parameters = " + Arrays.toString(parameters));
				long time = System.currentTimeMillis();
				Sequence bestSequence = sipnet.bestSearch();
				time = System.currentTimeMillis() - time;

				Evaluator evaluator = new Evaluator(groundtruth, bestSequence);

				resultWriter.write(
						parameters[0] + " " +
						parameters[1] + " " +
						parameters[2] + " " +
						parameters[3] + " " +
						parameters[4] + " " +
						parameters[5] + " " +
						evaluator.getNumSplitErrors() + " " +
						evaluator.getNumMergeErrors() + " " +
						time/1000.0 + "\n");

				for (int i = 0; i < startParameters.length; i++) {

					parameters[i] += stepParameters[i];
					if (parameters[i] <= endParameters[i])
						break;

					if (i == startParameters.length - 1)
						done = true;
					else
						parameters[i] = startParameters[i];
				}
			}
		} catch (IOException e) {

			e.printStackTrace();

		} finally {

			try {
				if (resultWriter != null)
					resultWriter.close();
			} catch (IOException e) {
				// oh screw you, IOException!
			}
		}
	}

	public void run(String args) {

		IJ.log("Starting plugin Sipnet");

		// add drop-down boxes for open images
		int[] windowIds = WindowManager.getIDList();

		if (windowIds == null || windowIds.length == 0) {
			IJ.error("At least one image needs to be open");
			return;
		}

		// try to read parameters from file
		readParametersFromFile("./sipnet.conf");

		String[] windowNames = new String[windowIds.length];

		for (int i = 0; i < windowIds.length; i++)
			windowNames[i] = WindowManager.getImage(windowIds[i]).getTitle();

		// ask for parameters
		GenericDialog gd = new GenericDialog("Settings");
		gd.addNumericField("delta:", delta, 0);
		gd.addNumericField("min area:", minArea, 0);
		gd.addNumericField("max area:", maxArea, 0);
		gd.addNumericField("max variation:", maxVariation, 2);
		gd.addNumericField("min diversity:", minDiversity, 2);
		gd.addNumericField("first slice:", 1, 0);
		gd.addNumericField("last slice:", WindowManager.getCurrentImage().getNSlices(), 0);
		gd.addCheckbox("no inference - only visualisation", visualisationOnly);
		gd.addCheckbox("compare to ground-truth", compareToGroundtruth);
		gd.addChoice("ground-truth image",  windowNames, windowNames[0]);
		gd.addChoice("use for visualisation",  windowNames, windowNames[0]);

		gd.showDialog();
		if (gd.wasCanceled())
			return;
	
		delta = (int)gd.getNextNumber();
		minArea = (int)gd.getNextNumber();
		maxArea = (int)gd.getNextNumber();
		maxVariation = gd.getNextNumber();
		minDiversity = gd.getNextNumber();

		firstSlice = (int)gd.getNextNumber();
		lastSlice  = (int)gd.getNextNumber();

		visualisationOnly    = gd.getNextBoolean();
		compareToGroundtruth = gd.getNextBoolean();
		String groundtruthName = gd.getNextChoice();
		String visualisationName = gd.getNextChoice();

		if (compareToGroundtruth)
			groundtruthImp = WindowManager.getImage(groundtruthName);
		visualisationImp = WindowManager.getImage(visualisationName);

		if (firstSlice < 1 ||
		    lastSlice > WindowManager.getCurrentImage().getNSlices() ||
		    lastSlice < firstSlice) {

			IJ.error("Please make sure that 1 ≤ first slice < last slice ≤ number of slices!");
			return;
		}

		// start processing thread and return
		ProcessingThread procThread = new ProcessingThread();
		procThread.start();
	}

	private GroundTruth readGroundTruth(ImagePlus groundtruthImp) {

		// prepare mser image
		msersImp = groundtruthImp.createImagePlus();

		ImageStack regStack = new ImageStack(groundtruthImp.getWidth(), groundtruthImp.getHeight());
		for (int s = 1; s <= numSlices; s++) {
			ImageProcessor duplProcessor = groundtruthImp.getStack().getProcessor(s).duplicate();
			regStack.addSlice("", duplProcessor);
		}
		msersImp.setStack(regStack);
		msersImp.setDimensions(1, numSlices, 1);
		if (numSlices > 1)
			msersImp.setOpenAsHyperStack(true);
		IJ.run(msersImp, "Fire", "");

		msersImp.setTitle("msers of " + groundtruthImp.getTitle());

		// create slice images
		Vector<Image<T>> sliceImages = new Vector<Image<T>>();
		Vector<Image<T>> sliceMsers  = new Vector<Image<T>>();

		for (int s = firstSlice-1; s <= lastSlice-1; s++) {

			ImagePlus sliceGroundtruthImp = new ImagePlus("slice " + s+1, groundtruthImp.getStack().getProcessor(s+1));
			Image<T>  sliceGroundtruth    = ImagePlusAdapter.wrap(sliceGroundtruthImp);
			ImagePlus sliceMserImp        = new ImagePlus("slice " + s+1, msersImp.getStack().getProcessor(s+1));
			Image<T>  sliceMser           = ImagePlusAdapter.wrap(sliceMserImp);

			// black out msers image
			Cursor<T> msersCursor = sliceMser.createCursor();
			while (msersCursor.hasNext()) {
				msersCursor.fwd();
				msersCursor.getType().setReal(0.0);
			}

			sliceImages.add(sliceGroundtruth);
			sliceMsers.add(sliceMser);
		}

		// process ground truth image
		GroundTruth groundtruth = new GroundTruth();
		groundtruth.readFromLabelImages(sliceImages);

		IJ.log("setting mean gray values of ground-truth candidates...");
		assert(groundtruth.getSequence().consistent());

		// set mean gray values according to membrane image
		int s = 1;
		for (Assignment assignment : groundtruth.getSequence()) {

			Image<T> membraneImg =
					ImagePlusAdapter.wrap(new ImagePlus("", membraneImp.getStack().getProcessor(s)));

			// sources have their gray value in slice s
			for (SingleAssignment singleAssignment : assignment) {
				for (Candidate source : singleAssignment.getSources())
					setMeanGrayValue(source, membraneImg);
			}

			// targets have their gray values in slice s+1 (this is only
			// necessary in last assignment)
			if (s == groundtruth.getSequence().size()) {

				membraneImg =
					ImagePlusAdapter.wrap(new ImagePlus("", membraneImp.getStack().getProcessor(s+1)));

				for (SingleAssignment singleAssignment : assignment) {
					for (Candidate target : singleAssignment.getTargets())
						setMeanGrayValue(target, membraneImg);
				}
			}

			s++;
		}
		IJ.log("done.");

		return groundtruth;
	}

	private void setMeanGrayValue(Candidate candidate, Image<T> image) {

		LocalizableByDimCursor<T> cursor = image.createLocalizableByDimCursor();

		double value = 0.0;

		for (int[] pixel : candidate.getPixels()) {

			cursor.setPosition(pixel);
			value += cursor.getType().getRealFloat();
		}

		candidate.setMeanGrayValue(value/candidate.getPixels().size());
	}

	private boolean readParametersFromFile(String filename) {

		Properties parameterFile = new Properties();

		try {
			parameterFile.load(new FileInputStream(new File(filename)));

			delta        = Integer.valueOf(parameterFile.getProperty("mser_delta"));
			minArea      = Integer.valueOf(parameterFile.getProperty("mser_min_area"));;
			maxArea      = Integer.valueOf(parameterFile.getProperty("mser_max_area"));
			maxVariation = Double.valueOf(parameterFile.getProperty("mser_max_variation"));
			minDiversity = Double.valueOf(parameterFile.getProperty("mser_min_diversity"));

			performGridSearch = Boolean.valueOf(parameterFile.getProperty("perform_grid_search"));

			if (performGridSearch) {

				String[] start = parameterFile.getProperty("parameters_start").split("\\s");

				startParameters = new double[start.length];
				for (int i = 0; i < start.length; i++)
					startParameters[i] = Double.valueOf(start[i]);

				String[] end = parameterFile.getProperty("parameters_end").split("\\s");

				endParameters = new double[end.length];
				for (int i = 0; i < end.length; i++)
					endParameters[i] = Double.valueOf(end[i]);

				String[] step = parameterFile.getProperty("parameters_step").split("\\s");

				stepParameters = new double[step.length];
				for (int i = 0; i < step.length; i++)
					stepParameters[i] = Double.valueOf(step[i]);
			}

		} catch (IOException e) {

			return false;
		}

		return true;
	}
}
