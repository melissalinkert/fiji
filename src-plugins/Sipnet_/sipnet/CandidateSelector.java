package sipnet;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Rectangle;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.util.HashSet;
import java.util.Set;

import java.util.concurrent.CountDownLatch;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

import fiji.util.gui.OverlayedImageCanvas;

import ij.IJ;
import ij.ImagePlus;

import ij.gui.ImageWindow;
import java.awt.Component;

public class CandidateSelector {

	private ImagePlus   regionImp;
	private Set<Candidate> regions;

	private CountDownLatch latch;

	// did the user chose to cancel?
	private boolean abort;

	/**
	 * Custom canvas to deal with zooming an panning
	 */
	@SuppressWarnings("serial")
	private class CustomCanvas extends OverlayedImageCanvas {

		CustomCanvas(ImagePlus imp) {

			super(imp);

			Dimension dim = new Dimension(Math.min(512, imp.getWidth()), Math.min(512, imp.getHeight()));
			setMinimumSize(dim);
			setSize(dim.width, dim.height);
			setDstDimensions(dim.width, dim.height);

			addKeyListener(new KeyAdapter() {
				public void keyReleased(KeyEvent ke) {
					repaint();
				}
			});
		}

		//@Override
		public void setDrawingSize(int w, int h) {}

		public void setDstDimensions(int width, int height) {

			super.dstWidth = width;
			super.dstHeight = height;

			// adjust srcRect: can it grow/shrink?
			int w = Math.min((int)(width  / magnification), imp.getWidth());
			int h = Math.min((int)(height / magnification), imp.getHeight());
			int x = srcRect.x;
			if (x + w > imp.getWidth())
				x = w - imp.getWidth();
			int y = srcRect.y;
			if (y + h > imp.getHeight())
				y = h - imp.getHeight();

			srcRect.setRect(x, y, w, h);
			repaint();
		}

		//@Override
		public void paint(Graphics g) {

			Rectangle srcRect = getSrcRect();
			double mag = getMagnification();
			int dw = (int)(srcRect.width * mag);
			int dh = (int)(srcRect.height * mag);
			g.setClip(0, 0, dw, dh);

			super.paint(g);

			int w = getWidth();
			int h = getHeight();
			g.setClip(0, 0, w, h);

			// Paint away the outside
			g.setColor(getBackground());
			g.fillRect(dw, 0, w - dw, h);
			g.fillRect(0, dh, w, h - dh);
		}
	}

	/**
	 * Custom window to show the candidate selection interface
	 */
	@SuppressWarnings("serial")
	private class SelectionWindow extends ImageWindow {

		private Panel  all           = new Panel();
		private JPanel optionsPanel  = new JPanel();

		private JButton doneButton   = new JButton("Done");
		private JButton cancelButton = new JButton("Cancel");
		
		private ActionListener listener = new ActionListener() {

			public void actionPerformed(final ActionEvent e) {

				if (e.getSource() == doneButton)
					setVisible(false);
				else if (e.getSource() == cancelButton) {
					setVisible(false);
					abort = true;
				}

				latch.countDown();
			}
		};

		SelectionWindow(ImagePlus imp) 
		{
			super(imp, new CustomCanvas(imp));

			final CustomCanvas canvas = (CustomCanvas) getCanvas();
			
			// Remove the canvas from the window, to add it later
			removeAll();

			setTitle("Candidate Selection");

			// Add listeners
			doneButton.addActionListener(listener);
			cancelButton.addActionListener(listener);

			// Options panel
			optionsPanel.setBorder(BorderFactory.createTitledBorder("Options"));
			GridBagLayout optionsLayout = new GridBagLayout();
			GridBagConstraints optionsConstraints = new GridBagConstraints();
			optionsConstraints.anchor = GridBagConstraints.NORTHWEST;
			optionsConstraints.fill = GridBagConstraints.HORIZONTAL;
			optionsConstraints.gridwidth = 1;
			optionsConstraints.gridheight = 1;
			optionsConstraints.gridx = 0;
			optionsConstraints.gridy = 0;
			optionsConstraints.insets = new Insets(5, 5, 6, 6);
			optionsPanel.setLayout(optionsLayout);
			
			optionsPanel.add(doneButton, optionsConstraints);
			optionsConstraints.gridx++;
			optionsPanel.add(cancelButton, optionsConstraints);
			optionsConstraints.gridx++;

			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints allConstraints = new GridBagConstraints();
			all.setLayout(layout);

			allConstraints.anchor = GridBagConstraints.NORTHWEST;
			allConstraints.fill = GridBagConstraints.BOTH;
			allConstraints.gridwidth = 1;
			allConstraints.gridheight = 1;
			allConstraints.gridx = 0;
			allConstraints.gridy = 0;
			allConstraints.weightx = 0;
			allConstraints.weighty = 0;

			all.add(canvas, allConstraints);
			allConstraints.gridy++;

			allConstraints.weightx = 1;
			allConstraints.weighty = 1;

			all.add(optionsPanel, allConstraints);
			allConstraints.gridy++;

			allConstraints.anchor = GridBagConstraints.NORTHEAST;
			allConstraints.weightx = 0;
			allConstraints.weighty = 0;

			GridBagLayout wingb = new GridBagLayout();
			GridBagConstraints winc = new GridBagConstraints();
			winc.anchor = GridBagConstraints.NORTHWEST;
			winc.fill = GridBagConstraints.BOTH;
			winc.weightx = 1;
			winc.weighty = 1;
			setLayout(wingb);
			add(all, winc);

			doneButton.setSelected(true);

			// Propagate all listeners
			for (Component p : new Component[]{all, optionsPanel}) {
				for (KeyListener kl : getKeyListeners()) {
					p.addKeyListener(kl);
				}
			}

			addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					doneButton.removeActionListener(listener);
					cancelButton.removeActionListener(listener);
				}
			});

			canvas.addComponentListener(new ComponentAdapter() {
				public void componentResized(ComponentEvent ce) {
					Rectangle r = canvas.getBounds();
					canvas.setDstDimensions(r.width, r.height);
				}
			});
		}
	}

	public CandidateSelector(ImagePlus regionImp, Set<Candidate> regions) {

		this.regionImp = regionImp;
		this.regions   = regions;
	}

	public Set<Candidate> getUserSelection() {

		Set<Candidate> selection = new HashSet<Candidate>();

		// highligh region centers in region image
		for (Candidate region : regions)
			regionImp.getProcessor().setf((int)region.getCenter()[0], (int)region.getCenter()[1], 255.0f);

		// show the region image
		regionImp.updateAndDraw();

		// ask the user to select regions
		latch = new CountDownLatch(1);
		SelectionWindow window = new SelectionWindow(regionImp);
		window.pack();

		// wait until RIOs have been placed
		try {
			latch.await();
		} catch (InterruptedException e) {
			return null;
		}
		if (abort)
			return null;

		// fallback for easy testing
		if (regionImp.getRoi() == null) {
			int[] starterIds = new int[]{143, 247, 248, 294, 937, 976, 985, 1036, 1108, 1113};
			for (Candidate region : regions)
				if (contains(starterIds, region.getId()))
					selection.add(region);
			return selection;
		}

		// select all regions that are the closest to the RIOs
		for (int x = 0; x < regionImp.getWidth(); x++)
			for (int y = 0; y < regionImp.getHeight(); y++)
				if (regionImp.getRoi().contains(x, y)) {

					double minDistance = 0;
					Candidate bestRegion  = null;

					for (Candidate region : regions) {

						double distance = (x-region.getCenter()[0])*(x-region.getCenter()[0]) +
										  (y-region.getCenter()[1])*(y-region.getCenter()[1]);
						if (distance < minDistance || bestRegion == null) {
							minDistance = distance;
							bestRegion  = region;
						}
					}

					selection.add(bestRegion);
				}

		IJ.log("selected candidates:");
		for (Candidate candidate : selection)
			IJ.log("  " + candidate.getId());
		return selection;
	}

	private boolean contains(int[] list, int value) {

		for (int x : list)
			if (x == value)
				return true;
		return false;
	}
}
