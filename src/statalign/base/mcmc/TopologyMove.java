package statalign.base.mcmc;

import statalign.base.AlignColumn;
import statalign.base.Tree;
import statalign.base.Utils;
import statalign.base.Vertex;
import statalign.mcmc.McmcModule;
import statalign.mcmc.McmcMove;
import statalign.mcmc.PriorDistribution;
import statalign.mcmc.ProposalDistribution;

public class TopologyMove extends McmcMove {

	Tree tree = null;
	int vnum;
	Vertex nephew;
	Vertex uncle;
	
	public TopologyMove (McmcModule m, String n) {
		owner = m;
		name = n;
		autoTune = false;
	}

	public void copyState(Object externalState) {
		// This is handled inside the Vertex
	}
	public double proposal(Object externalState) {
		int vertId, rnd = Utils.generator.nextInt(vnum - 3);
		vertId = tree.getTopVertexId(rnd);
		if (vertId != -1) {
			int lastId[] = new int[3], num = 0, newId = vertId;
	
			for (int i = vnum - 3; i < vnum; i++) {
				int id = tree.getTopVertexId(i);
				if (id == -1)
					lastId[num++] = i;
				else if (id < vertId)
					newId--;
			}
			rnd = lastId[newId];
		}
		nephew = tree.vertex[rnd];
		uncle = nephew.parent.brother();
		((CoreMcmcModule) owner).getModelExtMan().beforeTreeChange(tree, nephew);		
		// Should also do a beforeAlignChange here, but not obvious what to pass
		// as the selectedRoot argument.
		double logProposalRatio = nephew.fastSwapWithUncle();
		// Below is another version, slow and slightly better mixing
		// double logProposalRatio = nephew.swapWithUncleAlignToParent();
		return logProposalRatio;
	}
	public double logPriorDensity(Object externalState) {
		return 0.0;
	}
	public void updateLikelihood(Object externalState) {
		owner.setLogLike(((CoreMcmcModule) owner).getModelExtMan().logLikeTreeChange(tree, nephew));
	}
	public void restoreState(Object externalState) {
		uncle.fastSwapBackUncle();
		// If using the alternative move:
        // uncle.swapBackUncleAlignToParent();
	}
	
	public void move(Object externalState) {
//		if (Utils.DEBUG) {
//			System.out.println("TopologyMove");
//		}
		if (externalState instanceof Tree) {
			if (tree == null) {
				tree = (Tree) externalState;
			}
		}
		else {
			throw new IllegalArgumentException("AlignmentMove.copyState must take an argument of type Tree.");
		}
		vnum = tree.vertex.length;
		if (vnum <= 3) {
			return;
		}
		
		super.move(externalState);
		((CoreMcmcModule) owner).getModelExtMan().afterTreeChange(tree,lastMoveAccepted ? uncle : nephew,lastMoveAccepted);
		// Should also do an afterAlignChange here, but not obvious what to pass
		// as the selectedRoot argument.
		
		if (Utils.DEBUG) {
			for (int i = 0; i < tree.vertex.length; i++) {
				if (tree.vertex[i].left != null && tree.vertex[i].right != null) {
					tree.vertex[i].checkPointers();
					AlignColumn p;
					// checking pointer integrity
					for (AlignColumn c = tree.vertex[i].left.first; c != null; c = c.next) {
						p = tree.vertex[i].first;
						while (c.parent != p && p != null)
							p = p.next;
						if (p == null)
							throw new Error(
									"children does not have a parent!!!"
											+ tree.vertex[i] + " "
											+ tree.vertex[i].print());
					}
					for (AlignColumn c = tree.vertex[i].right.first; c != null; c = c.next) {
						p = tree.vertex[i].first;
						while (c.parent != p && p != null)
							p = p.next;
						if (p == null)
							throw new Error(
									"children does not have a parent!!!"
											+ tree.vertex[i] + " "
											+ tree.vertex[i].print());
					}
	
				}
			}
		}
	}
	
	 
	
}