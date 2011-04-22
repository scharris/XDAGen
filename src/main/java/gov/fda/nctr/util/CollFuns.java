package gov.fda.nctr.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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


    public static <X> List<X> sorted(Collection<X> xs, Comparator<X> c)
    {
        List<X> sorted_xs = new ArrayList<X>(xs);
        Collections.sort(sorted_xs, c);
        return sorted_xs;
    }

    public static <X extends Comparable<X>> List<X> sorted(Collection<X> xs)
    {
        List<X> sorted_xs = new ArrayList<X>(xs);
        Collections.sort(sorted_xs);
        return sorted_xs;
    }

    public static <X> List<List<X>> partitionBy(Collection<X> xs, Comparator<X> c)
    {
        List<List<X>> parts = new ArrayList<List<X>>();

        List<X> part = new ArrayList<X>();

        for(X x: sorted(xs,c))
        {
            if ( part.size() == 0 || c.compare(x, part.get(0)) == 0 )
                part.add(x);
            else
            {
                parts.add(part);
                part = new ArrayList<X>();
                part.add(x);
            }
        }

        if ( part.size() > 0 )
            parts.add(part);

        return parts;
    }

    public static <X> Set<X> setMinus(Set<X> xs1, Set<X> xs2)
    {
        Set<X> xs = new HashSet<X>(xs1);
        xs.removeAll(xs2);
        return xs;
    }
}
