package sipnet;

import java.util.Comparator;

public class PixelComparator implements Comparator<int[]> {

	private int[] offset1;

	public PixelComparator() {

		this.offset1 = new int[]{0, 0};
	}

	public PixelComparator(int[] offset1, int[] offset2) {

		this.offset1 = offset1;
		this.offset1[0] -= offset2[0];
		this.offset1[1] -= offset2[1];
	}

	public int compare(int[] pixel1, int[] pixel2) {

		final int x1 = pixel1[0] - offset1[0];
		final int y1 = pixel1[1] - offset1[1];
		final int x2 = pixel2[0];
		final int y2 = pixel2[1];

		if (y1 < y2)
			return -1;
		else if (y1 > y2)
			return 1;
		else if (x1 < x2)
			return -1;
		else if (x1 > x2)
			return 1;
		else
			return 0;
	}
}

