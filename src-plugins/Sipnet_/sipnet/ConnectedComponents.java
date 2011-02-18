package sipnet;

import java.util.HashMap;
import java.util.Vector;

import ij.IJ;

import mpicbg.imglib.cursor.LocalizableByDimCursor;

import mpicbg.imglib.image.Image;

import mpicbg.imglib.type.numeric.RealType;

public class ConnectedComponents<T extends RealType<T>> {

	class ConnectedComponent {

		ConnectedComponent(int[] imageDimensions) {
			this.pixels = new boolean[imageDimensions[0]][imageDimensions[1]];
		}

		public boolean contains(int[] position) {

			try {
				return pixels[position[0]][position[1]];
			} catch (ArrayIndexOutOfBoundsException e) {
				return false;
			}
		}

		public void addPixel(int[] position) {
			pixels[position[0]][position[1]] = true;
		}

		public void merge(ConnectedComponent other) {

			for (int x = 0; x < pixels.length; x++)
				for (int y = 0; y < pixels[x].length; y++)
					pixels[x][y] = pixels[x][y] || other.pixels[x][y];
		}

		public Vector<int[]> getPixels() {

			Vector<int[]> result = new Vector<int[]>();

			for (int x = 0; x < pixels.length; x++)
				for (int y = 0; y < pixels[x].length; y++)
					if (pixels[x][y])
						result.add(new int[]{x, y});
			
			return result;
		}

		public  double      value;
		private boolean[][] pixels;
	}

	public Vector<ConnectedComponent> find(Image<T> image) {

		HashMap<Integer, Vector<ConnectedComponent>> components =
				new HashMap<Integer, Vector<ConnectedComponent>>();

		LocalizableByDimCursor<T> cursor = image.createLocalizableByDimCursor();

		int[] imageDimensions = image.getDimensions();
		int[] position        = new int[2];

		for (int y = 0; y < imageDimensions[1]; y++) {
			for (int x = 0; x < imageDimensions[0]; x++) {

				position[0] = x;
				position[1] = y;

				cursor.setPosition(position);

				int[] topRightNeighbor = new int[]{position[0] + 1, position[1] - 1};
				int[] topNeighbor      = new int[]{position[0],     position[1] - 1};
				int[] topLeftNeighbor  = new int[]{position[0] - 1, position[1] - 1};
				int[] leftNeighbor     = new int[]{position[0] - 1, position[1]};

				int currentValue   = (int)cursor.getType().getRealFloat();

				if (currentValue == 0)
					continue;

				if (components.get(currentValue) == null)
					components.put(currentValue, new Vector<ConnectedComponent>());

				Vector<ConnectedComponent> neighbors = new Vector<ConnectedComponent>();
				neighbors.setSize(4);
				int numNotNull = 0;

				for (ConnectedComponent comp : components.get(currentValue)) {

					if (comp.contains(topRightNeighbor)) {
						neighbors.set(0, comp);
						numNotNull++;
					}
					
					if (comp.contains(topNeighbor)) {
						neighbors.set(1,  comp);
						numNotNull++;
					}

					if (comp.contains(topLeftNeighbor)) {
						neighbors.set(2,  comp);
						numNotNull++;
					}

					if (comp.contains(leftNeighbor)) {
						neighbors.set(3,  comp);
						numNotNull++;
					}
				}

				if (numNotNull == 0) {

					// new component
					ConnectedComponent comp = new ConnectedComponent(imageDimensions);
					comp.addPixel(new int[]{position[0], position[1]});
					comp.value = currentValue;

					components.get(currentValue).add(comp);

				} else if (numNotNull == 1) {

					for (ConnectedComponent neighbor : neighbors)
						if (neighbor != null)
							neighbor.addPixel(new int[]{position[0], position[1]});

				} else {

					// there should only be two different components
					ConnectedComponent first  = null;
					ConnectedComponent second = null;

					for (ConnectedComponent neighbor : neighbors)
						if (neighbor != null)
							if (first == null)
								first = neighbor;
							else if (second == null)
								second = neighbor;
							else if (neighbor != first && neighbor != second)
								System.out.println("MORE THAN TWO DIFFERENT COMPONENTS OF SAME VALUE ARE NEIGHBORS!");

					first.addPixel(new int[]{position[0], position[1]});

					// merge both components
					if (first != second) {
						first.merge(second);
						components.get(currentValue).remove(second);
					}
				}
			}
		}

		Vector<ConnectedComponent> result = new Vector<ConnectedComponent>();
		for (int value : components.keySet()) {

			if (components.get(value).size() > 2)
				IJ.log("" + components.get(value).size() + " components for " + value);
			
			result.addAll(components.get(value));
		}

		return result;
	}
}
