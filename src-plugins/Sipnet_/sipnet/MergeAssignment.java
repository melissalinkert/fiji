package sipnet;

public class MergeAssignment extends SingleAssignment {

	public MergeAssignment(Candidate source1, Candidate source2, Candidate target) {

		addSource(source1);
		addSource(source2);
		addTarget(target);
	}

	public double getCosts(AssignmentModel assignmentModel) {

		return assignmentModel.costBisect(
				getTargets().get(0),
				getSources().get(0),
				getSources().get(1));
	}
}

