package sipnet;

import java.util.LinkedList;
import java.util.Set;

@SuppressWarnings("serial")
public class Sequence extends LinkedList<Assignment> {

	public Set<Candidate> getCandidates(int slice) {

		if (slice == size())
			return get(slice - 1).getTargets();
		else
			return get(slice).getSources();
	}

	/**
	 * Check consistency of sequence, i.e., whether the set of sources of one
	 * assignment is equal to the set of targets of the previous assignment.
	 */
	public boolean consistent() {

		for (int i = 0; i < size()-1; i++) {

			Set<Candidate> targets = get(i).getTargets();
			Set<Candidate> sources = get(i+1).getSources();

			for (Candidate target : targets)
				if (!sources.contains(target))
					return false;
			for (Candidate source : sources)
				if (!targets.contains(source))
					return false;
		}

		return true;
	}
}
