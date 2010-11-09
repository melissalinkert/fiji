
public interface RegionFactory<R extends Region<R>> {

	public R create();

	public R create(int size, double[] center);
}
