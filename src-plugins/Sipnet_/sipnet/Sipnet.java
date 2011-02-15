package sipnet;

import java.util.List;
import java.util.Vector;

public class Sipnet {

	private Texifyer       texifyer;
	private SequenceSearch sequenceSearch;

	public Sipnet(
			List<Vector<Candidate>> sliceCandidates,
			String parameterFilename,
			Texifyer texifyer) {

		this.texifyer = texifyer;

		sequenceSearch =
				new SequenceSearch(
						sliceCandidates,
						texifyer,
						parameterFilename);
	}

	public Sequence bestSearch() {

		return sequenceSearch.getBestAssignmentSequence();
	}
}
