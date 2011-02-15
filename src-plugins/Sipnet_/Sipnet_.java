
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
import sipnet.io.IO;
import sipnet.io.ResultCacher;

import sipnet.AssignmentModel;
import sipnet.Candidate;
import sipnet.CandidateFactory;
import sipnet.Sequence;
import sipnet.Sipnet;
import sipnet.Texifyer;
import sipnet.Visualiser;
import sipnet.Visualiser3D;

import mpicbg.imglib.cursor.LocalizableByDimCursor;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;

import mpicbg.imglib.type.numeric.RealType;

public class Sipnet_<T extends RealType<T>> implements PlugIn {

	// the stack to process
	private ImagePlus    membraneImp;
	private ImagePlus    msersImp;
	private int          numSlices;

	private int          firstSlice;
	private int          lastSlice;

	private Texifyer     texifyer;
	private Visualiser   visualiser;
	private Visualiser3D visualiser3d;
	private IO           io;

	private Sipnet       sipnet;

	// parameters
	private int    delta        = 10;
	private int    minArea      = 10;
	private int    maxArea      = 100000;
	private double maxVariation = 10.0;
	private double minDiversity = 0.5;

	// don't perform inference, visualise only
	private boolean visualisationOnly = false;

	private class ProcessingThread extends Thread {

		public void run() {

			// read image
			membraneImp = WindowManager.getCurrentImage();
			if (membraneImp == null) {
				IJ.showMessage("Please open an image first.");
				return;
			}
			numSlices = membraneImp.getStack().getSize();
	
			// setup visualisation and file IO
			visualiser   = new Visualiser();
			visualiser3d = new Visualiser3D();
			io           = new IO();

			// create result cacher
			ResultCacher resultCacher = new ResultCacher("./.cache", io);

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

				texifyer = new Texifyer(msersImp, "./sipnet-tex/");

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

			// perform search
			texifyer = new Texifyer(msersImp, "./sipnet-tex/");
			sipnet   = new Sipnet(
					selectedSliceCandidates,
					"./sequence_search.conf",
					texifyer);

			if (!visualisationOnly) {

				IJ.log("Searching for the best path");
				Sequence bestSequence = sipnet.bestSearch();
			
				if (bestSequence == null) {
					IJ.log("No sequence could be found.");
					return;
				}

				visualiser.drawSequence(membraneImp, bestSequence, false, false);
				visualiser.drawSequence(msersImp, bestSequence, false, true);
				//visualiser3d.showAssignments(bestSequence);
				//visualiser3d.showSlices(msersImp);

			} else {

				IJ.log("visualising most likely candidates...");
				visualiser.drawMostLikelyCandidates(membraneImp, selectedSliceCandidates, "./mlcs");
				IJ.log("done.");
			}
		}
	}

	public void run(String args) {

		IJ.log("Starting plugin Sipnet");

		// ask for parameters
		GenericDialog gd = new GenericDialog("Settings");
		gd.addNumericField("delta:", delta, 0);
		gd.addNumericField("min area:", minArea, 0);
		gd.addNumericField("max area:", maxArea, 0);
		gd.addNumericField("max variation:", maxVariation, 2);
		gd.addNumericField("min diversity:", minDiversity, 2);
		gd.addNumericField("first slice:", 1, 0);
		gd.addNumericField("last slice:", WindowManager.getCurrentImage().getNSlices(), 0);
		gd.addCheckbox("no inference - only visualisation", false);
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

		visualisationOnly = gd.getNextBoolean();

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
}
