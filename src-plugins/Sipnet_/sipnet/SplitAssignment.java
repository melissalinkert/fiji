package sipnet;

public class SplitAssignment extends SingleAssignment {

	public SplitAssignment(Candidate source, Candidate target1, Candidate target2) {

		addSource(source);
		addTarget(target1);
		addTarget(target2);
	}

	public double getCosts() {

		AssignmentModel assignmentModel = AssignmentModel.getInstance();

		return assignmentModel.costBisect(
				getSources().get(0),
				getTargets().get(0),
				getTargets().get(1));
	}
}
