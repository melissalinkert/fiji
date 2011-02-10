package mser;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import java.util.Vector;

public class Region<R extends Region<R>> implements Externalizable {

	private static int NextId = 0;

	private int id;

	private R         parent;
	private Vector<R> children;

	private int      size;
	private int      perimeter;
	private double[] center;

	private RegionFactory<R> regionFactory;

	public Region(int size, int perimeter, double[] center, RegionFactory<R> regionFactory) {

		this.id        = NextId;
		NextId++;

		this.size      = size;
		this.perimeter = perimeter;
		this.center    = new double[center.length];
		System.arraycopy(center, 0, this.center, 0, center.length);
		this.parent    = null;
		this.children  = new Vector<R>();

		this.regionFactory = regionFactory;
	}

	public void setParent(R parent) {

		this.parent = parent;
	}

	public void addChildren(Vector<R> children) {

		this.children.addAll(children);
	}

	public Vector<R> getChildren() {

		return this.children;
	}

	public int getSize() {

		return this.size;
	}

	public int getPerimeter() {

		return this.perimeter;
	}

	public double[] getCenter() {

		double[] center = new double[this.center.length];
		System.arraycopy(this.center, 0, center, 0, this.center.length);
		return center;
	}

	public double getCenter(int index) {
		return this.center[index];
	}

	public R getParent() {
		return this.parent;
	}

	public boolean isAncestorOf(R other) {

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
		for (R child : children)
			child.writeExternal(out);
		out.writeInt(size);
		out.writeInt(perimeter);
		out.writeObject(center);
	}

	public void readExternal(ObjectInput in) throws IOException {

		id = in.readInt();
		if (id >= NextId)
			NextId = id + 1;
		int numChildren = in.readInt();
		for (int i = 0; i < numChildren; i++) {
			R child = regionFactory.create();
			child.readExternal(in);
			child.setParent((R)this);
			children.add(child);
		}
		size      = in.readInt();
		perimeter = in.readInt();
		try {
			center = (double[])in.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
}
