
public interface ShapeDissimilarity {

	/**
	 * Compute a measure for the dissimilarity of the shape of two candidates.
	 */
	public double dissimilarity(Candidate candidate1, Candidate candidate2);

	/**
	 * Compute a measure for the dissimilarity of the shape of one candidate
	 * against the joint shapre of two others. This is used for splits and
	 * merges of candidates.
	 */
	public double dissimilarity(Candidate candidate1, Candidate candidate2a, Candidate candidate2b);
}
