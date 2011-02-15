package sipnet;

public class SplitAssignment extends SingleAssignment {

	public SplitAssignment(Candidate source, Candidate target1, Candidate target2) {

		addSource(source);
		addTarget(target1);
		addTarget(target2);
	}
}
