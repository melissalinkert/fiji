
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class AssignmentSearch extends AStarSearch<Assignment, SingleAssignment> {

	private Vector<Region> sourceRegions;
	private Vector<Region> targetRegions;

	public AssignmentSearch(Set<Region> sourceRegions, Set<Region> targetRegions) {

		this.sourceRegions = new Vector<Region>();
		this.targetRegions = new Vector<Region>();

		this.sourceRegions.addAll(sourceRegions);
		this.targetRegions.addAll(targetRegions);
	}

	protected Set<SingleAssignment> expand(Assignment path) {

		Set<SingleAssignment> assignments = new HashSet<SingleAssignment>();

		// get region that should new list its possible targets
		Region sourceRegion = sourceRegions.get(path.size());

		// get all possible targets
		// TODO: don't consider occupied targets!
		// TODO: don't consider forbidden assignments!
		for (Region targetRegion : targetRegions) {

			double negLogPAssignement = AssignmentModel.negLogP(sourceRegion, targetRegion);
			if (negLogPAssignement <= AssignmentModel.MaxNegLogPAssignment) {

				SingleAssignment assignment = new SingleAssignment(sourceRegion, targetRegion, negLogPAssignement);

				Assignment bestPath = new Assignment();
				bestPath.addAll(path);
				bestPath.push(assignment);
				assignment.setBestPath(bestPath);

				assignments.add(assignment);
			}
		}

		if (assignments.size() == 0)
			IJ.log("Oh noh! There are no candidates for region " + sourceRegion +
			       " within the threshold of " + AssignmentModel.MaxNegLogPAssignment);

		return assignments;
	}

	protected double g(Assignment path, SingleAssignment node) {

		return (path.peek() != null ? path.peek().getDistanceFromStart() : 0.0) + node.getNegLogP();
	}

	protected double h(SingleAssignment node) {

		double distance = 0.0;

		// for all source regions, that have not been assigned yet...
		for (int i = node.getBestPath().size(); i < sourceRegions.size(); i++) {

			// ...compute (and cache) best possible assignment probability...
			if (sourceRegions.get(i).getMinNegLogPAssignment() < 0) {

				for (Region targetRegion : targetRegions) {

					double negLogPAssignement = AssignmentModel.negLogP(sourceRegions.get(i), targetRegion);

					if (sourceRegions.get(i).getMinNegLogPAssignment() > negLogPAssignement ||
					    sourceRegions.get(i).getMinNegLogPAssignment() < 0) {
						sourceRegions.get(i).setMinNegLogPAssignment(negLogPAssignement);
						sourceRegions.get(i).setClosestRegion(targetRegion);
					}
				}
			}
			// ...and use the sum of all as our optimistic estimate of the
			// remaining distance
			distance += sourceRegions.get(i).getMinNegLogPAssignment();
		}

		return distance;
	}

	protected boolean reachedTarget(Assignment assignment) {

		IJ.showProgress(assignment.size(), sourceRegions.size());

		return assignment.size() == sourceRegions.size();
	}
}
