package gov.fda.nctr.xdagen.tests;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class TestingResources {

    private String metadataDir = "metadata";
    private String testdbsDir = "testdbs";
    private String expectedResultsDir = "expected_results";

    public String metadataResourcePath(String db, String resource_base_name)
    {
        return metadataDir + "/" + (db != null ? db+"_" : "") + resource_base_name;
    }

    public InputStream metadataResourceAsStream(String db, String resource_base_name)
    {
        return this.getClass().getClassLoader().getResourceAsStream(metadataResourcePath(db, resource_base_name));
    }

    public String expectedResultPath(String db, String resource_base_name)
    {
        return expectedResultsDir + "/" + (db != null ? db+"_" : "") + resource_base_name;
    }

    public InputStream expectedResultAsStream(String db, String resource_base_name)
    {
        return this.getClass().getResourceAsStream(expectedResultPath(db, resource_base_name));
    }

    public String expectedResultPath(String resource_base_name)
    {
        return expectedResultPath(null, resource_base_name);
    }

    public InputStream expectedResultAsStream(String resource_base_name)
    {
        return expectedResultAsStream(null, resource_base_name);
    }

    public String expectedResultAsString(String resource_base_name) throws IOException
    {
        return expectedResultAsString(null, resource_base_name);
    }

    public String expectedResultAsString(String db, String resource_base_name) throws IOException
    {
        return resourceAsString(expectedResultPath(db, resource_base_name));
    }


    public String testdbsResPath(String db, String resource_base_name)
    {
        return testdbsDir + "/" + (db != null ? db+"_" : "") + resource_base_name;
    }

    public String mdResStr(String db, String resource_base_name) throws IOException
    {
        return resourceAsString(metadataResourcePath(db, resource_base_name));
    }

    public String testResourcesClasspathBaseDir()
    {
        return "src/test/resources/";
    }


    public static String readStreamAsString(InputStream is) throws IOException
    {
        StringBuilder sb = new StringBuilder();

        final char[] buffer = new char[4096];
        Reader r = new InputStreamReader(is, "UTF-8");

        int n;
        while ( (n = r.read(buffer,0,buffer.length)) >= 0 )
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
        return readStreamAsString(
            TestingResources.class.getClassLoader().getResourceAsStream(resource_path)
        );
    }



}
