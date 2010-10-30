
public class SingleAssignment extends SearchNode<Assignment, SingleAssignment> {

	Region source;
	Region target;

	double negLogP;

	public SingleAssignment(Region source, Region target, double negLogP)
	{
		this.source = source;
		this.target = target;

		this.negLogP = negLogP;
	}

	public Region getSource()
	{
		return this.source;
	}

	public void setSource(Region source)
	{
		this.source = source;
	}

	public void setTarget(Region target)
	{
		this.target = target;
	}

	public Region getTarget()
	{
		return this.target;
	}

	public double getNegLogP()
	{
		return this.negLogP;
	}
}
