package sipnet;
public class SequenceNode extends SearchNode<Sequence, SequenceNode> {

	private Assignment assignment;
	double  negLogP;

	public SequenceNode(Assignment assignment) {

		this.assignment = assignment;
	}

	public void setNegLogP(double negLogP)
	{
		this.negLogP = negLogP;
	}

	public Assignment getAssignment()
	{
		return this.assignment;
	}

	public double getNegLogP()
	{
		return this.negLogP;
	}
}
