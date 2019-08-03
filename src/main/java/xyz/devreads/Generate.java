package xyz.devreads;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.FileSystems;
import java.util.Date;
import java.util.Properties;

/**
 * Project: artifinder
 * User: DevReads
 */
public class Generate {

    public static void main(String[] args){
        try
        {
            OkHttpClient client = new OkHttpClient();
            String workingDir = FileSystems.getDefault().getPath(".").toString();

            int exactFoundCount = 0;
            int notFoundCount = 0;
            int multipleFoundCount = 0;
            int outDatedCount = 0;
            int totalCount = 0;

            String configFilePath = "./config/application.properties";

            for (int i = 0; i < args.length; i++) {
                String string = args[i];
                if (string.startsWith("configFile")) {
                    configFilePath = string.split("=")[1];
                }
            }
            
            Properties prop = new Properties();

            try {
                InputStream is = new FileInputStream(configFilePath);
                prop.load(is);
            } catch (Exception ex) {
                System.out.println("Exception: "+ ex);
                return;
            }

            String repoUrl = prop.getProperty("url");
            String scanPath = prop.getProperty("libs");

            if(null==repoUrl || null==scanPath){
                System.out.println("url and scan path not declared in config file.");
                return;
            }

            String outputDir = workingDir + File.separator + "output" + File.separator;

            File directory = new File(outputDir);
            if (! directory.exists()){
                directory.mkdir();
            }

            String matchesFile = outputDir + "matches.xml";
            String multiMatchesFile = outputDir + "multi-matches.xml";
            String noMatchesFile = outputDir + "no-matches.xml";

            writeToFile(matchesFile, "------------------------------"+ new Date() +"----------------------------\n",false);
            writeToFile(matchesFile, "Started searching for artifacts at ["+scanPath+"]\n",true);
            writeToFile(matchesFile, "--------------------------------------------------------------------------------------\n",true);

            writeToFile(multiMatchesFile, "------------------------------"+ new Date() +"----------------------------\n",false);
            writeToFile(multiMatchesFile, "Started searching for artifacts at ["+scanPath+"]\n",true);
            writeToFile(multiMatchesFile, "--------------------------------------------------------------------------------------\n",true);

            writeToFile(noMatchesFile, "------------------------------"+ new Date() +"----------------------------\n",false);
            writeToFile(noMatchesFile, "Started searching for artifacts at ["+scanPath+"\n",true);
            writeToFile(noMatchesFile, "--------------------------------------------------------------------------------------\n",true);

            Process p=Runtime.getRuntime().exec("fciv "+scanPath+" -type *.jar -wp -sha1", null, new File(workingDir));

            BufferedReader reader=new BufferedReader( new InputStreamReader(p.getInputStream()));

            String line;
            while((line = reader.readLine()) != null) {
                if (line.startsWith("//")) continue;
                String[] output = line.split(" ");
                System.out.println();
                System.out.println("jar: " + output[1]);
                System.out.println("hash: " + output[0]);

                totalCount++;

                String response = doGetRequest(client, repoUrl + output[0]);

                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                InputSource src = new InputSource();
                src.setCharacterStream(new StringReader(response));

                Document doc = builder.parse(src);

                NodeList children = doc.getElementsByTagName("data").item(0).getChildNodes();

                int artifactCount = 0;
                for (int j = 0; j < children.getLength(); j++) {
                    Node child = children.item(j);
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) child;

                        String groupId = eElement.getElementsByTagName("groupId").item(0).getTextContent();
                        String artifactId = eElement.getElementsByTagName("artifactId").item(0).getTextContent();
                        String version = eElement.getElementsByTagName("version").item(0).getTextContent();
                        String latestRelease = eElement.getElementsByTagName("latestRelease").item(0).getTextContent();

                        String content = "<dependency>\n" +
                                "            <groupId>"+groupId+"</groupId>\n" +
                                "            <artifactId>ok"+artifactId+"http</artifactId>\n" +
                                "            <version>"+version+"</version>\n" +
                                "        </dependency>" +
                                "\n\n";

                        if(artifactCount==0) {

                            System.out.println("groupId: " + groupId);
                            System.out.println("artifactId: " + artifactId);
                            System.out.println("version: " + version);

                            writeToFile(matchesFile, content, true);

                            if(!version.equalsIgnoreCase(latestRelease)) {
                                outDatedCount++;
                                System.out.println("Outdated version used. current version: " + latestRelease);
                            }
                        } else{
                            writeToFile(multiMatchesFile, content, true);
                        }

                        artifactCount++;
                    }
                }

                if(artifactCount>1)
                {
                    multipleFoundCount++;
                    System.out.println("Found ["+artifactCount+"] matching artifacts. Picked the first one. Please review manually.");
                }
                else if(artifactCount==1)
                {
                    exactFoundCount++;
                } else
                {
                    notFoundCount++;
                    System.out.println("Artifact not found..");
                    writeToFile(noMatchesFile, output[1]+"\n", true);
                }
            }

            System.out.println("-----------------------------");
            System.out.println("        summary report       ");
            System.out.println("-----------------------------");
            System.out.println("Processed: " + totalCount);
            System.out.println("Found: " + (exactFoundCount + multipleFoundCount));
            System.out.println("Not found: " + notFoundCount);

            System.out.println("Exact matches: " + exactFoundCount);
            System.out.println("Multiple matches: " + multipleFoundCount);
            System.out.println("Outdated versions used: " + outDatedCount);


        }
        catch(Exception e1) {
            e1.printStackTrace();
        }

    }

    public static void writeToFile(String filePath, String content, boolean append){

        try(BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(filePath,append))) {
            bufferedWriter.write(content);
        } catch (IOException e) {
            // exception handling
        }
    }


    public static String doGetRequest(OkHttpClient client, String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }

}
