
import java.util.HashSet;
import java.util.Vector;

public class Sipnet {

	double distanceWeight;
	double volumeWeight;

	public Sipnet(double distanceWeight, double volumeWeight) {

		this.distanceWeight = distanceWeight;
		this.volumeWeight   = volumeWeight;
	}

	public Sequence greedySearch(Vector<Region> startCandidates, Vector<HashSet<Region>> sliceCandidates) {

		Sequence       greedySeequence  = new Sequence();
		Vector<Region> activeCandidates = startCandidates;

		for (HashSet<Region> candidates : sliceCandidates) {

			Assignment bestAssignment = getBestAssignment(activeCandidates, candidates);
			greedySeequence.add(bestAssignment);

			activeCandidates.clear();
			activeCandidates.addAll(bestAssignment.values());
		}
		return greedySeequence;
	}

	private final Assignment getBestAssignment(Vector<Region> activeCandidates, HashSet<Region> candidates) {

		Assignment bestAssignment = new Assignment();

		// first, assign each candidate to its best partner
		for (Region activeCandidate : activeCandidates) {

			Region bestPartner = getBestPartner(activeCandidate, candidates);
			bestAssignment.put(activeCandidate, bestPartner);
		}

		return bestAssignment;
	}

	private final Region getBestPartner(Region activeCandidate, HashSet<Region> candidates) {

		Region bestPartner = null;
		double bestValue   = 0;

		for (Region assignmentCandidate : candidates) {

			double value = getCandidateAssignmentValue(activeCandidate, assignmentCandidate);

			if (value < bestValue || bestPartner == null) {

				bestValue   = value;
				bestPartner = assignmentCandidate;
			}
		}

		return bestPartner;
	}

	private final double getCandidateAssignmentValue(Region candidate1, Region candidate2) {

		double distanceValue = getCandidateDistanceValue(candidate1, candidate2);
		double volumeValue   = getCandidateVolumeValue(candidate1, candidate2);

		return distanceWeight*distanceValue + volumeWeight*volumeValue;
	}

	private final double getCandidateDistanceValue(Region candidate1, Region candidate2) {

		double distance2 = (candidate1.center[0] - candidate2.center[0])*(candidate1.center[0] - candidate2.center[0]) +
						   (candidate1.center[1] - candidate2.center[1])*(candidate1.center[1] - candidate2.center[1]);

		return distance2;
	}

	private final double getCandidateVolumeValue(Region candidate1, Region candidate2) {

		// TODO: consider volume of candidates
		return (candidate1.size - candidate2.size)*(candidate1.size - candidate2.size);
	}
}
