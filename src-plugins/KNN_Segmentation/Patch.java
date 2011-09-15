
import mpicbg.imglib.cursor.Cursor;
import mpicbg.imglib.cursor.LocalizableByDimCursor;

import mpicbg.imglib.image.Image;

import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.algorithm.kdtree.node.Leaf;

public class Patch<T extends RealType<T>> implements Leaf<Patch<T>> {

	Image<T>                  img;
	LocalizableByDimCursor<T> cursor;
	int                       label;

	public Patch(Image<T> img) {

		this.img = img;
		this.cursor = img.createLocalizableByDimCursor();
	}

	public void setLabel(int label) {

		this.label = label;
	}

	public int getLabel() {

		return label;
	}

	public Image<T> getImage() {

		return img;
	}

	public Cursor<T> getCursor() {

		return cursor;
	}

	@Override
	public boolean isLeaf() {
		return true;
	}

	@Override
	public Patch<T>[] createArray(int n) {

		return new Patch[n];
	}

	@Override
	public float distanceTo(Patch<T> other) {

		cursor.reset();
		other.getCursor().reset();

		float distance = 0;

		while (cursor.hasNext()) {

			cursor.fwd();
			other.getCursor().fwd();

			distance +=
					Math.pow(cursor.getType().getRealFloat() -
					other.getCursor().getType().getRealFloat(), 2);
		}

		return distance;
	}

	@Override
	public float get(int component) {

		cursor.reset();

		while (component >= 0) {

			cursor.fwd();
			component--;
		}

		return cursor.getType().getRealFloat();
	}

	@Override
	public Patch<T> getEntry() {

		return this;
	}

	@Override
	public int getNumDimensions() {

		return img.size();
	}

}
