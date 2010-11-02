
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class Sipnet {

	private AssignmentSearch assignmentSearch;
	private Visualiser       visualiser;

	public Sipnet(Visualiser visualiser) {

		this.visualiser = visualiser;
	}

	public Sequence greedySearch(Set<Region> startCandidates, Vector<Set<Region>> sliceCandidates) {

		Sequence    greedySeequence = new Sequence();
		Set<Region> sourceRegions   = startCandidates;

		IJ.log("Starting greedy search for " + sliceCandidates.size() + " assignments");

		for (Set<Region> targetRegions : sliceCandidates) {

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
