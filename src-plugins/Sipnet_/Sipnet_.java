
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;

import ij.gui.GenericDialog;

import ij.plugin.PlugIn;

import ij.process.ImageProcessor;

import mpicbg.imglib.cursor.LocalizableByDimCursor;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;

import mpicbg.imglib.type.numeric.RealType;

public class Sipnet_<T extends RealType<T>> implements PlugIn {

	// the stack to process
	private ImagePlus    imp;
	private ImagePlus    reg;
	private int          numSlices;

	private Image<T>     sliceImage;
	private Image<T>     sliceRegion;

	private Texifyer     texifyer;
	private Visualiser3D visualiser;
	private IO           io;

	private MSER<T, Candidate> mser;
	private Sipnet             sipnet;

	private Vector<Set<Candidate>> sliceCandidates;

	// parameters
	private int    delta        = 10;
	private int    minArea      = 10;
	private int    maxArea      = 100000;
	private double maxVariation = 10.0;
	private double minDiversity = 0.5;

	private class ProcessingThread extends Thread {

		public void run() {

			// read image
			imp = WindowManager.getCurrentImage();
			if (imp == null) {
				IJ.showMessage("Please open an image first.");
				return;
			}
	
			// setup image stack
			ImageStack stack = imp.getStack();
			numSlices = stack.getSize();
			sliceCandidates = new Vector<Set<Candidate>>(numSlices - 1);
			sliceCandidates.setSize(numSlices - 1);
	
			// prepare segmentation image
			reg = imp.createImagePlus();
			ImageStack regStack = new ImageStack(imp.getWidth(), imp.getHeight());
			for (int s = 1; s <= numSlices; s++) {
				ImageProcessor duplProcessor = imp.getStack().getProcessor(s).duplicate();
				regStack.addSlice("", duplProcessor);
			}
			reg.setStack(regStack);
			reg.setDimensions(1, numSlices, 1);
			if (numSlices > 1)
				reg.setOpenAsHyperStack(true);
			IJ.run(reg, "Fire", "");
		
			reg.setTitle("msers of " + imp.getTitle());
		
			// setup visualisation and file IO
			texifyer   = new Texifyer(reg, "./sipnet-tex/");
			visualiser = new Visualiser3D();
			io         = new IO();

			// create set of start points
			Set<Candidate> startCandidates = new HashSet<Candidate>();
	
			for (int s = 0; s < numSlices; s++) {
	
				IJ.log("Processing slice " + s + "...");
	
				String mserFilename       = "./cache/" + imp.getTitle() + "top-msers-" + s + ".sip";
				String sliceImageFilename = "./cache/" + imp.getTitle() + "msers-" + s + ".tif";
	
				HashSet<Candidate> topMsers = null;
				HashSet<Candidate> msers    = null;

				// create slice image
				IJ.log("Creating slice image " + (s+1));
				ImagePlus sliceImp = new ImagePlus("slice " + s+1, stack.getProcessor(s+1));
				sliceImage  = ImagePlusAdapter.wrap(sliceImp);
				ImagePlus sliceReg = new ImagePlus("slice " + s+1, regStack.getProcessor(s+1));
				sliceRegion = ImagePlusAdapter.wrap(sliceReg);
				
				if (io.exists(mserFilename)) {
	
					IJ.log("Reading Msers from " + mserFilename);
					topMsers = io.readMsers(mserFilename);
					msers    = flatten(topMsers);
					sliceReg = IJ.openImage(sliceImageFilename);
	
				} else {
	
					// black out region image
					LocalizableByDimCursor<T> regionsCursor = sliceRegion.createLocalizableByDimCursor();
					while (regionsCursor.hasNext()) {
						regionsCursor.fwd();
						regionsCursor.getType().setReal(0.0);
					}
		
					// set up algorithm
					if (mser == null)
						mser = new MSER<T, Candidate>(sliceImage.getDimensions(), delta, minArea, maxArea, maxVariation, minDiversity);
		
					mser.process(sliceImage, true, false, sliceRegion);

					topMsers = mser.getTopMsers();
					msers    = mser.getMsers();
		
					IJ.log("Found " + topMsers.size() + " parent candidates in slice " + s);
		
					// visualise result
					IJ.run(sliceReg, "Fire", "");
					regionsCursor.reset();
					double maxValue = 0.0;
					while (regionsCursor.hasNext()) {
						regionsCursor.fwd();
						if (regionsCursor.getType().getRealFloat() > maxValue)
							maxValue = regionsCursor.getType().getRealFloat();
					}
					regionsCursor.reset();
					while (regionsCursor.hasNext()) {
						regionsCursor.fwd();
						regionsCursor.getType().setReal(
							128.0 * regionsCursor.getType().getRealFloat()/maxValue);
					}
					texifyer.texifyMserTree(mser, s);
		
					// write msers and msers image to file
					io.writeMsers(topMsers, mserFilename);
					IJ.save(sliceReg, sliceImageFilename);
				}
	
				// for the first slice, let the user select the start candidates
				if (s == 0) {
	
					CandidateSelector candidateSelector = new CandidateSelector(reg, msers);
					startCandidates = candidateSelector.getUserSelection();
	
					if (startCandidates == null)
						return;
				} else
					sliceCandidates.set(s - 1, msers);
			}
	
			// perform greedy search
			IJ.log("Searching for the best path greedily");
			sipnet = new Sipnet(texifyer);
			Sequence greedySeequence = sipnet.greedySearch(startCandidates, sliceCandidates);
	
			if (greedySeequence == null) {
				IJ.log("No sequence could be found.");
				return;
			}
	
			// visualize result
			IJ.setForegroundColor(255, 255, 255);
			IJ.selectWindow(imp.getTitle());
			int slice = greedySeequence.size();
			for (Assignment assignment : greedySeequence) {
				for (SingleAssignment singleAssignment : assignment) {
	
					Candidate source = singleAssignment.getSource();
					Candidate target = singleAssignment.getTarget();
	
					int x = (int)source.getCenter()[0];
					int y = (int)source.getCenter()[1];
					IJ.setSlice(slice);
					IJ.runMacro("drawString(\"" + source.getId() + "\", " + x + ", " + y + ")");
	
					x = (int)target.getCenter()[0];
					y = (int)target.getCenter()[1];
					IJ.setSlice(slice+1);
					IJ.runMacro("drawString(\"" + source.getId() + "\", " + x + ", " + y + ")");
				}
				slice--;
			}
	
			imp.updateAndDraw();

			visualiser.showAssignments(greedySeequence);
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
		gd.showDialog();
		if (gd.wasCanceled())
			return;
	
		delta = (int)gd.getNextNumber();
		minArea = (int)gd.getNextNumber();
		maxArea = (int)gd.getNextNumber();
		maxVariation = gd.getNextNumber();
		minDiversity = gd.getNextNumber();

		// start processing thread and return
		ProcessingThread procThread = new ProcessingThread();
		procThread.start();
	}

	private HashSet<Candidate> flatten(Collection<Candidate> parents) {

		HashSet<Candidate> allRegions = new HashSet<Candidate>();

		allRegions.addAll(parents);
		for (Candidate parent : parents)
			allRegions.addAll(flatten(parent.getChildren()));

		return allRegions;
	}
}
