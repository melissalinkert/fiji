package sipnet;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

import mpicbg.imglib.image.Image;

import mpicbg.imglib.type.numeric.RealType;

public class GroundTruth {

	private static int MaxDistance = 200;

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

				// find splits/merges
				while (true) {

					double bestSplitValue = -1;
					double bestMergeValue = -1;
					Candidate bestSplitSource  = null;
					Candidate bestSplitTarget1 = null;
					Candidate bestSplitTarget2 = null;
					Candidate bestMergeSource  = null;
					Candidate bestMergeTarget1 = null;
					Candidate bestMergeTarget2 = null;

					// splits
					for (Candidate source : sameNeuron)
						for (Candidate target1 : targets)
							if (overlap(source, target1))
								for (Candidate target2 : targets)
									if (target1 != target2 && overlap(source, target2)) {

										double splitValue =
												source.distanceTo(target1) +
												source.distanceTo(target2);

										if (splitValue < bestSplitValue || bestSplitValue < 0) {

											bestSplitValue   = splitValue;
											bestSplitSource  = source;
											bestSplitTarget1 = target1;
											bestSplitTarget2 = target2;
										}
									}


					// merges
					for (Candidate source : targets)
						for (Candidate target1 : sameNeuron)
							if (overlap(source, target1))
								for (Candidate target2 : sameNeuron)
									if (target1 != target2 && overlap(source, target2)) {

										double mergeValue =
												source.distanceTo(target1) +
												source.distanceTo(target2);

										if (mergeValue < bestMergeValue || bestMergeValue < 0) {

											bestMergeValue   = mergeValue;
											bestMergeSource  = source;
											bestMergeTarget1 = target1;
											bestMergeTarget2 = target2;
										}
									}
					// no more splits/merges?
					if (bestSplitValue < 0 && bestMergeValue < 0)
						break;

					if (bestMergeValue < 0 || (bestMergeValue >= 0 && bestSplitValue >= 0 && bestSplitValue < bestMergeValue)) {

						assignment.add(new SplitAssignment(bestSplitSource, bestSplitTarget1, bestSplitTarget2));
						sameNeuron.remove(bestSplitSource);
						targets.remove(bestSplitTarget1);
						targets.remove(bestSplitTarget2);

					} else {

						assignment.add(new MergeAssignment(bestMergeTarget1, bestMergeTarget2, bestMergeSource));
						sameNeuron.remove(bestMergeTarget1);
						sameNeuron.remove(bestMergeTarget2);
						targets.remove(bestMergeSource);
					}
				}

				// assign close candidates as continuations
				while (true) {

					double    bestDistance = -1;
					Candidate bestSource   = null;
					Candidate bestTarget   = null;

					for (Candidate source : sameNeuron) {
						for (Candidate target : targets) {

							double distance =
									source.distanceTo(target);

							if ((distance < bestDistance || bestDistance < 0) && distance < MaxDistance) {

								bestDistance = distance;
								bestSource   = source;
								bestTarget   = target;
							}
						}
					}

					// no more good continuations?
					if (bestSource == null)
						break;

					assignment.add(new OneToOneAssignment(bestSource, bestTarget));
					sameNeuron.remove(bestSource);
					targets.remove(bestTarget);
				}

				// treat remainders as death/split
				for (Candidate dying : sameNeuron)
					assignment.add(new OneToOneAssignment(dying, SequenceSearch.deathNode));
				for (Candidate emerging : targets)
					assignment.add(new OneToOneAssignment(SequenceSearch.emergeNode, emerging));

			} // for each set of source candidates with the same label

			// all remaining candidates must have appeared
			for (Candidate candidate : remainders)
				assignment.add(new OneToOneAssignment(SequenceSearch.emergeNode, candidate));

			// store this assignment
			groundtruth.add(assignment);

			prevCandidates = candidates;
		}
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

	private boolean overlap(Candidate c1, Candidate c2) {

		int numMatches =
				(new SetDifference()).numMatches(c1.getPixels(), new int[]{0, 0}, c2.getPixels(), new int[]{0, 0});

		return numMatches > 0;
	}
}
