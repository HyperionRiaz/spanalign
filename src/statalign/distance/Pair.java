package statalign.distance;


/**
 * A Pair container.
 * 
 * @author ingolfur
 *
 * @param <L>	Element to the left
 * @param <R>	Element to the right
 */
public class Pair<L,R>  {

	  private final L left;
	  private final R right;

	  public Pair(L left, R right) {
	    this.left = left;
	    this.right = right;
	  }

	  public L getLeft() { return left; }
	  public R getRight() { return right; }

	  @Override
	  public boolean equals(Object o) {
	    if (o == null) return false;
	    if (!(o instanceof Pair)) return false;
	    Pair pairo = (Pair) o;
	    return this.left.equals(pairo.getLeft()) &&
	           this.right.equals(pairo.getRight());
	  }
	  
	  @Override
	  public String toString(){
		  return "(" + this.getLeft() + "," + this.getRight() + ")";
	  }
	  
	}
