
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class AssignmentSearch extends AStarSearch<Assignment, SingleAssignment> {

	public static final int MaxTargetCandidates = 5;
	public static final int MinTargetCandidates = 1;

	// number of neighbors to consider for mean neighbor distance
	public static final int NumNeighbors = 3;

	//private static final double MinPAssignment       = 1e-20;
	public static final double MaxNegLogPAssignment = 1e25; //-Math.log(MinPAssignment);

	private Vector<Candidate> sourceCandidates;
	private Vector<Candidate> targetCandidates;

	public AssignmentSearch(Set<Candidate> sourceCandidates, Set<Candidate> targetCandidates) {

		this.sourceCandidates = new Vector<Candidate>();
		this.targetCandidates = new Vector<Candidate>();

		this.sourceCandidates.addAll(sourceCandidates);
		this.targetCandidates.addAll(targetCandidates);

		// build cache and find neighbors in source candidates
		for (Candidate sourceCandidate : this.sourceCandidates) {
			sourceCandidate.cacheMostSimilarCandidates(this.targetCandidates);
			sourceCandidate.findNeighbors(this.sourceCandidates);
		}
	}

	protected Set<SingleAssignment> expand(Assignment path) {

		Set<SingleAssignment> assignments = new HashSet<SingleAssignment>();

		// get region that should now list its possible targets
		Candidate sourceCandidate = sourceCandidates.get(path.size());

		// get all possible targets
A:		for (Candidate targetCandidate : sourceCandidate.getMostLikelyCandidates()) {

			// check if target region was already assigned
			// TODO: optimize
			for (SingleAssignment singleAssignment : path)
				if (targetCandidate == singleAssignment.getTarget())
					continue A;

			// check for concurrent hypothesis consistency
			// TODO: optimize
			for (SingleAssignment singleAssignment : path)
				if (conflicts(targetCandidate, singleAssignment.getTarget()))
					continue A;

			// create a new assignment, then "move" to it and remember the
			// distance
			SingleAssignment singleAssignment =
			    new SingleAssignment(sourceCandidate, targetCandidate);

			Assignment bestPath = new Assignment();
			bestPath.addAll(path);
			bestPath.push(singleAssignment);
			singleAssignment.setBestPath(bestPath);

			assignments.add(singleAssignment);

			// the appearance part of the distance
			double distance = AssignmentModel.negLogPAppearance(sourceCandidate, targetCandidate);

			// for each set of source neurons and its neighbors that was completed
			// by this assignment, add the mean-neighbor-distance probabiltiy
			
			// are all neighbors of source assigned?
			boolean allAssigned = true;
			for (int neighborIndex : sourceCandidate.getNeighborIndices())
				if (neighborIndex >= path.size())
					allAssigned = false;
			if (allAssigned)
				distance += AssignmentModel.negLogPNeighbors(sourceCandidate, targetCandidate, bestPath);

			// from all assigned candidates with only one unassigned neighbor, is source
			// the neighbor?
			for (SingleAssignment prevSingleAssignment : path) {

				Candidate assignedSourceCandidate = prevSingleAssignment.getSource();
				Candidate assignedTargetCandidate = prevSingleAssignment.getTarget();

				boolean sourceIsNeighbor       = false;
				int     numUnassignedNeighbors = 0;

				for (int neighborIndex : assignedSourceCandidate.getNeighborIndices()) {

					if (neighborIndex >= path.size())
						numUnassignedNeighbors++;

					if (neighborIndex == path.size())
						sourceIsNeighbor = true;
				}

				// now that all neighbors are assigned in "path", we can compute
				// the exact mean-neighbor-distance probability
				if (numUnassignedNeighbors == 1 && sourceIsNeighbor)
					distance += AssignmentModel.negLogPNeighbors(assignedSourceCandidate, assignedTargetCandidate, bestPath);
			}

			singleAssignment.setNegLogP(distance);
		}

		return assignments;
	}


	protected double g(Assignment path, SingleAssignment node) {

		return (path.peek() != null ? path.peek().getDistanceFromStart() : 0.0) + node.getNegLogP();
	}

	protected double h(SingleAssignment node) {

		double distance = 0.0;

		// for all source candidates, that have not been assigned yet...
		for (int i = node.getBestPath().size(); i < sourceCandidates.size(); i++) {

			// ...sum up all best distances to still available candidates as our optimistic
			// estimate of the path length
			Candidate closestAvailableCandidate = null;

			// TODO: optimize
A:			for (Candidate region : sourceCandidates.get(i).getMostLikelyCandidates()) {
				for (SingleAssignment assignment : node.getBestPath()) {

					// this close region is assigned already
					if (closestAvailableCandidate == assignment.getTarget())
						continue A;
				}

				// we found a close unassigned region
				closestAvailableCandidate = region;
				break;
			}

			if (closestAvailableCandidate == null) {
				IJ.log("Oh no! For the computation of h there are no more available close regions!");
				continue;
			}

			// optimistic guess on the appearance probability
			distance += sourceCandidates.get(i).getNegLogPAppearance(closestAvailableCandidate);
			// optimistic guess on the mean neighbor distance probability
			distance += AssignmentModel.negLogPNeighbors(sourceCandidates.get(i), closestAvailableCandidate, node.getBestPath());

			sourceCandidates.get(i).getNegLogPAppearance(closestAvailableCandidate);
		}

		// for all source candidates, who's neighbors are not assigned yet and
		// that are already assigned
		for (SingleAssignment singleAssignment : node.getBestPath()) {

			Candidate sourceCandidate = singleAssignment.getSource();
			Candidate targetCandidate = singleAssignment.getTarget();

			boolean allAssigned = true;

			for (int neighborIndex : sourceCandidate.getNeighborIndices())
				// neighbor unassigned?
				if (neighborIndex >= node.getBestPath().size()) {
					allAssigned = false;
					break;
				}

			if (!allAssigned) {

				// optimistic guess on the mean neighbor distance probability
				distance += AssignmentModel.negLogPNeighbors(sourceCandidate, targetCandidate, node.getBestPath());
			}
		}

		return distance;
	}

	protected boolean reachedTarget(Assignment assignment) {

		IJ.showProgress(assignment.size(), sourceCandidates.size());

		return assignment.size() == sourceCandidates.size();
	}

	protected void noMoreOpenNodes(Assignment assignment) {

		IJ.log("Oh no! There are no more open nodes and I didn't reach my target yet.");
		IJ.showProgress(sourceCandidates.size(), sourceCandidates.size());
	}

	protected void goingTo(SingleAssignment singleAssignment) {

		IJ.log("connected " + singleAssignment.getSource() + " to " + singleAssignment.getTarget());
	}

	private boolean conflicts(Candidate candidate1, Candidate candidate2) {

		// two candidates are in conflict, if one is the ancestor of the other
		return (candidate1.isAncestorOf(candidate2) || candidate2.isAncestorOf(candidate1));
	}
}
