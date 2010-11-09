
public class SingleAssignment extends SearchNode<Assignment, SingleAssignment> {

	Candidate source;
	Candidate target;

	double negLogP;

	public SingleAssignment(Candidate source, Candidate target, double negLogP)
	{
		this.source = source;
		this.target = target;

		this.negLogP = negLogP;
	}

	public Candidate getSource()
	{
		return this.source;
	}

	public void setSource(Candidate source)
	{
		this.source = source;
	}

	public void setTarget(Candidate target)
	{
		this.target = target;
	}

	public Candidate getTarget()
	{
		return this.target;
	}

	public double getNegLogP()
	{
		return this.negLogP;
	}
}
