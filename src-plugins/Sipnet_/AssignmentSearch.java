
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class AssignmentSearch extends AStarSearch<Assignment, SingleAssignment> {

	public static final int MaxTargetCandidates = 5;
	public static final int MinTargetCandidates = 1;

	// number of neighbors to consider for mean neighbor distance
	public static final int NumNeighbors = 5;

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

	protected Set<SingleAssignment> expand(final Assignment path) {

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

			// the neighbor part of the distance

			// for all already assigned neighbors of this target
			for (int i = 0; i < AssignmentSearch.NumNeighbors; i++) {

				// the index of the source neighbor
				int neighborIndex = sourceCandidate.getNeighborIndices().get(i);

				// this neighbor was assigned already
				if (neighborIndex < path.size()) {

					// get the asignee of this neighbor
					Candidate correspond = path.getSingleAssignment(neighborIndex).getTarget();
					// distance to correspondence
					double[] neighborOffset = targetCandidate.offsetTo(correspond);
					// probability of distance change
					distance += AssignmentModel.negLogPNeighbor(sourceCandidate.getNeighborOffsets().get(i), neighborOffset);
				}
			}

			// for all assigned candidates that have the target as neighbor now
			for (SingleAssignment prevSingleAssignment : path) {

				Candidate assignedSourceCandidate = prevSingleAssignment.getSource();
				Candidate assignedTargetCandidate = prevSingleAssignment.getTarget();

				for (int i = 0; i < AssignmentSearch.NumNeighbors; i++) {

					int neighborIndex = assignedSourceCandidate.getNeighborIndices().get(i);

					// targetCandidate is this neighbor of assignedTargetCandidate now
					if (neighborIndex == path.size()) {
						// distance to correspondence of original neighbor
						double[] neighborOffset = targetCandidate.offsetTo(assignedTargetCandidate);
						// probability of distance change
						distance += AssignmentModel.negLogPNeighbor(assignedSourceCandidate.getNeighborOffsets().get(i), neighborOffset);
					}
				}
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

		IJ.log("continuing with node:");
		for (SingleAssignment sa : singleAssignment.getBestPath())
			IJ.log("   " + sa.getSource() + " -> " + sa.getTarget());
	}

	private boolean conflicts(Candidate candidate1, Candidate candidate2) {

		// two candidates are in conflict, if one is the ancestor of the other
		return (candidate1.isAncestorOf(candidate2) || candidate2.isAncestorOf(candidate1));
	}
}
