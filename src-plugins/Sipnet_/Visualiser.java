
import java.util.HashMap;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.Duplicator;

public class Visualiser {

	public void drawSequence(ImagePlus blockImage, Sequence sequence) {

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

					IJ.setSlice(slice+1);
					IJ.runMacro("drawString(\"" + id + "\", " + x + ", " + y + ")");

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
				IJ.setSlice(slice);
				IJ.runMacro("drawString(\"" + id + "\", " + x + ", " + y + ")");
			}
			slice--;
		}

		blockCopy.updateAndDraw();
	}

}
