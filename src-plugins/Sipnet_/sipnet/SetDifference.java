package sipnet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SetDifference implements ShapeDissimilarity {

	final public double dissimilarity(final Candidate candidate1, final Candidate candidate2) {

		final List<int[]> pixels1 = candidate1.getPixels();
		final List<int[]> pixels2 = candidate2.getPixels();
		final int[]       offset1 = new int[]{(int)candidate1.getCenter(0), (int)candidate1.getCenter(1)};
		final int[]       offset2 = new int[]{(int)candidate2.getCenter(0), (int)candidate2.getCenter(1)};

		return setDifference(pixels1, offset1, pixels2, offset2);
	}

	final public double dissimilarity(final Candidate candidate1, final Candidate candidate2a, final Candidate candidate2b) {

		final List<int[]> pixels1 = candidate1.getPixels();
		final List<int[]> pixels2 = new ArrayList<int[]>(candidate2a.getSize() + candidate2b.getSize());
		final int[]       offset1 = new int[]{(int)candidate1.getCenter(0),  (int)candidate1.getCenter(1)};
		final int[]       offset2 = new int[]{(int)candidate2a.getCenter(0), (int)candidate2a.getCenter(1)};

		pixels2.addAll(candidate2a.getPixels());
		pixels2.addAll(candidate2b.getPixels());
		Collections.sort(pixels2, new PixelComparator());

		offset2[0] =
				(candidate2a.getSize()*offset2[0] + candidate2b.getSize()*(int)candidate2b.getCenter(0))/
				(candidate2a.getSize() + candidate2b.getSize());
		offset2[1] =
				(candidate2a.getSize()*offset2[1] + candidate2b.getSize()*(int)candidate2b.getCenter(1))/
				(candidate2a.getSize() + candidate2b.getSize());

		return setDifference(pixels1, offset1, pixels2, offset2);
	}

	final public double setDifferenceRatio(final List<int[]> pixels1, final int[] offset1, final List<int[]> pixels2, final int[] offset2) {

		int numMatches   = numMatches(pixels1, offset1, pixels2, offset2);
		int numDifferent = pixels1.size() + pixels2.size() - 2*numMatches;

		return (double)numDifferent/Math.min(pixels1.size(), pixels2.size());
	}

	final public int setDifference(final List<int[]> pixels1, final int[] offset1, final List<int[]> pixels2, final int[] offset2) {

		int numMatches   = numMatches(pixels1, offset1, pixels2, offset2);
		int numDifferent = pixels1.size() + pixels2.size() - 2*numMatches;

		return numDifferent;
	}

	final public int numMatches(final List<int[]> pixels1, final int[] offset1, final List<int[]> pixels2, final int[] offset2) {

		PixelComparator pixelComparator = new PixelComparator(offset1, offset2);

		int numMatches = 0;

		// we can assume that the pixels are sorted

		Iterator<int[]> i1 = pixels1.iterator();
		Iterator<int[]> i2 = pixels2.iterator();

		int[] pixel2 = null;

		// initialise pixel2 to first one
		if (i2.hasNext())
			pixel2 = i2.next();

		while (i1.hasNext() && i2.hasNext()) {

			// get the next pixel1
			int[] pixel1 = i1.next();

			//System.out.println("pixel1: " + pixel1[0] + ", " + pixel1[1]);
			//System.out.println("pixel2: " + pixel2[0] + ", " + pixel2[1]);

			// iterate through all pixels2 that are smaller than the current
			// pixel1
			while (i2.hasNext() && pixelComparator.compare(pixel1, pixel2) == 1) {
				pixel2 = i2.next();
				//System.out.println("pixel2: " + pixel2[0] + ", " + pixel2[1]);
			}

			// if there is a pixel2 equal to pixel1, increase number of matches
			if (pixelComparator.compare(pixel1, pixel2) == 0) {
				numMatches++;
				//System.out.println("match");
			}
			//System.out.println("continue");

			// continue with next pixel1
		}

		return numMatches;
	}

}
