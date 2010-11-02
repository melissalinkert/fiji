
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class Sipnet {

	private AssignmentSearch assignmentSearch;
	private Visualiser       visualiser;

	public Sipnet(double distanceWeight, double areaWeight, Visualiser visualiser) {

		AssignmentModel.setDistanceWeight(distanceWeight);
		AssignmentModel.setAreaWeight(areaWeight);

		this.visualiser = visualiser;
	}

	public Sequence greedySearch(Set<Region> startCandidates, Vector<Set<Region>> sliceCandidates) {

		Sequence    greedySeequence = new Sequence();
		Set<Region> sourceRegions   = startCandidates;

		for (Set<Region> targetRegions : sliceCandidates) {

			assignmentSearch = new AssignmentSearch(sourceRegions, targetRegions);

			visualiser.texifyClosestCandidates(sourceRegions, greedySeequence.size());

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
