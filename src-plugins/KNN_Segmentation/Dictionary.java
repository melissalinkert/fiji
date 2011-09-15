
import java.util.List;

import mpicbg.imglib.algorithm.kdtree.KDTree;
import mpicbg.imglib.algorithm.kdtree.NNearestNeighborSearch;

import mpicbg.imglib.type.numeric.RealType;

import script.imglib.ImgLib;

public class Dictionary<T extends RealType<T>> {

	KDTree<Patch<T>> kdTree;
	private int patchSize;
	private int numLabels;

	NNearestNeighborSearch<Patch<T>> search;

	public Dictionary(int patchSize, int numLabels) {

		this.patchSize = patchSize;
		this.numLabels = numLabels;
		this.kdTree    = null;
		this.search    = null;
	}

	public void fill(final List<Patch<T>> patches) {

		kdTree = new KDTree<Patch<T>>(patches);
		search = new NNearestNeighborSearch<Patch<T>>(kdTree);

		int i = 0;
		for (Patch<T> patch : patches) {

			ImgLib.save(
					patch.getImage(),
					(patch.getLabel() == 0 ? "pos-" : "neg-") + i + ".jpg");
			i++;
		}
	}

	/**
	 * @param n Number of nearest neighbors to ask.
	 */
	public int getLabel(final Patch<T> patch, int n) {

		Patch<T>[] neighbors = search.findNNearestNeighbors(patch, n);

		int[] labels = new int[numLabels];
		for (Patch<T> neighbor : neighbors)
			labels[neighbor.getLabel()]++;

		int bestLabel = 0;
		int maxSuport = 0;
		for (int i = 0; i < numLabels; i++)
			if (labels[i] > maxSuport) {
				bestLabel = i;
				maxSuport = labels[i];
			}

		return bestLabel;
	}

	public int getPatchSize() {

		return this.patchSize;
	}
}
