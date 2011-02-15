package sipnet;

import java.util.Vector;

public class SingleAssignment extends SearchNode<Assignment, SingleAssignment> {

	Vector<Candidate> sources;
	Vector<Candidate> targets;

	double negLogP;

	public SingleAssignment() {

		this.sources = new Vector<Candidate>();
		this.targets = new Vector<Candidate>();
	}

	public SingleAssignment(Vector<Candidate> sources, Vector<Candidate> targets)
	{
		this.sources = sources;
		this.targets = targets;
	}

	public Vector<Candidate> getSources()
	{
		return this.sources;
	}

	public void addSource(Candidate source)
	{
		this.sources.add(source);
	}

	public void addTarget(Candidate target)
	{
		this.targets.add(target);
	}

	public void setNegLogP(double negLogP)
	{
		this.negLogP = negLogP;
	}

	public Vector<Candidate> getTargets()
	{
		return this.targets;
	}

	public double getNegLogP()
	{
		return this.negLogP;
	}
}
