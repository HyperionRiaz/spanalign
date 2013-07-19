package statalign.postprocess.plugins;

import java.awt.BorderLayout;
import java.io.IOException;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import statalign.base.InputData;
import statalign.base.State;
import statalign.base.Utils;
import statalign.postprocess.gui.AlignmentGUI;

/**
 * This is the postprocessmanager for showing the current alignment.
 * 
 * @author miklos, novak
 *
 */
public class CurrentAlignment extends statalign.postprocess.Postprocess{

	public JPanel pan;
	// public String[] allAlignment;
	// public String[] leafAlignment;
	public String title;
	
	InputData input;
	// String[] seqNames;
	
	AlignmentGUI gui;
	
	/**
	 * It construct a postprocess that is screenable (=can be shown on the GUI),
	 * outputable (= can be written into th elog file) but not postprocessable
	 * (= cannot generate its own output file).
	 */
	public CurrentAlignment(){
		screenable = true;
		outputable = true;
		postprocessable = false;
		sampling = true;
		rnaAssociated = false;
	}
	
	/**
	 * It constructs a new JPanel, and returns with it
	 */
	@Override
	public JPanel getJPanel() {
		pan = new JPanel(new BorderLayout());
		return pan;
	}

	/**
	 * It generates a new icon based on the figure in file icons/calignment.gif
	 */
	@Override
	public Icon getIcon() {		
//		return new ImageIcon("icons/calignment.gif");
		return new ImageIcon(ClassLoader.getSystemResource("icons/calignment.gif"));
	}

	/**
	 * It returns with its tab name, 'Alignment'
	 */
	@Override
	public String getTabName() {
		return "Alignment";
	}

	/**
	 * It returns with the tip of the tabulated panel 'Current alignment in the Markov chain'
	 * (shown when mouse is moved over the panel)
	 */
	@Override
	public String getTip() {
		return "Current alignment in the Markov chain";
	}

	@Override
	public double getTabOrder() {
		return 2.0d;
	}

	@Override
	public String getFileExtension() {
		return "aln";
	}

	/**
	 * Updates the alignment.
	 */
	@Override
	public void newPeek(State state){
		if(show) {
			updateAlignment(state);
		}
	}
	
	private String[] updateAlignment(State state) {
		String[] rows = state.getFullAlign();
        String[] allAlignment = new String[rows.length];
		for(int i = 0; i < rows.length; i++) {
            String name = state.name[i];
            if (name.isEmpty())
                name = "-";
            allAlignment[i] = name + '\t' + rows[i];
        }
        /*
		int ind = 0;
		for(int i = 0; i < allAlignment.length; i++) {
            // TODO: Fix null check. Inserted for testing.
            String[] leafAlignment = new String[state.nl];
			if(allAlignment[i] != null && allAlignment[i].charAt(0) != ' ')
				leafAlignment[ind++] = allAlignment[i];
        } */
		if(show) {
			gui.alignment = allAlignment;
			gui.repaint();
		}

        return allAlignment;
	}

	/**
	 * Initializes the graphical interface.
	 */
	@Override
	public void beforeFirstSample(InputData input) {
		if(show) {
			pan.removeAll();
			title = input.title;
			JScrollPane scroll = new JScrollPane();
			scroll.setViewportView(gui = new AlignmentGUI(title,input.model));//, mcmc.tree.printedAlignment()));
			gui.title = input.title;
			pan.add(scroll, BorderLayout.CENTER);
		}
		// leafAlignment = new String[input.seqs.size()];
		this.input = input;
	}

	/**
	 * At each mcmc sampling point, the current alignment is shown on the GUI.
	 * Also, it writes the current alignment into the log file when in
	 * sampling mode.
	 */
	@Override
	public void newSample(State state, int no, int total) {
		//System.out.println("THIS IS THE CURRENT ALIGNMENT" + state.getFullAlign());
		String[] allAlignment = updateAlignment(state);
		
		if(sampling){
			try {
				String[] alignment = Utils.alignmentTransformation(allAlignment, alignmentType, input);
				for(int i = 0; i < alignment.length; i++){
					file.write("Sample "+no+"\tAlignment:\t"+alignment[i]+"\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	/**
	 * Temporary fileprinting method to generate single files containing the current alignment. Used when testing
	 * the performance of cold and heated chains in mcmcmc.
	 */
    /*
	public void singleFilePrint(int no, int total) {
		if(sampling){
			try {
				System.out.println("Alignment type: "+ alignmentType);
				String[] alignment = Utils.alignmentTransformation(allAlignment, alignmentType, input);
				for(int i = 0; i < alignment.length; i++){
					file.write(alignment[i]+"\n");
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	*/
	
	/**
	 * Toggles sampling mode.
	 */
	@Override
	public void setSampling(boolean enabled){
		sampling = enabled;
	}

}
