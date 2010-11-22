
import java.util.Set;
import java.util.List;

public class Sipnet {

	private Texifyer       texifyer;

	public Sipnet(Texifyer texifyer) {

		this.texifyer = texifyer;
	}

	public Sequence bestSearch(Set<Candidate> startCandidates, List<Set<Candidate>> sliceCandidates) {

		SequenceSearch sequenceSearch = new SequenceSearch(startCandidates, sliceCandidates, texifyer);

		return sequenceSearch.findBestPath(new Sequence());
	}
}
