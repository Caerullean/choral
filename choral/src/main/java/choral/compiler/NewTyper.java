package choral.compiler;

import java.util.Collection;

import choral.ast.CompilationUnit;

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
        }
    }
}
