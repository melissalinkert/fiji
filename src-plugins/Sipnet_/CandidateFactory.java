
public class CandidateFactory implements RegionFactory<Candidate> {

	public Candidate create() {
		return new Candidate();
	}

	public Candidate create(MSER<?, Candidate>.ConnectedComponent component) {

		return new Candidate(
				component.size,
				component.getPerimeter(),
				component.center,
				component.getPixels(),
				component.meanValue);
	}
}
