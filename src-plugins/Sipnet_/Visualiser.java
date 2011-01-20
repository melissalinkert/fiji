
import java.awt.Color;

import ij.IJ;
import ij.ImagePlus;

import ij.plugin.Duplicator;

import ij.process.ImageProcessor;

public class Visualiser {

	public void drawSequence(ImagePlus blockImage, Sequence sequence, boolean drawConfidence) {

		// visualize result
		ImagePlus blockCopy = (new Duplicator()).run(blockImage);
		blockCopy.show();

		IJ.setForegroundColor(255, 255, 255);
		IJ.selectWindow(blockCopy.getTitle());
		IJ.run("RGB Color", "");

		int slice = sequence.size();
		for (SequenceNode sequenceNode : sequence) {

			// previous slice
			ImageProcessor pip = blockCopy.getStack().getProcessor(slice);

			// next slice
			ImageProcessor nip = blockCopy.getStack().getProcessor(slice + 1);

			for (SingleAssignment singleAssignment : sequenceNode.getAssignment()) {

				Candidate source = singleAssignment.getSource();
				Candidate target = singleAssignment.getTarget();

				if (source == SequenceSearch.emergeNode)

					drawEmerge((int)target.getCenter()[0], (int)target.getCenter()[1], nip);

				else if (target == SequenceSearch.deathNode)

					drawDeath((int)source.getCenter()[0], (int)source.getCenter()[1], pip);

				else {

					drawConnectionTo(
							(int)source.getCenter()[0], (int)source.getCenter()[1],
							(int)target.getCenter()[0], (int)target.getCenter()[1],
							pip,
							singleAssignment.getNegLogP());
					drawConnectionFrom(
							(int)source.getCenter()[0], (int)source.getCenter()[1],
							(int)target.getCenter()[0], (int)target.getCenter()[1],
							nip,
							singleAssignment.getNegLogP());
				}
			}
			slice--;
		}

		blockCopy.updateAndDraw();
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

		ip.setColor(new Color(100, 255, 100));
		ip.drawLine((x1 + x2)/2, (y1 + y2)/2, x2, y2);
	}

	private void drawEmerge(int x, int y, ImageProcessor ip) {

		ip.setColor(new Color(255, 255, 0));
		ip.drawOval(x-2, y-2, 5, 5);
	}

	private void drawDeath(int x, int y, ImageProcessor ip) {

		ip.setColor(new Color(255, 0, 0));
		ip.drawOval(x-2, y-2, 5, 5);
	}
}
