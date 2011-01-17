
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

@SuppressWarnings("serial")
public class Assignment extends LinkedList<SingleAssignment> {

	/**
	 * Get the single assignments in temporal order.
	 *
	 * @param index The number of the single assignment, where 0 corresponds to
	 * the first single assignment that was added to this assignment
	 * @return The single assignment that was added as the <code>index</code>th
	 */
	public SingleAssignment getSingleAssignment(int index) {

		return get(size() - 1 - index);
	}

	public Set<Candidate> getTargets() {

		Set<Candidate> targets = new HashSet<Candidate>();

		for (SingleAssignment singleAssignment : this)
			if (singleAssignment.getTarget() != AssignmentSearch.deathNode)
				targets.add(singleAssignment.getTarget());

		return targets;
	}

	public double getNegLogP() {

		double negLogP = 0.0;

		for (SingleAssignment singleAssignment : this)
			negLogP += singleAssignment.getNegLogP();

		return negLogP;
	}
}
