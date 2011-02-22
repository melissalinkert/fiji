
import java.util.HashSet;
import java.util.List;

import ij.gui.GenericDialog;

import mpicbg.imglib.cursor.Cursor;

import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;

import ij.plugin.PlugIn;

import ij.process.ImageProcessor;

import mpicbg.imglib.cursor.LocalizableByDimCursor;

import mser.MSER;

import sipnet.Assignment;
import sipnet.Candidate;
import sipnet.CandidateFactory;
import sipnet.GroundTruth;

import sipnet.AssignmentModel;
import sipnet.ParameterEstimator;
import sipnet.SequenceNode;
import sipnet.SingleAssignment;
import sipnet.Sipnet;
import sipnet.Visualiser;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;

import mpicbg.imglib.type.numeric.RealType;

import sipnet.io.IO;
import sipnet.io.ResultCacher;

public class Estimate_Parameters<T extends RealType<T>> implements PlugIn {

	// the stack to process
	private ImagePlus    groundtruthImp;
	private ImagePlus    membraneImp;
	private ImagePlus    msersImp;
	private int          numSlices;

	private Visualiser   visualiser;

	private int          firstSlice;
	private int          lastSlice;

	// parameters
	private int    delta        = 10;
	private int    minArea      = 10;
	private int    maxArea      = 100000;
	private double maxVariation = 10.0;
	private double minDiversity = 0.5;

	private double parameterStdDeviation = 1.0;
 
	private class ProcessingThread extends Thread {

		public void run() {

			GenericDialog gd = new GenericDialog("Elastix");
		
			// add drop-down boxes for open images
			int[]    windowIds   = WindowManager.getIDList();

			if (windowIds == null || windowIds.length < 1) {
				IJ.error("At least two images need to be open");
				return;
			}

			String[] windowNames = new String[windowIds.length];

			for (int i = 0; i < windowIds.length; i++)
				windowNames[i] = WindowManager.getImage(windowIds[i]).getTitle();

			gd.addChoice("ground-truth image",  windowNames, windowNames[0]);
			gd.addChoice("membrane image", windowNames, windowNames[1]);
			gd.addNumericField("delta:", delta, 0);
			gd.addNumericField("min area:", minArea, 0);
			gd.addNumericField("max area:", maxArea, 0);
			gd.addNumericField("max variation:", maxVariation, 2);
			gd.addNumericField("min diversity:", minDiversity, 2);
			gd.addNumericField("first slice:", 1, 0);
			gd.addNumericField("last slice:", WindowManager.getCurrentImage().getNSlices(), 0);
			gd.addNumericField("parameter std. deviation:", parameterStdDeviation, 2);

			gd.showDialog();

			if (gd.wasCanceled())
				return;

			String groundtruthName = gd.getNextChoice();
			String membraneName    = gd.getNextChoice();

			delta = (int)gd.getNextNumber();
			minArea = (int)gd.getNextNumber();
			maxArea = (int)gd.getNextNumber();
			maxVariation = gd.getNextNumber();
			minDiversity = gd.getNextNumber();

			firstSlice = (int)gd.getNextNumber();
			lastSlice  = (int)gd.getNextNumber();

			parameterStdDeviation = gd.getNextNumber();

			groundtruthImp = WindowManager.getImage(groundtruthName);
			membraneImp    = WindowManager.getImage(membraneName);

			numSlices = groundtruthImp.getStack().getSize();

			GroundTruth             groundtruth = readGroundTruth();
			List<Vector<Candidate>> msers       = readMsers();

			// read assignment model paramters
			AssignmentModel.readFromFile(
					"./assignment_model_caching.conf",
					new int[]{groundtruthImp.getWidth(), groundtruthImp.getHeight()});

			// let sipnet (i.e., sequence search) cache the most likely
			// candidates:
			new Sipnet(msers, "./sequence_search.conf", null);
	
			// perform parameter learning
			ParameterEstimator parameterEstimator =
					new ParameterEstimator(
							groundtruth.getSequence(),
							msers,
							parameterStdDeviation);

			parameterEstimator.estimate();

			// write result
			AssignmentModel.writeToFile("./result.conf", "learnt parameters");

			// visualisation
			visualiser   = new Visualiser();
			visualiser.drawSequence(msersImp, groundtruth.getSequence(), false, true);
		}
	}

	private GroundTruth readGroundTruth() {

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
		// set mean gray values according to membrane image
		int s = 1;
		for (SequenceNode node : groundtruth.getSequence()) {

			Image<T> membraneImg =
					ImagePlusAdapter.wrap(new ImagePlus("", membraneImp.getStack().getProcessor(s)));

			for (SingleAssignment singleAssignment : node.getAssignment()) {
				for (Candidate source : singleAssignment.getSources())
					setMeanGrayValue(source, membraneImg);
				for (Candidate target : singleAssignment.getTargets())
					setMeanGrayValue(target, membraneImg);
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

	private List<Vector<Candidate>> readMsers() {

		// create result cacher
		IO io = new IO();
		ResultCacher resultCacher = new ResultCacher("./.cache", io);

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
			}

			resultCacher.writeMserImages(msersImp, membraneImp.getOriginalFileInfo().fileName, mser.getParameters());
			resultCacher.writeMsers(sliceTopMsers, membraneImp.getOriginalFileInfo().fileName, mser.getParameters());
		}

		List<Vector<Candidate>> selectedSliceCandidates =
				sliceCandidates.subList(firstSlice - 1, lastSlice);

		return selectedSliceCandidates;
	}

	public void run(String args) {

		IJ.log("Starting plugin Sipnet");

		// start processing thread and return
		ProcessingThread procThread = new ProcessingThread();
		procThread.start();
	}
}
