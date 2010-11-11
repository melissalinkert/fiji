
import java.util.LinkedList;

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
}
