package choral.compiler;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import choral.ast.CompilationUnit;
import choral.ast.type.FormalWorldParameter;
import choral.ast.type.WorldArgument;
import choral.exceptions.AstPositionedException;
import choral.exceptions.StaticVerificationException;

public class NewTyper {
    // Does this hashmap need to be threadsafe?
    private static final HashMap<String, HashSet<String>> classToRolesMap = new HashMap<>();

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
                HashSet<String> roles = new HashSet<>();
                for (FormalWorldParameter param : visitedClass.worldParameters()){
                    roles.add(param.name().identifier());
                }
                classToRolesMap.put(visitedClass.name().identifier(), roles);

                HashSet<String> fieldDeclarations = new HashSet<>();
                for (choral.ast.body.Field field : visitedClass.fields()){
                    visitField(field, visitedClass.name().identifier(), fieldDeclarations);
                }
            } else {
                throw new AstPositionedException(visitedClass.position(), 
                        new StaticVerificationException("Duplicate class definition: " + 
                            visitedClass.name().identifier()
                            + " already defined."));
            }  
        }

        private void visitField(choral.ast.body.Field visitedField, String classIdentifier, HashSet<String> fieldDeclarations){
            if (classToRolesMap.containsKey(visitedField.name().identifier())){
                throw new AstPositionedException(visitedField.position(),
                        new StaticVerificationException("Identifier \"" + visitedField.name().identifier() + "\" already taken by class"));
            }
            if (!fieldDeclarations.contains(visitedField.name().identifier())){
                fieldDeclarations.add(visitedField.name().identifier());
            } else {
                throw new AstPositionedException(visitedField.position(), 
                        new StaticVerificationException("Identifier \"" + visitedField.name().identifier() + "\" already declared in scope"));
            }
            HashSet<String> roles = classToRolesMap.get(classIdentifier);
            for (WorldArgument role : visitedField.typeExpression().worldArguments()){
                if (!roles.contains(role.name().identifier())){
                    throw new AstPositionedException(visitedField.position(),
                            new StaticVerificationException("Role not present in class roles: " + 
                                role.name().identifier()));
                }
            }
        }
    }
}
