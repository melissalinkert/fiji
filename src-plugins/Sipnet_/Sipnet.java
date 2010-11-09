
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class Sipnet {

	private AssignmentSearch assignmentSearch;
	private Texifyer       visualiser;

	public Sipnet(Texifyer visualiser) {

		this.visualiser = visualiser;
	}

	public Sequence greedySearch(Set<Candidate> startCandidates, Vector<Set<Candidate>> sliceCandidates) {

		Sequence    greedySeequence = new Sequence();
		Set<Candidate> sourceRegions   = startCandidates;

		IJ.log("Starting greedy search for " + sliceCandidates.size() + " assignments");

		for (Set<Candidate> targetRegions : sliceCandidates) {

			IJ.log("Finding assignments to slice " + (greedySeequence.size() + 1));

			assignmentSearch = new AssignmentSearch(sourceRegions, targetRegions);

			visualiser.texifyClosestCandidates(sourceRegions, greedySeequence.size() + 1);

			Assignment assignment = assignmentSearch.findBestPath(new Assignment());

			if (assignment == null) {

				IJ.log("No assignments could be found that have the minimum probability.");
				return null;
			}

			greedySeequence.push(assignment);

			sourceRegions.clear();
			for (SingleAssignment singleAssignment : assignment)
				sourceRegions.add(singleAssignment.getTarget());
		}

		return greedySeequence;
	}
}
