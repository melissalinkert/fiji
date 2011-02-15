package sipnet;

public class MergeAssignment extends SingleAssignment {

	public MergeAssignment(Candidate source1, Candidate source2, Candidate target) {

		addSource(source1);
		addSource(source2);
		addTarget(target);
	}
}

