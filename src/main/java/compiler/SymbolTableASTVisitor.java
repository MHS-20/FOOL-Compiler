package compiler;

import java.util.*;

import compiler.AST.*;
import compiler.exc.*;
import compiler.lib.*;

public class SymbolTableASTVisitor extends BaseASTVisitor<Void, VoidException> {

    /**
     * The symbol table is implemented as a list of maps,
     * where each map represents a single scope (block) and maps IDs (keys)
     * to the corresponding symbol table entries (values).
     * <ul>
     *   <li>Each map in the list corresponds to a nesting level.</li>
     *   <li>The innermost scope (current nesting level) is at the last position of the list,
     *   <li>It allows managing and accessing declarations of variables and functions
     *       within the appropriate scopes during parsing and analysis.</li>
     * </ul>
     */
    private List<Map<String, STentry>> symTable = new ArrayList<>();

    /**
     * Represents the symbol table for classes, where:
     * <ul>
     *   <li>The first-level keys are strings representing the names of classes.</li>
     *
     *   <li>The second-level maps correspond to the symbol table of each class.</li>
     *
     *   <li>These second-level maps associate identifiers (fields and methods)
     *       to the corresponding instances of {@link STentry}.</li>
     * </ul>
     */
    private Map<String, Map<String, STentry>> classTable = new HashMap<>();
    private int nestingLevel = 0; // current nesting level
    private int decOffset = -2; // counter for offset of local declarations at current nesting level
    int stErrors = 0;

    SymbolTableASTVisitor() {
    }

    SymbolTableASTVisitor(boolean debug) {
        super(debug);
    } // enables print for debugging

    /**
     * Search for the symbol table entry corresponding to the passed ID, iterating through
     * the list of Maps, starting from the current nesting level to the
     * outermost level (from highest to lowest).
     *
     * @param id the identifier to search for in the symbol table
     * @return the symbol table entry associated with the identifier, or null
     * if the identifier is not found in any visibility scope
     */
    private STentry stLookup(String id) {
        int j = nestingLevel;
        STentry entry = null;
        while (j >= 0 && entry == null)
            entry = symTable.get(j--).get(id);
        return entry;
    }

    @Override
    public Void visitNode(ProgLetInNode n) {
        if (print) printNode(n);
        Map<String, STentry> hm = new HashMap<>();
        symTable.add(hm);
        for (Node dec : n.declist) visit(dec);
        visit(n.exp);
        symTable.remove(0);
        return null;
    }

    @Override
    public Void visitNode(ProgNode n) {
        if (print) printNode(n);
        visit(n.exp);
        return null;
    }

    @Override
    public Void visitNode(FunNode n) {
        if (print) printNode(n);

        // Get the HashMap for the current nesting level
        Map<String, STentry> hm = symTable.get(nestingLevel);

        // Collect the parameter types
        List<TypeNode> parTypes = new ArrayList<>();
        for (ParNode par : n.parList) parTypes.add(par.getType());

        // Create an STentry with: nesting level, Type and Offset
        STentry entry = new STentry(nestingLevel, new ArrowTypeNode(parTypes, n.retType), decOffset--);

        // Insert my ID + entry into the SymbolTable
        if (hm.put(n.id, entry) != null) {
            // If already present -> error
            System.out.println("Fun id " + n.id + " at line " + n.getLine() + " already declared");
            stErrors++;
        }

        // Create a new HashMap for the inner scope and add it to the SymbolTable
        nestingLevel++;
        Map<String, STentry> hmn = new HashMap<>();
        symTable.add(hmn);

        // Save the offset of this level before resetting for the next
        int prevNLDecOffset = decOffset;
        decOffset = -2;

        // Set the offset for parameters (upward) and add them to the new HashMap
        int parOffset = 1;
        for (ParNode par : n.parList)
            if (hmn.put(par.id, new STentry(nestingLevel, par.getType(), parOffset++)) != null) {
                System.out.println("Par id " + par.id + " at line " + n.getLine() + " already declared");
                stErrors++;
            }

        // Visit the function declarations (let)
        for (Node dec : n.decList) visit(dec);

        // Visit the function expression (in)
        visit(n.exp);

        // Remove the HashMap because I'm exiting the inner scope
        symTable.remove(nestingLevel--);

        // Restore the previous offset
        decOffset = prevNLDecOffset;
        return null;
    }

    @Override
    public Void visitNode(VarNode n) {
        if (print) printNode(n);

        // Execute the expression
        visit(n.exp);

        // Get the HashMap for the current nesting level
        Map<String, STentry> hm = symTable.get(nestingLevel);

        // Create an STentry with: nesting level, Type and Offset
        STentry entry = new STentry(nestingLevel, n.getType(), decOffset--);

        // Insert my ID + entry into the SymbolTable
        if (hm.put(n.id, entry) != null) {
            System.out.println("Var id " + n.id + " at line " + n.getLine() + " already declared");
            stErrors++;
        }
        return null;
    }

    @Override
    public Void visitNode(PrintNode n) {
        if (print) printNode(n);
        visit(n.exp);
        return null;
    }

    @Override
    public Void visitNode(IfNode n) {
        if (print) printNode(n);
        visit(n.cond);
        visit(n.th);
        visit(n.el);
        return null;
    }

    @Override
    public Void visitNode(EqualNode n) {
        if (print) printNode(n);
        visit(n.left);
        visit(n.right);
        return null;
    }

    @Override
    public Void visitNode(LessEqualNode n) {
        if (print) printNode(n);
        visit(n.left);
        visit(n.right);
        return null;
    }

    public Void visitNode(GreaterEqualNode n) {
        if (print) printNode(n);
        visit(n.left);
        visit(n.right);
        return null;
    }

    @Override
    public Void visitNode(TimesNode n) {
        if (print) printNode(n);
        visit(n.left);
        visit(n.right);
        return null;
    }

    public Void visitNode(DivNode n) {
        if (print) printNode(n);
        visit(n.left);
        visit(n.right);
        return null;
    }

    @Override
    public Void visitNode(PlusNode n) {
        if (print) printNode(n);
        visit(n.left);
        visit(n.right);
        return null;
    }

    @Override
    public Void visitNode(MinusNode n) {
        if (print) printNode(n);
        visit(n.left);
        visit(n.right);
        return null;
    }

    @Override
    public Void visitNode(NotNode n) {
        if (print) printNode(n);
        visit(n.exp);
        return null;
    }

    @Override
    public Void visitNode(AndNode n) {
        if (print) printNode(n);
        visit(n.left);
        visit(n.right);
        return null;
    }

    public Void visitNode(OrNode n) {
        if (print) printNode(n);
        visit(n.left);
        visit(n.right);
        return null;
    }

    @Override
    public Void visitNode(CallNode n) {
        if (print) printNode(n);

        // Search for the STentry of the function from the highest nesting level to the lowest
        STentry entry = stLookup(n.id);
        if (entry == null) {
            System.out.println("Fun id " + n.id + " at line " + n.getLine() + " not declared");
            stErrors++;
        } else {
            // If I find it, I save it in the node and set the nesting level to the current one
            n.entry = entry;
            n.nl = nestingLevel;
        }

        // Visit the function arguments
        for (Node arg : n.arglist) visit(arg);
        return null;
    }

    @Override
    public Void visitNode(IdNode n) {
        if (print) printNode(n);

        // Search for the STentry of the variable from the highest nesting level to the lowest
        STentry entry = stLookup(n.id);
        if (entry == null) {
            // If I don't find it, error
            System.out.println("Var or Par id " + n.id + " at line " + n.getLine() + " not declared");
            stErrors++;
        } else {
            // If I find it, I save it in the node and set the nesting level to the current one
            n.entry = entry;
            n.nl = nestingLevel;
        }
        return null;
    }

    @Override
    public Void visitNode(BoolNode n) {
        if (print) printNode(n, n.val.toString());
        return null;
    }

    @Override
    public Void visitNode(IntNode n) {
        if (print) printNode(n, n.val.toString());
        return null;
    }

    // OBJECT-ORIENTED

    @Override
    public Void visitNode(ClassNode n) throws VoidException {
        if (print) printNode(n);

        Map<String, STentry> globalSymTable = symTable.get(0);

        List<TypeNode> allFields = new ArrayList<>();
        List<ArrowTypeNode> allMethods = new ArrayList<>();

        // --- INHERITANCE: copy parent's fields/methods and set superEntry ---
        if (n.superId != null) {
            STentry superClassEntry = globalSymTable.get(n.superId);
            if (superClassEntry == null || !classTable.containsKey(n.superId)) {
                System.out.println("Super class id " + n.superId + " at line " + n.getLine() + " not declared");
                stErrors++;
            } else {
                n.superEntry = superClassEntry;
                ClassTypeNode classType = (ClassTypeNode) superClassEntry.type;
                allFields.addAll(classType.allFields);    // deep copy of contents (new ArrayList)
                allMethods.addAll(classType.allMethods);
            }
        }

        STentry entry = new STentry(0, new ClassTypeNode(allFields, allMethods), decOffset--);
        n.setType(entry.type);

        if (globalSymTable.put(n.id, entry) != null) {
            System.out.println("Class id " + n.id + " at line " + n.getLine() + " already declared");
            stErrors++;
        }

        nestingLevel++;
        Map<String, STentry> virtualTable = new HashMap<>();
        if (n.superId != null && classTable.containsKey(n.superId)) {
            virtualTable.putAll(classTable.get(n.superId));  // inherit parent's VT entries
        }
        symTable.add(virtualTable);
        classTable.put(n.id, virtualTable);

        int fieldOffset = -allFields.size() - 1;
        Set<String> declaredInThisClass = new HashSet<>();

        for (FieldNode field : n.fieldList) {
            if (print) printNode(field);
            if (!declaredInThisClass.add(field.id)) {
                System.out.println("Field id " + field.id + " at line " + field.getLine() + " already declared");
                stErrors++;
            }
            STentry oldEntry = virtualTable.get(field.id);
            STentry fieldEntry;
            if (oldEntry == null) {
                // New field
                fieldEntry = new STentry(nestingLevel, field.getType(), fieldOffset--);
            } else {
                if (oldEntry.type instanceof ArrowTypeNode) {
                    System.out.println("Cannot override method " + field.id + "() at line "
                            + field.getLine() + " with field " + field.id);
                    stErrors++;
                }
                // Override: keep parent's offset
                fieldEntry = new STentry(nestingLevel, field.getType(), oldEntry.offset);
            }
            field.offset = fieldEntry.offset;
            virtualTable.put(field.id, fieldEntry);

            int fieldPos = -fieldEntry.offset - 1;
            if (fieldPos < allFields.size()) {
                allFields.set(fieldPos, field.getType());
            } else {
                allFields.add(field.getType());
            }
        }

        int prevNLDecOffset = decOffset;
        decOffset = allMethods.size();  // method offsets continue from parent's count
        Set<String> declaredMethodsInThisClass = new HashSet<>();

        for (MethodNode method : n.methodList) {
            if (!declaredMethodsInThisClass.add(method.id)) {
                System.out.println("Method id " + method.id + " at line " + method.getLine() + " already declared");
                stErrors++;
            }
            visit(method);  // sets method.offset and method.type

            if (method.offset < allMethods.size()) {
                allMethods.set(method.offset, (ArrowTypeNode) method.getType());
            } else {
                allMethods.add((ArrowTypeNode) method.getType());
            }
        }

        symTable.remove(nestingLevel--);
        decOffset = prevNLDecOffset;
        return null;
    }

    @Override
    public Void visitNode(MethodNode n) throws VoidException {
        if (print) printNode(n);

        // Get the HashMap for the current nesting level
        Map<String, STentry> virtualTable = symTable.get(nestingLevel);

        // Collect the parameter types
        List<TypeNode> parTypes = new ArrayList<>();
        for (ParNode par : n.parList) parTypes.add(par.getType());

        STentry oldEntry = virtualTable.get(n.id);
        STentry methodEntry = null;

        if (oldEntry == null) {
            // Create an STentry with: nesting level, Type and Offset
            methodEntry = new STentry(nestingLevel, new ArrowTypeNode(parTypes, n.retType), decOffset++);
        } else {
            if (!(oldEntry.type instanceof ArrowTypeNode)) {
                System.out.println("Cannot override field " + n.id + " at line "
                        + n.getLine() + " with method " + n.id + "()");
                stErrors++;
            }
            methodEntry = new STentry(nestingLevel, new ArrowTypeNode(parTypes, n.retType), oldEntry.offset);

        }

        n.offset = methodEntry.offset;
        n.setType(methodEntry.type);

        // Insert the method ID + entry into the class VirtualTable or replace if it was already there
        virtualTable.put(n.id, methodEntry);

        nestingLevel++;
        Map<String, STentry> methodScope = new HashMap<>();
        symTable.add(methodScope);

        // Save the offset of this level before resetting for the next
        int prevNLDecOffset = decOffset;
        decOffset = -2;

        // Set the offset for parameters (upward) and add them to the new HashMap
        int parOffset = 1;
        for (ParNode par : n.parList)
            if (methodScope.put(par.id, new STentry(nestingLevel, par.getType(), parOffset++)) != null) {
                System.out.println("Par id " + par.id + " at line " + n.getLine() + " already declared");
                stErrors++;
            }

        // Visit the function declarations (let)
        for (Node dec : n.decList) visit(dec);

        // Visit the function expression (in)
        visit(n.exp);

        // Remove the HashMap because I'm exiting the inner scope
        symTable.remove(nestingLevel--);

        // Restore the previous offset
        decOffset = prevNLDecOffset;
        return null;
    }

    @Override
    public Void visitNode(ClassCallNode n) {
        if (print) printNode(n);

        // Search for the STentry of the reference from the highest nesting level to the lowest
        STentry entry = stLookup(n.refId);
        if (entry == null) {
            System.out.println("Reference id " + n.refId + " at line " + n.getLine() + " not declared");
            stErrors++;
        } else if (entry.type instanceof RefTypeNode) {
            n.classEntry = entry;
            n.nestingLevel = nestingLevel;
            // Get the STentry of the method from the Class Table
            String classId = ((RefTypeNode) entry.type).id;
            STentry methodEntry = classTable.get(classId).get(n.methodId);
            if (methodEntry == null) {
                System.out.println("Method id " + n.refId + "." + n.methodId + " at line " + n.getLine() + " not declared");
                stErrors++;
            } else {
                // If I find it, I save it in the node
                n.methodEntry = methodEntry;
            }
        } else {
            System.out.println("Reference id " + n.refId + " at line " + n.getLine() + " is not a RefType");
            stErrors++;
        }

        // Visit the method arguments
        for (Node arg : n.argList) visit(arg);
        return null;
    }

    @Override
    public Void visitNode(NewNode n) {
        if (print) printNode(n);
        if (!classTable.containsKey(n.id)) {
            System.out.println("Class id " + n.id + " at line " + n.getLine() + " not declared");
            stErrors++;
        }
        n.entry = symTable.get(0).get(n.id);
        for (Node arg : n.argList) visit(arg);
        return null;
    }

    @Override
    public Void visitNode(EmptyNode n) {
        if (print) printNode(n);
        return null;
    }
}
