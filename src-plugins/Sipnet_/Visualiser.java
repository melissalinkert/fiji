
import java.util.HashMap;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.Duplicator;

public class Visualiser {

	public void drawSequence(ImagePlus blockImage, Sequence sequence, boolean drawConfidence) {

		// visualize result
		ImagePlus blockCopy = (new Duplicator()).run(blockImage);
		blockCopy.show();

		IJ.setForegroundColor(255, 255, 255);
		IJ.selectWindow(blockCopy.getTitle());

		int slice = sequence.size();

		HashMap<Candidate, Integer> ids = new HashMap<Candidate, Integer>();

		for (SequenceNode node: sequence) {

			Assignment assignment = node.getAssignment();

			if (slice == sequence.size()) {

				int id = 1;
				for (SingleAssignment singleAssignment : assignment) {

					ids.put(singleAssignment.getTarget(), id);

					Candidate target = singleAssignment.getTarget();

					int x = (int)target.getCenter()[0];
					int y = (int)target.getCenter()[1];
					double confidence = singleAssignment.getNegLogP();

					drawCandidate(x, y, slice + 1, id, confidence, drawConfidence);
					id++;
				}
			}

			for (SingleAssignment singleAssignment : assignment) {

				Candidate source = singleAssignment.getSource();
				Candidate target = singleAssignment.getTarget();

				int id = ids.get(target);
				ids.put(source, id);
				int x = (int)source.getCenter()[0];
				int y = (int)source.getCenter()[1];
				double confidence = singleAssignment.getNegLogP();

				drawCandidate(x, y, slice, id, confidence, drawConfidence);
			}
			slice--;
		}

		blockCopy.updateAndDraw();
	}

	private void drawCandidate(int x, int y, int slice, int id, double confidence, boolean drawConfidence) {

		String annotation = "" + id;
		if (drawConfidence)
			annotation += " (" + confidence + ")";
		IJ.setSlice(slice);
		IJ.runMacro("drawString(\"" + annotation + "\", " + x + ", " + y + ")");
	}

}
