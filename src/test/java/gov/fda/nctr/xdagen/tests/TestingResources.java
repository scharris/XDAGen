package gov.fda.nctr.xdagen.tests;

import static gov.fda.nctr.util.StringFuns.resourceAsString;

import java.io.IOException;
import java.io.InputStream;

public class TestingResources {

    String metadataDir = "metadata";
    String testdbsDir = "testdbs";
    String expectedResultsDir = "expected_results";

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
}
