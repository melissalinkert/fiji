
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class AssignmentSearch extends AStarSearch<Assignment, SingleAssignment> {

	public static final int MaxTargetCandidates = 5;
	public static final int MinTargetCandidates = 1;

	//private static final double MinPAssignment       = 1e-20;
	public static final double MaxNegLogPAssignment = 1e25; //-Math.log(MinPAssignment);

	private Vector<Candidate> sourceCandidates;
	private Vector<Candidate> targetCandidates;

	public AssignmentSearch(Set<Candidate> sourceCandidates, Set<Candidate> targetCandidates) {

		this.sourceCandidates = new Vector<Candidate>();
		this.targetCandidates = new Vector<Candidate>();

		this.sourceCandidates.addAll(sourceCandidates);
		this.targetCandidates.addAll(targetCandidates);

		// build cache
		for (Candidate sourceCandidate : sourceCandidates)
			sourceCandidate.cacheClosestCandidates(targetCandidates);
	}

	protected Set<SingleAssignment> expand(Assignment path) {

		Set<SingleAssignment> assignments = new HashSet<SingleAssignment>();

		// get region that should now list its possible targets
		Candidate sourceCandidate = sourceCandidates.get(path.size());

		// get all possible targets
A:		for (Candidate targetCandidate : sourceCandidate.getClosestCandidates()) {

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

			SingleAssignment assignment =
			    new SingleAssignment(sourceCandidate, targetCandidate,
			                         AssignmentModel.negLogP(sourceCandidate, targetCandidate));

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
		for (int i = node.getBestPath().size(); i < sourceCandidates.size(); i++) {

			// ...sum up all best distances to still available candidates as our optimistic
			// estimate of the path length
			Candidate closestAvailableCandidate = null;

			// TODO: optimize
A:			for (Candidate region : sourceCandidates.get(i).getClosestCandidates()) {
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

			distance += sourceCandidates.get(i).getNegLogPAssignment(closestAvailableCandidate);
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
