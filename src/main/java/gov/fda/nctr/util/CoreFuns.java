package gov.fda.nctr.util;



public class CoreFuns {

    public static int hashcode(Object o)
    {
        if ( o == null ) return 0;
        else return o.hashCode();
    }

    public static <E> E requireArg(E obj, String descr)
    {
        if ( obj == null ) throw new IllegalArgumentException("Missing required argument: " + descr);
        else return obj;
    }

}
