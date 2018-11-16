package com.aurea.longmethodrefactor;

import com.github.javaparser.ast.Modifier;
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
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ApplicableCandidateProvider {

    private static final String GET = "get";
    private static final Pattern NAME_PATTERN = Pattern.compile("(.+)(\\d+)");

    private final int maxScoreLength;
    private final float lengthWeight;
    private final int minMethodLength;

    public ApplicableCandidateProvider(@Value("${maxScoreLength:3}") int maxScoreLength,
            @Value("${lengthWeight:0.1}") float lengthWeight, @Value("${minMethodLength:6}") int minMethodLength) {
        this.maxScoreLength = maxScoreLength;
        this.lengthWeight = lengthWeight;
        this.minMethodLength = minMethodLength;
    }

    Optional<ApplicableCandidate> chooseBestCandidate(List<RefactoringCandidate> candidates,
            MethodDeclaration method, ClassOrInterfaceDeclaration type) {
        return candidates.stream().map(candidate -> computeNewMethodAndScore(candidate, type, method))
                .filter(ApplicableCandidate::isReducesLength)
                .filter(candidate -> ScoreUtils.isLengthEnough(candidate.getRemainingMethod(), minMethodLength))
                .max(Comparator.comparing(ApplicableCandidate::getScore));
    }

    private ApplicableCandidate computeNewMethodAndScore(RefactoringCandidate candidate,
            ClassOrInterfaceDeclaration type, MethodDeclaration method) {
        MethodDeclaration candidateMethod = generateNewMethod(candidate, type, method);
        MethodDeclaration remainingMethod = generateRemainingMethod(candidate, method, candidateMethod);
        float score = computeScore(method, candidateMethod, remainingMethod);
        boolean reducesLength = ScoreUtils.reducesLength(method, candidateMethod, remainingMethod);
        return ApplicableCandidate.builder()
                .candidateMethod(candidateMethod)
                .remainingMethod(remainingMethod)
                .score(score)
                .reducesLength(reducesLength)
                .build();
    }

    private float computeScore(MethodDeclaration method, MethodDeclaration candidateMethod,
            MethodDeclaration remainingMethod) {
        float lengthScore = lengthScore(candidateMethod, remainingMethod);
        int nestDepthScore = ScoreUtils.nestingDepthSocre(method, candidateMethod, remainingMethod);
        float nestAreaScore = ScoreUtils.nestingAreaScore(method, candidateMethod, remainingMethod);
        int paramsScore = ScoreUtils.paramsScore(candidateMethod);
        return lengthScore + nestDepthScore + nestAreaScore + paramsScore;
    }

    private float lengthScore(MethodDeclaration candidateMethod, MethodDeclaration remainingMethod) {
        int candidateLength = ScoreUtils.methodLength(candidateMethod);
        int remainingLength = ScoreUtils.methodLength(remainingMethod);
        return Math.min(lengthWeight * Math.min(candidateLength, remainingLength), maxScoreLength);
    }

    private static MethodDeclaration generateRemainingMethod(RefactoringCandidate candidate, MethodDeclaration method,
            MethodDeclaration candidateMethod) {
        MethodDeclaration remainingMethod = method.clone();
        Statement replacingStatement = getReplacingExpression(candidate, candidateMethod, method);

        List<Statement> toReplace = getStatementsToReplace(candidate, remainingMethod);
        toReplace.get(0).replace(replacingStatement);
        for (int i = 1; i < toReplace.size(); i++) {
            Statement toRemove = toReplace.get(i);
            if (toRemove.isReturnStmt() && !toRemove.asReturnStmt().getExpression().isPresent()) {
                continue;
            }
            Optional<Node> parent = toRemove.getParentNode();
            parent.ifPresent(node -> node.remove(toRemove));
        }
        return remainingMethod;
    }

    private static Statement getReplacingExpression(RefactoringCandidate candidate, MethodDeclaration newMethod,
            MethodDeclaration method) {
        MethodCallExpr methodCallExpr = new MethodCallExpr(newMethod.getNameAsString(),
                newMethod.getParameters().stream().map(Parameter::getName).map(NameExpr::new)
                        .toArray(NameExpr[]::new));
        Expression replacingExpression = methodCallExpr;
        if (candidate.getReturnStmt() != null && candidate.getReturnStmt().getExpression().isPresent()) {
            return new ReturnStmt(methodCallExpr);
        }
        if (candidate.getValueToAssign() != null) {
            ResolvedValueDeclaration valueToAssign = candidate.getValueToAssign();
            List<Statement> statementsToReplace = getStatementsToReplace(candidate, method);
            if (AstUtils.isDeclaredIn(valueToAssign, statementsToReplace)) {
                VariableDeclarator variableDeclarator = new VariableDeclarator(AstUtils.getType(valueToAssign),
                        valueToAssign.getName(), methodCallExpr);
                List<VariableDeclarator> otherDeclarators = getOtherDeclarators(valueToAssign);
                NodeList<VariableDeclarator> declarators = new NodeList<>(otherDeclarators);
                declarators.add(variableDeclarator);
                EnumSet<Modifier> modifiers = AstUtils.getModifiers(valueToAssign);
                replacingExpression = new VariableDeclarationExpr(modifiers, declarators);
            } else {
                replacingExpression = new AssignExpr(new NameExpr(valueToAssign.getName()), methodCallExpr,
                        Operator.ASSIGN);
            }
        }
        return new ExpressionStmt(replacingExpression);
    }

    private static List<VariableDeclarator> getOtherDeclarators(ResolvedValueDeclaration valueToAssign) {
        Node declarationNode = AstUtils.getWrappedNode(valueToAssign);
        return declarationNode.getParentNode()
                .map(ApplicableCandidateProvider::getDeclarators).orElse(Collections.emptyList())
                .stream()
                .filter(declarator -> !declarator.equals(declarationNode))
                .collect(Collectors.toList());
    }

    private static List<VariableDeclarator> getDeclarators(Node node) {
        if (node instanceof VariableDeclarationExpr) {
            return ((VariableDeclarationExpr) node).getVariables();
        }
        return Collections.emptyList();
    }

    private static MethodDeclaration generateNewMethod(RefactoringCandidate candidate, ClassOrInterfaceDeclaration type,
            MethodDeclaration method) {
        MethodDeclaration newMethod = new MethodDeclaration();
        newMethod.setName(computeMethodName(candidate, type, method));
        newMethod.setPrivate(true);
        newMethod.setStatic(method.isStatic());
        newMethod.setType(computeReturnType(candidate, method));
        newMethod.setParameters(new NodeList<>(computeParameters(candidate)));
        newMethod.setThrownExceptions(cloneThrownExceptions(method));
        List<Statement> newMethodStatements =
                getStatementsToReplace(candidate, method).stream().map(Statement::clone).collect(
                        Collectors.toList());
        Statement lastStatement = newMethodStatements.get(newMethodStatements.size() - 1);
        if (lastStatement.isReturnStmt() && !lastStatement.asReturnStmt().getExpression().isPresent()) {
            newMethodStatements.remove(lastStatement);
        }
        if (candidate.getValueToAssign() != null) {
            ReturnStmt returnStmt = new ReturnStmt(new NameExpr(candidate.getValueToAssign().getName()));
            newMethodStatements.add(returnStmt);
        }
        newMethod.setBody(new BlockStmt(new NodeList<>(newMethodStatements)));
        return newMethod;
    }

    private static NodeList<ReferenceType> cloneThrownExceptions(MethodDeclaration method) {
        return method.getThrownExceptions().stream().map(ReferenceType::clone)
                .collect(Collectors.toCollection(NodeList::new));
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
        return candidate.getParameters().stream().map(param -> new Parameter(AstUtils.getType(param), param.getName()))
                .collect(
                        Collectors.toMap(Parameter::getNameAsString, Function.identity(), (p1, p2) -> p1)).values();
    }

    private static Type computeReturnType(RefactoringCandidate candidate, MethodDeclaration method) {
        if (candidate.getValueToAssign() != null) {
            ResolvedValueDeclaration valueToAssign = candidate.getValueToAssign();
            return AstUtils.getType(valueToAssign);
        }
        if (candidate.getReturnStmt() == null) {
            return new VoidType();
        }
        return method.getType();
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
            count = Integer.parseInt(matcher.group(2));
        }
        String newName = name;
        while (existingNames.contains(newName)) {
            newName = name + count;
            count++;
        }
        return newName;
    }
}
