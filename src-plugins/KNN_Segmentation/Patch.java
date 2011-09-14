
import mpicbg.imglib.image.Image;

import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.algorithm.kdtree.node.Leaf;

public class Patch<T extends RealType<T>> implements Leaf<Patch<T>> {

	Image<T> patchImg;
	int      label;

	public Patch(Image<T> patchImg) {

		this.patchImg = patchImg;
	}

	public void setLabel(int label) {

		this.label = label;
	}

	public int getLabel() {

		return label;
	}

	@Override
	public boolean isLeaf() {
		return true;
	}

	@Override
	public Patch<T>[] createArray(int arg0) {
		return null;
	}

	@Override
	public float distanceTo(Patch<T> other) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float get(int arg0) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Patch<T> getEntry() {

		return this;
	}

	@Override
	public int getNumDimensions() {

		return patchImg.size();
	}

}
