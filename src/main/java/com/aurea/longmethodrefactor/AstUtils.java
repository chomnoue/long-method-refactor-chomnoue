package com.aurea.longmethodrefactor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.ListUtils;

@UtilityClass
class AstUtils {

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

    private static Optional<ResolvedValueDeclaration> resolveNameExpression(NameExpr nameExpr) {
        try {
            return Optional.of(nameExpr.resolve());
        } catch (UnsolvedSymbolException ex) {
            return Optional.empty();
        }
    }

    static boolean isDeclaredIn(ResolvedValueDeclaration declaration, List<? extends Node> nodes) {
        Node wrappedNode = getWrappedNode(declaration);
        return isDescendantOf(wrappedNode, nodes);
    }

    private static boolean isDescendantOf(Node descendant, List<? extends Node> nodes) {
        return nodes.stream().anyMatch(node -> isDescendantOf(descendant, node));
    }

    private static boolean isDescendantOf(Node descendant, Node node) {
        if (node.equals(descendant)) {
            return true;
        }
        return isDescendantOf(descendant, node.getChildNodes());
    }

    static boolean isUsed(ResolvedValueDeclaration declaration, List<? extends Node> nodes) {
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

    static boolean isSameValue(ResolvedValueDeclaration declaration, ResolvedValueDeclaration other) {
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

    static Optional<ReturnStmt> getLastReturnStatement(List<Statement> currentStatements) {
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

    static boolean containsReturnChildNode(List<Statement> statements) {
        return statements.stream().anyMatch(statement -> statement.findFirst(ReturnStmt.class).isPresent());
    }

    static List<Statement> getChildNextStatements(List<Statement> nextStatements, List<Statement> children,
            int idx) {
        List<Statement> next = children.subList(Math.min(idx + 1, children.size() - 1), children.size());
        return ListUtils.union(next, nextStatements);
    }

    static List<Statement> getStatementChildren(Statement statement) {
        return statement.getChildNodes().stream().filter(node -> node instanceof Statement)
                .map(Statement.class::cast).collect(Collectors.toList());
    }

    static List<ResolvedValueDeclaration> getAssignedVariables(List<Statement> currentStatements) {
        return getNodesOfType(currentStatements, AssignExpr.class).stream()
                .map(AstUtils::resolve).collect(Collectors.toList());
    }

    static List<ResolvedValueDeclaration> getDeclaredVariables(List<Statement> currentStatements) {
        return getNodesOfType(currentStatements, VariableDeclarator.class).stream()
                .map(VariableDeclarator::resolve).collect(Collectors.toList());
    }

    static Set<ResolvedValueDeclaration> getParameters(List<? extends Node> nodes) {
        return nodes.stream().flatMap(node -> node.findAll(NameExpr.class).stream())
                .map(AstUtils::resolveNameExpression)
                .flatMap(valueDeclaration -> valueDeclaration.map(Stream::of).orElse(Stream.empty()))
                .filter(declaration -> !isDeclaredIn(declaration, nodes))
                .collect(Collectors.toSet());
    }

    static Type getType(ResolvedValueDeclaration declaration) {
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
}
