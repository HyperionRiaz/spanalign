package statalign.model.ext.plugins;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JToggleButton;

import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.util.MathArrays;

import statalign.base.InputData;
import statalign.base.Tree;
import statalign.base.Utils;
import statalign.base.Vertex;
import statalign.base.hmm.Hmm;
import statalign.io.DataType;
import statalign.io.ProteinSkeletons;
import statalign.model.ext.GammaProposal;
import statalign.model.ext.GaussianProposal;
import statalign.model.ext.ModelExtension;
import statalign.model.ext.McmcMove;
import statalign.model.ext.McmcCombinationMove;
import statalign.model.ext.HyperbolicPrior;
import statalign.model.ext.GammaPrior;
import statalign.model.ext.InverseGammaPrior;
import statalign.model.ext.plugins.structalign.*;
import statalign.model.ext.ParameterInterface;

import statalign.model.subst.SubstitutionModel;
import statalign.postprocess.PluginParameters;

public class StructAlign extends ModelExtension implements ActionListener {
	
	/** The command line identifier of this plugin */
	//private static final String CMD_LINE_PLUGIN_ID = "structal";
	private final String pluginID = "structal";
	
	@Override
	public String getPluginID() {
		return pluginID;
	}
	
	JToggleButton myButton;
	
	public boolean globalSigma = true;
	public boolean useLibrary = false;
	double structTemp = 1;

	
	/** Alpha-C atomic coordinate for each sequence and each residue */
	public double[][][] coords;
	
	/** Alpha-C atomic coordinates under the current set of rotations/translations */
	public double[][][] rotCoords;
	
	/** Axis of rotation for each sequence */
	public double[][] axes;
	/** Rotation angle for each protein along the rotation axis */
	public double[] angles;
	/** Translation vector for each protein */
	public double[][] xlats;

	/** Parameters of structural drift */
	public double[] sigma2;
	public double sigma2Hier;
	public double nu;
	public double tau;
	public double epsilon;
	// TODO Allow starting values to be specified at command line/GUI
	
	/** Covariance matrix implied by current tree topology */
	public double[][] fullCovar;
	/** Current alignment between all leaf sequences */
	public String[] curAlign;
	
	/** Current log-likelihood contribution */
	public double curLogLike = 0;
	
	/** independence rotation proposal distribution */
	public RotationProposal rotProp;

	public double[][] oldCovar;
	public String[] oldAlign;
	public double oldLogLi;
	
	
	// TODO change the above public variables to package visible and put 
	// StructAlign.java in statalign.model.ext.plugins.structalign ?
	
	/** Priors */
	private double sigma2PriorShape = 0.001;
	private double sigma2PriorRate = 0.001;
//	public InverseGammaPrior sigma2Prior = new InverseGammaPrior(sigma2PriorShape,sigma2PriorRate);
	public HyperbolicPrior sigma2Prior = new HyperbolicPrior();
	
	private double tauPriorShape = 0.001;
	private double tauPriorRate = 0.001;
	public InverseGammaPrior tauPrior = new InverseGammaPrior(tauPriorShape,tauPriorRate);
	
	private double epsilonPriorShape = 2;
	private double epsilonPriorRate = 2;
	public GammaPrior epsilonPrior = new GammaPrior(epsilonPriorShape,epsilonPriorRate);
	
//	private double sigma2HPriorShape = 0.001;
//	private double sigma2HPriorRate = 0.001;
//	public InverseGammaPrior sigma2HPrior = new InverseGammaPrior(sigma2HPriorShape,sigma2HPriorRate);
	public HyperbolicPrior sigma2HPrior = new HyperbolicPrior();

	private double nuPriorShape = 1;
	private double nuPriorRate = 1;
	public GammaPrior nuPrior = new GammaPrior(nuPriorShape,nuPriorRate);
	
	// priors for rotation and translation are uniform
	// so do not need to be included in M-H ratio
	
	
	/** Default proposal weights in this order: 
	 *  align, topology, edge, indel param, subst param, modelext param 
	 *  { 35, 20, 15, 15, 10, 0 };
	 */
	private final int pluginProposalWeight = 50; 
	
	int sigma2Weight = 15;
	int tauWeight = 10;
	int sigma2HierWeight = 10;
	int nuWeight = 10;
	int epsilonWeight = 10;
	int rotationWeight = 2;
	int translationWeight = 2;
	int libraryWeight = 2;
	int alignmentWeight = 2;
	
	int alignmentRotationWeight = 8;
	int alignmentTranslationWeight = 6;
	int alignmentLibraryWeight = 6;
	
	
	/** Starting value for rotation proposal tuning parameter. */
	public final double angleP = 1000;
	/** Starting value for translation proposal tuning parameter. */
	public final double xlatP = .1;
	
	/** Minimum value for epsilon, to prevent numerical errors. */
	public final double MIN_EPSILON = 0.01;
	
	@Override
	public List<JComponent> getToolBarItems() {
		myButton = new JToggleButton(new ImageIcon(ClassLoader.getSystemResource("icons/protein.png")));
    	myButton.setToolTipText("Structural alignment mode (for proteins only)");
    	myButton.addActionListener(this);
    	myButton.setEnabled(true);
    	myButton.setSelected(false);
    	return Arrays.asList((JComponent)myButton);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		setActive(myButton.isSelected());
	}
	

	@Override
	public String getUsageInfo() {
		StringBuilder usage = new StringBuilder();
		usage.append("StructAlign version 1.0\n\n");
		usage.append("java -jar statalign.jar -plugin:structal[OPTIONS]\n");
		usage.append("OPTIONS: \n");
		usage.append("\tepsilon=X\t(Fixes epsilon at X)\n");
		usage.append("\tuseLibrary\t(Allows rotation library moves to be used)\n");
	     		
		return usage.toString();
	}

	@Override
	public void setActive(boolean active) {
		super.setActive(active);
		System.out.println("StructAlign plugin is now "+(active?"enabled":"disabled"));
	}
	
	@Override
	public void init(PluginParameters params) {
		if(params != null && params.getParameter(pluginID) != null) {
			// TODO parse plugin parameters
			setActive(true);
		}
	}
	
	@Override
	public void initRun(InputData inputData) throws IllegalArgumentException {
		HashMap<String, Integer> seqMap = new HashMap<String, Integer>();
		int i = 0;
		for(String name : inputData.seqs.seqNames)
			seqMap.put(name.toUpperCase(), i++);
		coords = new double[inputData.seqs.seqNames.size()][][];
		for(DataType data : inputData.auxData) {
			if(!(data instanceof ProteinSkeletons))
				continue;
			ProteinSkeletons ps = (ProteinSkeletons) data;
			for(i = 0; i < ps.names.size(); i++) {
				String name = ps.names.get(i).toUpperCase();
				if(!seqMap.containsKey(name))
					throw new IllegalArgumentException("structalign: missing sequence or duplicate structure for "+name);
				int ind = seqMap.get(name);
				int len = inputData.seqs.sequences.get(ind).replaceAll("-", "").length();
				List<double[]> cl = ps.coords.get(i);
				if(len != cl.size())
					throw new IllegalArgumentException("structalign: sequence length mismatch with structure file for seq "+name);
				coords[ind] = new double[len][];
				for(int j = 0; j < len; j++)
					 coords[ind][j] = Utils.copyOf(cl.get(j));
				RealMatrix temp = new Array2DRowRealMatrix(coords[ind]);
				RealVector mean = Funcs.meanVector(temp);
				for(int j = 0; j < len; j++)
					 coords[ind][j]= temp.getRowVector(j).subtract(mean).toArray();
				seqMap.remove(name);
			}
		}
		if(seqMap.size() > 0)
			throw new IllegalArgumentException("structalign: missing structure for sequence "+seqMap.keySet().iterator().next());
		
		if (useLibrary) {
			rotProp = new RotationProposal(this);
		}
		
		
		rotCoords = new double[coords.length][][];
		axes = new double[coords.length][];
		angles = new double[coords.length];
		xlats = new double[coords.length][];

		axes[0] = new double[] { 1, 0, 0 };
		angles[0] = 0;
		xlats[0] = new double[] { 0, 0, 0 };
						
		sigma2Hier = 1;
		nu = 1;

		tau = 50;
		epsilon = 100;
		
		// alternative initializations
		// actual initialization now occurs in beforeSampling()
		/*
		for(i = 1; i < axes.length; i++) {
			Transformation initial = rotProp.propose(i);
			axes[i] = initial.axis.toArray();
			angles[i] = initial.rot;
			xlats[i] = initial.xlat.toArray();
		}
		for(i = 0; i < axes.length; i++) {
			axes[i] = new double[] { 1, 0, 0 };
			angles[i] = 0;
			xlats[i] = new double[] { 0, 0, 0 };
		} */
		
		// number of branches in the tree is 2*leaves - 1
		if (globalSigma) {
			sigma2 = new double[1];
		}
		else {
			sigma2 = new double[2*coords.length - 1];
		}
		
		for(i = 0; i < sigma2.length; i++)
			sigma2[i] = 1;
		
		// Probably remove the following?
//		sigma2Weight *= coords.length;
//		tauWeight *= coords.length;
		
		/* Add alignment and rotation/translation moves */
		RotationMove rotationMove = new RotationMove(this,"rotation"); 
		addMcmcMove(rotationMove,rotationWeight); 
		
		TranslationMove translationMove = new TranslationMove(this,"translation");
		addMcmcMove(translationMove,translationWeight); 
		
		LibraryMove libraryMove = null;
		if (useLibrary) {
			libraryMove = new LibraryMove(this,"library");
			addMcmcMove(libraryMove,libraryWeight);
		}
		
		AlignmentMove alignmentMove = new AlignmentMove(this,"alignment");
		addMcmcMove(alignmentMove,alignmentWeight); 
		
		/* Combination moves */
		ArrayList<McmcMove> alignmentRotation = new ArrayList<McmcMove>();
		alignmentRotation.add(alignmentMove);
		alignmentRotation.add(rotationMove);
		McmcCombinationMove alignmentRotationMove = 
			new McmcCombinationMove(alignmentRotation);
		addMcmcMove(alignmentRotationMove,alignmentRotationWeight); 
		
		ArrayList<McmcMove> alignmentTranslation = new ArrayList<McmcMove>(); 
		alignmentTranslation.add(alignmentMove);
		alignmentTranslation.add(translationMove);
		McmcCombinationMove alignmentTranslationMove = 
			new McmcCombinationMove(alignmentTranslation);
		addMcmcMove(alignmentTranslationMove,alignmentTranslationWeight); 
		
		if (useLibrary) { 
			ArrayList<McmcMove> alignmentLibrary = new ArrayList<McmcMove>();
			alignmentLibrary.add(alignmentMove);
			alignmentLibrary.add(libraryMove);
			McmcCombinationMove alignmentLibraryMove = 
				new McmcCombinationMove(alignmentLibrary);
			addMcmcMove(alignmentLibraryMove,alignmentLibraryWeight);
		}
		
		/** Add moves for scalar parameters */
		StructAlignParameterInterface paramInterfaceGenerator = new StructAlignParameterInterface(this); 
		
		// Random walk proposals 
		GammaProposal gProp = new GammaProposal(0.001,0.001);
		GaussianProposal nProp = new GaussianProposal();

		ParameterInterface tauInterface = paramInterfaceGenerator.new TauInterface();
		ContinuousPositiveParameterMove tauMove = 
			new ContinuousPositiveParameterMove(this,tauInterface,tauPrior,gProp,"τ");
		tauMove.setPlottable();
		tauMove.setPlotSide(1);
		addMcmcMove(tauMove,tauWeight);
		
		ParameterInterface epsilonInterface = paramInterfaceGenerator.new EpsilonInterface();
		ContinuousPositiveParameterMove epsilonMove = 
			new ContinuousPositiveParameterMove(this,epsilonInterface,epsilonPrior,gProp,"ε");
		epsilonMove.setMinValue(MIN_EPSILON);
		epsilonMove.setPlottable();
		epsilonMove.setPlotSide(1);
		addMcmcMove(epsilonMove,epsilonWeight); 
				
		HierarchicalContinuousPositiveParameterMove sigma2HMove = null;
		HierarchicalContinuousPositiveParameterMove nuMove = null;
		if (!globalSigma) {
			ParameterInterface sigma2HInterface = paramInterfaceGenerator.new Sigma2HInterface();
			sigma2HMove = new HierarchicalContinuousPositiveParameterMove(this,sigma2HInterface,sigma2HPrior,gProp,"σ_g");
			sigma2HMove.setPlottable();
			sigma2HMove.setPlotSide(0);
			addMcmcMove(sigma2HMove,sigma2HierWeight); 
			
			ParameterInterface nuInterface = paramInterfaceGenerator.new NuInterface();
			nuMove = new HierarchicalContinuousPositiveParameterMove(this,nuInterface,nuPrior,gProp,"ν");
			nuMove.setPlottable();
			nuMove.setPlotSide(1);
			addMcmcMove(nuMove,nuWeight); 
		}
		
		for (int j=0; j<sigma2.length; j++) {
			String sigmaName;
			if (sigma2.length == 1) {
				sigmaName = "σ";
			}
			else {
				sigmaName = "σ_"+j;
			}
			ParameterInterface sigma2Interface = paramInterfaceGenerator.new Sigma2Interface(j);
			ContinuousPositiveParameterMove m = new ContinuousPositiveParameterMove(
														this,sigma2Interface,
														sigma2Prior,nProp,sigmaName);
														//sigma2Prior,gProp,sigmaName);
			m.setPlottable();
			m.setPlotSide(0);
			addMcmcMove(m,sigma2Weight);
			if (!globalSigma) {
				sigma2HMove.addChildMove(m);
				nuMove.addChildMove(m);
			}
		}
	}
	
	@Override
	public void beforeSampling(Tree tree) {
		Funcs.initLSRotations(tree,coords,xlats,axes,angles);
	}
	
	public double getLogLike() {
		return curLogLike;
	}
	@Override
	public double logLikeFactor(Tree tree) {
		String[] align = tree.getState().getLeafAlign();
		checkConsAlign(align); 		
		curAlign = align;
		
		double[][] covar = calcFullCovar(tree);
		checkConsCovar(covar); 
		fullCovar = covar;
		
		if(!checkConsRots() && rotCoords[0] == null)
			calcAllRotations();
		
		/** TESTING
		
		System.out.println();
		System.out.println("Parameters for structural log likelihood:");		
		System.out.println("Sigma2: " + sigma2);
		System.out.println("Theta: " + theta);
		System.out.println("Branch length: " + (tree.root.left.edgeLength + tree.root.right.edgeLength));
		
		System.out.println("Rotation matrices:");
		for(int i = 1; i < xlats.length; i++) {
			Rotation rot = new Rotation(new Vector3D(axes[i]), angles[i]);
			double[][] m = rot.getMatrix();
			for(int j = 0; j < m.length; j++)
				System.out.println(Arrays.toString(m[j]));
		}
		
		System.out.println("Translations:");
		for(int i = 0; i < xlats.length; i++)
			System.out.println(Arrays.toString(xlats[i]));
		
		/** END TESTING */
		
		
		double logli = calcAllColumnContrib();
		checkConsLogLike(logli); 
		curLogLike = logli;
		
		// testing
		//System.out.println("Total log likelihood " + curLogLike);
		
		return curLogLike;
	}
	
	public double calcAllColumnContrib() {
		String[] align = curAlign;
		double logli = 0;
		int[] inds = new int[align.length];		// current char indices
		int[] col = new int[align.length];  
		for(int i = 0; i < align[0].length(); i++) {
			for(int j = 0; j < align.length; j++)
				col[j] = align[j].charAt(i) == '-' ? -1 : inds[j]++;
			double ll = columnContrib(col); 
			logli += ll;
			//System.out.println("Column: " + Arrays.toString(col) + "  ll: " + ll);
		}
		return structTemp * logli;
	}
	// TODO Change visibility of this to package, after moving
	// StructAlign.java to statalign.model.ext.plugins.structalign

	private boolean checkConsAlign(String[] align) {
		if(!Utils.DEBUG || curAlign == null)
			return false;
		if(align.length != curAlign.length)
			throw new Error("Inconsistency in StructAlign, alignment length: "+align.length+", "+curAlign.length);
		for(int i = 0; i < align.length; i++)
			if(!align[i].equals(curAlign[i]))
				throw new Error("Inconsistency in StructAlign, alignment: "+align[i]+", "+curAlign[i]);
		return true;
	}

	private boolean checkConsCovar(double[][] covar) {
		if(!Utils.DEBUG || fullCovar == null)
			return false;
		if(covar.length != fullCovar.length)
			throw new Error("Inconsistency in StructAlign, covar matrix length: "+covar.length+", "+fullCovar.length);
		for(int i = 0; i < covar.length; i++) {
			if(covar[i].length != fullCovar[i].length)
				throw new Error("Inconsistency in StructAlign, covar matrix "+i+" length: "+covar[i].length+", "+fullCovar[i].length);
			for(int j = 0; j < covar[i].length; j++)
				if(Math.abs(covar[i][j]-fullCovar[i][j]) > 1e-5)
					throw new Error("Inconsistency in StructAlign, covar matrix "+i+","+j+" value: "+covar[i][j]+", "+fullCovar[i][j]+", "+tau+", "+epsilon);
		}
		return true;
	}
	
	private boolean checkConsRots() {
		if(!Utils.DEBUG || rotCoords[0] == null)
			return false;
		double[][][] rots = new double[rotCoords.length][][];
		for(int i = 0; i < rots.length; i++) {
			rots[i] = new double[rotCoords[i].length][];
			for(int j = 0; j < rots[i].length; j++)
				rots[i][j] = MathArrays.copyOf(rotCoords[i][j]);
		}
		calcAllRotations();
		for(int i = 0; i < rots.length; i++)
			for(int j = 0; j < rots[i].length; j++)
				for(int k = 0; k < rots[i][j].length; k++)
					if(Math.abs(rots[i][j][k]-rotCoords[i][j][k]) > 1e-5)
						throw new Error("Inconsistency in StructAlign, rotation "+i+","+j+","+k+": "+rots[i][j][k]+" vs "+rotCoords[i][j][k]);
		return true;
	}

	private boolean checkConsLogLike(double logli) {
		if(!Utils.DEBUG || curLogLike == 0)
			return false;
		if(Math.abs(logli-curLogLike) > 1e-5)
			throw new Error("Inconsistency in StructAlign, log-likelihood "+logli+" vs "+curLogLike);
		return true;
	}

	/**
	 * Calculates the structural likelihood contribution of a single alignment column
	 * @param col the column, id of the residue for each sequence (or -1 if gapped in column)
	 * @return the likelihood contribution
	 */
	public double columnContrib(int[] col) {
		// count the number of ungapped positions in the column
		int numMatch = 0;
		for(int i = 0; i < col.length; i++){
			if(col[i] != -1)
				numMatch++;
		}
		if(numMatch == 0) 
			return 1;
		// collect indices of ungapped positions
		int[] notgap = new int[numMatch];
		int j = 0;
		for(int i = 0; i < col.length; i++)
			if(col[i] != -1)
				notgap[j++] = i;
		
		// extract covariance corresponding to ungapped positions
		double[][] subCovar = Funcs.getSubMatrix(fullCovar, notgap, notgap);
		// create normal distribution with mean 0 and covariance subCovar
		MultiNormCholesky multiNorm = new MultiNormCholesky(new double[numMatch], subCovar);
		
		double logli = 0;
		double[] vals = new double[numMatch];
		// loop over all 3 coordinates
		
		/*System.out.println("Calculating log likelihood: ");
		System.out.println("Mean: " + Arrays.toString(multiNorm.getMeans()));
		System.out.println("Variance: " + Arrays.toString(subCovar[0]));*/
		for(j = 0; j < 3; j++){
			for(int i = 0; i < numMatch; i++)
				vals[i] = rotCoords[notgap[i]][col[notgap[i]]][j];
			//System.out.println("Values: " + Arrays.toString(vals));
			logli += multiNorm.logDensity(vals);
			//System.out.println("LL: " + multiNorm.logDensity(vals));
		}
		return logli;
	}

	/**
	 * extracts the specified rows and columns of a 2d array
	 * @param matrix, 2d array from which to extract; rows, rows to extract; cols, columns to extract
	 * @return submatrix
	 */
		
	private void calcAllRotations() {
		for(int i = 0; i < coords.length; i++)
			calcRotation(i);
	}
	
	public void calcRotation(int ind) {
		double[][] ci = coords[ind], rci = rotCoords[ind];
		if(rci == null)
			rci = rotCoords[ind] = new double[ci.length][];
		Rotation rot = new Rotation(new Vector3D(axes[ind]), angles[ind]);
		for(int i = 0; i < ci.length; i++) {
			rci[i] = rot.applyTo(new Vector3D(ci[i])).add(new Vector3D(xlats[ind])).toArray();
		}
	}
	// TODO Change visibility of this to package, after moving
	// StructAlign.java into statalign.model.ext.plugins.structalign.

	/**
	 * return the full covariance matrix for the tree topology and branch lengths
	 */	
	public double[][] calcFullCovar(Tree tree) {
		// I'm assuming that tree.names.length is equal to the number of vertices here
		double[][] distMat = new double[tree.names.length][tree.names.length];
		calcDistanceMatrix(tree.root, distMat);
		//System.out.print("Distance: " + distMat[0][1]);
		
		//System.out.println("Current tree:");
		//printTree(tree.root, "o");
		
		for(int i = 0; i < tree.names.length; i++)
			for(int j = i; j < tree.names.length; j++)
				distMat[j][i] = distMat[i][j] = tau * Math.exp(-distMat[i][j]);
		for(int i = 0; i < tree.names.length; i++)
			distMat[i][i] += epsilon;
		return distMat;
	}
	

	public void printTree(Vertex v, String vname){
		System.out.println(vname +"-" + v.name + ": " + v.edgeLength);
		if(v.left!=null){
			printTree(v.left, vname + "l");
			printTree(v.right, vname + "r");
		}
	}
	
	
	/**
	 * recursive algorithm to traverse tree and calculate distance matrix between leaves 
	 */		
	public int[] calcDistanceMatrix(Vertex vertex, double[][] distMat){
		int[] subTree = new int[distMat.length + 1];
		
		// either both left and right are null or neither is
		if(vertex.left != null){
			int[] subLeft  = calcDistanceMatrix(vertex.left, distMat);
			int[] subRight = calcDistanceMatrix(vertex.right, distMat);
			int i = 0;
			while(subLeft[i] > -1){
				subTree[i] = subLeft[i];
				i++;
			}
			for(int j = 0; i+j < subTree.length; j++)
				subTree[i+j] = subRight[j];
		}
		else{
			subTree[0] = vertex.index;
			for(int j = 1; j < subTree.length; j++)
				subTree[j] = -1;
		}

		if (globalSigma) {
			addEdgeLength(distMat, subTree, vertex.edgeLength * sigma2[0] / (2*tau));	
		}
		else {
			addEdgeLength(distMat, subTree, vertex.edgeLength * sigma2[vertex.index] / (2*tau));
		}
		/*System.out.println();
		System.out.println("Distmat:");
		for(int i = 0; i < distMat.length; i++)
			for(int j = 0; j < distMat[0].length; j++)
				System.out.println(distMat[i][j]);*/
		return subTree;
	}
		
	// adds the length of the current edge to the distance between all leaves
	// of a subtree to all other leaves
	// 'rows' contains the indices of vertices in the subtree
	public void addEdgeLength(double[][] distMat, int[] subTree, double edgeLength){
		
		int i = 0;
		while(subTree[i] > -1){
			for(int j = 0; j < distMat.length; j++){  
				distMat[subTree[i]][j] += edgeLength;
				distMat[j][subTree[i]] += edgeLength;
			}
			i++;		
		}
			
		// edge length should not be added to distance between vertices in the subtree
		// subtract the value from these entries of the distance matrix
		i = 0;
		while(subTree[i] > -1){
			int j = 0;
			while(subTree[j] > -1){
				distMat[subTree[i]][subTree[j]] -= edgeLength;
				distMat[subTree[j]][subTree[i]] -= edgeLength;
				j++;
			}
			i++;
		}
	}


	@Override
	public int getParamChangeWeight() {
		// TODO test converge and tune value
		return pluginProposalWeight;
	}

	@Override
	public double logLikeModExtParamChange(Tree tree, ModelExtension ext) {
		// current log-likelihood always precomputed (regardless of whether ext == this)
		return curLogLike;
	}
	
	@Override
	public double logLikeAlignChange(Tree tree, Vertex selectRoot) {
		oldAlign = curAlign;
		oldLogLi = curLogLike;
		curAlign = tree.getState().getLeafAlign();
		curLogLike = calcAllColumnContrib();
		return curLogLike;
	}
	
	@Override
	public void afterAlignChange(Tree tree, Vertex selectRoot, boolean accepted) {
		if(accepted)	// accepted, do nothing
			return;
		// rejected, restore
		curAlign = oldAlign;
		curLogLike = oldLogLi;
	}
	
	@Override
	public double logLikeTreeChange(Tree tree, Vertex nephew) {
		oldCovar = fullCovar;
		oldAlign = curAlign;
		oldLogLi = curLogLike;
		fullCovar = calcFullCovar(tree);
		curAlign = tree.getState().getLeafAlign();
		curLogLike = calcAllColumnContrib();
		return curLogLike;
	}
	
	@Override
	public void afterTreeChange(Tree tree, Vertex nephew, boolean accepted) {
		if(accepted)	// accepted, do nothing
			return;
		// rejected, restore
		fullCovar = oldCovar;
		curAlign = oldAlign;
		curLogLike = oldLogLi;
	}
	
	@Override
	public double logLikeEdgeLenChange(Tree tree, Vertex vertex) {
		// do exactly the same as for topology change
		return logLikeTreeChange(tree, vertex);
	}
	
	@Override
	public void afterEdgeLenChange(Tree tree, Vertex vertex, boolean accepted) {
		// do exactly the same as for topology change
		afterTreeChange(tree, vertex, accepted);
	}
	
	@Override
	public double logLikeIndelParamChange(Tree tree, Hmm hmm, int ind) {
		// does not affect log-likelihood
		return curLogLike;
	}
	
	@Override
	public double logLikeSubstParamChange(Tree tree, SubstitutionModel model,
			int ind) {
		// does not affect log-likelihood
		return curLogLike;
	}

	// </StructAlign>
}


