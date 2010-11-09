
public class CandidateFactory implements RegionFactory<Candidate> {

	public Candidate create() {
		return create(0, new double[0]);
	}

	public Candidate create(int size, double[] center) {
		return new Candidate(size, center);
	}
}
