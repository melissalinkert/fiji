
public class SingleAssignment extends SearchNode<SingleAssignment> {

	Region source;
	Region target;

	public SingleAssignment(Region source, Region target)
	{
		this.source = source;
		this.target = target;
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
}
