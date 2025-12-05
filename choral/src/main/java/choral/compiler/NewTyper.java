package choral.compiler;

import java.util.Collection;

import choral.ast.CompilationUnit;
import choral.ast.type.FormalWorldParameter;

public class NewTyper {
    public static Collection<CompilationUnit> annotate(Collection<CompilationUnit> sourceUnits){
        Visitor visitor = new Visitor();
        sourceUnits.forEach( cu -> visitor.visit(cu));
        return sourceUnits;
    }

    private static class Visitor{
        private void visit(CompilationUnit compUnit){
            for (choral.ast.body.Class individualClass : compUnit.classes()){
                visitClass(individualClass);
            }
        }

        private void visitClass(choral.ast.body.Class visitedClass){
            System.out.println("Found class: " + visitedClass.name().identifier());
            System.out.println("With role(s):");
            for (FormalWorldParameter param : visitedClass.worldParameters()){
                System.out.println(param.name().identifier());
            }
        }
    }
}
