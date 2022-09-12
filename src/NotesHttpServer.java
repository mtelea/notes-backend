import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

public class NotesHttpServer {

    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String JAVA_BIN = JAVA_HOME +
            File.separator + "bin" +
            File.separator + "java";
    private static final List<String> listCommand = List.of(JAVA_BIN, "-jar", "notes", "-list");
    private static final List<String> addCommand = List.of(JAVA_BIN, "-jar", "notes", "-add");


    public static void main(String[] args) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(8888), 0);
        final HttpContext listNotesContext = server.createContext("/list");
        final HttpContext addNoteContext = server.createContext("/add");
        setContextHandlers(listNotesContext, addNoteContext);
        server.start();
    }

    private static void setContextHandlers(HttpContext listNotesContext, HttpContext addNoteContext) {
        listNotesContext.setHandler(NotesHttpServer::handleListNoteRequest);
        addNoteContext.setHandler(NotesHttpServer::handleAddNoteRequest);
    }

    private static void handleListNoteRequest(HttpExchange exchange) throws IOException {
        String response;
        int code = 200;
        String query = exchange.getRequestURI().getQuery();
        List<String> listCommandWithArgs = new ArrayList<>(listCommand);
        if(query != null) {
            String arg = query.substring(query.indexOf("=") + 1);
            listCommandWithArgs.add(arg);
        }
        try {
            response = outputToJSON(runNotesJar(listCommandWithArgs));
        } catch (InterruptedException e) {
            e.printStackTrace();
            response = "Error retrieving notes";
            code = 500;
        } catch (IOException e) {
            e.printStackTrace();
            response = "Error reading notes";
            code = 500;
        }
        sendHTTPResponse(exchange, code, response);
    }
    private static void handleAddNoteRequest(HttpExchange exchange) throws IOException {
        String response;
        int code = 200;
        try {
            List<String> args = parsePostRequestBody(exchange);
            List<String> addCommandWithArgs = Stream.concat(addCommand.stream(), args.stream()).toList();
            response = outputToJSON(runNotesJar(addCommandWithArgs));
        } catch (InterruptedException e) {
            e.printStackTrace();
            response = "Error retrieving notes";
            code = 500;
        } catch (IOException e) {
            e.printStackTrace();
            response = "Error reading notes";
            code = 500;
        } catch (ParseException e) {
            e.printStackTrace();
            response = "Error parsing POST data";
            code = 500;
        }
        sendHTTPResponse(exchange, code, response);
    }

    private static List<String> parsePostRequestBody(HttpExchange exchange) throws IOException, ParseException {
        StringBuilder stringBuilder = new StringBuilder();
        final InputStream ios = exchange.getRequestBody();
        final String noteTitle;
        final String noteContent;

        int i;
        while ((i = ios.read()) != -1) {
            stringBuilder.append((char) i);
        }

        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject) parser.parse(stringBuilder.toString());
        noteTitle = (String) jsonObject.get("title");
        noteContent = (String) jsonObject.get("content");

        return List.of(noteTitle, noteContent);

    }

    private static void sendHTTPResponse(HttpExchange exchange, int code, String response) throws IOException {
        exchange.sendResponseHeaders(code, response.getBytes().length);//response code and length
        final OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private static HashMap<String, String> runNotesJar(List<String> toExecute) throws IOException, InterruptedException {
        final ProcessBuilder pb = new ProcessBuilder(toExecute);
        pb.directory(new File(System.getProperty("user.home") + "/Desktop"));
        pb.redirectErrorStream(true);
        final Process p = pb.start();
        HashMap<String, String> results = readInputStream(p);

        return results;
    }

    private static HashMap<String, String> readInputStream(Process p) throws IOException {
        final BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String s = "";
        HashMap<String, String> results = new HashMap<>();
        while((s = in.readLine()) != null){
            String[] tokens = s.split(" - ");
            results.put(tokens[0], tokens[1]);
        }
        return results;
    }

    private static String outputToJSON(HashMap<String, String> input) {
        final String result;
        JSONObject json = new JSONObject(input);
        result = json.toString();

        return result;
    }

}
