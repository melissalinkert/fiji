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

import ij.plugin.Duplicator;

import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

public class Visualiser {

	private AssignmentModel assignmentModel;

	public Visualiser(AssignmentModel assignmentModel) {

		this.assignmentModel = assignmentModel;
	}

	public void drawSequence(ImagePlus blockImage, Sequence sequence, boolean drawConfidence, boolean drawCandidateId) {

		// visualize result
		ImagePlus blockCopy = (new Duplicator()).run(blockImage);

		blockCopy.show();
		IJ.selectWindow(blockCopy.getTitle());
		IJ.run("RGB Color", "");

		HashMap<Candidate, Color> colors    = new HashMap<Candidate, Color>();
		Vector<Set<Candidate>>    treelines = new Vector<Set<Candidate>>();

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

		// assign random colors to the treelines
		for (Set<Candidate> treeline : treelines) {

			int r = (int)(Math.random()*255.0);
			int g = (int)(Math.random()*255.0);
			int b = (int)(Math.random()*255.0);
			Color color = new Color(r, g, b);

			for (Candidate candidate : treeline)
				colors.put(candidate, color);
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
							drawCandidate(target, nip, color, (drawCandidateId ? "" + target.getId() : null));
						}
					}
				}

				for (Candidate source : singleAssignment.getSources()) {

					if (source != SequenceSearch.emergeNode) {

						Color color = colors.get(source);
						drawCandidate(source, pip, color, (drawCandidateId ? "" + source.getId() : null));
					}
				}
			}
			slice++;
		}

		/*
		 * DRAW CONNECTIONS
		 */
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

				drawCandidate(candidate, candidateIp, new Color(255, 255, 255), null);

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

					drawCandidate(similar, similarIp, new Color(red, green, 0), "" + similarity);
				}

				i++;

				// create imageplus and store as tif
				ImagePlus shapeImp = new ImagePlus("most similar shapes", similarIp);
				IJ.save(shapeImp, outputDirectory + "/s" + s + "c" + candidate.getId() + "-candidates.tif");
			}

			s++;
		}
	}

	public void drawUnexplainedErrors(ImagePlus blockImage, Sequence sequence, GroundTruth groundtruth, Evaluator evaluator) {

		// visualize result
		ImagePlus blockCopy = (new Duplicator()).run(blockImage);

		blockCopy.show();
		blockCopy.updateAndDraw();
		blockCopy.setTitle("unexplained regions");
		IJ.selectWindow(blockCopy.getTitle());
		IJ.run("RGB Color", "");

		for (int s = 1; s <= sequence.size() + 1; s++) {

			ImageProcessor ip = blockCopy.getStack().getProcessor(s);

			for (Candidate candidate : evaluator.getUnexplainedGroundtruth().get(s-1))
				drawCandidate(candidate, ip, new Color(255, 0, 0), "");

			for (Candidate candidate : evaluator.getUnexplainedResult().get(s-1))
				drawCandidate(candidate, ip, new Color(0, 0, 255), "");
		}
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

	private void drawCandidate(Candidate candidate, ImageProcessor ip, Color color, String annotation) {

		// draw pixels
		ip.setColor(color);
		for (int[] pixel : candidate.getPixels())
			ip.drawPixel(pixel[0], pixel[1]);

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
}
