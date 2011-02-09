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

	Image<T> image;

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
	}

	@Test
	public void testHeritage() {

		MSER<T, Candidate> mser =
				new MSER<T, Candidate>(
						image.getDimensions(),
						delta,
						minArea,
						maxArea,
						maxVariation,
						minDiversity,
						new CandidateFactory());

		// process from dark to bright
		mser.process(image, true, false);

		// read back msers
		HashSet<Candidate> topMsers = mser.getTopMsers();
		HashSet<Candidate> msers    = mser.getMsers();

		System.out.println("Found " + msers.size() + " candidates");

		// test for consistency and completenes
		consistentHeritage(msers, topMsers);

		System.out.println("Done.");
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
