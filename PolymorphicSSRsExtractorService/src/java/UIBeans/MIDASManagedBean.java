/*
* To change this license header, choose License Headers in Project Properties.
* To change this template uploadedFile, choose Tools | Templates
* and open the template in the editor.
*/
package UIBeans;

import java.awt.HeadlessException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import static java.lang.Thread.sleep;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import javax.annotation.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import other_resources.BrowserControl;
import org.apache.commons.io.FilenameUtils;
import org.apache.poi.util.IOUtils;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import other_resources.StringComparator;
import pssrextractor.Generic_Result;
import pssrextractor.HtBLASTParser_Unit;
import pssrextractor.Polymorphism;
import pssrextractor.SSr;
import pssrextractor.Specific_Result;

/**
 *
 * @author carlos2
 */
@ManagedBean
@SessionScoped
public class MIDASManagedBean implements Serializable{
    
    /**
     * Creates a new instance of MIDASManagedBean
     */
    private UploadedFile uploadedFile;
    private String model;
    private int MaxUnit=6;
    private String results;
    private File result;
    private InputStream Input;
    private String MIDASInput="";
    private String organism;
    private int maxTargetSequences=100;
    private int expectThreshold=30;
    private float identityPercent=90;
    private float coveragePercent=90;
    private ArrayList<Specific_Result> specificResults;
    private ArrayList<Generic_Result> genericResults;
    private ArrayList<HtBLASTParser_Unit> hitTable;
    
    public ArrayList<String> search(String term){
        
        ArrayList<String> searchResult=new ArrayList<>(100);
        
        try {
            try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/taxadb","root","")) {
                
                Statement s=c.createStatement();
                ResultSet r;
                
                term=term.toLowerCase();
                
                if(term.charAt(0)=='a')
                    r=s.executeQuery("SELECT * FROM namea WHERE taxaname LIKE '"+term+"%' LIMIT 100");
                else if(term.charAt(0)=='b' || term.charAt(0)=='c')
                    r=s.executeQuery("SELECT * FROM namebc WHERE taxaname LIKE '"+term+"%' LIMIT 100");
                else if(term.charAt(0)>='d' && term.charAt(0)<='g')
                    r=s.executeQuery("SELECT * FROM namedg WHERE taxaname LIKE '"+term+"%' LIMIT 100");
                else if(term.charAt(0)>='h' && term.charAt(0)<='k')
                    r=s.executeQuery("SELECT * FROM namehk WHERE taxaname LIKE '"+term+"%' LIMIT 100");
                else if(term.charAt(0)>='l' && term.charAt(0)<='o')
                    r=s.executeQuery("SELECT * FROM namelo WHERE taxaname LIKE '"+term+"%' LIMIT 100");
                else if(term.charAt(0)=='p' || term.charAt(0)=='q')
                    r=s.executeQuery("SELECT * FROM namepq WHERE taxaname LIKE '"+term+"%' LIMIT 100");
                else if(term.charAt(0)=='r' || term.charAt(0)=='s')
                    r=s.executeQuery("SELECT * FROM namers WHERE taxaname LIKE '"+term+"%' LIMIT 100");
                else 
                    r=s.executeQuery("SELECT * FROM nametz WHERE taxaname LIKE '"+term+"%' LIMIT 100");
                
                while(r.next())
                    searchResult.add(r.getString("taxaname"));
                
                searchResult.sort(new StringComparator());
            }
        } catch (SQLException ex) {
            
            System.out.println("Connection fail");
        }
        
        return searchResult;
    }
    
    public String doNavigation(){
        
        if((uploadedFile!=null && !MIDASInput.equals("")) || organism==null)
            return "error";
        else if (uploadedFile!=null || !MIDASInput.equals("")) {
            
            String filename;
            String basename;
            String extension;
            
            if(uploadedFile!=null){
                filename = FilenameUtils.getName(uploadedFile.getFileName());
                basename = FilenameUtils.getBaseName(filename) + "_";
                extension = "." + FilenameUtils.getExtension(filename);
            }
            else{
                filename="MIDASImput";
                basename =filename + "_";
                extension=".fasta";
            }
            
            File file=null;
            try {
                
                file = File.createTempFile(basename, extension,new File(FacesContext.getCurrentInstance().getExternalContext().getRealPath(File.separator+"resources"+File.separator+"tempfiles"+File.separator)));
                
            } catch (IOException ex) {
            }
            
            if(uploadedFile!=null){
                
                InputStream input=null;
                try {
                    input = uploadedFile.getInputStream();
                } catch (IOException ex) {
                }
                
                FileOutputStream output=null;
                try {
                    output = new FileOutputStream(file);
                } catch (FileNotFoundException ex) {
                }
                
                try {
                    try {
                        IOUtils.copy(input, output);
                    } catch (IOException ex) {
                    }
                } finally {
                    IOUtils.closeQuietly(input);
                    IOUtils.closeQuietly(output);
                }
            }
            else{
                try {
                    try (FileWriter fw = new FileWriter(file)) {
                        
                        if(MIDASInput.charAt(0)!='>')
                            MIDASInput="> \n"+MIDASInput;
                        
                        fw.write(MIDASInput);
                    }
                } catch (IOException ex) {
                }
            }
            
            String[] command = new String[]{FacesContext.getCurrentInstance().getExternalContext().getRealPath(File.separator+"resources"+File.separator+"MIDAS"+File.separator+"midasweb_v1.0.exe"), file.getAbsolutePath(), String.valueOf(this.getMaxUnit()), this.getModel()};
            ArrayList<String> toProcess=new ArrayList();
            
            try {
                
                Process process = Runtime.getRuntime().exec(command);
                
                try (Scanner scanner = new Scanner(process.getInputStream())) {
                    
                    if(!scanner.hasNext())
                    {
                        file.delete();
                        return "error";
                    }
                    
                    scanner.useDelimiter("\r\n");
                    
                    results="";
                    
                    while (scanner.hasNext()){
                        
                        String r=scanner.next()+"\n";
                        
                        results+=r;
                        toProcess.add(r);
                    }
                    file.delete();
                }
            } catch (IOException ex) {
                System.out.println("midasweb_v1.0.exe problem");
            }
            
            List<String> seq_array = new ArrayList<>();
            String seq="";
            int count = 1;
            Iterator<String> s=toProcess.iterator();
            
            seq=s.next();
            while (s.hasNext()){
                count++;
                seq += s.next();
                if (count == 200) {seq_array.add(seq); seq = ""; count=0;}
            }
            if (!"".equals(seq)) seq_array.add(seq);
            
            specificResults=new ArrayList();
            genericResults=new ArrayList();
            
            for (String seq1 : seq_array) {
                try {
                    
                    String req = "";
                    
                    while ("".equals(req = get_RID_RTOE(seq1)))
                        sleep(3500);
                    
                    BrowserControl.displayURL(req);
                    
                    while ((hitTable = getHitTable(req)) == null)
                        sleep(3500);
                    
                    
                    specificResults.addAll(Polymorphism.evaluate(hitTable, identityPercent, coveragePercent));
                    
                    genericResults.addAll(Polymorphism.evaluate(specificResults));
                    
                } catch (Exception ex) {
                    ex.getMessage();
                }
            }
            
            return "result";
        }
        else
            return "error";
    }
    
    public String get_RID_RTOE(String seq) throws Exception{
        String url2 = "";
        boolean rid = false;
        String data = constructRequest(seq,"\""+organism+"\"[organism]",
                Integer.toString(maxTargetSequences),
                Integer.toString(expectThreshold));
        
        try{
            // Send data
            URL url = new URL("https://blast.ncbi.nlm.nih.gov/Blast.cgi");
            URLConnection conn = url.openConnection();
            conn.setDoOutput(true);
            BufferedReader rd;
            String urlResults;
            String eta;
            
            try (OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream())) {
                wr.write(data);
                wr.flush();
                rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                urlResults = url + "?RID=";
                String line;
                eta = "10";
                while ((line = rd.readLine()) != null){
                    int index;
                    if ((index = line.indexOf("RID =")) > -1){
                        line = line.substring(index + 5).trim();
                        urlResults = urlResults.concat(line);
                        rid = true;
                        if (line.equals("")){
                            sleep(100);
                            System.err.println(urlResults + "Blast search error");
                            
                        }
                    }
                    else if ((index = line.indexOf("RTOE =")) > -1){
                        eta = line.substring(index + 6).trim();
                        System.err.println(line);
                        sleep(100);
                    }
                    
                    sleep(100);
                }
            }
            rd.close();
            int waitTime = Integer.parseInt(eta);
            Thread.sleep(waitTime * 1000);
            if (rid){
                url2 = urlResults + "&RESULTS_FILE=on&OLD_BLAST=false" + "&FORMAT_TYPE=Text&FORMAT_OBJECT=Alignment&ALIGNMENT_VIEW=Tabular&CMD=Get";
                System.err.println(url2);
            }
            else url2="";
        }
        catch (HeadlessException | IOException | InterruptedException | NumberFormatException e){
            e.getMessage();
        }
        return url2;
    }
    
    public ArrayList<HtBLASTParser_Unit> getHitTable(String url) throws MalformedURLException, IOException {
        
        ArrayList<HtBLASTParser_Unit> units=new ArrayList<>();
        ArrayList<String> lecture=new ArrayList<>();
        List<String> check=new ArrayList<>();
        List<String> qav=new ArrayList<>();
        List<String> stream =new ArrayList<>();
        List<String> headers=new ArrayList<>();
        boolean hit_rend= false;
        boolean rend_excp= false;
        String html_chk="";
        boolean html_lg=true;
        BufferedReader rd=null;
        
        try {
            sleep(100);
            while(html_lg){
                URL url2 = new URL(url);
                URLConnection conn = url2.openConnection();
                conn.setConnectTimeout(10000);
                conn.setDoOutput(true);
                rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                
                html_chk = rd.readLine();
                if (!html_chk.contains("# blastn")){
                    html_lg=true;
                    sleep(3500);
                }
                else  html_lg = false;
            }
            
            
        }catch(IOException | InterruptedException e){
            e.getMessage();
            rend_excp = true;
        }
        
        if (rend_excp) {units = null; return units;}
        
        String s;
        
        while((s=rd.readLine()) != null){
            stream.add(s);
        }
        
        hit_rend = true;
        if (hit_rend){
            
            String checkHitTable = stream.toString();
            
            if(!checkHitTable.contains("subject acc.ver") ||
                    !checkHitTable.contains("% identity") ||
                    !checkHitTable.contains("q. start") ||
                    !checkHitTable.contains("q. end") ||
                    !checkHitTable.contains("s. start") ||
                    !checkHitTable.contains("s. end") ||
                    !checkHitTable.contains("evalue")){
                return null;
            }
            
            for(String s1:stream){
                if(s1.contains("#"))
                {
                    if(s1.startsWith("# Query:") && !check.contains(s1))
                        check.add(s1);
                    if( s1.equals("# blastn") && headers.size()==5)
                    {
                        lecture.add(headers.get(2));
                        headers.clear();
                        headers.add(s1);
                    }
                    else
                        headers.add(s1);
                }
                else if(!s1.equals("") && !s1.contains("#"))
                {
                    lecture.add(s1);
                    headers.clear();
                }
            }
            for(String value:lecture)
            {
                if(value.startsWith("# Query:"))
                    units.add(new HtBLASTParser_Unit(value.substring(9),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null));
                else{
                    
                    String[] lectemp=value.split("\t");
                    
                    units.add(new HtBLASTParser_Unit(lectemp[0],
                            lectemp[1],
                            Float.parseFloat(lectemp[2]),
                            Integer.parseInt(lectemp[3]),
                            Integer.parseInt(lectemp[4]),
                            Integer.parseInt(lectemp[5]),
                            Integer.parseInt(lectemp[6]),
                            Integer.parseInt(lectemp[7]),
                            Integer.parseInt(lectemp[8]),
                            Integer.parseInt(lectemp[9]),
                            Float.parseFloat(lectemp[10]),
                            Float.parseFloat(lectemp[11])));
                }
            }
            
            for (HtBLASTParser_Unit u: units)
            {
                if(!qav.contains(u.getQuery_acc_ver()))
                    qav.add(u.getQuery_acc_ver());
            }
            
            if(qav.size()<check.size())
                units.add(new HtBLASTParser_Unit(check.get(check.size()-1).substring(9),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
        }
        else units = null;
        
        return units;
    }
    
    public void clear(){
        
        result.delete();
        
        try {
            Input.close();
        } catch (IOException ex) {
        }
    }
    
    private static String addData(String data, String key, String value)throws UnsupportedEncodingException  {
        
        data += "&" +URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8");
        return data;
    }
    
    private static String addUnits(String data){
        
        String[] lecture=data.split("\n");
        String result="";
        SSr query=null;
        String temp=null;
        
        for (int i = 0, j=1; i < lecture.length; i+=2,j+=2) {
            
            query=new SSr(lecture[i].substring(1),"");
            result+=lecture[i]+"\n";
            
            if(lecture[j].length()<60){
                
                temp=query.getF5().toUpperCase()+query.getPattern();
                
                do{
                    
                    temp+=query.getPattern();
                    
                }while(temp.length()<40);
                
                temp+=query.getF3().toUpperCase();
                
                result+=temp+"\n";
            }
            else
                result+=lecture[j]+"\n";
        }
        
        return result;
    }
    
    public static String constructRequest( String residues, String e_query, String hitListSize, String expect) throws UnsupportedEncodingException, Exception{
        
        String request = URLEncoder.encode("CMD", "UTF-8") + "=" + URLEncoder.encode("Put", "UTF-8");
        request = addData(request, "QUERY_BELIEVE_DEFLINE", "false");
        request = addData(request, "QUERY", addUnits(residues));
        request = addData(request, "DATABASE", "nr");
        request = addData(request, "ENTREZ_QUERY", e_query);
        request = addData(request, "LCASE_MASK", "true");
        //Number of hits to keep 500
        request = addData(request, "HITLIST_SIZE", hitListSize);
        // Filter: String[]{"None", "Low Complexity", "Human Repeats", "Masked" }
        request = addData(request, "FILTER", "F");
        //Expect value 10.0
        if(Float.parseFloat(expect)<30)
            throw new Exception("expect must be >= 30");
        else
            request = addData(request, "EXPECT", expect);
        //HTML, XML, Text, Tabulear, etc.
        request = addData(request, "FORMAT_TYPE", "Tabular");
        request = addData(request, "PROGRAM", "blastn");
        request = addData(request, "CLIENT", "web");
        request = addData(request, "BLAST_PROGRAM", "blastn");
        return request;
    }
    
    public StreamedContent getFile() {
        
        Input=null;
        result=new File("C:"+File.separator+"Users"+File.separator+"Alejandro"+File.separator+"Documents"+File.separator+"NetBeansProjects"+File.separator+"PolymorphicSSRsExtractorService"+File.separator+"web"+File.separator+"resources"+File.separator+"tempfiles"+File.separator+uploadedFile.getFileName()+"_MIDAS_result.mfaa");
        
        try {
            try (FileWriter fw = new FileWriter(result)) {
                
                fw.write(results);
            }
        } catch (IOException ex) {
        }
        
        try {
            Input= new FileInputStream(result);
        } catch (FileNotFoundException ex) {
        }
        
        return new DefaultStreamedContent(Input, "text/plain", result.getName());
    }
    
    public int getMaxUnit() {
        return MaxUnit;
    }
    
    public UploadedFile getUploadedFile() {
        
        return uploadedFile;
    }
    
    public String getModel() {
        return model;
    }
    
    public String getResults() {
        return results;
    }
    
    public String getOrganism() {
        return organism;
    }
    
    public int getMaxTargetSequences() {
        return maxTargetSequences;
    }
    
    public int getExpectThreshold() {
        return expectThreshold;
    }
    
    public float getIdentityPercent() {
        return identityPercent;
    }
    
    public float getCoveragePercent() {
        return coveragePercent;
    }
    
    public ArrayList<HtBLASTParser_Unit> getHitTable() {
        return hitTable;
    }
    
    public String getMIDASInput() {
        return MIDASInput;
    }
    
    public void setMIDASInput(String MIDASInput) {
        this.MIDASInput = MIDASInput;
    }
    
    public void setUploadedFile(UploadedFile uploadedFile) {
        this.uploadedFile = uploadedFile;
    }
    
    public void setMaxUnit(int MaxUnit) {
        this.MaxUnit = MaxUnit;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public void setHitTable(ArrayList<HtBLASTParser_Unit> hitTable) {
        this.hitTable = hitTable;
    }
    
    public void setOrganism(String Organism) {
        this.organism = Organism;
    }
    
    public void setMaxTargetSequences(int MaxTargetSequences) {
        this.maxTargetSequences = MaxTargetSequences;
    }
    
    public void setExpectThreshold(int ExpectThreshold) {
        this.expectThreshold = ExpectThreshold;
    }
    
    public void setIdentityPercent(float IdentityPercent) {
        this.identityPercent = IdentityPercent;
    }
    
    public void setCoveragePercent(float CoveragePercent) {
        this.coveragePercent = CoveragePercent;
    }
    
    public void setSpecificResults(ArrayList<Specific_Result> specificResults) {
        this.specificResults = specificResults;
    }
    
    public void setGenericResults(ArrayList<Generic_Result> genericResults) {
        this.genericResults = genericResults;
    }
    
    public ArrayList<Specific_Result> getSpecificResults() {
        return specificResults;
    }
    
    public ArrayList<Generic_Result> getGenericResults() {
        return genericResults;
    }
}
