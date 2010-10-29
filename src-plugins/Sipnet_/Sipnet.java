
import java.util.Set;
import java.util.Vector;

public class Sipnet {

	private AssignmentSearch assignmentSearch;

	public Sipnet(double distanceWeight) {

		AssignmentModel.setDistanceWeight(distanceWeight);
	}

	public Sequence greedySearch(Set<Region> startCandidates, Vector<Set<Region>> sliceCandidates) {

		Sequence    greedySeequence = new Sequence();
		Set<Region> sourceRegions   = startCandidates;

		for (Set<Region> targetRegions : sliceCandidates) {

			assignmentSearch = new AssignmentSearch(sourceRegions, targetRegions);

			Assignment assignment = (Assignment)assignmentSearch.findBestPath();

			greedySeequence.push(assignment);

			sourceRegions.clear();
			for (SingleAssignment singleAssignment : assignment)
				sourceRegions.add(singleAssignment.getTarget());
		}

		return greedySeequence;
	}
}
