package sipnet;

import java.util.List;
import java.util.Vector;

public class Sipnet {

	private SequenceSearch  sequenceSearch;

	public Sipnet(
			List<Vector<Candidate>> sliceCandidates,
			String                  parameterFilename,
			Texifyer                texifyer,
			AssignmentModel         assignmentModel) {

		sequenceSearch =
				new SequenceSearch(
						sliceCandidates,
						texifyer,
						parameterFilename,
						assignmentModel);
	}

	public Sequence bestSearch() {

		return sequenceSearch.getBestAssignmentSequence();
	}
}
