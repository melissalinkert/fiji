package sipnet;

import java.util.LinkedList;
import java.util.Set;

@SuppressWarnings("serial")
public class Sequence extends LinkedList<SequenceNode> {

	/**
	 * Returns a set of all active nodes in the sequence, i.e., all the target
	 * nodes of the last assignment.
	 *
	 * @return The set of all active nodes.
	 */
	public Set<Candidate> getActiveNodes() {

		// linked list is like a stack - first element is the most recently
		// added one
		return peekFirst().getAssignment().getTargets();
	}
}
