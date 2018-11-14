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
import com.github.javaparser.utils.Utils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.springframework.util.StringUtils;

@Slf4j
public class LongMethodRefactor {

    private static final int MIN_STATEMENTS = 3;
    private static final String GET = "get";
    private static final int MAX_LENGTH = 10;
    private static final String JAVA_SUFFIX = ".java";
    private static final int MAX_SCORE_LENGTH = 3;
    private static final float LENGTH_WEIGHT = 0.1f;
    private static final int MAX_SCORE_PARAM = 4;
    private static final Pattern NAME_PATTERN = Pattern.compile("(.+)(\\d+)");

    public static void main(String[] args) throws IOException {
        Path rootPath = Paths.get("/home/chomnoue/projects/bootcamp/long-method-refactor-reference/src/main/java");
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(new JavaParserTypeSolver(rootPath));
        List<Path> javaFiles = Files.walk(rootPath)
                .filter(path -> Files.isRegularFile(path) && path.toFile().getName().endsWith(JAVA_SUFFIX))
                .collect(Collectors.toList());
        int total = javaFiles.size();
        log.debug("Performing Long Method refactoring for {} files", total);
        for (int i = 0; i < total; i++) {
            float percent = 100f * (i + 1) / total;
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
            round++;
        }
        if (refactored) {
            Files.write(path, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
        }

    }

    private static int countMethods(Node node) {
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
        if (length <= MAX_LENGTH) {
            return false;
        }
        List<RefactoringCandidate> candidates = method.getBody().map(body -> refactorLongStatement(body,
                Collections.emptyList(), Collections.emptyList())).orElse(Collections.emptyList());
        Optional<ApplicableCandidate> bestRefactoring = chooseBestCandidate(candidates, method, type);
        if (bestRefactoring.isPresent()) {
            applyRefactoring(bestRefactoring.get(), type, method);
            return true;
        } else {
            return false;
        }
    }

    private static void applyRefactoring(ApplicableCandidate candidate, ClassOrInterfaceDeclaration type,
            MethodDeclaration method) {
        type.addMember(candidate.getCandidateMethod());
        method.replace(candidate.getRemainingMethod());
    }

    private static ApplicableCandidate computeNewMethodAndScore(RefactoringCandidate candidate,
            ClassOrInterfaceDeclaration type, MethodDeclaration method) {
        MethodDeclaration candidateMethod = generateNewMethod(candidate, type, method);
        MethodDeclaration remainingMethod = generateRemainingMethod(candidate, method, candidateMethod);
        float score = computeScore(method, candidateMethod, remainingMethod);
        boolean reducesLength = reducesLength(method, candidateMethod, remainingMethod);
        return ApplicableCandidate.builder()
                .candidateMethod(candidateMethod)
                .remainingMethod(remainingMethod)
                .score(score)
                .reducesLength(reducesLength)
                .build();
    }

    private static boolean reducesLength(MethodDeclaration method, MethodDeclaration candidateMethod,
            MethodDeclaration remainingMethod) {
        int length = methodLength(method);
        return length > methodLength(candidateMethod) && length > methodLength(remainingMethod);
    }

    private static float computeScore(MethodDeclaration method, MethodDeclaration candidateMethod,
            MethodDeclaration remainingMethod) {
        float lengthScore = lengthScore(candidateMethod, remainingMethod);
        int nestDepthScore = nestingDepthSocre(method, candidateMethod, remainingMethod);
        float nestAreaScore = nestingAreaScore(method, candidateMethod, remainingMethod);
        int paramsScore = paramsScore(candidateMethod);
        return lengthScore + nestDepthScore + nestAreaScore + paramsScore;
    }

    private static int paramsScore(MethodDeclaration candidateMethod) {
        int returns = candidateMethod.getType() instanceof VoidType ? 0 : 1;
        return MAX_SCORE_PARAM - returns - candidateMethod.getParameters().size();
    }

    private static int nestingDepthSocre(MethodDeclaration method, MethodDeclaration candidateMethod,
            MethodDeclaration remainingMethod) {
        int methodDepth = nestingDepth(method);
        int candidateDepth = nestingDepth(candidateMethod);
        int remainingDepth = nestingDepth(remainingMethod);
        return Math.min(methodDepth - remainingDepth, methodDepth - candidateDepth);
    }

    private static float nestingAreaScore(MethodDeclaration method, MethodDeclaration candidateMethod,
            MethodDeclaration remainingMethod) {
        int methodNestArea = nestingArea(method);
        int candidateNestingArea = nestingArea(candidateMethod);
        int remainingNestArea = nestingArea(remainingMethod);
        int areaReduction = Math.min(methodNestArea - candidateNestingArea, methodNestArea - remainingNestArea);
        int methodDepth = nestingDepth(method);
        return 2f * methodDepth * areaReduction / methodNestArea;
    }

    private static int nestingArea(MethodDeclaration methodDeclaration) {
        return methodDeclaration.getBody().map(LongMethodRefactor::nestingArea).orElse(0);
    }

    private static int nestingArea(BlockStmt blockStmt) {
        return blockStmt.getChildNodes().stream().mapToInt(LongMethodRefactor::nestingDepth).sum();
    }

    private static float lengthScore(MethodDeclaration candidateMethod, MethodDeclaration remainingMethod) {
        int candidateLength = methodLength(candidateMethod);
        int remainingLength = methodLength(remainingMethod);
        return Math.min(LENGTH_WEIGHT * Math.min(candidateLength, remainingLength), MAX_SCORE_LENGTH);
    }

    private static int nestingDepth(Node node) {
        int addedDepth = node instanceof BlockStmt ? 1 : 0;
        return addedDepth + node.getChildNodes().stream().mapToInt(LongMethodRefactor::nestingDepth).max().orElse(0);
    }

    private static int methodLength(MethodDeclaration method) {
        return method.toString().split(Utils.EOL).length;
    }

    private static MethodDeclaration generateRemainingMethod(RefactoringCandidate candidate, MethodDeclaration method,
            MethodDeclaration candidateMethod) {
        MethodDeclaration remainingMethod = method.clone();
        Expression replacingExpression = getReplacingExpression(candidate, candidateMethod, method);

        List<Statement> toReplace = getStatementsToReplace(candidate, remainingMethod);
        toReplace.get(0).replace(new ExpressionStmt(replacingExpression));
        for (int i = 1; i < toReplace.size(); i++) {
            Statement toRemove = toReplace.get(i);
            Optional<Node> parent = toRemove.getParentNode();
            parent.ifPresent(node -> node.remove(toRemove));
        }
        return remainingMethod;
    }

    private static Expression getReplacingExpression(RefactoringCandidate candidate, MethodDeclaration newMethod,
            MethodDeclaration method) {
        MethodCallExpr methodCallExpr = new MethodCallExpr(newMethod.getNameAsString(),
                newMethod.getParameters().stream().map(Parameter::getName).map(NameExpr::new)
                        .toArray(NameExpr[]::new));
        Expression replacingExpression = methodCallExpr;
        if (candidate.valueToAssign != null) {
            ResolvedValueDeclaration valueToAssign = candidate.valueToAssign;
            List<Statement> statementsToReplace = getStatementsToReplace(candidate, method);
            if (isDeclaredIn(valueToAssign, statementsToReplace)) {
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
        MethodDeclaration newMethod = new MethodDeclaration();
        newMethod.setName(computeMethodName(candidate, type, method));
        newMethod.setPrivate(true);
        newMethod.setStatic(method.isStatic());
        newMethod.setType(computeReurnType(candidate, method));
        newMethod.setParameters(new NodeList<>(computeParameters(candidate)));
        List<Statement> newMethodStatements =
                getStatementsToReplace(candidate, method).stream().map(Statement::clone).collect(
                        Collectors.toList());
        if (candidate.valueToAssign != null) {
            ReturnStmt returnStmt = new ReturnStmt(new NameExpr(candidate.valueToAssign.getName()));
            newMethodStatements.add(returnStmt);
        }
        newMethod.setBody(new BlockStmt(new NodeList<>(newMethodStatements)));
        return newMethod;
    }

    private static List<Statement> getStatementsToReplace(RefactoringCandidate candidate,
            MethodDeclaration methodDeclaration) {
        Optional<BlockStmt> body = methodDeclaration.getBody();
        if (!body.isPresent()) {
            return Collections.emptyList();
        }
        Statement currentsStatement = body.get();
        for (int idx : candidate.path) {
            currentsStatement = getStatementChildren(currentsStatement).get(idx);
        }
        return getStatementChildren(currentsStatement).subList(candidate.firstStatement, candidate.lastStatement + 1);
    }

    private static Collection<Parameter> computeParameters(RefactoringCandidate candidate) {
        return candidate.parameters.stream().map(param -> new Parameter(getType(param), param.getName())).collect(
                Collectors.toMap(Parameter::getNameAsString, Function.identity(), (p1, p2) -> p1)).values();
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
        Node wrappedNode = getWrappedNode(declaration);
        if (wrappedNode instanceof VariableDeclarationExpr) {
            return ((VariableDeclarationExpr) wrappedNode).getCommonType();
        }
        if (wrappedNode instanceof Parameter) {
            return ((Parameter) wrappedNode).getType();
        }
        if (wrappedNode instanceof VariableDeclarator) {
            return ((VariableDeclarator) wrappedNode).getType();
        }
        throw new IllegalArgumentException("Unsupported node type: " + wrappedNode);
    }

    private static Node getWrappedNode(ResolvedValueDeclaration declaration) {
        if (declaration instanceof JavaParserVariableDeclaration) {
            return ((JavaParserVariableDeclaration) declaration).getWrappedNode();
        }
        if (declaration instanceof JavaParserParameterDeclaration) {
            return ((JavaParserParameterDeclaration) declaration).getWrappedNode();
        }
        if (declaration instanceof JavaParserSymbolDeclaration) {
            return ((JavaParserSymbolDeclaration) declaration).getWrappedNode();
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
        Matcher matcher = NAME_PATTERN.matcher(name);
        if (matcher.matches()) {
            name = matcher.group(1);
            count = Integer.valueOf(matcher.group(2));
        }
        String newName = name;
        while (existingNames.contains(newName)) {
            newName = name + count;
            count++;
        }
        return newName;
    }

    private static Optional<ApplicableCandidate> chooseBestCandidate(List<RefactoringCandidate> candidates,
            MethodDeclaration method, ClassOrInterfaceDeclaration type) {
        return candidates.stream().map(candidate -> computeNewMethodAndScore(candidate, type, method))
                .filter(ApplicableCandidate::isReducesLength)
                .max(Comparator.comparing(ApplicableCandidate::getScore));
    }

    private static List<RefactoringCandidate> refactorLongStatement(Statement statement,
            List<Statement> nextStatements, List<Integer> candidatePath) {
        List<Statement> children = getStatementChildren(statement);
        List<RefactoringCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            Statement child = children.get(i);
            List<Statement> childNextStatements = getChildNextStatements(nextStatements, children, i);
            List<Integer> newPath = new ArrayList<>(candidatePath);
            newPath.add(i);
            candidates.addAll(refactorLongStatement(child, childNextStatements, newPath));
        }
        for (int begin = 0; begin < children.size() - MIN_STATEMENTS; begin++) {
            for (int end = begin + MIN_STATEMENTS - 1; end <= children.size() - 1; end++) {
                Optional<RefactoringCandidate> candidate = getRefactoringCandidate(statement, nextStatements, children,
                        begin, end, candidatePath);
                candidate.ifPresent(candidates::add);
            }
        }
        return candidates;
    }

    private static List<Statement> getStatementChildren(Statement statement) {
        return statement.getChildNodes().stream().filter(node -> node instanceof Statement)
                .map(Statement.class::cast).collect(Collectors.toList());
    }

    private static Optional<RefactoringCandidate> getRefactoringCandidate(Statement statement,
            List<Statement> nextStatements,
            List<Statement> children, int begin, int end, List<Integer> candidatePath) {
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
            List<ResolvedValueDeclaration> declarations = ListUtils.union(getAssignedVariables(currentStatements),
                    getDeclaredVariables(currentStatements));
            List<Statement> currentNext = getChildNextStatements(nextStatements, children, end);
            for (ResolvedValueDeclaration declaration : declarations) {
                if (isUsed(declaration, currentNext)) {
                    if (valueToAssign == null) {
                        valueToAssign = declaration;
                    } else if (!isSameValue(valueToAssign, declaration)) {
                        //too many values to return
                        return Optional.empty();
                    }
                }
            }
        }
        Set<ResolvedValueDeclaration> parameters = getParameters(currentStatements);
        RefactoringCandidate refactoringCandidate = RefactoringCandidate.builder()
                .firstStatement(begin)
                .lastStatement(end)
                .path(candidatePath)
                .returnStmt(lastReturn.orElse(null))
                .valueToAssign(valueToAssign)
                .parameters(parameters)
                .build();
        return Optional.of(refactoringCandidate);
    }

    private static List<ResolvedValueDeclaration> getAssignedVariables(List<Statement> currentStatements) {
        return getNodesOfType(currentStatements, AssignExpr.class).stream()
                .map(LongMethodRefactor::resolve).collect(Collectors.toList());
    }

    private static List<ResolvedValueDeclaration> getDeclaredVariables(List<Statement> currentStatements) {
        return getNodesOfType(currentStatements, VariableDeclarator.class).stream()
                .map(VariableDeclarator::resolve).collect(Collectors.toList());
    }

    private static Set<ResolvedValueDeclaration> getParameters(List<? extends Node> nodes) {
        return nodes.stream().flatMap(node -> (node.findAll(NameExpr.class).stream()))
                .map(LongMethodRefactor::resolveNameExpression)
                .flatMap(valueDeclaration -> valueDeclaration.map(Stream::of).orElse(Stream.empty()))
                .filter(declaration -> !isDeclaredIn(declaration, nodes))
                .collect(Collectors.toSet());
    }

    private static Optional<ResolvedValueDeclaration> resolveNameExpression(NameExpr nameExpr) {
        try {
            return Optional.of(nameExpr.resolve());
        } catch (UnsolvedSymbolException ex) {
            return Optional.empty();
        }
    }

    private static boolean isDeclaredIn(ResolvedValueDeclaration declaration, List<? extends Node> nodes) {
        Node wrappedNode = getWrappedNode(declaration);
        return isDescendantOf(wrappedNode, nodes);
    }

    private static boolean isDescendantOf(Node descendant, List<? extends Node> nodes) {
        return nodes.stream().anyMatch(node -> isDescendantOf(descendant, node));
    }

    private static boolean isDescendantOf(Node descendant, Node node) {
        if (node == descendant) {
            return true;
        }
        return isDescendantOf(descendant, node.getChildNodes());
    }

    private static boolean isUsed(ResolvedValueDeclaration declaration, List<? extends Node> nodes) {
        return nodes.stream().anyMatch(child -> isUsed(declaration, child));
    }

    private static boolean isUsed(ResolvedValueDeclaration declaration, Node node) {
        if (node instanceof NameExpr) {
            NameExpr nameExpr = (NameExpr) node;
            Optional<ResolvedValueDeclaration> maybeNameDeclaration = resolveNameExpression(nameExpr);
            return maybeNameDeclaration.map(nameDeclaration -> isSameValue(declaration, nameDeclaration)).orElse(false);
        }
        return isUsed(declaration, node.getChildNodes());
    }

    private static boolean isSameValue(ResolvedValueDeclaration declaration, ResolvedValueDeclaration other) {
        return getSymbolDeclarationWrappedNode(declaration) == getSymbolDeclarationWrappedNode(other);
    }

    private static Node getSymbolDeclarationWrappedNode(ResolvedValueDeclaration declaration) {
        Node node = getWrappedNode(declaration);
        if (node instanceof VariableDeclarationExpr) {
            return ((VariableDeclarationExpr) node).getVariable(0);
        }
        return node;
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

    private static <T extends Node> List<T> getNodesOfType(List<Statement> currentStatements, Class<T> type) {
        return currentStatements.stream().flatMap(statement -> statement.findAll(type).stream()).collect(
                Collectors.toList());
    }

    private static boolean containsReturnChildNode(List<Statement> statements) {
        return statements.stream().anyMatch(statement -> statement.findFirst(ReturnStmt.class).isPresent());
    }

    private static List<Statement> getChildNextStatements(List<Statement> nextStatements, List<Statement> children,
            int idx) {
        List<Statement> next = children.subList(Math.min(idx + 1, children.size() - 1), children.size());
        return ListUtils.union(next, nextStatements);
    }

    @Builder
    private static class RefactoringCandidate {

        private final int firstStatement;
        private final int lastStatement;
        private final List<Integer> path;
        private final Set<ResolvedValueDeclaration> parameters;
        private final ResolvedValueDeclaration valueToAssign;
        private final ReturnStmt returnStmt;
    }

    @Getter
    @Builder
    private static class ApplicableCandidate {

        private final MethodDeclaration remainingMethod;
        private final MethodDeclaration candidateMethod;
        private final float score;
        private boolean reducesLength;
    }
}
