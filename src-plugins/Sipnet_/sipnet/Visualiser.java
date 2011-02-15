package sipnet;

import java.awt.Color;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.Duplicator;

import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

public class Visualiser {

	private class VColor {

		Color color;

		public VColor(int r, int g, int b) {
			color = new Color(r, g, b);
		}
	}

	public void drawSequence(ImagePlus blockImage, Sequence sequence, boolean drawConfidence, boolean drawCandidateId) {

		// visualize result
		ImagePlus blockCopy = (new Duplicator()).run(blockImage);

		blockCopy.show();
		IJ.selectWindow(blockCopy.getTitle());
		IJ.run("RGB Color", "");

		HashMap<Candidate, VColor> candidateColors = new HashMap<Candidate, VColor>();

		/*
		 * ASSIGN COLORS
		 */
		int slice = sequence.size();
		for (SequenceNode sequenceNode : sequence) {

			for (SingleAssignment singleAssignment : sequenceNode.getAssignment()) {

				// associate random colors to last candidates (if not done
				// already)
				if (slice == sequence.size()) {

					int r = (int)(Math.random()*255.0);
					int g = (int)(Math.random()*255.0);
					int b = (int)(Math.random()*255.0);
					VColor color = new VColor(r, g, b);

					for (Candidate target : singleAssignment.getTargets())
						if (target != SequenceSearch.deathNode)
							candidateColors.put(target, color);
				}

				for (Candidate source : singleAssignment.getSources())

					if (source != SequenceSearch.emergeNode) {

						// see, if there was a color for this source already
						VColor color = candidateColors.get(source);

						// no - take color of first target
						if (color == null)
							color = candidateColors.get(singleAssignment.getTargets().get(0));
						// yes - force all targets to take this color
						else {
							for (Candidate target : singleAssignment.getTargets())
								candidateColors.get(target).color = color.color;
							break;
						}

						// if target was not assigned as well, this source died -
						// give it a new color
						if (color == null) {

							int r = (int)(Math.random()*255.0);
							int g = (int)(Math.random()*255.0);
							int b = (int)(Math.random()*255.0);

							color = new VColor(r, g, b);
						}

						// store new color for all sources now
						for (Candidate otherSources : singleAssignment.getSources())
							candidateColors.put(otherSources, color);
					}
			}
			slice--;
		}

		/*
		 * DRAW CANDIDATES
		 */
		slice = sequence.size();
		for (SequenceNode sequenceNode : sequence) {

			// previous slice
			ImageProcessor pip = blockCopy.getStack().getProcessor(slice);

			// next slice
			ImageProcessor nip = blockCopy.getStack().getProcessor(slice + 1);

			for (SingleAssignment singleAssignment : sequenceNode.getAssignment()) {

				// last assignment
				if (slice == sequence.size()) {

					for (Candidate target : singleAssignment.getTargets()) {

						if (target != SequenceSearch.deathNode) {

							VColor color = candidateColors.get(target);
							drawCandidate(target, nip, color.color, (drawCandidateId ? "" + target.getId() : null));
						}
					}
				}

				for (Candidate source : singleAssignment.getSources()) {

					if (source != SequenceSearch.emergeNode) {

						VColor color = candidateColors.get(source);
						drawCandidate(source, pip, color.color, (drawCandidateId ? "" + source.getId() : null));
					}
				}
			}
			slice--;
		}

		/*
		 * DRAW CONNECTIONS
		 */
		slice = sequence.size();
		for (SequenceNode sequenceNode : sequence) {

			// previous slice
			ImageProcessor pip = blockCopy.getStack().getProcessor(slice);

			// next slice
			ImageProcessor nip = blockCopy.getStack().getProcessor(slice + 1);

			for (SingleAssignment singleAssignment : sequenceNode.getAssignment()) {

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
									singleAssignment.getCosts());
							drawConnectionFrom(
									(int)source.getCenter(0), (int)source.getCenter(1),
									(int)target.getCenter(0), (int)target.getCenter(1),
									nip,
									singleAssignment.getCosts());
						}
					}
				}
			}
			slice--;
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
