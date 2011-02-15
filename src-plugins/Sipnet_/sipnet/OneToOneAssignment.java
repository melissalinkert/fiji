package sipnet;

public class OneToOneAssignment extends SingleAssignment {

	public OneToOneAssignment(Candidate source, Candidate target) {

		addSource(source);
		addTarget(target);
	}
}
