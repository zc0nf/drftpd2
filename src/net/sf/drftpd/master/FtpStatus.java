package net.sf.drftpd.master;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Properties;

/**
 * Ftp status line parser class. This class loads <code>FtpStatus.properties</code>
 * file from the classpath. It generates the descriptive ftp status for
 * astatus code. The actual response depends on the status code, the ftp
 * command and the passed argument list.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */
public
class FtpStatus extends Properties {

    private final static String RESOURCE = "net/sf/distributedftpd/master/FtpStatus.properties";
    private final static String PREFIX   = "FtpServer.status."; 
    private final static String EMPTY    = "";
    private final static String CRLF     = "\r\n";

    private final static String CMD      = "CMD";
    private final static String ARG      = "ARG";

    /**
     * Load status propeties file from the classpath.
     */
    public FtpStatus() throws IOException {
        InputStream pis = getClass().getClassLoader().getResourceAsStream(RESOURCE);
        load(pis);
        pis.close();
    }


    /**
     * Process ftp response new line character.
     */
    public String processNewLine(String msg, int status) {
        
        // no newline
        if(msg.indexOf('\n') == -1) {
            return status + " " + msg + CRLF;
        }
        
        StringBuffer sw = new StringBuffer(256);
        
        try {
            BufferedReader sr = new BufferedReader(new StringReader(msg));
        
            sw.append(String.valueOf(status));
            sw.append('-');
        
            String line = sr.readLine();
            for(;;) {
                String nextLine = sr.readLine();    
            
                if(nextLine != null) {
                    sw.append(line);
                    sw.append(CRLF);
                }
                else {
                    sw.append(String.valueOf(status));
                    sw.append(' ');
                    sw.append(line);
                    sw.append(CRLF);
                    break;
                }
                line = nextLine;
            }
            sr.close();
        }
        catch(IOException ex) {
        }

        return sw.toString();
    }
     
    
    /**
     * Get ftp message from the properties file and replace the variables.
     */
    private String getMessage(int status, FtpRequest cmdLine, String[] args) {
        
        // make the key from the passed parameters
        String key = PREFIX + status;
        String keyc = key;
        if(cmdLine != null) {
            keyc = keyc + '.' + cmdLine.getCommand();
        }
        
        // get status property
        String str = getProperty(keyc);
        if(str == null) {
            str = getProperty(key);
        }
        if(str == null) {
            str = EMPTY;
        }
        
        // replace variables
        int startIndex = 0;
        int openIndex = str.indexOf('{', startIndex);
        int closeIndex = str.indexOf('}', startIndex);
        
        if( (openIndex == -1) || (closeIndex == -1) || (openIndex > closeIndex) ) {
            return str;
        }
        
        StringBuffer sb = new StringBuffer();
        sb.append(str.substring(startIndex, openIndex));
        while(true) {
            String intStr = str.substring(openIndex+1, closeIndex);
            sb.append(getParam(cmdLine, args, intStr));
            
            startIndex = closeIndex + 1;
            openIndex = str.indexOf('{', startIndex);
            closeIndex = str.indexOf('}', startIndex);
            
            if( (openIndex == -1) || (closeIndex == -1) || (openIndex > closeIndex) ) {
               sb.append(str.substring(startIndex));
               break;
            }
            sb.append(str.substring(startIndex, openIndex));
        }
        return sb.toString();
    }
    
    
    /**
     * Get variable value. 
     */
    private String getParam(FtpRequest cmdLine, String[] elms, String intStr) {
        
        // command line param
        if(cmdLine != null) {
            if(intStr.equals(CMD)) {
                return cmdLine.getCommand();
            }
            if(intStr.equals(ARG)) {
                return cmdLine.getArgument();
            }
        }
        
        // list param
        if(elms == null) {
            return EMPTY;
        }
        
        int index = 0;
        try {
            index = Integer.parseInt(intStr);
        }
        catch(NumberFormatException ex) {
            return EMPTY;
        }
        if( (index < 0) || (index >= elms.length) ) {
            return EMPTY;
        }
        return elms[index];
    }
    
    
    /**
     * Get ftp response.
     * @param status ftp status code.
     * @param cmd ftp request object (may be null).
     * @param ars variable arguent list (may be null).
     */
    public String getResponse(int status, FtpRequest cmd, FtpUser user, String[] args) {
        String strRes = getMessage(status, cmd, args);
        return processNewLine(strRes, status);
    }
    
}
