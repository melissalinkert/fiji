
import java.lang.String;

import java.math.BigInteger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;

public class ResultCacher {

	private String cacheDir;
	private IO     io;

	public ResultCacher(String cacheDir, IO io) {

		this.cacheDir = cacheDir;
		this.io       = io;
	}

	public ImagePlus readMembraneProbabilities(String stackFile, String classifierFile) {

		String membraneFile = cacheDir + "/" + createFilename(stackFile, classifierFile) + "-membrane.tif";

		IJ.log("Reading membrane images from " + membraneFile);
		return IJ.openImage(membraneFile);
	}

	public ImagePlus readMserImages(String stackFile, String classifierFile, MSER<?,?>.Parameters mserParameters) {

		String msersFile = cacheDir + "/" + createFilename(stackFile, classifierFile, mserParameters) + "-msers.tif";

		IJ.log("Reading MSER images from " + msersFile);
		return IJ.openImage(msersFile);
	}

	/**
	 * Reads MSER forests (one for each slice) from a file and returns a
	 * flattened set of MSERs for each slice.
	 */
	public Vector<Set<Candidate>> readMsers(
			String stackFile,
			String classifierFile,
			MSER<?,?>.Parameters mserParameters,
			int firstSlice,
			int lastSlice) {

		String msersFile = cacheDir + "/" + createFilename(stackFile, classifierFile, mserParameters) + "-msers.sip";

		IJ.log("Reading Msers from " + msersFile);
		Vector<Set<Candidate>> sliceTopMsers = io.readMsers(msersFile, firstSlice, lastSlice);

		if (sliceTopMsers == null)
			return null;

		Vector<Set<Candidate>> sliceMsers = new Vector<Set<Candidate>>();

		for (Set<Candidate> topMsers : sliceTopMsers)
			sliceMsers.add(flatten(topMsers));

		return sliceMsers;
	}

	public void writeMembraneProbabilities(ImagePlus membraneImp, String stackFile, String classifierFile) {

		String msersFile = cacheDir + "/" + createFilename(stackFile, classifierFile) + "-membrane.tif";

		IJ.save(membraneImp, msersFile);
	}

	public void writeMserImages(ImagePlus msersImp, String stackFile, String classifierFile, MSER<?,?>.Parameters mserParameters) {

		String msersFile = cacheDir + "/" + createFilename(stackFile, classifierFile, mserParameters) + "-msers.tif";

		IJ.save(msersImp, msersFile);
	}

	/**
	 * Writes MSER forests (one for each slice) to a file.
	 */
	public void writeMsers(Vector<Set<Candidate>> topMsers, String stackFile, String classifierFile, MSER<?,?>.Parameters mserParameters) {

		String topMsersFile = cacheDir + "/" + createFilename(stackFile, classifierFile, mserParameters) + "-msers.sip";

		io.writeMsers(topMsers, topMsersFile);
	}

	private String createFilename(String stackFile, String classifierFile) {

		String configuration = stackFile + classifierFile;

		return hash(configuration);
	}

	private String createFilename(String stackFile, String classifierFile, MSER<?,?>.Parameters mserParameters) {

		String configuration = stackFile + classifierFile + mserParameters.toString();

		return hash(configuration);
	}

	private String hash(String text) {

		try {

			MessageDigest messageDigest = MessageDigest.getInstance("MD5");
			messageDigest.update(text.getBytes(), 0, text.length());

			return String.format("%1$032X", new BigInteger(1, messageDigest.digest()));

		} catch (NoSuchAlgorithmException e) {

			e.printStackTrace();
		}

		return text;
	}

	private HashSet<Candidate> flatten(Collection<Candidate> parents) {

		HashSet<Candidate> allRegions = new HashSet<Candidate>();

		allRegions.addAll(parents);
		for (Candidate parent : parents)
			allRegions.addAll(flatten(parent.getChildren()));

		return allRegions;
	}
}
