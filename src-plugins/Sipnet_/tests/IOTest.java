package tests;

import java.util.List;
import java.util.Vector;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import sipnet.Candidate;

import sipnet.io.IO;

public class IOTest {

	private IO io;

	private Vector<Vector<Candidate>> sliceMsers;

	private int numSlices   = 10;
	private int numRegions  = 100;
	private int numChildren = 10;

	@Before
	public void setUp() throws Exception {

		System.out.println("Setting up test...");

		io = new IO();

		sliceMsers    = new Vector<Vector<Candidate>>();

		int lastId = -1;

		// create some random regions
		for (int s = 0; s < numSlices; s++) {

			Vector<Candidate> msers = new Vector<Candidate>();

			for (int c = 0; c < numRegions; c++) {

				int size        = 10;
				int perimeter   = 5;
				double[] center = new double[2];
				int[][]  pixels = new int[size][2];
				double meanGray = 0.0;

				for (int i = 0; i < size; i++) {
					pixels[i][0] = (int)(Math.random()*100.0);
					pixels[i][1] = (int)(Math.random()*100.0);
				}

				Candidate candidate = new Candidate(size, perimeter, center, pixels, meanGray);

				Vector<Candidate> children = new Vector<Candidate>();
				for (int k = 0; k < numChildren; k++) {

					Candidate child = new Candidate(0, 0, new double[]{0, 0}, new int[][]{}, 0.0);
					child.setParent(candidate);
					children.add(child);
				}
				candidate.addChildren(children);

				if (lastId != -1)
					assertEquals(candidate.getId(), lastId+1+numChildren);
				lastId = candidate.getId();

				msers.add(candidate);
			}

			sliceMsers.add(msers);
		}

		System.out.println("...done");
	}

	@After
	public void tearDown() throws Exception {

		io         = null;
		sliceMsers = null;
	}

	@Test
	public void testWriteReadMsers() {

		io.writeMsers(sliceMsers, "test-msers.sip");

		for (int s = 0; s < numSlices; s++) {

			List<Vector<Candidate>> candidates = io.readMsers("test-msers.sip", s, s);

			for (int c = 0; c < numRegions; c++) {

				Vector<Candidate> fromFile =
						candidates.get(0);
				Vector<Candidate> prepared =
						sliceMsers.get(s);

				Candidate read  = fromFile.get(c);
				Candidate write = prepared.get(c);

				System.out.println("slice " + s + ", candidate " + c + " with id " + read.getId());

				// id is the same?
				assertEquals(read.getId(), write.getId());

				// size is the same?
				int size = read.getSize();
				assertEquals(size, write.getSize());

				// center is the same?
				assertEquals(read.getCenter()[0], write.getCenter()[0], 1.0e-20);
				assertEquals(read.getCenter()[1], write.getCenter()[1], 1.0e-20);

				// perimeter is the same?
				assertEquals(read.getPerimeter(), write.getPerimeter());

				// content the same?
				for (int i = 0; i < size; i++) {

					assertEquals(read.getPixels().get(i)[0], write.getPixels().get(i)[0]);
					assertEquals(read.getPixels().get(i)[1], write.getPixels().get(i)[1]);
				}

				// gray level the same?
				assertEquals(read.getMeanGrayValue(), write.getMeanGrayValue(), 1.0e-20);

				// children are the same?
				assertEquals(read.getChildren().size(), numChildren);
				for (int k = 0; k < numChildren; k++)
					assertEquals(read.getChildren().get(k).getId(), write.getChildren().get(k).getId());

				// parents are the same?
				assertTrue(read.getParent() == null);
			}

			Vector<Candidate> msers = flatten(candidates.get(0));

			// have all children be reconstructed?
			assertEquals(msers.size(), numRegions * (numChildren + 1));

			// is the heritage structure consistent?
			MSERTest.consistentHeritage(msers, candidates.get(0));
		}

	}

	private Vector<Candidate> flatten(Vector<Candidate> parents) {

		Vector<Candidate> allRegions = new Vector<Candidate>();

		allRegions.addAll(parents);
		for (Candidate parent : parents)
			allRegions.addAll(flatten(parent.getChildren()));

		return allRegions;
	}
}
