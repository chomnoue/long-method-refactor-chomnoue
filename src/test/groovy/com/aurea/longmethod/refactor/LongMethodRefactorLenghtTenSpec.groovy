package com.aurea.longmethod.refactor


class LongMethodRefactorLenghtTenSpec extends LongMethodRefactorSpec{

    def "Should refactor long method with length > 10"(){
        expect:
        onClassCodeExpect """
        package com.aurea.longmethod.refactor;

        import java.io.File;
        import java.util.logging.Logger;
        import org.apache.commons.cli.CommandLine;
        import org.apache.commons.cli.CommandLineParser;
        import org.apache.commons.cli.DefaultParser;
        import org.apache.commons.cli.HelpFormatter;
        import org.apache.commons.cli.Option;
        import org.apache.commons.cli.Options;
        import org.apache.commons.cli.ParseException;
        
        public class Test {
        
            public static void main(String[] args) throws Exception {
        
                Options options = new Options();
        
                Option input = new Option("i", "input", true, "input file path");
                input.setRequired(true);
                options.addOption(input);
        
                Option output = new Option("o", "output", true, "output file");
                output.setRequired(true);
                options.addOption(output);
        
                CommandLineParser parser = new DefaultParser();
                HelpFormatter formatter = new HelpFormatter();
                CommandLine cmd = null;
        
                try {
                    cmd = parser.parse(options, args);
                } catch (ParseException e) {
                    System.out.println(e.getMessage());
                    formatter.printHelp("utility-name", options);
        
                    System.exit(1);
                }
        
                String inputFilePath = cmd.getOptionValue("input");
                String outputFilePath = cmd.getOptionValue("output");
        
                System.out.println(inputFilePath);
                System.out.println(outputFilePath);
            }
        }
        """, """
        package com.aurea.longmethod.refactor;
         
        import java.io.File;
        import java.util.logging.Logger;
        import org.apache.commons.cli.CommandLine;
        import org.apache.commons.cli.CommandLineParser;
        import org.apache.commons.cli.DefaultParser;
        import org.apache.commons.cli.HelpFormatter;
        import org.apache.commons.cli.Option;
        import org.apache.commons.cli.Options;
        import org.apache.commons.cli.ParseException;
         
        public class Test {
         
            public static void main(String[] args) throws Exception {
                Options options = getOptions();
                CommandLineParser parser = new DefaultParser();
                CommandLine cmd = getCmd(args, parser, options);
                main1(cmd);
            }
         
            private static Options getOptions() throws Exception {
                Options options = new Options();
                Option input = new Option("i", "input", true, "input file path");
                input.setRequired(true);
                options.addOption(input);
                Option output = new Option("o", "output", true, "output file");
                output.setRequired(true);
                options.addOption(output);
                return options;
            }
         
            private static void main1(CommandLine cmd) throws Exception {
                String inputFilePath = cmd.getOptionValue("input");
                String outputFilePath = cmd.getOptionValue("output");
                System.out.println(inputFilePath);
                System.out.println(outputFilePath);
            }
         
            private static CommandLine getCmd(String[] args, CommandLineParser parser, Options options) throws Exception {
                HelpFormatter formatter = new HelpFormatter();
                CommandLine cmd = null;
                try {
                    cmd = parser.parse(options, args);
                } catch (ParseException e) {
                    System.out.println(e.getMessage());
                    formatter.printHelp("utility-name", options);
                    System.exit(1);
                }
                return cmd;
            }
        }
        """
    }

    @Override
    LongMethodRefactor longMethodRefactor(String srcDir) {
        return longMethodRefactorWithLength(srcDir, 10)
    }
}
