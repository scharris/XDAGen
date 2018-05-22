package gov.fda.nctr.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


public class StringFuns {

    public static String makeNameNotInSet(String baseName, Set<String> existingNames)
    {
        if ( !existingNames.contains(baseName) )
            return baseName;
        else
        {
            int i = 1;
            while(existingNames.contains(baseName + i))
                ++i;
            return baseName + i;
        }
    }

    public static String lowercaseInitials(String name, String sep)
    {
        StringBuilder sb = new StringBuilder();

        for ( String word: name.split(sep) )
        {
            if ( word.length() > 0 )
                sb.append(word.charAt(0));
        }

        return sb.toString().toLowerCase();
    }

    public static String camelCase(String name)
    {
        StringBuilder res = new StringBuilder();
        for (String word : name.split("_"))
        {
            res.append(Character.toUpperCase(word.charAt(0)));
            res.append(word.substring(1).toLowerCase());
        }
        return res.toString();
    }

    public static String camelCaseInitialLower(String name)
    {
        StringBuilder res = new StringBuilder();
        for (String word : name.split("_"))
        {
            res.append(res.length() == 0 ? Character.toLowerCase(word.charAt(0)) : Character.toUpperCase(word.charAt(0)));
            res.append(word.substring(1).toLowerCase());
        }
        return res.toString();
    }

    public static List<String> dotQualify(List<String> names, String maybeAlias)
    {
        if ( maybeAlias == null )
            return names;
        else
        {
            List<String> qnames = new ArrayList<>(names.size());

            for(String name: names)
                qnames.add(maybeAlias + "." + name);

            return qnames;
        }
    }

    public static String indent(String linesStr, String withStr, boolean indentFirstLine)
    {
        return
            (indentFirstLine ? withStr : "") +
            linesStr.replaceAll("\n", "\n" + withStr);
    }

    public static String indent(String linesStr, String withStr)
    {
        return indent(linesStr, withStr, true);
    }
    public static String stringFrom(Collection<?> items, String sep)
    {
        return stringFrom(items, sep, null, null, null);
    }

    // ----------------------------------------------------------
    // Nulls are printed as nullRep if it's not null, if it's null then it's as if the nulls weren't in the list
    // The prefix and suffix if present are only applied for non-null items.
    public static String stringFrom
    (
        Collection<?> items,
        String sep,
        String itemsPrefix,
        String itemsSuffix,
        String nullRep
    )
    {
        StringBuilder buf = new StringBuilder(10 * items.size());

        boolean past_first = false;

        for (Object item: items)
        {
            if ( item == null && nullRep == null )
                continue;

            if ( !past_first )
                past_first = true;
            else
                buf.append(sep);

            if ( item != null )
            {
                String item_str = item.toString();

                if ( itemsPrefix != null )
                {
                    // Replace $$ in items prefix with the item string value
                    if (itemsPrefix.contains("$el"))
                        buf.append(itemsPrefix.replaceAll("\\$el",item_str));
                    else
                        buf.append(itemsPrefix);
                }

                buf.append(item_str);

                if ( itemsSuffix != null )
                {
                    // Replace $e in items suffix with the item string value
                    if (itemsSuffix.contains("$el"))
                        buf.append(itemsSuffix.replaceAll("\\$el",item_str));
                    else
                        buf.append(itemsSuffix);
                }
            }
            else
                buf.append(nullRep);
        }

        return buf.toString();
    }

    public static List<String> lc(List<String> ss)
    {
        ArrayList<String> l = new ArrayList<>();

        for ( String s: ss )
            l.add(s.toLowerCase());

        return l;
    }

}
