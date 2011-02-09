package mser;

import java.util.Collection;
import java.util.HashSet;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;

import ij.gui.GenericDialog;

import ij.plugin.PlugIn;

import ij.process.ImageProcessor;

import mpicbg.imglib.cursor.LocalizableByDimCursor;

import mpicbg.imglib.cursor.special.HyperSphereIterator;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;

import mpicbg.imglib.type.numeric.RealType;

public class MSER_<T extends RealType<T>> implements PlugIn {

	// the image to process
	private Image<T>  image;
	private Image<T>  regions;
	private ImagePlus imp;
	private ImagePlus reg;

	private LocalizableByDimCursor<T> regionsCursor;

	private int[] dimensions;

	private SimpleRegionFactory regionFactory = new SimpleRegionFactory();

	// for the plugin, use plain regions without candy
	private class SimpleRegionFactory implements RegionFactory<SimpleRegion> {

		public SimpleRegion create() {
			return new SimpleRegion(0, new double[]{0.0, 0.0});
		}

		public SimpleRegion create(MSER<?, SimpleRegion>.ConnectedComponent component) {
			return new SimpleRegion(component.size, component.center);
		}
	}

	private class SimpleRegion extends Region<SimpleRegion> {
		public SimpleRegion(int size, double[] center) {
			super(size, 0, center, regionFactory);
		}
	};

	private MSER<T, SimpleRegion>   mser;

	public void run(String args) {

		IJ.log("Starting plugin MSER");

		// read image
		imp = WindowManager.getCurrentImage();
		if (imp == null) {
			IJ.showMessage("Please open an image first.");
			return;
		}

		int delta = 10;
		int minArea = 10;
		int maxArea = 100000;
		double maxVariation = 10.0;
		double minDiversity = 0.5;
		final boolean darkToBright;
		final boolean brightToDark;

		// ask for parameters
		GenericDialog gd = new GenericDialog("Settings");
		gd.addNumericField("delta:", delta, 0);
		gd.addNumericField("min area:", minArea, 0);
		gd.addNumericField("max area:", maxArea, 0);
		gd.addNumericField("max variation:", maxVariation, 2);
		gd.addNumericField("min diversity:", minDiversity, 2);
		gd.addCheckbox("dark to bright", true);
		gd.addCheckbox("bright to dark", false);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
	
		delta = (int)gd.getNextNumber();
		minArea = (int)gd.getNextNumber();
		maxArea = (int)gd.getNextNumber();
		maxVariation = gd.getNextNumber();
		minDiversity = gd.getNextNumber();
		darkToBright = gd.getNextBoolean();
		brightToDark = gd.getNextBoolean();

		image      = ImagePlusAdapter.wrap(imp);
		dimensions = image.getDimensions();
		int width  = dimensions[0];
		int height = dimensions[1];
		int slices = 1;
		if (dimensions.length > 2)
			slices = dimensions[2];
	
		// prepare segmentation image
		reg = imp.createImagePlus();
		ImageStack stack = new ImageStack(width, height);
		for (int s = 1; s <= slices; s++) {
			ImageProcessor duplProcessor = imp.getStack().getProcessor(s).duplicate();
			stack.addSlice("", duplProcessor);
		}
		reg.setStack(stack);
		reg.setDimensions(1, slices, 1);
		if (slices > 1)
			reg.setOpenAsHyperStack(true);
	
		reg.setTitle("msers of " + imp.getTitle());
	
		regions = ImagePlusAdapter.wrap(reg);
		regionsCursor = regions.createLocalizableByDimCursor();
		while (regionsCursor.hasNext()) {
			regionsCursor.fwd();
			regionsCursor.getType().setReal(0.0);
		}
	
		// set up algorithm
		mser = new MSER<T, SimpleRegion>(dimensions, delta, minArea, maxArea, maxVariation, minDiversity, regionFactory);
	
		Thread processThread = new Thread(new Runnable() {
			public void run() {
				mser.process(image, darkToBright, brightToDark, regions);
				// change the LUT for the segmentation image
				IJ.run(reg, "glasbey", "");
				reg.show();
				reg.updateAndDraw();
			}
		});
		processThread.start();

		// wait for the thread to finish
		try {
			processThread.join();
		} catch (InterruptedException e) {
			processThread.interrupt();
		}

		// visualize MSER centers
		HashSet<SimpleRegion> topMsers = mser.getTopMsers();

		regionsCursor = regions.createLocalizableByDimCursor();
		drawRegions(topMsers);
	}

	private void drawRegions(Collection<SimpleRegion> msers) {

		for (SimpleRegion mser: msers) {
			drawRegions(mser.getChildren());
			drawRegion(mser, (int)(Math.sqrt(mser.getSize())/10));
		}
	}

	private void drawRegion(SimpleRegion mser, int radius) {

		int[] center = new int[dimensions.length];
		for (int d = 0; d < dimensions.length; d++) {
			center[d] = (int)mser.getCenter()[d];
			if (center[d] < radius || center[d] > dimensions[d] - radius - 1)
				return;
		}

		// draw a circle
		regionsCursor.setPosition(center);
		HyperSphereIterator<T> sphereIterator = new HyperSphereIterator<T>(regions, regionsCursor, radius);
		while (sphereIterator.hasNext()) {
			sphereIterator.fwd();
			sphereIterator.getType().setReal(255);
		}
	}
}
