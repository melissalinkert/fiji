
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;

import ij.gui.GenericDialog;

import ij.plugin.PlugIn;

import mpicbg.imglib.container.array.ArrayContainerFactory;

import mpicbg.imglib.cursor.LocalizableByDimCursor;

import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.image.ImagePlusAdapter;

import mpicbg.imglib.type.numeric.RealType;

public class KNN_Segmentation<T extends RealType<T>> implements PlugIn {

	private int patchSize  = 9;
	private int numSamples = 100;
	private int numNeighbors = 1;

	private ImagePlus inputImp;
	private ImagePlus groundtruthImp;
	private ImagePlus testImp;
	private Image<T>  inputImg;
	private Image<T>  groundtruthImg;
	private Image<T>  testImg;

	Dictionary<T> dictionary;

	public final void run(String args) {

		IJ.log("Starting plugin KNN-Segmentation");

		// add drop-down boxes for open images
		int[] windowIds = WindowManager.getIDList();

		if (windowIds == null || windowIds.length < 2) {
			IJ.error("At least two images need to be open (input, ground-truth)");
			return;
		}

		String[] windowNames = new String[windowIds.length];

		for (int i = 0; i < windowIds.length; i++)
			windowNames[i] = WindowManager.getImage(windowIds[i]).getTitle();

		// ask for parameters
		GenericDialog gd = new GenericDialog("Settings");
		gd.addNumericField("patch size:", patchSize, 0);
		gd.addNumericField("number of samples:", numSamples, 0);
		gd.addNumericField("number of neighbors:", numNeighbors, 0);
		gd.addChoice("input image",  windowNames, windowNames[(windowIds.length > 2 ? 2 : 1)]);
		gd.addChoice("ground-truth image",  windowNames, windowNames[0]);
		gd.addChoice("test image",  windowNames, windowNames[1]);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		patchSize  = (int)gd.getNextNumber();
		numSamples = (int)gd.getNextNumber();
		numNeighbors = (int)gd.getNextNumber();
		String inputName = gd.getNextChoice();
		String groundtruthName = gd.getNextChoice();
		String testName = gd.getNextChoice();

		inputImp = WindowManager.getImage(inputName);
		groundtruthImp = WindowManager.getImage(groundtruthName);
		testImp = WindowManager.getImage(testName);

		inputImg       = ImagePlusAdapter.wrap(inputImp);
		groundtruthImg = ImagePlusAdapter.wrap(groundtruthImp);
		testImg        = ImagePlusAdapter.wrap(testImp);

		ImagePlus outputImp = testImp.duplicate();
		Image<T> outputImg  = ImagePlusAdapter.wrap(outputImp);

		IJ.log("Creating dictionary from " + numSamples + " random samples...");
		dictionary = createDictionary(inputImg, groundtruthImg, patchSize, numSamples);
		IJ.log("Dictionary created.");

		IJ.log("Classifying test image...");
		classify(testImg, dictionary, numNeighbors, outputImg);
		IJ.log("Done.");

		outputImp.show();
		outputImp.updateAndDraw();
	}

	// LIBRARY STYLE METHODS

	private final Dictionary<T> createDictionary(
			final Image<T> inputImg,
			final Image<T> groundtruthImg,
			int patchSize,
			int numSamples) {

		Dictionary<T> dictionary = new Dictionary<T>(patchSize, 2);

		List<Patch<T>> patches = new ArrayList<Patch<T>>();

		for (int i = 0; i < numSamples; i++) {

			Patch<T> patch = extractRandomPatch(inputImg, groundtruthImg, patchSize);

			patches.add(patch);
		}

		dictionary.fill(patches);

		return dictionary;
	}

	public final void classify(
			Image<T> inputImg,
			Dictionary<T> dictionary,
			int numNeighbors,
			Image<T> outputImg) {

		int patchSize = dictionary.getPatchSize();

		LocalizableByDimCursor<T> inputCursor = inputImg.createLocalizableByDimCursor();
		LocalizableByDimCursor<T> outputCursor = outputImg.createLocalizableByDimCursor();

		int[] dimensions = inputImg.getDimensions();
		int[] position = dimensions.clone();
		int   numDims = dimensions.length;

		double numPixels = 1;
		for (int d = 0; d < numDims; d++)
			numPixels *= dimensions[d];
		double current = 0;

		A: while (inputCursor.hasNext() && outputCursor.hasNext()) {

			inputCursor.fwd();
			outputCursor.fwd();

			inputCursor.getPosition(position);

			current++;
			if (current % 10 == 0)
				IJ.showProgress(current/numPixels);

			for (int d = 0; d < numDims; d++)
				if (position[d] < patchSize/2 ||
				    position[d] >= dimensions[d] - patchSize/2)
					continue A;

			Patch<T> patch = extractPatch(inputCursor, patchSize);

			int label = dictionary.getLabel(patch, numNeighbors);

			outputCursor.getType().setReal(label*255.0);
		}
	}

	// PRIVATE METHODS

	private final Patch<T> extractRandomPatch(
			Image<T> inputImg,
			Image<T> groundtruthImg,
			int patchSize) {

		// TODO: reuse
		int numDimensions = inputImg.getNumDimensions();

		int[] dimensions = new int[numDimensions];
		int[] position  = new int[numDimensions];

		// TODO: reuse
		inputImg.getDimensions(dimensions);

		// TODO: reuse
		Random random = new Random();

		for (int d = 0; d < numDimensions; d++) {

			position[d] = Math.abs(random.nextInt()) % (dimensions[d] - patchSize) + patchSize/2;
			System.out.println(position[d]);
		}
		System.out.println();

		LocalizableByDimCursor<T> cursor =
				inputImg.createLocalizableByDimCursor();
		LocalizableByDimCursor<T> gtCursor =
				groundtruthImg.createLocalizableByDimCursor();

		cursor.setPosition(position);
		gtCursor.setPosition(position);

		Patch<T> patch = extractPatch(cursor, patchSize);

		if (gtCursor.getType().getRealFloat() != 0)
			patch.setLabel(1);
		else
			patch.setLabel(0);

		return patch;
	}

	private final Patch<T> extractPatch(
			LocalizableByDimCursor<T> cursor,
			int patchSize) {

		ImageFactory<T> factory =
				new ImageFactory<T>(cursor.getType(), new ArrayContainerFactory());

		int[] patchDims = new int[cursor.getNumDimensions()];
		for (int d = 0; d < cursor.getNumDimensions(); d++)
			patchDims[d] = patchSize;

		int[] offset = cursor.getPosition().clone();

		Image<T> patchImg = factory.createImage(patchDims, "patch");

		LocalizableByDimCursor<T> outputCursor =
				patchImg.createLocalizableByDimCursor();

		int[] position = offset.clone();

		while (outputCursor.hasNext()) {

			outputCursor.fwd();

			for (int d = 0; d < cursor.getNumDimensions(); d++)
				position[d] = offset[d] + outputCursor.getPosition()[d] - patchSize/2;

			cursor.setPosition(position);

			outputCursor.getType().setReal(
					cursor.getType().getRealFloat());
		}

		cursor.setPosition(offset);

		return new Patch<T>(patchImg);
	}
}
