
import java.util.Arrays;
import java.util.List;

public class CandidateFactory implements RegionFactory<Candidate> {

	public Candidate create() {
		return new Candidate();
	}

	public Candidate create(MSER<?, Candidate>.ConnectedComponent component) {

		List<int[]> pixels = Arrays.asList(component.getPixels());

		return new Candidate(
				component.size,
				component.getPerimeter(),
				component.center,
				pixels,
				component.meanValue);
	}
}
