package gov.fda.nctr.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;


public class StringFuns {

	public static String makeNameNotInSet(String basename, Set<String> existing_names)
	{
		if ( !existing_names.contains(basename) )
			return basename;
		else
		{
			int i = 1;
			while(existing_names.contains(basename + i))
				++i;
			return basename + i;
		}
	}

	public static String lowercaseInitials(String name, String seperator)
	{
		StringBuilder sb = new StringBuilder();
		
		for(String word: name.split(seperator))
		{
			if ( word.length() > 0 )
				sb.append(word.charAt(0));
		}
		
		return sb.toString().toLowerCase();
	}
	
	public static String splitAndCapitalized(String name, String seperator)
	{
		StringBuilder sb = new StringBuilder();
		
		for(String word: name.split(seperator))
		{
			if ( word.length() > 0 )
			{
				if ( sb.length() > 0 )
					sb.append(' ');
				sb.append(capitalize(word.toLowerCase()));
			}
		}
		
		return sb.toString();
	}

	
	public static String capitalize(String s)
	{
		return s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase();
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

	public static List<String> dotQualify(List<String> names, String maybe_alias)
	{
		if ( maybe_alias == null )
			return names;
		else
		{
			List<String> qnames = new ArrayList<String>(names.size());
			
			for(String name: names)
				qnames.add((maybe_alias != null ? maybe_alias + "." : "") + name);
			
			return qnames;
		}
	}

	public static String indent(String lines_str, String with_str, boolean indent_first_line)
	{
		return (indent_first_line ? with_str : "") + lines_str.replaceAll("\n", "\n" + with_str);
	}

	public static String indent(String lines_str, String with_str)
	{
		return indent(lines_str, with_str, true);
	}

	public static String readStreamAsString(InputStream is) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		
		final char[] buffer = new char[4096];
		Reader r = new InputStreamReader(is, "UTF-8");
		
		int n;
		while( (n = r.read(buffer,0,buffer.length)) >= 0 )
		{
			sb.append(buffer, 0, n);
		}
		
		return sb.toString();
	}
	
	public static String readStreamAsString(Reader r) throws IOException
	{
		StringBuilder sb = new StringBuilder();
		
		final char[] buffer = new char[4096];
		
		int n;
		while( (n = r.read(buffer,0,buffer.length)) >= 0 )
		{
			sb.append(buffer, 0, n);
		}
		
		return sb.toString();
	}
	
	public static String resourceAsString(String resource_path) throws IOException
	{
		return StringFuns.readStreamAsString(StringFuns.class.getClassLoader().getResourceAsStream(resource_path));
	}
	

	public static String stringFrom(Collection<?> items,
	                                String sep)
	{
		return stringFrom(items, sep, null, null, null);
	}
	
    // ----------------------------------------------------------
    // Nulls are printed as null_rep if it's not null, if it's null then it's as if the nulls weren't in the list
    // The prefix and suffix if present are only applied for non-null items. 
    public static String stringFrom(Collection<?> items,
                                    String sep, 
                                    String items_prefix,
                                    String items_suffix,
                                    String null_rep)
    {
        StringBuilder buf = new StringBuilder(10 * items.size());
        
        boolean past_first = false;
    
        for (Object item: items)
        {
            if ( item == null && null_rep == null )
                continue;
            
            if ( !past_first )
                past_first = true;
            else
                buf.append(sep);
            
            if ( item != null )
            {
                String item_str = item.toString();
                
                if ( items_prefix != null )
                {
                    // Replace $$ in items prefix with the item string value
                    if ( items_prefix.indexOf("$el") != -1 )
                        buf.append(items_prefix.replaceAll("\\$el",item_str));
                    else
                        buf.append(items_prefix);
                }
                
                buf.append(item_str);
                
                if ( items_suffix != null )
                {
                    // Replace $e in items suffix with the item string value
                    if ( items_suffix.indexOf("$el") != -1 )
                        buf.append(items_suffix.replaceAll("\\$el",item_str));
                    else
                        buf.append(items_suffix);
                }
            }
            else
                buf.append(null_rep);
        }
        
        return buf.toString();
    }
    
    public static List<String> lc(List<String> ss)
    {
    	ArrayList<String> l = new ArrayList<String>();
    	
    	for(String s: ss)
    		l.add(s.toLowerCase());
    	
    	return l;
    }

	public static void writeStringToFile(String s, String file_path) throws IOException
	{
		writeStringToFile(s, new File(file_path));
	}
	
	public static void writeStringToFile(String s, File file) throws IOException
	{
		FileWriter fw = new FileWriter(file);
		fw.write(s);
		fw.close();
	}

}
