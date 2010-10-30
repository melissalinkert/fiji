
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;

import ij.gui.GenericDialog;

import ij.plugin.PlugIn;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;

import mpicbg.imglib.type.numeric.RealType;

public class Sipnet_<T extends RealType<T>> implements PlugIn {

	// the stack to process
	private ImagePlus imp;
	private int numSlices;

	private Image<T> sliceImage;

	private MSER<T>  mser;
	private Sipnet   sipnet;

	private Vector<Set<Region>> sliceCandidates;

	public void run(String args) {

		IJ.log("Starting plugin Sipnet");

		int delta = 10;
		int minArea = 10;
		int maxArea = 100000;
		double maxVariation = 10.0;
		double minDiversity = 0.5;

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

		// read image
		imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.showMessage("Please open an image first.");
			return;
		}

		// start points
		Set<Region> startCandidates = new HashSet<Region>();

		// extract neuron center candidates
		ImageStack stack = imp.getStack();
		numSlices = stack.getSize();
		sliceCandidates = new Vector<Set<Region>>(numSlices);
		sliceCandidates.setSize(numSlices - 1);

		for (int s = 0; s < numSlices; s++) {

			IJ.log("Processing slice " + s + "...");

			// create slice image
			ImagePlus sliceImp = new ImagePlus("slice " + s+1, stack.getProcessor(s+1));
			sliceImage = ImagePlusAdapter.wrap(sliceImp);

			// set up algorithm
			if (mser == null)
				mser = new MSER<T>(sliceImage.getDimensions(), delta, minArea, maxArea, maxVariation, minDiversity);

			mser.process(sliceImage, true, false);

			IJ.log("Found " + mser.getTopMsers().size() + " parent candidates in slice " + s);

			// TODO; pick startCandidates via GUI
			if (s == 0)
				startCandidates.addAll(mser.getTopMsers());
			else
				sliceCandidates.set(s - 1, mser.getTopMsers());
		}

		// perform greedy search
		IJ.log("Searching for the best path greedily");
		sipnet = new Sipnet(1.0);
		Sequence greedySeequence = sipnet.greedySearch(startCandidates, sliceCandidates);

		if (greedySeequence == null)
			return;

		// visualize result
		IJ.setForegroundColor(255, 255, 255);
		int slice = 1;
		for (Assignment assignment : greedySeequence) {
			int id = 0;
			for (SingleAssignment singleAssignment : assignment) {

				Region source = singleAssignment.getSource();
				Region target = singleAssignment.getTarget();

				int x = (int)source.getCenter()[0];
				int y = (int)source.getCenter()[1];
				IJ.setSlice(slice);
				IJ.runMacro("drawString(\"" + id + "\", " + x + ", " + y + ")");

				x = (int)target.getCenter()[0];
				y = (int)target.getCenter()[1];
				IJ.setSlice(slice+1);
				IJ.runMacro("drawString(\"" + id + "\", " + x + ", " + y + ")");

				id++;
			}
			slice++;
		}

		imp.updateAndDraw();
	}
}
