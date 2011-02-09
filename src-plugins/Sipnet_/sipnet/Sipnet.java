package sipnet;

import java.util.List;
import java.util.Vector;

public class Sipnet {

	private Texifyer       texifyer;

	public Sipnet(Texifyer texifyer) {

		this.texifyer = texifyer;
	}

	public Sequence bestSearch(List<Vector<Candidate>> sliceCandidates, AssignmentModel assignmentModel) {

		SequenceSearch sequenceSearch = new SequenceSearch(sliceCandidates, assignmentModel, texifyer);

		return sequenceSearch.getBestAssignmentSequence();
	}
}
