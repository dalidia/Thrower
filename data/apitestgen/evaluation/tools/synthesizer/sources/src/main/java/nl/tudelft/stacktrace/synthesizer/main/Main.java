package nl.tudelft.stacktrace.synthesizer.main;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import nl.tudelft.stacktrace.synthesizer.JSONStackTraceSynthesizer;
import nl.tudelft.stacktrace.synthesizer.StackTrace;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Xavier Devroey - xavier.devroey@gmail.com
 */
public class Main {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static final String INPUT = "i";
    private static final String CASE = "c";
    private static final String OUTPUT_DIR = "o";

    private static final String CASE_JSON_PROPERTY = "cases";

    private static final String JSON_OUTPUT = ".json";

    private static final String HELP = "h";

    private final Options options;

    private File inputFile;
    private File outputFolder;
    private String caseName;

    private Main() {
        this.options = new Options();

        options.addOption(Option.builder(INPUT)
                .numberOfArgs(1)
                .argName("input-file.json")
                .desc("JSON file generated by eRec (mandatory).")
                .build());

        options.addOption(Option.builder(CASE)
                .numberOfArgs(1)
                .argName("case-name")
                .desc("The name of the case as used by ExRunner (mandatory).")
                .build());

        options.addOption(Option.builder(OUTPUT_DIR)
                .numberOfArgs(1)
                .argName("output/")
                .desc("The output directory where the stack traces and mapping file will be generated (mandatory).")
                .build());

        options.addOption(HELP, false, "Prints this message");
    }

    private void printHelpMessage() {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(128);
        formatter.printHelp("java -jar stacktrace-synthesizer.jar <options>", options);
    }

    private CommandLine initialise(String[] args) throws ParseException, IOException {
        LOG.info("Initilization");
        CommandLineParser parser = new DefaultParser();
        CommandLine line = parser.parse(options, args);

        // Check Help message 
        if (line.hasOption(HELP)) {
            printHelpMessage();
            return null;
        }
        // Get input file
        Preconditions.checkArgument(line.hasOption(INPUT), "Option -%s is mandatory!", INPUT);
        this.inputFile = new File(line.getOptionValue(INPUT));
        Preconditions.checkArgument(this.inputFile.isFile(), "File %s not found!", this.inputFile.getName());
        // Get case name
        Preconditions.checkArgument(line.hasOption(CASE), "Option -%s is mandatory!", CASE);
        this.caseName = line.getOptionValue(CASE);
        // Get output folder
        Preconditions.checkArgument(line.hasOption(OUTPUT_DIR), "Option -%s is mandatory!", OUTPUT_DIR);
        this.outputFolder = new File(line.getOptionValue(OUTPUT_DIR));
        if (!this.outputFolder.exists()) {
            LOG.info("Folder {} does not exist, will be created.", this.outputFolder.getName());
            this.outputFolder.mkdirs();
        }
        return line;
    }

    private void launch() throws IOException {
        JsonParser parser = new JsonParser();
        JsonObject elements = parser.parse(new FileReader(inputFile)).getAsJsonObject();
        int count = 0;
        JSONStackTraceSynthesizer synthesizer = new JSONStackTraceSynthesizer();
        for (Map.Entry<String, JsonElement> entry : elements.entrySet()) {
            LOG.info("Processing next entry");
            JsonObject value = entry.getValue().getAsJsonObject();
            LOG.debug("Processing entry {}", value);
            List<StackTrace> traces = synthesizer.synthesize(value);
            if (traces.isEmpty()) {
                LOG.warn("No stack traces generated for entry {}", value);
            } else {
                // Output each trace =
                JsonObject outputedFiles = new JsonObject();
                for (StackTrace trace : traces) {
                    count++;
                    String outputFolderName = trace.getFrame(trace.getFramesCount()-1).getFileName().replace(".java", "");
                    File caseFolder = new File(outputFolder, outputFolderName);
                    if(!caseFolder.exists()){
                        caseFolder.mkdir();
                    }
                    String outputName = caseName + "-" + count;
                    File output = new File(caseFolder, outputName + ".log");
                    Files.asCharSink(output, Charsets.UTF_8)
                            .write(trace.toString());
                    outputedFiles.addProperty(outputName, trace.toString());
                }
                // Add outputed files to the JSon entry
                value.add(CASE_JSON_PROPERTY, outputedFiles);
                LOG.info("Files created: {}", outputedFiles.size());
            }
        }
        LOG.info("Printing updated JSON file into {}/{}{}", outputFolder, caseName, JSON_OUTPUT);
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        Files.asCharSink(new File(outputFolder, caseName + JSON_OUTPUT), Charsets.UTF_8)
                .write(gson.toJson(elements));
    }

    public static void main(String[] args) {
        Main main = new Main();
        try {
            CommandLine line = main.initialise(args);
            if (line != null) {
                main.launch();
            }
        } catch (ParseException | IOException ex) {
            LOG.error("Exception while processing file {}!", main.inputFile.getName(), ex);
        }

    }

}
