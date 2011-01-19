
import java.util.Set;
import java.util.List;

public class Sipnet {

	private Texifyer       texifyer;

	public Sipnet(Texifyer texifyer) {

		this.texifyer = texifyer;
	}

	public Sequence bestSearch(List<Set<Candidate>> sliceCandidates) {

		SequenceSearch sequenceSearch = new SequenceSearch(sliceCandidates, texifyer);

		return sequenceSearch.getBestAssignmentSequence();
	}
}
