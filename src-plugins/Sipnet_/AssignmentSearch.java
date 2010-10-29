
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Vector;

public class AssignmentSearch extends AStarSearch<SingleAssignment> {

	private Vector<Region> sourceRegions;
	private Vector<Region> targetRegions;

	public AssignmentSearch(Set<Region> sourceRegions, Set<Region> targetRegions) {

		this.sourceRegions = new Vector<Region>();
		this.targetRegions = new Vector<Region>();

		this.sourceRegions.addAll(sourceRegions);
		this.targetRegions.addAll(targetRegions);
	}

	protected Set<SingleAssignment> expand(LinkedList<SingleAssignment> path) {

		Set<SingleAssignment> assignments = new HashSet<SingleAssignment>();

		// get region that should new list its possible targets
		Region sourceRegion = sourceRegions.get(path.size());

		// get all possible targets
		// TODO: don't consider occupied targets!
		for (Region targetRegion : targetRegions)
			if (AssignmentModel.negLogP(sourceRegion, targetRegion) <= AssignmentModel.MaxNegLogPAssignment)
				assignments.add(new SingleAssignment(sourceRegion, targetRegion));

		return assignments;
	}

	protected double g(LinkedList<SingleAssignment> path, SingleAssignment node) {

		return path.peek().getDistanceFromStart() + AssignmentModel.negLogP(node);
	}

	protected double h(SingleAssignment node) {

		// greedy search
		return 1.0;
	}

	protected boolean reachedTarget(LinkedList<SingleAssignment> path) {

		return path.size() == sourceRegions.size();
	}
}
