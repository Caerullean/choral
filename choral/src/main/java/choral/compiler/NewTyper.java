package choral.compiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;

import choral.ast.CompilationUnit;
import choral.ast.body.ClassMethodDefinition;
import choral.ast.body.ClassModifier;
import choral.ast.body.FieldModifier;
import choral.ast.statement.Statement;
import choral.ast.type.FormalWorldParameter;
import choral.ast.type.WorldArgument;
import choral.exceptions.AstPositionedException;
import choral.exceptions.StaticVerificationException;
import choral.types.Member.Field;
import choral.types.HigherClass;
import choral.types.Modifier;
import choral.types.World;

public class NewTyper {
    private static class SymbolTable{
        HashSet<String> roles;
        HashSet<String> identifiers;

        public SymbolTable(HashSet<String> roles){
            this.roles = roles;
        }

        public HashSet<String> roles(){
            return roles;
        }

        public HashSet<String> identifiers(){
            return identifiers;
        }
    }

    // maps from name of class to symboltable of class
    // maybe shouldn't be a hashmap? And simply have a tree structure instead?
    // current implementation implies multiple "outer" classes is okay, it's not per java. 
    private static final HashMap<String, SymbolTable> classToSymbolTableMap = new HashMap<>();

    public static Collection<CompilationUnit> annotate(Collection<CompilationUnit> sourceUnits,
                                                Collection<CompilationUnit> headerUnits){
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
            if (!classToSymbolTableMap.containsKey(visitedClass.name().identifier())){
                HashSet<String> roles = new HashSet<>();
                for (FormalWorldParameter param : visitedClass.worldParameters()){
                    roles.add(param.name().identifier());
                }
                classToSymbolTableMap.put(visitedClass.name().identifier(), new SymbolTable(roles));

                EnumSet<Modifier> modifiers = toGenericModifier(visitedClass.modifiers());
                HigherClass higherClass = new HigherClass( 
                    null, 
                    modifiers, 
                    visitedClass.name().identifier(), 
                    visitWorldParameters(visitedClass.worldParameters()), 
                    null, 
                    visitedClass);

                for (choral.ast.body.Field field : visitedClass.fields()){
                    visitField(field, visitedClass.name().identifier(), higherClass);
                }

                for (ClassMethodDefinition method : visitedClass.methods()){
                    visitMethod(method);
                }
            } else {
                throw new AstPositionedException(visitedClass.position(), 
                        new StaticVerificationException("Duplicate class definition: " + 
                            visitedClass.name().identifier()
                            + " already defined."));
            }  
        }

        private void visitField(choral.ast.body.Field visitedField, String classIdentifier, HigherClass higherClass){
            if (classToSymbolTableMap.containsKey(visitedField.name().identifier())){
                throw new AstPositionedException(visitedField.position(),
                        new StaticVerificationException("Identifier \"" + visitedField.name().identifier() + "\" already taken by class"));
            }
            SymbolTable currentTable = classToSymbolTableMap.get(classIdentifier); 
            if (!currentTable.identifiers().contains(visitedField.name().identifier())){
                currentTable.identifiers().add(visitedField.name().identifier());
            } else {
                throw new AstPositionedException(visitedField.position(), 
                        new StaticVerificationException("Identifier \"" + visitedField.name().identifier() + "\" already declared in scope"));
            }
            // Check if field role(s) is subset of class role(s)
            HashSet<String> roles = classToSymbolTableMap.get(classIdentifier).roles();
            for (WorldArgument role : visitedField.typeExpression().worldArguments()){
                if (!roles.contains(role.name().identifier())){
                    throw new AstPositionedException(visitedField.position(),
                            new StaticVerificationException("Role: " + role.name().identifier() 
                            + " not present in class roles: " + roles));
                }
            }
            EnumSet<Modifier> modifiers = toGenericModifier(visitedField.modifiers());
            
            Field typedField = new Field(
                higherClass.innerType(), 
                visitedField.name().identifier(), 
                modifiers, 
                null);
        }

        private void visitMethod(ClassMethodDefinition visitedMethod){
            System.out.println(visitedMethod.signature().name().identifier());
            try {
                Statement body = visitedMethod.body().get(); 
                // a better method of checking whether it has body or not is to check abstract
            } catch (NoSuchElementException e) {
                System.out.println("No body in method");
            }
        }

        private List<World> visitWorldParameters(List<FormalWorldParameter> worldParameters){
            List<World> worlds = new ArrayList<>();
            for (FormalWorldParameter worldParam : worldParameters){
                World world = new World(null, worldParam.name().identifier(), worldParam); // what does universe do??
                worldParam.setTypeAnnotation(world);
                worlds.add(world);
            }
            return worlds;
        }

        private <E extends Enum<E>> EnumSet<Modifier> toGenericModifier(Iterable<E> sourceModifiers){
            EnumSet<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
            for (E modifier : sourceModifiers){
                modifiers.add(Modifier.valueOf(modifier.name()));
            }
            return modifiers;
        }
    }
}
