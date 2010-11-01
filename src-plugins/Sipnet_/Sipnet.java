
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class Sipnet {

	private AssignmentSearch assignmentSearch;

	public Sipnet(double distanceWeight, double areaWeight) {

		AssignmentModel.setDistanceWeight(distanceWeight);
		AssignmentModel.setAreaWeight(areaWeight);
	}

	public Sequence greedySearch(Set<Region> startCandidates, Vector<Set<Region>> sliceCandidates) {

		Sequence    greedySeequence = new Sequence();
		Set<Region> sourceRegions   = startCandidates;

		for (Set<Region> targetRegions : sliceCandidates) {

			assignmentSearch = new AssignmentSearch(sourceRegions, targetRegions);

			Assignment assignment = assignmentSearch.findBestPath(new Assignment());

			if (assignment == null) {

				IJ.log("No assignments could be found that have the minimum probability.");
				return null;
			} else {

				for (SingleAssignment singleAssignment : assignment) {

					IJ.log(singleAssignment.getSource().toString());
					IJ.log(" -> " + singleAssignment.getTarget());
				}
			}

			greedySeequence.push(assignment);

			sourceRegions.clear();
			for (SingleAssignment singleAssignment : assignment)
				sourceRegions.add(singleAssignment.getTarget());
		}

		return greedySeequence;
	}
}
