package sipnet;

import java.util.Vector;

public abstract class SingleAssignment extends SearchNode<Assignment, SingleAssignment> {

	Vector<Candidate> sources;
	Vector<Candidate> targets;

	public SingleAssignment() {

		this.sources = new Vector<Candidate>();
		this.targets = new Vector<Candidate>();
	}

	public SingleAssignment(Vector<Candidate> sources, Vector<Candidate> targets)
	{
		for (Candidate c : sources)
			if (c == null)
				throw new RuntimeException("one of the sources is null!");
		for (Candidate c : targets)
			if (c == null)
				throw new RuntimeException("one of the targets is null!");

		this.sources = sources;
		this.targets = targets;
	}

	public Vector<Candidate> getSources()
	{
		return this.sources;
	}

	public void addSource(Candidate source)
	{
		if (source == null)
			throw new RuntimeException("source is null!");
		this.sources.add(source);
	}

	public void addTarget(Candidate target)
	{
		if (target == null)
			throw new RuntimeException("target is null!");
		this.targets.add(target);
	}

	public Vector<Candidate> getTargets()
	{
		return this.targets;
	}

	abstract public double getCosts();
}
