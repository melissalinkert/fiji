
import java.util.ArrayList;
import java.util.List;

import javax.media.j3d.Transform3D;

import javax.vecmath.Color3f;
import javax.vecmath.Point3f;
import javax.vecmath.Vector3d;

import ij.IJ;
import ij.ImagePlus;

import ij3d.Content;
import ij3d.Image3DUniverse;

public class Visualiser3D {

	private static final double sliceDistance = 100.0;

	private Image3DUniverse universe;

	public Visualiser3D() {

		universe = new Image3DUniverse();
	}

	public void showSlices(ImagePlus slices) {

		int numSlices = slices.getStack().getSize();

		int resample  = 1;

		if (numSlices > 10)
			resample *= 2;
		if (numSlices > 20)
			resample *= 2;

		// display slices
		for (int s = 0; s < numSlices; s++) {

			ImagePlus sliceImp = new ImagePlus("slice " + s+1, slices.getStack().getProcessor(s+1));
			Content slice = universe.addVoltex(sliceImp, new Color3f(255, 255, 255), "slice-"+s, 0, new boolean[]{true, true, true}, 1);

			Transform3D translate = new Transform3D();
			translate.setTranslation(new Vector3d(0.0, 0.0, s*sliceDistance));

			slice.applyTransform(translate);
			slice.setLocked(true);
		}

	}

	public void showAssignments(Sequence sequence) {

		// connect regions
		int s = sequence.size() - 1;
		// from back to front (sequence is a stack)
		for (SequenceNode node : sequence) {

			Assignment assignment = node.getAssignment();

			for (SingleAssignment singleAssignment : assignment) {

				if (singleAssignment.getSource() == SequenceSearch.emergeNode ||
				    singleAssignment.getTarget() == SequenceSearch.deathNode)
					continue;

				double[] from = singleAssignment.getSource().getCenter();
				double[] to   = singleAssignment.getTarget().getCenter();

				int id1 = singleAssignment.getSource().getId();
				int id2 = singleAssignment.getTarget().getId();

				List<Point3f> line = new ArrayList<Point3f>(2);
				line.add(new Point3f((float)from[0], (float)from[1], (float)(s*sliceDistance)));
				line.add(new Point3f((float)to[0],   (float)to[1],   (float)((s+1)*sliceDistance)));

				universe.addLineMesh(line, new Color3f(0, 255, 0), "sa-" + s + "-" + id1 + "-" + id2, true);
			}
			s--;
		}

		IJ.log("Opening 3D viewer to display result...");
		universe.show();
	}
}
