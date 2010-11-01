
import java.util.Vector;

public class Region {

	private static int NextId = 0;

	private int id;

	private Region         parent;
	private Vector<Region> children;

	private int      size;
	private double[] center;

	private Region closestRegion;
	private double minNegLogPAssignment;

	public Region(int size, double[] center) {

		this.id       = NextId;
		NextId++;

		this.size     = size;
		this.center   = new double[center.length];
		System.arraycopy(center, 0, this.center, 0, center.length);
		this.parent   = null;
		this.children = new Vector<Region>();

		this.closestRegion        = null;
		this.minNegLogPAssignment = -1;
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

	public void setClosestRegion(Region closestRegion) {
		this.closestRegion = closestRegion;
	}

	public void setMinNegLogPAssignment(double minNegLogPAssignment) {
		this.minNegLogPAssignment = minNegLogPAssignment;
	}

	public double getMinNegLogPAssignment() {
		return this.minNegLogPAssignment;
	}

	public Region getClosestRegion() {
		return this.closestRegion;
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

		String ret = "Region at ";

		for (int d = 0; d < center.length; d++)
			ret += " " + center[d];

		ret += ", size: " + size;

		return ret;
	}
}
