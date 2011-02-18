
import mpicbg.imglib.cursor.Cursor;

import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;

import ij.plugin.PlugIn;

import ij.process.ImageProcessor;

import sipnet.GroundTruth;

import sipnet.AssignmentModel;
import sipnet.Visualiser;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImagePlusAdapter;

import mpicbg.imglib.type.numeric.RealType;

public class Estimate_Parameters<T extends RealType<T>> implements PlugIn {

	// the stack to process
	private ImagePlus    groundtruthImp;
	private ImagePlus    msersImp;
	private int          numSlices;

	private Visualiser   visualiser;

	private class ProcessingThread extends Thread {

		public void run() {

			// read image
			groundtruthImp = WindowManager.getCurrentImage();
			if (groundtruthImp == null) {
				IJ.showMessage("Please open an image first.");
				return;
			}
			numSlices = groundtruthImp.getStack().getSize();
	
			// setup visualisation and file IO
			visualiser   = new Visualiser();

			// read assignment model paramters
			AssignmentModel.getInstance().setImageDimensions(
					new int[]{groundtruthImp.getWidth(), groundtruthImp.getHeight()});

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

			for (int s = 0; s < numSlices; s++) {

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

			for (int s = 0; s < numSlices; s++) {

				Cursor<T> msersCursor = sliceMsers.get(s).createCursor();

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

			visualiser.drawSequence(msersImp, groundtruth.getSequence(), false, true);
		}
	}

	public void run(String args) {

		IJ.log("Starting plugin Sipnet");

		// start processing thread and return
		ProcessingThread procThread = new ProcessingThread();
		procThread.start();
	}
}
