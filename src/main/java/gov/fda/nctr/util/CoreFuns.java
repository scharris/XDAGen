package gov.fda.nctr.util;



public class CoreFuns {

	public final static int hashcode(Object o)
	{
		if ( o == null )
			return 0;
		else
			return o.hashCode();
	}
	
	public static final <E> E requireArg(E obj, String descr)
	{
		if ( obj == null )
			throw new IllegalArgumentException("Missing required argument: " + descr);
		else
			return obj;
	}
	
	public static final boolean eqOrNull(Object o1, Object o2)
	{
		if ( o1 == null )
			return o2 == null;
		else
			return o1.equals(o2);
	}
	
	public static <E> E nvl(E e1, E e2)
	{
		return e1 != null ? e1 : e2;
	}
	
}
