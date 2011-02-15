package sipnet;

public class OneToOneAssignment extends SingleAssignment {

	public OneToOneAssignment(Candidate source, Candidate target) {

		addSource(source);
		addTarget(target);
	}

	public double getCosts() {

		AssignmentModel assignmentModel = AssignmentModel.getInstance();

		return assignmentModel.costContinuation(
				getTargets().get(0),
				getSources().get(0),
				true);
	}
}
