package mser;

public interface RegionFactory<R extends Region<R>> {

	public R create();

	public R create(MSER<?,R>.ConnectedComponent component);
}
