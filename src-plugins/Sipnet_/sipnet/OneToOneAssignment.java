package sipnet;

public class OneToOneAssignment extends SingleAssignment {

	public OneToOneAssignment(Candidate source, Candidate target) {

		addSource(source);
		addTarget(target);
	}

	public double getCosts(AssignmentModel assignmentModel) {

		if (getSources().get(0) == SequenceSearch.emergeNode)
			return assignmentModel.costEnd(getTargets().get(0));
		if (getTargets().get(0) == SequenceSearch.deathNode)
			return assignmentModel.costEnd(getSources().get(0));

		return assignmentModel.costContinuation(
				getTargets().get(0),
				getSources().get(0),
				true);
	}
}
