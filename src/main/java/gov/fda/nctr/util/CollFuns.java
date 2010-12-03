package gov.fda.nctr.util;

import java.util.ArrayList;
import java.util.List;


public class CollFuns {

	public static <K,V> List<Pair<K,V>> associativeListWithEntry(final List<Pair<K,V>> l, final K k, final V v)
	{
		List<Pair<K,V>> res = new ArrayList<Pair<K,V>>();
		
		Pair<K,V> new_entry = Pair.make(k,v);
		
		boolean added = false;
		for(Pair<K,V> entry: l)
		{
			if (entry.fst().equals(k))
			{
				res.add(new_entry);
				added = true;
			}
			else
				res.add(entry);
		}
		
		if ( !added )
			res.add(new_entry);
		
		return res;
	}

	public static <E> List<E> snoc(List<E> l, E e)
	{
	    List<E> cl = new ArrayList<E>(l);
	    cl.add(e);
	    return cl;
	}

}
