package sipnet;

import java.util.HashMap;
import java.util.Vector;

import mpicbg.imglib.cursor.LocalizableByDimCursor;

import mpicbg.imglib.image.Image;

import mpicbg.imglib.type.numeric.RealType;

public class ConnectedComponents<T extends RealType<T>> {

	class ConnectedComponent {

		ConnectedComponent() {
			this.pixels = new Vector<int[]>();
		}

		public Vector<int[]> pixels;
		public double        value;
	}

	public Vector<ConnectedComponent> find(Image<T> image) {

		HashMap<Integer, Vector<ConnectedComponent>> components =
				new HashMap<Integer, Vector<ConnectedComponent>>();

		LocalizableByDimCursor<T> cursor = image.createLocalizableByDimCursor();

		int[] position = new int[2];

		while (cursor.hasNext()) {

			cursor.fwd();
			cursor.getPosition(position);

			int[] leftNeighbor = new int[]{position[0] - 1, position[1]};
			int[] topNeighbor  = new int[]{position[0], position[1] - 1};

			int currentValue   = (int)cursor.getType().getRealFloat();

			//int leftValue = -1;
			//if (leftNeighbor[0] >= 0 && leftNeighbor[0] < image.getDimension(0) &&
				//leftNeighbor[1] >= 1 && leftNeighbor[1] < image.getDimension(1)) {
				//cursor.setPosition(leftNeighbor);
				//leftValue = (int)cursor.getType().getRealFloat();
			//}

			//int topValue = -1;
			//if (topNeighbor[0] >= 0 && topNeighbor[0] < image.getDimension(0) &&
				//topNeighbor[1] >= 1 && topNeighbor[1] < image.getDimension(1)) {
				//cursor.setPosition(topNeighbor);
				//topValue = (int)cursor.getType().getRealFloat();
			//}

			if (components.get(currentValue) == null)
				components.put(currentValue, new Vector<ConnectedComponent>());

			ConnectedComponent leftComp = null;
			ConnectedComponent topComp  = null;
			for (ConnectedComponent comp : components.get(currentValue)) {

				if (comp.pixels.contains(leftNeighbor))
					leftComp = comp;
				
				if (comp.pixels.contains(topNeighbor))
					topComp = comp;
			}

			if (leftComp == null && topComp == null) {

				// new component
				ConnectedComponent comp = new ConnectedComponent();
				comp.pixels.add(new int[]{position[0], position[1]});
				comp.value = currentValue;

				components.get(currentValue).add(comp);

			} else if (leftComp != null && topComp == null) {

				leftComp.pixels.add(new int[]{position[0], position[1]});

			} else if (leftComp == null && topComp != null) {

				topComp.pixels.add(new int[]{position[0], position[1]});

			} else {

				leftComp.pixels.add(new int[]{position[0], position[1]});

				// merge both components
				leftComp.pixels.addAll(topComp.pixels);
				components.get(currentValue).remove(topComp);
			}

		}

		Vector<ConnectedComponent> result = new Vector<ConnectedComponent>();
		for (int value : components.keySet())
			result.addAll(components.get(value));

		return result;
	}
}
