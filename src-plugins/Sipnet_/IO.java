
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import ij.IJ;

public class IO {

	public boolean exists(String filename) {

		return (new File(filename)).exists();
	}

	public boolean newer(String filename1, String filename2) {

		return ((new File(filename1)).lastModified() > (new File(filename2)).lastModified());
	}

	public void writeMsers(Vector<Set<Candidate>> sliceTopMsers, String filename) {

		File outfile = new File(filename);

		try {
			FileOutputStream out = new FileOutputStream(outfile);
	
			ObjectOutput oout = new ObjectOutputStream(out);
	
			oout.writeInt(sliceTopMsers.size());

			for (Collection<Candidate> topMsers : sliceTopMsers) {

				oout.writeInt(topMsers.size());

				for (Candidate mser : topMsers)
					mser.writeExternal(oout);
			}

			out.flush();

		} catch (FileNotFoundException e) {

			IJ.log("File " + filename + " could not be opened for writing.");

		} catch (IOException e) {

			IJ.log("Something went wrong when trying to write to " + filename);
			e.printStackTrace();
		}
	}

	public Vector<Set<Candidate>> readMsers(String filename, int firstSlice, int lastSlice) {

		Vector<Set<Candidate>> sliceTopMsers = new Vector<Set<Candidate>>();

		File infile = new File(filename);

		try {
			FileInputStream in = new FileInputStream(infile);
	
			ObjectInput oin = new ObjectInputStream(in);

			int numSlices = oin.readInt();

			if (numSlices < lastSlice)
				throw new RuntimeException("not enough slices in mser file " + filename);

			for (int s = 0; s < lastSlice; s++) {
	
				Set<Candidate> topMsers = new HashSet<Candidate>();

				int numMsers = oin.readInt();
				IJ.log("Trying to read " + numMsers + " top msers");

				for (int i = 0; i < numMsers; i++) {
					Candidate region = new Candidate();
					region.readExternal(oin);
					topMsers.add(region);
				}

				sliceTopMsers.add(topMsers);
			}

		} catch (FileNotFoundException e) {

			IJ.log("File " + filename + " could not be opened for reading.");

			return null;

		} catch (IOException e) {

			IJ.log("Something went wrong when trying to read from " + filename);
			e.printStackTrace();

			return null;
		}

		return sliceTopMsers;
	}
}
