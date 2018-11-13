package com.aurea.longmethodrefactor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.AssignExpr.Operator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class LongMethodRefactor {

    private static final int MIN_STATEMENTS = 3;
    private static final String GET = "get";
    private static final int maxLength = 10;
    private static final String JAVA_SUFFIX = ".java";

    public static void main(String[] args) throws IOException {
        Path rootPath = Paths.get("/home/chomnoue/projects/bootcamp/long-method-refactor-reference/src/main/java");
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(new JavaParserTypeSolver(rootPath));
        List<Path> javaFiles = Files.walk(rootPath)
                .filter(path -> Files.isRegularFile(path) && path.toFile().getName().endsWith(JAVA_SUFFIX))
                .collect(Collectors.toList());
        int total = javaFiles.size();
        log.debug("Performing Long Method refactoring for {} files", total);
        for (int i = 0; i < total; i++) {
            float percent = 100f * (i+1) / total;
            log.debug("Refactoring {}: {}%s", javaFiles.get(i), percent);
            refactorLonMethods(javaFiles.get(i), symbolSolver);
        }
    }

    private static void refactorLonMethods(Path path, JavaSymbolSolver symbolSolver) throws IOException {
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        CompilationUnit compilationUnit = getCompilationUnit(symbolSolver, content);
        log.debug("Initial Methods: {}", countMethods(compilationUnit));
        boolean refactored = false;
        int round = 1;
        while (refactorLongMethod(compilationUnit)) {
            //re-init positions, set start and end lines to new added and modified methods
            compilationUnit = getCompilationUnit(symbolSolver, compilationUnit.toString());
            refactored = true;
            log.debug("Methods after round {} : {}", round, countMethods(compilationUnit));
            round ++;
        }
        if (refactored) {
            Files.write(path, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
        }

    }

    private static int countMethods(Node node){
        return node.findAll(MethodDeclaration.class).size();
    }

    private static CompilationUnit getCompilationUnit(JavaSymbolSolver symbolSolver, String content) {
        CompilationUnit compilationUnit = JavaParser.parse(content);
        compilationUnit.setData(Node.SYMBOL_RESOLVER_KEY, symbolSolver);
        return compilationUnit;
    }

    private static boolean refactorLongMethod(CompilationUnit compilationUnit) {
        return compilationUnit.getTypes().stream().filter(type -> type instanceof ClassOrInterfaceDeclaration)
                .anyMatch(type -> refactorLongMethod(type.asClassOrInterfaceDeclaration()));
    }

    private static boolean refactorLongMethod(ClassOrInterfaceDeclaration type) {
        return type.getMethods().stream().anyMatch(method -> refactorLongMethod(type, method));
    }

    private static boolean refactorLongMethod(ClassOrInterfaceDeclaration type, MethodDeclaration method) {
        Optional<Position> begin = method.getBegin();
        Optional<Position> end = method.getEnd();
        if (!begin.isPresent() || !end.isPresent()) {
            return false;
        }
        int length = end.get().line - begin.get().line + 1;
        if (length <= maxLength) {
            return false;
        }
        List<RefactoringCandidate> candidates = method.getBody().map(body -> refactorLongStatement(body,
                Collections.emptyList())).orElse(Collections.emptyList());
        if (!candidates.isEmpty()) {
            RefactoringCandidate bestCandidate = chooseBestCandidate(candidates);
            applyRefactoring(bestCandidate, type, method);
            return true;
        } else {
            return false;
        }
    }

    private static void applyRefactoring(RefactoringCandidate candidate, ClassOrInterfaceDeclaration type,
            MethodDeclaration method) {
        MethodDeclaration newMethod = generateNewMethod(candidate, type, method);

        Expression replacingExpression = getReplacingExpression(candidate, newMethod);

        List<Statement> toReplace = candidate.statementsToReplace;
        toReplace.get(0).replace(new ExpressionStmt(replacingExpression));
        for (int i = 1; i < toReplace.size(); i++) {
            Statement toRemove = toReplace.get(i);
            Optional<Node> parent = toRemove.getParentNode();
            parent.ifPresent(node -> node.remove(toRemove));
        }
    }

    private static Expression getReplacingExpression(RefactoringCandidate candidate, MethodDeclaration newMethod) {
        MethodCallExpr methodCallExpr = new MethodCallExpr(newMethod.getNameAsString(),
                newMethod.getParameters().stream().map(Parameter::getName).map(NameExpr::new)
                        .toArray(NameExpr[]::new));
        Expression replacingExpression = methodCallExpr;
        if (candidate.valueToAssign != null) {
            ResolvedValueDeclaration valueToAssign = candidate.valueToAssign;
            if (isDeclaredIn(valueToAssign, candidate.statementsToReplace)) {
                replacingExpression = new VariableDeclarationExpr(new VariableDeclarator(getType(valueToAssign),
                        valueToAssign.getName(), methodCallExpr));
            } else {
                replacingExpression = new AssignExpr(new NameExpr(valueToAssign.getName()), methodCallExpr,
                        Operator.ASSIGN);
            }
        }
        return replacingExpression;
    }

    private static MethodDeclaration generateNewMethod(RefactoringCandidate candidate, ClassOrInterfaceDeclaration type,
            MethodDeclaration method) {
        MethodDeclaration newMethod = type.addMethod(computeMethodName(candidate, type, method));
        newMethod.setPrivate(true);
        newMethod.setStatic(method.isStatic());
        newMethod.setType(computeReurnType(candidate, method));
        newMethod.setParameters(new NodeList<>(computeParameters(candidate)));
        List<Statement> newMethodStatements = candidate.statementsToReplace.stream().map(Statement::clone).collect(
                Collectors.toList());
        if (candidate.valueToAssign != null) {
            ReturnStmt returnStmt = new ReturnStmt(new NameExpr(candidate.valueToAssign.getName()));
            newMethodStatements.add(returnStmt);
        }
        newMethod.setBody(new BlockStmt(new NodeList<>(newMethodStatements)));
        return newMethod;
    }

    private static List<Parameter> computeParameters(RefactoringCandidate candidate) {
        return candidate.parameters.stream().map(param -> new Parameter(getType(param), param.getName())).collect(
                Collectors.toList());
    }

    private static Type computeReurnType(RefactoringCandidate candidate, MethodDeclaration method) {
        if (candidate.valueToAssign != null) {
            ResolvedValueDeclaration valueToAssign = candidate.valueToAssign;
            return getType(valueToAssign);
        }
        if (candidate.returnStmt == null) {
            return new VoidType();
        }
        return method.getType();
    }

    private static Type getType(ResolvedValueDeclaration declaration) {
        if (declaration instanceof JavaParserVariableDeclaration) {
            return ((JavaParserVariableDeclaration) declaration).getWrappedNode().getCommonType();
        }
        if (declaration instanceof JavaParserParameterDeclaration) {
            return ((JavaParserParameterDeclaration) declaration).getWrappedNode().getType();
        }
        if(declaration instanceof JavaParserSymbolDeclaration){
            Node node = ((JavaParserSymbolDeclaration) declaration).getWrappedNode();
            if(node instanceof VariableDeclarator){
                return ((VariableDeclarator) node).getType();
            }
        }
        throw new IllegalArgumentException("Unsupported type: " + declaration);
    }

    private static String computeMethodName(RefactoringCandidate candidate, ClassOrInterfaceDeclaration type,
            MethodDeclaration method) {
        String name = method.getNameAsString();
        if (candidate.valueToAssign != null) {
            name = GET + StringUtils.capitalize(candidate.valueToAssign.getName());
        }
        Set<String> existingNames = type.getMethods().stream().map(MethodDeclaration::getNameAsString).collect(
                Collectors.toSet());
        int count = 1;
        while (existingNames.contains(name)) {
            name += count;
            count++;
        }
        return name;
    }

    private static RefactoringCandidate chooseBestCandidate(List<RefactoringCandidate> candidates) {
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    private static List<RefactoringCandidate> refactorLongStatement(Statement statement,
            List<Statement> nextStatements) {
        List<Statement> children = statement.getChildNodes().stream().filter(node -> node instanceof Statement)
                .map(Statement.class::cast).collect(Collectors.toList());
        List<RefactoringCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            Statement child = children.get(i);
            List<Statement> childNextStatements = getChildNextStatements(nextStatements, children, i);
            candidates.addAll(refactorLongStatement(child, childNextStatements));
        }
        for (int begin = 0; begin < children.size() - MIN_STATEMENTS; begin++) {
            for (int end = begin + MIN_STATEMENTS - 1; end <= children.size() - 1; end++) {
                Optional<RefactoringCandidate> candidate = getRefactoringCandidate(statement, nextStatements, children,
                        begin, end);
                candidate.ifPresent(candidates::add);
            }
        }
        return candidates;
    }

    private static Optional<RefactoringCandidate> getRefactoringCandidate(Statement statement,
            List<Statement> nextStatements,
            List<Statement> children, int begin, int end) {
        //avoid moving entire method body to another method
        if (statement.getParentNode().isPresent() && statement.getParentNode().get() instanceof MethodDeclaration
                && begin == 0 && end == children.size() - 1) {
            return Optional.empty();
        }
        List<Statement> currentStatements = children.subList(begin, end + 1);
        if (containsReturnChildNode(currentStatements)) {
            return Optional.empty();
        }
        Optional<ReturnStmt> lastReturn = getLastReturnStatement(currentStatements);
        ResolvedValueDeclaration valueToAssign = null;
        if (!lastReturn.isPresent()) {
            List<AssignExpr> assignExprs = getAssignExpressions(currentStatements);
            Set<ResolvedValueDeclaration> declarations = assignExprs.stream()
                    .map(LongMethodRefactor::resolve).collect(Collectors.toSet());
            List<Statement> currentNext = getChildNextStatements(nextStatements, children, end);
            for (ResolvedValueDeclaration declaration : declarations) {
                if (isUsed(declaration, currentNext)) {
                    if (valueToAssign == null) {
                        valueToAssign = declaration;
                    } else {
                        //too many values to return
                        return Optional.empty();
                    }
                }
            }
        }

        return Optional.of(getRefactoringCandidate(currentStatements, lastReturn.orElse(null), valueToAssign));
    }

    private static RefactoringCandidate getRefactoringCandidate(List<Statement> currentStatements,
            ReturnStmt returnStmt, ResolvedValueDeclaration valueToAssign) {
        Set<ResolvedValueDeclaration> parameters = getParameters(currentStatements);
        return new RefactoringCandidate(currentStatements, parameters, valueToAssign, returnStmt);
    }

    private static Set<ResolvedValueDeclaration> getParameters(List<? extends Node> nodes) {
        return nodes.stream().flatMap(node -> (node.findAll(NameExpr.class).stream()))
                .map(LongMethodRefactor::resolveNameExpression)
                .flatMap(valueDeclaration -> valueDeclaration.map(Stream::of).orElse(Stream.empty()))
                .filter(declaration -> !isDeclaredIn(declaration, nodes))
                .collect(Collectors.toSet());
    }

    private static Optional<ResolvedValueDeclaration> resolveNameExpression(NameExpr nameExpr){
        try {
            return Optional.of(nameExpr.resolve());
        }catch (UnsolvedSymbolException ex){
            return Optional.empty();
        }
    }

    private static boolean isDeclaredIn(ResolvedValueDeclaration declaration, List<? extends Node> nodes) {
        if (declaration instanceof JavaParserVariableDeclaration) {
            JavaParserVariableDeclaration variableDeclaration = (JavaParserVariableDeclaration) declaration;
            VariableDeclarationExpr expression = variableDeclaration.getWrappedNode();
            return isDescendantOf(expression, nodes);
        }
        return declaration.isParameter();
    }

    private static boolean isDescendantOf(VariableDeclarationExpr expression, List<? extends Node> nodes) {
        return nodes.stream().anyMatch(node -> isDescendantOf(expression, node));
    }

    private static boolean isDescendantOf(VariableDeclarationExpr expression, Node node) {
        if (node == expression) {
            return true;
        }
        return isDescendantOf(expression, node.getChildNodes());
    }

    private static boolean isUsed(ResolvedValueDeclaration declaration, List<? extends Node> nodes) {
        return nodes.stream().anyMatch(child -> isUsed(declaration, child));
    }

    private static boolean isUsed(ResolvedValueDeclaration declaration, Node node) {
        if (node instanceof NameExpr) {
            NameExpr nameExpr = (NameExpr) node;
            Optional<ResolvedValueDeclaration> maybeNameDeclaration = resolveNameExpression(nameExpr);
            return maybeNameDeclaration.map(nameDeclaration->isSameValue(declaration, nameDeclaration)).orElse(false);
        }
        return isUsed(declaration, node.getChildNodes());
    }

    private static boolean isSameValue(ResolvedValueDeclaration declaration, ResolvedValueDeclaration nameDeclaration) {
        return declaration == nameDeclaration;
    }

    private static ResolvedValueDeclaration resolve(AssignExpr assignExpr) {
        return assignExpr.getTarget().asNameExpr().resolve();
    }

    private static Optional<ReturnStmt> getLastReturnStatement(List<Statement> currentStatements) {
        if (currentStatements.isEmpty() || !(currentStatements
                .get(currentStatements.size() - 1) instanceof ReturnStmt)) {
            return Optional.empty();
        }
        return Optional.of(currentStatements.get(currentStatements.size() - 1).asReturnStmt());
    }

    private static List<AssignExpr> getAssignExpressions(List<Statement> currentStatements) {
        return currentStatements.stream().flatMap(statement -> statement.findAll(AssignExpr.class).stream()).collect(
                Collectors.toList());
    }

    private static boolean containsReturnChildNode(List<Statement> statements) {
        return statements.stream().anyMatch(statement -> statement.findFirst(ReturnStmt.class).isPresent());
    }

    private static List<Statement> getChildNextStatements(List<Statement> nextStatements, List<Statement> children,
            int idx) {
        List<Statement> next = children.subList(Math.min(idx + 1, children.size() - 1), children.size() - 1);
        return ListUtils.union(next, nextStatements);
    }

    private static class RefactoringCandidate {

        final List<Statement> statementsToReplace;
        final Set<ResolvedValueDeclaration> parameters;
        final ResolvedValueDeclaration valueToAssign;
        final ReturnStmt returnStmt;

        RefactoringCandidate(List<Statement> statementsToReplace,
                Set<ResolvedValueDeclaration> parameters,
                ResolvedValueDeclaration valueToAssign, ReturnStmt returnStmt) {
            this.statementsToReplace = statementsToReplace;
            this.parameters = parameters;
            this.valueToAssign = valueToAssign;
            this.returnStmt = returnStmt;
        }
    }
}
