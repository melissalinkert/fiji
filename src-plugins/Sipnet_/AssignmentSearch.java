
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class AssignmentSearch extends AStarSearch<Assignment, SingleAssignment> {

	private static final int MaxTargetRegions = 5;
	private static final int MinTargetRegions = 1;

	//private static final double MinPAssignment       = 1e-20;
	private static final double MaxNegLogPAssignment = 1e25; //-Math.log(MinPAssignment);

	private Vector<Region> sourceRegions;
	private Vector<Region> targetRegions;

	private class RegionComparator implements Comparator<Region> {

		private Region sourceRegion;

		public RegionComparator(Region sourceRegion) {
			this.sourceRegion = sourceRegion;
		}

		public int compare(Region region1, Region region2) {

			double p1 = AssignmentModel.negLogP(sourceRegion, region1);
			double p2 = AssignmentModel.negLogP(sourceRegion, region2);

			if (p1 < p2)
				return -1;
			else if (p1 > p2)
				return 1;
			else
				return 0;
		}
	}

	public AssignmentSearch(Set<Region> sourceRegions, Set<Region> targetRegions) {

		this.sourceRegions = new Vector<Region>();
		this.targetRegions = new Vector<Region>();

		this.sourceRegions.addAll(sourceRegions);
		this.targetRegions.addAll(targetRegions);

		// build cache
		for (Region sourceRegion : sourceRegions)
			cacheClosestRegions(sourceRegion);
	}

	protected Set<SingleAssignment> expand(Assignment path) {

		Set<SingleAssignment> assignments = new HashSet<SingleAssignment>();

		// get region that should now list its possible targets
		Region sourceRegion = sourceRegions.get(path.size());

		// get all possible targets
A:		for (Region targetRegion : sourceRegion.getClosestRegions()) {

			// check if target region was already assigned
			// TODO: optimize
			for (SingleAssignment singleAssignment : path)
				if (targetRegion == singleAssignment.getTarget())
					continue A;

			// check for concurrent hypothesis consistency
			// TODO: optimize
			for (SingleAssignment singleAssignment : path)
				if (conflicts(targetRegion, singleAssignment.getTarget()))
					continue A;

			SingleAssignment assignment =
			    new SingleAssignment(sourceRegion, targetRegion,
			                         AssignmentModel.negLogP(sourceRegion, targetRegion));

			Assignment bestPath = new Assignment();
			bestPath.addAll(path);
			bestPath.push(assignment);
			assignment.setBestPath(bestPath);

			assignments.add(assignment);
		}

		return assignments;
	}


	protected double g(Assignment path, SingleAssignment node) {

		return (path.peek() != null ? path.peek().getDistanceFromStart() : 0.0) + node.getNegLogP();
	}

	protected double h(SingleAssignment node) {

		double distance = 0.0;

		// for all source regions, that have not been assigned yet...
		for (int i = node.getBestPath().size(); i < sourceRegions.size(); i++) {

			// ...sum up all best distances as our optimistic estimate of
			distance += sourceRegions.get(i).getMinNegLogPAssignment();
		}

		return distance;
	}

	protected boolean reachedTarget(Assignment assignment) {

		IJ.showProgress(assignment.size(), sourceRegions.size());

		return assignment.size() == sourceRegions.size();
	}

	protected void noMoreOpenNodes(Assignment assignment) {

		IJ.showProgress(sourceRegions.size(), sourceRegions.size());
	}

	protected void goingTo(SingleAssignment singleAssignment) {

		IJ.log("connected " + singleAssignment.getSource() + " to " + singleAssignment.getTarget());
	}

	private void cacheClosestRegions(Region sourceRegion) {

		// cache all possible targets
		if (sourceRegion.getClosestRegions() == null) {

			PriorityQueue<Region> closestRegions = new PriorityQueue<Region>(MaxTargetRegions, new RegionComparator(sourceRegion));

			closestRegions.addAll(targetRegions);

			// remove unlikely regions
			PriorityQueue<Region> prunedClosestRegions = new PriorityQueue<Region>(MaxTargetRegions, new RegionComparator(sourceRegion));

			while (prunedClosestRegions.size() < MaxTargetRegions &&
				   AssignmentModel.negLogP(sourceRegion, closestRegions.peek()) <= MaxNegLogPAssignment)
				prunedClosestRegions.add(closestRegions.poll());

			if (prunedClosestRegions.size() < MinTargetRegions) {
				IJ.log("Oh no! For region " + sourceRegion +
				       " there are less than " + MinTargetRegions + " within the threshold of " +
				       MaxNegLogPAssignment);
				IJ.log("Closest non-selected candidate distance: " + AssignmentModel.negLogP(sourceRegion, closestRegions.peek()));

				// fill in these values anyway
				sourceRegion.setClosestRegions(closestRegions);
				sourceRegion.setMinNegLogPAssignment(AssignmentModel.negLogP(sourceRegion, closestRegions.peek()));
			} else {

				closestRegions = prunedClosestRegions;
				sourceRegion.setClosestRegions(closestRegions);
				sourceRegion.setMinNegLogPAssignment(AssignmentModel.negLogP(sourceRegion, closestRegions.peek()));
			}
		}
	}

	private boolean conflicts(Region region1, Region region2) {

		// two regions are in conflict, if one is the ancestor of the other
		return (region1.isAncestorOf(region2) || region2.isAncestorOf(region1));
	}
}
