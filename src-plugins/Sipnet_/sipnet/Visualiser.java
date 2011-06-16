package sipnet;

import java.awt.Color;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import ij.plugin.Duplicator;

import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

public class Visualiser {

	private AssignmentModel assignmentModel;

	public Visualiser(AssignmentModel assignmentModel) {

		this.assignmentModel = assignmentModel;
	}

	public void drawSequence(
			ImagePlus blockImage,
			String name,
			Sequence sequence,
			boolean drawConfidence,
			boolean drawCandidateId,
			boolean drawLines,
			boolean idMap,
			double opacity) {

		// visualize result
		ImagePlus blockCopy;
		
		if (!idMap) {
		
			blockCopy = (new Duplicator()).run(blockImage);

			blockCopy.show();
			blockCopy.setTitle(name);
			IJ.run("RGB Color", "");
		} else {

			int width  = blockImage.getWidth();
			int height = blockImage.getHeight();

			ImageStack stack = new ImageStack(width, height);

			for (int s = 0; s <= sequence.size(); s++) {

				ImageProcessor ip = new ShortProcessor(width, height);
				stack.addSlice("", ip);
			}

			blockCopy = new ImagePlus(name, stack);
			blockCopy.show();
		}

		Vector<Set<Candidate>>      treelines   = new Vector<Set<Candidate>>();
		HashMap<Candidate, Color>   colors      = new HashMap<Candidate, Color>();
		HashMap<Candidate, Integer> treelineIds = new HashMap<Candidate, Integer>();

		/*
		 * ASSIGN COLORS
		 */
		for (Assignment assignment : sequence) {
			for (SingleAssignment singleAssignment : assignment) {

				// all sources and all targets are in the same treeline

				// find all (or create) treelines for sources
				Vector<Set<Candidate>> sourceTreelines = new Vector<Set<Candidate>>();
				for (Candidate source : singleAssignment.getSources()) {

					boolean found = false;

					if (source != SequenceSearch.emergeNode)
						for (Set<Candidate> treeline : treelines)
							if (treeline.contains(source)) {
								found = true;
								sourceTreelines.add(treeline);
							}

					if (!found) {

						Set<Candidate> treeline = new HashSet<Candidate>();
						treeline.add(source);
						treelines.add(treeline);
						sourceTreelines.add(treeline);
					}
				}

				// merge them
				Set<Candidate> newTreeline = new HashSet<Candidate>();
				for (Set<Candidate> sourceTreeline : sourceTreelines)
					newTreeline.addAll(sourceTreeline);

				// add all targets to them
				for (Candidate target : singleAssignment.getTargets())
					if (target != SequenceSearch.deathNode)
						newTreeline.add(target);

				// remove old treelines
				for (Set<Candidate> oldTreeline : sourceTreelines)
					treelines.remove(oldTreeline);

				// add new treeline
				treelines.add(newTreeline);
			}
		}

		// assign random colors and ids to the treelines
		int id = 1;
		for (Set<Candidate> treeline : treelines) {

			int r = (int)(Math.random()*255.0);
			int g = (int)(Math.random()*255.0);
			int b = (int)(Math.random()*255.0);
			Color color = new Color(r, g, b);

			for (Candidate candidate : treeline) {
				colors.put(candidate, color);
				treelineIds.put(candidate, id);
			}

			id++;
		}

		/*
		 * DRAW CANDIDATES
		 */
		int slice = 1;
		for (Assignment assignment : sequence) {

			// previous slice
			ImageProcessor pip = blockCopy.getStack().getProcessor(slice);

			// next slice
			ImageProcessor nip = blockCopy.getStack().getProcessor(slice + 1);

			for (SingleAssignment singleAssignment : assignment) {

				// last assignment
				if (slice == sequence.size()) {

					for (Candidate target : singleAssignment.getTargets()) {

						if (target != SequenceSearch.deathNode) {

							Color color = colors.get(target);
							if (idMap)
								drawCandidate(target, nip, treelineIds.get(target));
							else
								drawCandidate(target, nip, color, (drawCandidateId ? "" + target.getId() : null), opacity);
						}
					}
				}

				for (Candidate source : singleAssignment.getSources()) {

					if (source != SequenceSearch.emergeNode) {

						Color color = colors.get(source);
						if (idMap)
							drawCandidate(source, pip, treelineIds.get(source));
						else
							drawCandidate(source, pip, color, (drawCandidateId ? "" + source.getId() : null), opacity);
					}
				}
			}
			slice++;
		}

		/*
		 * DRAW CONNECTIONS
		 */
		if (drawLines) {
			slice = 1;
			for (Assignment assignment : sequence) {

				// previous slice
				ImageProcessor pip = blockCopy.getStack().getProcessor(slice);

				// next slice
				ImageProcessor nip = blockCopy.getStack().getProcessor(slice + 1);

				for (SingleAssignment singleAssignment : assignment) {

					for (Candidate source : singleAssignment.getSources()) {
						for (Candidate target : singleAssignment.getTargets()) {

							if (source == SequenceSearch.emergeNode) {

								drawEmerge((int)target.getCenter(0), (int)target.getCenter(1), nip);

							} else if (target == SequenceSearch.deathNode) {

								drawDeath((int)source.getCenter(0), (int)source.getCenter(1), pip);

							} else {

								drawConnectionTo(
										(int)source.getCenter(0), (int)source.getCenter(1),
										(int)target.getCenter(0), (int)target.getCenter(1),
										pip,
										singleAssignment.getCosts(assignmentModel));
								drawConnectionFrom(
										(int)source.getCenter(0), (int)source.getCenter(1),
										(int)target.getCenter(0), (int)target.getCenter(1),
										nip,
										singleAssignment.getCosts(assignmentModel));
							}
						}
					}
				}
				slice++;
			}
		}

		blockCopy.updateAndDraw();
	}

	public void drawMostLikelyCandidates(ImagePlus blockImage, List<Vector<Candidate>> sliceCandidates, String outputDirectory) {

		int width  = blockImage.getWidth();
		int height = blockImage.getHeight();

		int i = 0;
		int s = 1;

		for (Vector<Candidate> candidates : sliceCandidates) {

			for (Candidate candidate : candidates) {

				if (candidate.getMostLikelyCandidates().size() == 0) {
					IJ.log("candidate " + candidate.getId() + " does not have likely candidates");
					continue;
				}

				ImageProcessor candidateIp = new ColorProcessor(width, height);

				drawCandidate(candidate, candidateIp, new Color(255, 255, 255), null, 0.0);

				ImagePlus candidateImp = new ImagePlus("candidate", candidateIp);
				IJ.save(candidateImp, outputDirectory + "/s" + s + "c" + candidate.getId() + ".tif");

				ImageProcessor similarIp = new ColorProcessor(width, height);

				double minSimilarity = 0;
				double maxSimilarity = 0;
				for (Candidate similar : candidate.getMostLikelyCandidates()) {
					if (candidate.getNegLogPAppearance(similar) < minSimilarity ||
					    minSimilarity == 0)
						minSimilarity = candidate.getNegLogPAppearance(similar);
					if (candidate.getNegLogPAppearance(similar) > maxSimilarity ||
					    maxSimilarity == 0)
						maxSimilarity = candidate.getNegLogPAppearance(similar);
				}

				Vector<Candidate> sortedBySize = new Vector<Candidate>();
				sortedBySize.addAll(candidate.getMostLikelyCandidates());

				class SizeComparator implements Comparator<Candidate> {
					public int compare(Candidate c1, Candidate c2) {
						if (c1.getSize() > c2.getSize())
							return -1;
						if (c1.getSize() < c2.getSize())
							return 1;
						return 0;
					}
				}
				Collections.sort(sortedBySize, new SizeComparator());

				for (Candidate similar : sortedBySize) {

					double similarity = candidate.getNegLogPAppearance(similar);

					int red   = (int)(255.0*(similarity - minSimilarity)/(maxSimilarity - minSimilarity));
					int green = (int)(255.0*(maxSimilarity - similarity)/(maxSimilarity - minSimilarity));

					drawCandidate(similar, similarIp, new Color(red, green, 0), "" + similarity, 0.0);
				}

				i++;

				// create imageplus and store as tif
				ImagePlus shapeImp = new ImagePlus("most similar shapes", similarIp);
				IJ.save(shapeImp, outputDirectory + "/s" + s + "c" + candidate.getId() + "-candidates.tif");
			}

			s++;
		}
	}

	public void drawCorrespondences(
			ImagePlus blockImage,
			String name,
			List<Vector<Candidate>> leftSliceCandidates,
			List<Vector<Candidate>> rightSliceCandidates,
			HashMap<Candidate,Vector<Candidate>> correspondences,
			boolean oneToOne) {

		int width  = blockImage.getWidth();
		int height = blockImage.getHeight();

		ImageStack stack = new ImageStack(width, height);

		for (int s = 0; s < leftSliceCandidates.size(); s++) {

			Vector<Candidate> leftCandidates  = leftSliceCandidates.get(s);
			Vector<Candidate> rightCandidates = rightSliceCandidates.get(s);

			ImageProcessor leftIp  = new ColorProcessor(width, height);
			ImageProcessor rightIp = new ColorProcessor(width, height);

			stack.addSlice("", leftIp);
			stack.addSlice("", rightIp);

			for (Candidate leftCandidate : leftCandidates) {

				// don't draw subregions->superregions
				if (correspondences.get(leftCandidate) != null &&
				    correspondences.get(leftCandidate).size() == 1 &&
					correspondences.get(
							correspondences.get(leftCandidate).get(0)).size() > 1)
					continue;

				int r = (int)(Math.random()*255.0);
				int g = (int)(Math.random()*255.0);
				int b = (int)(Math.random()*255.0);
				Color color = new Color(r, g, b);

				drawCandidate(leftCandidate, leftIp, color, null, 0.0);

				if (correspondences.get(leftCandidate) != null)
					for (Candidate partner : correspondences.get(leftCandidate))
						drawCandidate(partner, rightIp, color, null, 0.0);
			}

			// for one-to-one mappings, there is no need to draw right
			// candidates (they should have been drawn already in the previous
			// loop)
			if (!oneToOne) {
				for (Candidate rightCandidate : rightCandidates) {

					// don't draw subregions->superregions
					if (correspondences.get(rightCandidate) != null &&
						correspondences.get(rightCandidate).size() == 1 &&
						correspondences.get(
								correspondences.get(rightCandidate).get(0)).size() > 1)
						continue;

					int r = (int)(Math.random()*255.0);
					int g = (int)(Math.random()*255.0);
					int b = (int)(Math.random()*255.0);
					Color color = new Color(r, g, b);

					drawCandidate(rightCandidate, rightIp, color, null, 0.0);

					if (correspondences.get(rightCandidate) != null)
						for (Candidate partner : correspondences.get(rightCandidate))
							drawCandidate(partner, leftIp, color, null, 0.0);
				}
			}
		}

		ImagePlus imp = new ImagePlus("correspondences", stack);
		imp.setTitle(name);
		imp.updateAndDraw();
		imp.show();
	}
	public void drawErrors(
			ImagePlus blockImage,
			String name,
			Sequence sequence,
			GroundTruth groundtruth,
			Evaluator evaluator,
			double opacity) {

		// visualize result
		ImagePlus blockCopy = (new Duplicator()).run(blockImage);
		blockCopy.setTitle(name);

		blockCopy.show();
		IJ.run("RGB Color", "");

		// unexplained regions
		for (int s = 1; s <= sequence.size() + 1; s++) {

			ImageProcessor ip = blockCopy.getStack().getProcessor(s);

			// missed regions
			for (Candidate candidate : evaluator.getUnexplainedGroundtruth().get(s-1))
				drawCandidate(candidate, ip, new Color(255, 0, 0), "", opacity);

			// additional regions
			for (Candidate candidate : evaluator.getUnexplainedResult().get(s-1))
				drawCandidate(candidate, ip, new Color(0, 0, 255), "", opacity);
		}

		// merge and split errors
		for (int s = 0; s < sequence.size(); s++) {

			ImageProcessor ip = blockCopy.getStack().getProcessor(s+1);

			for (Candidate[] pair : evaluator.getMergeErrors().get(s)) {

				drawMergeError(
						(int)pair[0].getCenter(0), (int)pair[0].getCenter(1),
						(int)pair[1].getCenter(0), (int)pair[1].getCenter(1),
						ip);
			}

			for (Candidate[] pair : evaluator.getSplitErrors().get(s)) {

				drawSplitError(
						(int)pair[0].getCenter(0), (int)pair[0].getCenter(1),
						(int)pair[1].getCenter(0), (int)pair[1].getCenter(1),
						ip);
			}
		}

		blockCopy.updateAndDraw();
	}

	private void drawMergeError(int x1, int y1, int x2, int y2, ImageProcessor ip) {

		ip.setColor(new Color(255, 0, 0));
		ip.drawLine(x1, y1, x2, y2);
	}

	private void drawSplitError(int x1, int y1, int x2, int y2, ImageProcessor ip) {

		ip.setColor(new Color(0, 255, 0));
		ip.drawLine(x1, y1, x2, y2);
	}

	private void drawConnectionTo(int x1, int y1, int x2, int y2, ImageProcessor ip, double confidence) {

		ip.setColor(new Color(0, 255, 0));
		ip.drawLine(x1, y1, (x1 + x2)/2, (y1 + y2)/2);

		ip.setColor(new Color(100, 100, 100));
		ip.drawLine((x1 + x2)/2, (y1 + y2)/2, x2, y2);
	}

	private void drawConnectionFrom(int x1, int y1, int x2, int y2, ImageProcessor ip, double confidence) {

		ip.setColor(new Color(100, 100, 100));
		ip.drawLine(x1, y1, (x1 + x2)/2, (y1 + y2)/2);

		ip.setColor(new Color(0, 0, 255));
		ip.drawLine((x1 + x2)/2, (y1 + y2)/2, x2, y2);
	}

	private void drawEmerge(int x, int y, ImageProcessor ip) {

		ip.setColor(new Color(255, 255, 0));
		ip.drawOval(x-2, y-2, 5, 5);
	}

	private void drawDeath(int x, int y, ImageProcessor ip) {

		ip.setColor(new Color(255, 0, 0));
		ip.drawOval(x-3, y-3, 6, 6);
	}

	private void drawCandidate(Candidate candidate, ImageProcessor ip, Color color, String annotation, double opacity) {

		// draw pixels
		int[] value = new int[4];
		for (int[] pixel : candidate.getPixels()) {

			ip.getPixel(pixel[0], pixel[1], value);
			ip.setColor(
					new Color(
							(int)(opacity*value[0] + (1 - opacity)*color.getRed()),
							(int)(opacity*value[1] + (1 - opacity)*color.getGreen()),
							(int)(opacity*value[2] + (1 - opacity)*color.getBlue())));
			ip.drawPixel(pixel[0], pixel[1]);
		}

		// draw id
		if (annotation != null) {
			ip.setColor(
					new Color(
							255 - color.getRed(),
							255 - color.getGreen(),
							255 - color.getBlue()));
			ip.drawString(
					annotation,
					(int)candidate.getCenter(0),
					(int)candidate.getCenter(1));
		}
	}

	private void drawCandidate(Candidate candidate, ImageProcessor ip, int value) {

		// draw pixels
		for (int[] pixel : candidate.getPixels()) {

			ip.setColor(value);
			ip.drawPixel(pixel[0], pixel[1]);
		}
	}
}
