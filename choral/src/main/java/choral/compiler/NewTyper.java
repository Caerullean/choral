package choral.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import choral.ast.CompilationUnit;
import choral.ast.type.FormalWorldParameter;
import choral.exceptions.AstPositionedException;
import choral.exceptions.StaticVerificationException;

public class NewTyper {
    // Does this hashmap need to be threadsafe?
    private static final HashMap<String, List<String>> classToRolesMap = new HashMap<>();

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
            if (!classToRolesMap.containsKey(visitedClass.name().identifier())){
                List<String> roles = new ArrayList<>();
                for (FormalWorldParameter param : visitedClass.worldParameters()){
                    roles.add(param.name().identifier());
                }
                classToRolesMap.put(visitedClass.name().identifier(), roles);
            } else {
                throw new AstPositionedException(visitedClass.position(), 
                        new StaticVerificationException("Duplicate class definition: " + 
                            visitedClass.name().identifier()
                            + " already defined."));
            }           
        }
    }
}
