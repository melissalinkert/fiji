
import java.util.List;

import mpicbg.imglib.algorithm.kdtree.KDTree;
import mpicbg.imglib.algorithm.kdtree.NNearestNeighborSearch;

import mpicbg.imglib.type.numeric.RealType;

public class Dictionary<T extends RealType<T>> {

	KDTree<Patch<T>> kdTree;
	private int patchSize;

	NNearestNeighborSearch<Patch<T>> search;

	public Dictionary(int patchSize) {

		this.patchSize = patchSize;
		this.kdTree    = null;
		this.search    = null;
	}

	public void fill(final List<Patch<T>> patches) {

		kdTree = new KDTree<Patch<T>>(patches);
		search = new NNearestNeighborSearch<Patch<T>>(kdTree);
	}

	/**
	 * @param n Number of nearest neighbors to ask.
	 */
	public int getLabel(final Patch<T> patch, int n) {

		Patch<T>[] neighbors = search.findNNearestNeighbors(patch, n);

		return 0;
	}

	public int getPatchSize() {

		return this.patchSize;
	}
}
