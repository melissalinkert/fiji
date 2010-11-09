
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import java.util.PriorityQueue;
import java.util.Vector;

public class Region implements Externalizable {

	private static int NextId = 0;

	private int id;

	private Region         parent;
	private Vector<Region> children;

	private int      size;
	private double[] center;

	private PriorityQueue<Region> closestRegions;
	private double                minNegLogPAssignment;
	private double                meanNeighborDistance;

	public Region(int size, double[] center) {

		this.id       = NextId;
		NextId++;

		this.size     = size;
		this.center   = new double[center.length];
		System.arraycopy(center, 0, this.center, 0, center.length);
		this.parent   = null;
		this.children = new Vector<Region>();

		this.closestRegions       = null;
		this.minNegLogPAssignment = -1;
		this.meanNeighborDistance = 0.0;
	}

	public void setParent(Region parent) {

		this.parent = parent;
	}

	public Vector<Region> getChildren() {
		return this.children;
	}

	public int getSize() {
		return this.size;
	}

	public double[] getCenter() {
		return this.center;
	}

	public double getCenter(int index) {
		return this.center[index];
	}

	public void setClosestRegions(PriorityQueue<Region> closestRegions) {
		this.closestRegions = closestRegions;
	}

	public void setMinNegLogPAssignment(double minNegLogPAssignment) {
		this.minNegLogPAssignment = minNegLogPAssignment;
	}

	public void setMeanNeighborDistance(double meanNeighborDistance)
	{
		this.meanNeighborDistance = meanNeighborDistance;
	}

	public double getMeanNeighborDistance()
	{
		return this.meanNeighborDistance;
	}

	public double getMinNegLogPAssignment() {
		return this.minNegLogPAssignment;
	}

	public PriorityQueue<Region> getClosestRegions() {
		return this.closestRegions;
	}

	public Region getParent() {
		return this.parent;
	}

	public void addChildren(Vector<Region> children) {

		this.children.addAll(children);
	}

	public boolean isAncestorOf(Region other) {

		while (other.getParent() != null)
			if (other.getParent() == this)
				return true;
			else
				other = other.getParent();
		return false;
	}

	public int getId() {
		return id;
	}

	public String toString() {

		String ret = "Region " + id + ",";

		for (int d = 0; d < center.length; d++)
			ret += " " + (int)center[d];

		ret += ", size: " + size;

		return ret;
	}

	public void writeExternal(ObjectOutput out) throws IOException {

		out.writeInt(id);
		// omit parent
		out.writeInt(children.size());
		for (Region child : children)
			child.writeExternal(out);
		out.writeInt(size);
		out.writeObject(center);
		// omit closestRegions
		// omit minNegLogPAssignment

	}

	public void readExternal(ObjectInput in) throws IOException {

		id = in.readInt();
		if (id >= NextId)
			NextId = id + 1;
		int numChildren = in.readInt();
		for (int i = 0; i < numChildren; i++) {
			Region child = new Region(0, new double[0]);
			child.readExternal(in);
			child.setParent(this);
			children.add(child);
		}
		size = in.readInt();
		try {
			center = (double[])in.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
}
