
import java.util.Iterator;
import java.util.List;

public class SetDifference implements ShapeDissimilarity {

	public double dissimilarity(Candidate candidate1, Candidate candidate2) {

		List<int[]> pixels1 = candidate1.getPixels();
		List<int[]> pixels2 = candidate2.getPixels();
		int[]       offset1 = new int[]{(int)candidate1.getCenter()[0], (int)candidate1.getCenter()[1]};
		int[]       offset2 = new int[]{(int)candidate2.getCenter()[0], (int)candidate2.getCenter()[1]};

		return setDifferenceRatio(pixels1, offset1, pixels2, offset2);
	}

	public double dissimilarity(Candidate candidate1, Candidate candidate2a, Candidate candidate2b) {

		List<int[]> pixels1 = candidate1.getPixels();
		List<int[]> pixels2 = candidate2a.getPixels();
		int[]       offset1 = new int[]{(int)candidate1.getCenter()[0],  (int)candidate1.getCenter()[1]};
		int[]       offset2 = new int[]{(int)candidate2a.getCenter()[0], (int)candidate2a.getCenter()[1]};

		pixels2.addAll(candidate2b.getPixels());
		offset2[0] = (candidate2a.getSize()*offset2[0] + candidate2b.getSize()*(int)candidate2b.getCenter()[0])/(candidate2a.getSize() + candidate2b.getSize());
		offset2[1] = (candidate2a.getSize()*offset2[1] + candidate2b.getSize()*(int)candidate2b.getCenter()[1])/(candidate2a.getSize() + candidate2b.getSize());

		return setDifferenceRatio(pixels1, offset1, pixels2, offset2);
	}

	private double setDifferenceRatio(List<int[]> pixels1, int[] offset1, List<int[]> pixels2, int[] offset2) {

		Iterator<int[]> iterator1 = pixels1.iterator();

		int numMatches = 0;

		while (iterator1.hasNext()) {

			Iterator<int[]> iterator2 = pixels2.iterator();

			while (iterator2.hasNext()) {

				int[] pixel1 = iterator1.next();
				int[] pixel2 = iterator2.next();

				if (pixel1[0] - offset1[0] == pixel2[0] - offset2[0] &&
				    pixel1[1] - offset1[1] == pixel2[1] - offset2[1]) {
					numMatches++;
					break;
				}
			}
		}

		int numDifferent = pixels1.size() + pixels2.size() - 2*numMatches;

		return (double)numDifferent/Math.max(pixels1.size(), pixels2.size());
	}
}
