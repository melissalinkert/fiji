package tests;

import java.util.Collection;
import java.util.HashSet;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import mpicbg.imglib.image.Image;

import mpicbg.imglib.io.ImageOpener;

import mpicbg.imglib.type.numeric.RealType;

import mser.MSER;

import sipnet.Candidate;
import sipnet.CandidateFactory;

public class MSERTest <T extends RealType<T>> {

	Image<T>           image;
	MSER<T, Candidate> mser;

	// parameters
	private int    delta        = 10;
	private int    minArea      = 10;
	private int    maxArea      = 100000;
	private double maxVariation = 10.0;
	private double minDiversity = 0.5;

	@Before
	public void setUp() throws Exception {

		final ImageOpener imageOpener = new ImageOpener();

		try {
			image = imageOpener.openImage("/home/jan/workspace/mpi/fiji/src-plugins/Sipnet_/MSERTest.tif");
		} catch (RuntimeException e) {
			e.printStackTrace();
		}

		mser = new MSER<T, Candidate>(
						image.getDimensions(),
						delta,
						minArea,
						maxArea,
						maxVariation,
						minDiversity,
						new CandidateFactory());

		// process from dark to bright
		mser.process(image, true, false);
	}

	@Test
	public void testHeritage() {

		// read back msers
		HashSet<Candidate> topMsers = mser.getTopMsers();
		HashSet<Candidate> msers    = mser.getMsers();

		System.out.println("Found " + msers.size() + " candidates");

		// test for consistency and completenes
		consistentHeritage(msers, topMsers);

		System.out.println("Done.");
	}

	@Test
	public void testGeometry() {

		HashSet<Candidate> msers = mser.getMsers();

		for (Candidate candidate : msers) {

			// size correct?
			assertEquals(candidate.getSize(), candidate.getPixels().size());

			// center correct?
			double[] center = new double[]{0.0, 0.0};

			for (int[] pixel : candidate.getPixels()) {
				center[0] += pixel[0];
				center[1] += pixel[1];
			}

			center[0] /= (double)candidate.getPixels().size();
			center[1] /= (double)candidate.getPixels().size();

			assertEquals(center[0], candidate.getCenter(0), 1e-10);
			assertEquals(center[1], candidate.getCenter(1), 1e-10);

			// all pixels inside image?
			for (int[] pixel : candidate.getPixels()) {
				assertTrue(pixel[0] >= 0);
				assertTrue(pixel[0] < image.getDimensions()[0]);
				assertTrue(pixel[1] >= 0);
				assertTrue(pixel[1] < image.getDimensions()[1]);
			}
		}
	}

	public static void consistentHeritage(Collection<Candidate> msers, Collection<Candidate> topMsers) {

		// are all msers children of topMsers?
		for (Candidate candidate : msers) {

			Candidate traverse = candidate;
			while (traverse.getParent() != null)
				traverse = traverse.getParent();

			assertTrue(topMsers.contains(traverse));
		}

		// are all topMsers in msers?
		for (Candidate top : topMsers)
			assertTrue(msers.contains(top));

		// do children know their parents?
		for (Candidate candidate : msers)
			for (Candidate child : candidate.getChildren())
				assertTrue(child.getParent() == candidate);

		// do parents know their children?
		for (Candidate candidate : msers)
			if (candidate.getParent() != null)
				assertTrue(candidate.getParent().getChildren().contains(candidate));
	}
}
