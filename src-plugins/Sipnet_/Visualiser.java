
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

		int slice = sequence.size();
		for (SequenceNode sequenceNode : sequence) {
			for (SingleAssignment singleAssignment : sequenceNode.getAssignment()) {

				Candidate source = singleAssignment.getSource();
				Candidate target = singleAssignment.getTarget();

				if (source == AssignmentSearch.emergeNode)
					drawEmerge((int)target.getCenter()[0], (int)target.getCenter()[1],
							   slice+1);
				else if (target == AssignmentSearch.deathNode)
					drawDeath((int)target.getCenter()[0], (int)target.getCenter()[1],
							  slice);
				else
					drawConnection((int)source.getCenter()[0], (int)source.getCenter()[1],
					               (int)target.getCenter()[0], (int)target.getCenter()[1],
					               slice,
					               singleAssignment.getNegLogP());
			}
			slice--;
		}

		blockCopy.updateAndDraw();
	}

	//private void drawCandidate(int x, int y, int slice, int id) {

		//String annotation = "" + id;
		//IJ.setSlice(slice);
		//IJ.setForegroundColor(0, 0, 0);
		//IJ.runMacro("drawString(\"" + annotation + "\", " + x + ", " + y + ")");
	//}

	private void drawConnection(int x1, int y1, int x2, int y2, int slice, double confidence) {

		int red   = (int)(Math.min(confidence/MaxConfidence, 1.0)*255);
		int green = 255 - red;

		IJ.setSlice(slice);
		IJ.setForegroundColor(red, green, 0);
		IJ.makeLine(x1, y1, x2, y2);
		IJ.run("Draw", "slice");
		IJ.run("Select None", "");
	}

	private void drawEmerge(int x, int y, int slice) {

		IJ.setSlice(slice);
		IJ.setForegroundColor(255, 255, 0);
		IJ.makeOval(x, y, 5, 5);
		IJ.run("Draw", "slice");
		IJ.run("Select None", "");
	}

	private void drawDeath(int x, int y, int slice) {

		IJ.setSlice(slice);
		IJ.setForegroundColor(255, 0, 0);
		IJ.makeOval(x, y, 5, 5);
		IJ.run("Draw", "slice");
		IJ.run("Select None", "");
	}
}
