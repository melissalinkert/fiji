
public class CandidateFactory implements RegionFactory<Candidate> {

	public Candidate create() {
		return new Candidate(0, 0, new double[]{0.0, 0.0});
	}

	public Candidate create(MSER<?, Candidate>.ConnectedComponent component) {
		return new Candidate(component.size, component.getPerimeter(), component.center);
	}
}
