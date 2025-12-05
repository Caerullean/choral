package choral;

import java.util.Arrays;
import java.util.Collection;

import choral.ast.CompilationUnit;
import choral.compiler.NewTyper;
import choral.compiler.Parser;

public class NewTyperTester {
    public static void main(String[] args) {
        String sourceCode = """
            class Hello@A{
                String@A msg;
                String@A letter;
            }""";
        try {
            CompilationUnit compUnit = Parser.parseString(sourceCode);
            Collection<CompilationUnit> typedUnits = NewTyper.annotate(Arrays.asList(compUnit));

            System.out.println("Finished");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
