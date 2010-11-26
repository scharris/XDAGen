package gov.fda.nctr.util;

import java.io.Serializable;


/*
 * Created on Jan 28, 2005
 * @author sharris 
 */

public class Pair<C1,C2> implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private C1 fst;
    private C2 snd;
    
    public Pair(C1 fst, C2 snd)
    {
        this.fst = fst;
        this.snd = snd;
    }
 
    public C1 fst() { return fst; }
    
    public C2 snd() { return snd; }
    
    public static <C1,C2> Pair<C1,C2> make(C1 fst, C2 snd)
    {
        return new Pair<C1,C2>(fst, snd);
    }
    
	public boolean equals(Object other)
    {
        if ( other == null )
            return false;

        @SuppressWarnings("unchecked")
		Pair p = (Pair)other;
        
        return (fst == null && p.fst == null || fst != null && p.fst != null && fst.equals(p.fst)) &&
               (snd == null && p.snd == null || snd != null && p.snd != null && snd.equals(p.snd));
    }
    
    public int hashCode()
    {
        return (fst != null ? fst.hashCode() : 0 ) + 
               (snd != null ? snd.hashCode() : 0);
    }
    
    public String toString()
    {
        return "(" + fst + "," + snd + ")";
    }
}
