# FOOL Compiler

This is a compiler for **FOOL** (Functional Object-Oriented Language), a statically-typed language combining functional and object-oriented programming. 
The compiler is implemented in Java using ANTLR4 for lexing and parsing, and targets a custom **Stack Virtual Machine (SVM)**.

## Language Overview

FOOL is an expression-oriented language. Every program is either a bare expression or a `let...in` block that introduces declarations before a final expression. 
Declarations can be variables, functions, or classes (classes must appear first).

### Types

Primitive types are `int` and `bool`. Reference types are written as the class name. 
`null` has a special empty type that is a subtype of every reference type.

### Operators

Arithmetic: `+`, `-`, `*`, `/`  
Comparison: `==`, `<=`, `>=`  
Logical: `&&`, `||`, `!`

### A complete example

```fool
let
  class Animal (name: int) {
    fun speak: int () name ;
  }

  class Dog extends Animal (trick: int) {
    fun speak: int () name + trick ;
  }

  var a: Animal = new Animal(1) ;
  var d: Dog    = new Dog(2, 10) ;
in
  print(d.speak()) ;
```

Here `Dog` extends `Animal`, inheriting the `name` field and overriding `speak`. 
Calling `d.speak()` dispatches to Dog's implementation at runtime via the dispatch table.

---

## Compiler Architecture

The compilation pipeline has four sequential phases, each implemented as a visitor over the Abstract Syntax Tree (AST).

```
Source (.fool)
      │
      ▼
  ANTLR lexer/parser
      │
      ▼
  ASTGenerationSTVisitor   ← builds the AST from the parse tree
      │
      ▼
  SymbolTableASTVisitor    ← name resolution, scope analysis, symbol table construction
      │
      ▼
  TypeCheckEASTVisitor     ← type checking and inference on the enriched AST
      │
      ▼
  CodeGenerationASTVisitor ← SVM assembly code emission
      │
      ▼
  SVM assembly (.asm / in-memory)
```

### Phase 1 — Parsing and AST Generation

ANTLR generates a lexer and a recursive-descent parser from the grammar in `FOOL.g4`. 
The `ASTGenerationSTVisitor` walks the parse tree and builds a typed AST made of node classes (`ProgLetInNode`, `FunNode`, `ClassNode`, `IfNode`, etc.), discarding syntactic noise like parentheses and keywords.

### Phase 2 — Symbol Table Analysis

The `SymbolTableASTVisitor` performs a single pass over the AST to:

- Build and maintain a **multi-level symbol table** (a stack of hash maps, one per nesting level) to resolve identifiers to their declarations and detect uses before declaration or redeclaration errors.
- Compute and assign **offsets** for every declared symbol, matching the memory layout expected by the SVM at runtime.
- Decorate AST nodes that reference identifiers (variables, functions, classes, fields, methods) with the corresponding `STentry`, which carries the type, nesting level, and offset needed by later phases.

### Phase 3 — Type Checking

The `TypeCheckEASTVisitor` traverses the enriched AST and either returns the inferred type of each expression or throws a `TypeException` on mismatch. Key rules:

- Variables must be assigned a value whose type is a subtype of the declared type.
- Function/method calls check arity and per-argument subtyping.
- `if-then-else` expressions require a boolean condition; the two branches may have different but compatible types (see Optimizations below).
- `new ClassName(...)` checks argument types against the class field list.
- Arithmetic operators require `int` operands; logical operators require `bool`.

Subtyping (`isSubtype`) is structural for primitives and function types, and follows the class hierarchy for reference types.

### Phase 4 — Code Generation

The `CodeGenerationASTVisitor` produces SVM assembly as a string. The SVM is a stack-based virtual machine with a flat memory array, a stack growing downward from `MEMSIZE`, and a heap growing upward from address 0.

**Activation Records** follow a standard layout: Control Link, parameters (positive offsets from `$fp`), then local declarations at negative offsets. The Access Link (static link) points to the enclosing scope's AR, enabling correct closure over free variables across nesting levels.

**Functions and methods** emit their code to a separate pool via `putCode()` (appended after the main program), so the main code stream stays linear.

---

## Object-Oriented Extension

### Classes and Objects

A class declaration introduces a reference type. Fields are declared like parameters; methods are declared like functions. Objects are allocated on the heap with `new`. Fields are immutable after construction and are accessible only from within the class (or subclasses). Methods can be called from outside via `object.method(...)` notation.

```fool
let
  class Point (x: int, y: int) {
    fun getX: int () x ;
    fun getY: int () y ;
  }
  var p: Point = new Point(3, 4) ;
in
  print(p.getX()) ;
```

### Heap Layout

Each object in the heap has the following layout:

```
heap[obj]     = dispatch pointer   ← this address is the "object pointer" returned by new
heap[obj - 1] = first field
heap[obj - 2] = second field
...
heap[obj - n] = n-th field
```

The **dispatch pointer** points to the class's dispatch table, also stored in the heap. 
The dispatch table is an array of method addresses (labels), indexed by method offset starting from 0.

### Virtual Dispatch

Method calls go through the dispatch table at runtime. 
Given an object pointer, the VM reads the dispatch pointer at offset 0, adds the method's offset, and jumps to the loaded address. 
This means the correct method body is always selected at runtime, even when the static type of the reference is a superclass.

### Class Declarations and the Global Environment

Each class declaration, when executed, allocates its dispatch table on the heap and stores the resulting dispatch pointer in the global activation record at the class's offset. 
`new` recovers this pointer from the global AR to write into newly created objects.

### Virtual Table and Class Table

During symbol table analysis, each class scope uses a **Virtual Table** — a symbol table level that pre-populates inherited fields and methods from the parent before adding the class's own declarations. 
A separate **Class Table** (map from class name to its Virtual Table) persists each class's scope after analysis ends, so it can be looked up when resolving `obj.method(...)` calls later.

---

## Inheritance

A class can extend exactly one parent class with the `extends` keyword. 
The subclass inherits all fields and methods of the parent.

```fool
let
  class Shape (color: int) {
    fun area: int () 0 ;
  }

  class Circle extends Shape (radius: int) {
    fun area: int () radius * radius ;
  }
in
  print(new Circle(1, 5).area()) ;
```

### Field and Method Overriding

The subclass may re-declare a field name from the parent (changing its type) or re-declare a method name (overriding the implementation). 
When overriding:

- The **offset** is inherited from the parent declaration, ensuring the same memory slot is reused.
- Overriding a field with a method or vice versa is a static error.
- Declaring the same name twice within the same class body is also a static error.

### Subtyping

A reference of type `B` (subclass) is a subtype of `A` (superclass) and can be used wherever an `A` is expected. 
`null` is a subtype of every reference type. Function types support covariance on the return type and contravariance on parameter types, enabling correct method overriding checks.

The compiler builds a `superType` map during type checking (class name → parent name) and uses transitive reachability to decide subtyping between reference types.

### Override Correctness

When a subclass overrides a method, the new method's type must be a subtype of the parent's method type (covariant return, contravariant parameters). 
Field overrides must likewise produce a subtype of the parent field's type. 
The type checker enforces this, but only for fields/methods that actually override a parent member — new members are not checked against the parent.

---

## Optimizations

### Redefinition Error Detection

Declaring a field or method name more than once within the same class body is flagged as a static error. 
This is distinct from overriding (which is allowed): the check uses a per-class set and only catches duplicates within a single class declaration.

### Efficient Override Type Checking

Rather than re-checking every field and method of a subclass against the parent, the type checker only inspects members whose offset falls within the parent's field/method range — i.e., only the ones that are actually overriding something. 
New members (with offsets beyond the parent's size) are skipped entirely.

### Lowest Common Ancestor in if-then-else

The then and else branches of an `if` expression do not have to be exactly the same type. 
The type checker computes the **Lowest Common Ancestor (LCA)** of the two branch types and uses it as the expression's type:

- For primitive types (`int`, `bool`): `int` is returned if either branch is `int`, otherwise `bool`.
- For reference types: the compiler walks up the inheritance chain of the `then` type, checking at each step whether the `else` type is a subtype. The first ancestor that satisfies this is the LCA.
- If one branch is `null` (`EmptyTypeNode`), the other branch's type is used directly.
- If no common ancestor exists, a type error is raised.

This allows, for example, returning either a `Dog` or a `Cat` from an `if` expression when both extend `Animal`, with the result typed as `Animal`.

---

## Build
From the project root you can build using the Gradle wrapper:
```bash
  ./gradlew build
```
## Run / Compile a source file
You can directly run the application and pass the file to compile as a command-line argument (optional).
_Example_ without argument (will use default file `foolExamples/prova.fool`):
```bash
  ./gradlew run
```
_Example_ with argument:
```bash
  ./gradlew run --args="path/to/source.fool"
```
### Note
- replace `path/to/source.fool` with the actual file you want to compile.
- the file must be in the project directory.
- if your shell interprets backslashes or special characters, quote or escape the path as appropriate.
