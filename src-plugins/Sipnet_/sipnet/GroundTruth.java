package sipnet;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

import mpicbg.imglib.image.Image;

import mpicbg.imglib.type.numeric.RealType;

public class GroundTruth {

	// the groundtruth sequence - depending on the data provided it consists of
	// candidates with a center only or candidates with complete regions
	private Sequence groundtruth;

	// does the ground truth contain region information as well?
	private boolean  withRegionInformation;

	public GroundTruth() {

		this.groundtruth = new Sequence();
	}

	public void readFromTextFile() {

	}

	public <T extends RealType<T>> void readFromLabelImages(Vector<Image<T>> sliceImages) {

		if (sliceImages.size() == 0)
			return;

		IJ.log("reading ground truth...");
		withRegionInformation = true;

		int slices = sliceImages.size();

		// extract connected components 
		ConnectedComponents<T> connectedComponents = new ConnectedComponents<T>();

		Vector<Candidate> prevCandidates =
				componentsToCandidates(connectedComponents.find(sliceImages.get(0)));

		// process first slice
		IJ.log("Found " + prevCandidates.size() + " candidates in slice 0");

		for (int s = 1; s < slices; s++) {

			Assignment assignment = new Assignment();

			Vector<Candidate> candidates =
					componentsToCandidates(connectedComponents.find(sliceImages.get(s)));

			IJ.log("Found " + candidates.size() + " candidates in slice " + s);

			// set of candidates that have not been assigned yet
			Set<Candidate> remainders = new HashSet<Candidate>(candidates);

			for (Candidate prev : prevCandidates) {

				Vector<Candidate> sameNeuron = new Vector<Candidate>();

				// get all candidates with same label
				int maxId = 0;
				for (Candidate same : prevCandidates)
					if (same.getMeanGrayValue() == prev.getMeanGrayValue()) {

						if (maxId < same.getId())
							maxId = same.getId();

						sameNeuron.add(same);
					}

				// only consider this set, if prev is the biggest of all these
				if (prev.getId() != maxId)
					continue;

				// find all candidates in current slice that have the same label
				Vector<Candidate> targets = new Vector<Candidate>();

				for (Candidate candidate : candidates)
					if (candidate.getMeanGrayValue() == prev.getMeanGrayValue()) {

						targets.add(candidate);
						remainders.remove(candidate);
					}

				if (sameNeuron.size() == targets.size()) {

					for (Candidate candidate : sameNeuron) {

						Candidate closest    = null;
						double    minDistance = -1;
						for (Candidate target : targets) {

							double distance =
									(candidate.getCenter(0) - target.getCenter(0))*
									(candidate.getCenter(0) - target.getCenter(0))
									+
									(candidate.getCenter(1) - target.getCenter(1))*
									(candidate.getCenter(1) - target.getCenter(1));

							if (distance < minDistance || minDistance < 0) {

								minDistance = distance;
								closest = target;
							}
						}

						assignment.add(new OneToOneAssignment(candidate, closest));
						targets.remove(closest);
					}

				} else if (sameNeuron.size() == 1 && targets.size() == 2)

					assignment.add(new SplitAssignment(sameNeuron.get(0), targets.get(0), targets.get(1)));

				else if (sameNeuron.size() == 2 && targets.size() == 1)

					assignment.add(new MergeAssignment(sameNeuron.get(0), sameNeuron.get(1), targets.get(0)));

				else if (targets.size() == 0)

					for (Candidate candidate : sameNeuron)
						assignment.add(new OneToOneAssignment(candidate, SequenceSearch.deathNode));

				else
					IJ.log("Invalid assignment in groundtruth: " + sameNeuron.size() + " areas map to " + targets.size() + " areas");
			}

			// all remaining candidates must have appeared
			for (Candidate candidate : remainders)
				assignment.add(new OneToOneAssignment(SequenceSearch.emergeNode, candidate));

			// store this assignment
			groundtruth.push(new SequenceNode(assignment));

			prevCandidates = candidates;
		}
	}

	/**
	 * Find the closest sequence to the given MSERs with respect to the ground
	 * truth sequence. If region information is available in the ground truth,
	 * it is considered as well.
	 */
	public Sequence closestToMsers(Vector<Vector<Candidate>> msers) {

		// use closest centers, if region information is not available

		return null;
	}

	/**
	 * Get the number of topological errors of compareSequence in terms of number of splits.
	 */
	public int numSplits(Sequence compareSequence) {

		return 0;
	}

	/**
	 * Get the number of topological errors of compareSequence in term of number of merges.
	 */
	public int numMerges(Sequence compareSequence) {

		return 0;
	}

	public Sequence getSequence() {

		return groundtruth;
	}

	private <T extends RealType<T>> Vector<Candidate> componentsToCandidates(Vector<ConnectedComponents<T>.ConnectedComponent> components) {

		Vector<Candidate> candidates = new Vector<Candidate>();

		for (ConnectedComponents<T>.ConnectedComponent comp : components) {

			double[] center = new double[]{0.0, 0.0};
			Vector<int[]> pixels = comp.getPixels();

			for (int[] pixel : pixels) {
				center[0] += pixel[0];
				center[1] += pixel[1];
			}
			center[0] /= pixels.size();
			center[1] /= pixels.size();

			candidates.add(new Candidate(
					pixels.size(),
					0, // perimeter - not used
					center,
					pixels,
					comp.value));
		}

		return candidates;
	}
}
