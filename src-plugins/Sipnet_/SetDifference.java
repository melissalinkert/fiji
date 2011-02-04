
import java.util.Iterator;
import java.util.List;

public class SetDifference implements ShapeDissimilarity {

	public double dissimilarity(Candidate candidate1, Candidate candidate2) {

		List<int[]> pixels1 = candidate1.getPixels();
		List<int[]> pixels2 = candidate2.getPixels();

		return setDifferenceRatio(pixels1, pixels2);
	}

	public double dissimilarity(Candidate candidate1, Candidate candidate2a, Candidate candidate2b) {

		List<int[]> pixels1 = candidate1.getPixels();
		List<int[]> pixels2 = candidate2a.getPixels();
		pixels2.addAll(candidate2b.getPixels());

		return setDifferenceRatio(pixels1, pixels2);
	}

	private double setDifferenceRatio(List<int[]> pixels1, List<int[]> pixels2) {

		Iterator<int[]> iterator1 = pixels1.iterator();

		int numMatches = 0;

		while (iterator1.hasNext()) {

			Iterator<int[]> iterator2 = pixels2.iterator();

			while (iterator2.hasNext()) {

				int[] pixel1 = iterator1.next();
				int[] pixel2 = iterator2.next();

				if (pixel1[0] == pixel2[0] && pixel1[1] == pixel2[1]) {
					numMatches++;
					break;
				}
			}
		}

		int numDifferent = pixels1.size() + pixels2.size() - 2*numMatches;

		return (double)numDifferent/Math.max(pixels1.size(), pixels2.size());
	}
}
