
import java.util.HashMap;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.Duplicator;

public class Visualiser {

	private final double MaxConfidence = 100;

	public void drawSequence(ImagePlus blockImage, Sequence sequence, boolean drawConfidence) {

		// visualize result
		ImagePlus blockCopy = (new Duplicator()).run(blockImage);
		blockCopy.show();

		IJ.setForegroundColor(255, 255, 255);
		IJ.selectWindow(blockCopy.getTitle());
		IJ.run("RGB Color", "");

		int nextId = 1;
		int slice  = sequence.size();

		HashMap<Candidate, Integer>  ids             = new HashMap<Candidate, Integer>();
		HashMap<Candidate, double[]> previousCenters = new HashMap<Candidate, double[]>();

		for (SequenceNode node: sequence) {

			Assignment assignment = node.getAssignment();

			if (slice == sequence.size()) {

				for (SingleAssignment singleAssignment : assignment) {

					Candidate target = singleAssignment.getTarget();

					ids.put(target, nextId);
					previousCenters.put(target, target.getCenter());

					int x = (int)target.getCenter()[0];
					int y = (int)target.getCenter()[1];

					drawCandidate(x, y, slice + 1, nextId);
					nextId++;
				}
			}

			for (SingleAssignment singleAssignment : assignment) {

				Candidate source = singleAssignment.getSource();
				Candidate target = singleAssignment.getTarget();

				// new neuron
				if (ids.get(target) == null) {
					ids.put(target, nextId);
					nextId++;
				}

				int id = ids.get(target);
				double[] previousCenter = previousCenters.get(target);
				ids.put(source, id);
				previousCenters.put(source, source.getCenter());

				if (previousCenter == null)
					continue;

				int px = (int)previousCenter[0];
				int py = (int)previousCenter[1];
				int x  = (int)source.getCenter()[0];
				int y  = (int)source.getCenter()[1];
				double confidence = singleAssignment.getNegLogP();

				drawCandidate(x, y, slice, id);
				drawConnection(px, py, x, y, slice, confidence);
			}
			slice--;
		}

		blockCopy.updateAndDraw();
	}

	private void drawCandidate(int x, int y, int slice, int id) {

		String annotation = "" + id;
		IJ.setSlice(slice);
		IJ.setForegroundColor(0, 0, 0);
		IJ.runMacro("drawString(\"" + annotation + "\", " + x + ", " + y + ")");
	}

	private void drawConnection(int x1, int y1, int x2, int y2, int slice, double confidence) {

		int red   = (int)(Math.min(confidence/MaxConfidence, 1.0)*255);
		int green = 255 - red;

		IJ.setSlice(slice);
		IJ.setForegroundColor(red, green, 0);
		IJ.makeLine(x1, y1, x2, y2);
		IJ.run("Draw", "slice");
		IJ.run("Select None", "");
	}
}
