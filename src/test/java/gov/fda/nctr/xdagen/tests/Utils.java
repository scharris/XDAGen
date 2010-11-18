package gov.fda.nctr.xdagen.tests;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class Utils {

	public static Properties loadProperties(String props_resource_path) throws IOException
	{
		InputStream is = null;
		try
		{
			is = Utils.class.getClassLoader().getResourceAsStream(props_resource_path);
		
			Properties p = new Properties();
			p.load(is);
			
			return p;
		}
		finally
		{
			is.close();
		}
	}
	
}
