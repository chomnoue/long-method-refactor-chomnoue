package com.aurea.longmethodrefactor;

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
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.utils.Utils;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApplicableCandidateProvider {

    Optional<ApplicableCandidate> chooseBestCandidate(List<RefactoringCandidate> candidates,
            MethodDeclaration method, ClassOrInterfaceDeclaration type) {
        return candidates.stream().map(candidate -> computeNewMethodAndScore(candidate, type, method))
                .filter(ApplicableCandidate::isReducesLength)
                .max(Comparator.comparing(ApplicableCandidate::getScore));
    }

    private ApplicableCandidate computeNewMethodAndScore(RefactoringCandidate candidate,
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

    private float computeScore(MethodDeclaration method, MethodDeclaration candidateMethod,
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

    private float lengthScore(MethodDeclaration candidateMethod, MethodDeclaration remainingMethod) {
        int candidateLength = methodLength(candidateMethod);
        int remainingLength = methodLength(remainingMethod);
        return Math.min(lengthWeight * Math.min(candidateLength, remainingLength), maxScoreLength);
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
        if (candidate.getValueToAssign() != null) {
            ResolvedValueDeclaration valueToAssign = candidate.getValueToAssign();
            List<Statement> statementsToReplace = getStatementsToReplace(candidate, method);
            if (AstUtils.isDeclaredIn(valueToAssign, statementsToReplace)) {
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
        if (candidate.getValueToAssign() != null) {
            ReturnStmt returnStmt = new ReturnStmt(new NameExpr(candidate.getValueToAssign().getName()));
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
        Statement currentStatement = body.get();
        for (int idx : candidate.getPath()) {
            currentStatement = AstUtils.getStatementChildren(currentStatement).get(idx);
        }
        return AstUtils.getStatementChildren(currentStatement)
                .subList(candidate.getFirstStatement(), candidate.getLastStatement() + 1);
    }

    private static Collection<Parameter> computeParameters(RefactoringCandidate candidate) {
        return candidate.getParameters().stream().map(param -> new Parameter(getType(param), param.getName())).collect(
                Collectors.toMap(Parameter::getNameAsString, Function.identity(), (p1, p2) -> p1)).values();
    }

    private static Type computeReurnType(RefactoringCandidate candidate, MethodDeclaration method) {
        if (candidate.getValueToAssign() != null) {
            ResolvedValueDeclaration valueToAssign = candidate.getValueToAssign();
            return getType(valueToAssign);
        }
        if (candidate.getReturnStmt() == null) {
            return new VoidType();
        }
        return method.getType();
    }

    private static Type getType(ResolvedValueDeclaration declaration) {
        Node wrappedNode = AstUtils.getWrappedNode(declaration);
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

    private static String computeMethodName(RefactoringCandidate candidate, ClassOrInterfaceDeclaration type,
            MethodDeclaration method) {
        String name = method.getNameAsString();
        if (candidate.getValueToAssign() != null) {
            name = GET + StringUtils.capitalize(candidate.getValueToAssign().getName());
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
}
