package sipnet;

import Jama.Matrix;

public class KLDivergence implements ShapeDissimilarity {

	final public double dissimilarity(Candidate candidate1, Candidate candidate2) {

		final Matrix c1 = candidate1.getCovariance();
		final Matrix c2 = candidate2.getCovariance();

		final double logDet = Math.log(c1.det()/c2.det());
		final double trace  = c1.inverse().times(c2).trace();

		return 0.5*(logDet + trace + 2);
	}

	final public double dissimilarity(Candidate candidate1, Candidate candidate2a, Candidate candidate2b) {

		// TODO: implement it!
		return 0.0;
	}
}
