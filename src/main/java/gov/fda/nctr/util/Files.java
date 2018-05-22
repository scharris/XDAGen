package gov.fda.nctr.util;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Files
{
    public static void writeStringToFile(String s, String filePath) throws IOException
    {
        writeStringToFile(s, new File(filePath));
    }

    public static void writeStringToFile(String s, File file) throws IOException
    {
        try ( FileWriter fw = new FileWriter(file) )
        {
            fw.write(s);
        }
    }

}
