package sipnet;

public class SingleAssignment extends SearchNode<Assignment, SingleAssignment> {

	Candidate source;
	Candidate target;

	double negLogP;

	public SingleAssignment(Candidate source, Candidate target)
	{
		this.source = source;
		this.target = target;
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

	public void setNegLogP(double negLogP)
	{
		this.negLogP = negLogP;
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
