package com.aurea.longmethod.refactor.utils;

import static com.github.javaparser.ast.expr.UnaryExpr.Operator.POSTFIX_DECREMENT;
import static com.github.javaparser.ast.expr.UnaryExpr.Operator.POSTFIX_INCREMENT;
import static com.github.javaparser.ast.expr.UnaryExpr.Operator.PREFIX_DECREMENT;
import static com.github.javaparser.ast.expr.UnaryExpr.Operator.PREFIX_INCREMENT;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.LabeledStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserEnumConstantDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.ListUtils;

@UtilityClass
public class AstUtils {

    private static final EnumSet<UnaryExpr.Operator> UNARY_ASSIGN_OPERATORS = EnumSet.of(PREFIX_INCREMENT,
            PREFIX_DECREMENT, POSTFIX_INCREMENT, POSTFIX_DECREMENT);

    public static boolean isUsed(ResolvedValueDeclaration declaration, List<? extends Node> nodes) {
        return nodes.stream().anyMatch(child -> isUsed(declaration, child));
    }

    private static boolean isUsed(ResolvedValueDeclaration declaration, Node node) {
        if (node instanceof NameExpr) {
            NameExpr nameExpr = (NameExpr) node;
            Optional<ResolvedValueDeclaration> maybeNameDeclaration = ResolveUtils.resolveNameExpression(nameExpr);
            return maybeNameDeclaration.map(nameDeclaration -> isSameValue(declaration, nameDeclaration)).orElse(false);
        }
        return isUsed(declaration, node.getChildNodes());
    }

    public static boolean isSameValue(ResolvedValueDeclaration declaration, ResolvedValueDeclaration other) {
        return getSymbolDeclarationWrappedNode(declaration) == getSymbolDeclarationWrappedNode(other);
    }

    private static Node getSymbolDeclarationWrappedNode(ResolvedValueDeclaration declaration) {
        Node node = ResolveUtils.getWrappedNode(declaration);
        if (node instanceof VariableDeclarationExpr) {
            return ((VariableDeclarationExpr) node).getVariable(0);
        }
        return node;
    }

    public static Optional<ReturnStmt> getLastReturnStatement(List<Statement> currentStatements) {
        if (currentStatements.isEmpty() || !(currentStatements
                .get(currentStatements.size() - 1) instanceof ReturnStmt)) {
            return Optional.empty();
        }
        return Optional.of(currentStatements.get(currentStatements.size() - 1).asReturnStmt());
    }

    public static <T extends Node> List<T> getNodesOfType(List<Statement> currentStatements, Class<T> type) {
        return currentStatements.stream().flatMap(statement -> statement.findAll(type).stream()).collect(
                Collectors.toList());
    }

    public static Optional<Statement> getBreakParent(BreakStmt breakStmt) {
        if (breakStmt.getLabel().isPresent()) {
            return getAncestorWithLabel(breakStmt, breakStmt.getLabel().get().asString());
        }
        return getAncestorOfType(breakStmt,
                Arrays.asList(ForStmt.class, SwitchStmt.class, DoStmt.class, WhileStmt.class));
    }

    public static Optional<Statement> getContinueParent(ContinueStmt continueStmt) {
        if (continueStmt.getLabel().isPresent()) {
            return getAncestorWithLabel(continueStmt, continueStmt.getLabel().get().asString());
        }
        return getAncestorOfType(continueStmt,
                Arrays.asList(ForStmt.class, DoStmt.class, WhileStmt.class));
    }

    private static Optional<Statement> getAncestorWithLabel(Node node, String label) {
        Optional<Statement> maybeLabeled = getAncestorOfType(node, Collections.singletonList(LabeledStmt.class));
        if (maybeLabeled.isPresent()) {
            LabeledStmt labeledParent = (LabeledStmt) maybeLabeled.get();
            if (label.equals(labeledParent.getLabel().asString())) {
                return Optional.of(labeledParent);
            }
            return getAncestorWithLabel(labeledParent, label);
        }
        return Optional.empty();
    }

    private static Optional<Statement> getAncestorOfType(Node node, List<Class<? extends Statement>> candidates) {
        Optional<Node> maybeParent = node.getParentNode();
        if (maybeParent.isPresent()) {
            Node parent = maybeParent.get();
            for (Class<? extends Statement> candidateType : candidates) {
                if (candidateType.isInstance(parent)) {
                    return Optional.of((Statement) parent);
                }
            }
            return getAncestorOfType(parent, candidates);
        }
        return Optional.empty();
    }

    public static boolean containsReturnChildNode(List<Statement> statements) {
        return statements.stream().flatMap(statement -> statement.getChildNodes().stream())
                .anyMatch(statement -> statement.findFirst(ReturnStmt.class).isPresent());
    }

    public static List<Statement> getChildNextStatements(List<Statement> nextStatements, List<Statement> children,
            int idx) {
        List<Statement> next = children.size() <= idx + 1 ? Collections.emptyList() :
                children.subList(idx + 1, children.size());
        return ListUtils.union(next, nextStatements);
    }

    public static List<Statement> getStatementChildren(Statement statement) {
        return statement.getChildNodes().stream().filter(node -> node instanceof Statement)
                .map(Statement.class::cast).collect(Collectors.toList());
    }

    public static List<ResolvedValueDeclaration> getAssignedVariables(List<Statement> currentStatements) {
        return getNodesOfType(currentStatements, AssignExpr.class).stream()
                .filter(assignExpr -> !(assignExpr.getTarget() instanceof ArrayAccessExpr))
                .map(ResolveUtils::resolveTarget).collect(Collectors.toList());
    }

    public static List<ResolvedValueDeclaration> getUnaryAssignedVariables(List<Statement> currentStatements) {
        return getNodesOfType(currentStatements, UnaryExpr.class).stream()
                .filter(unaryExpr -> UNARY_ASSIGN_OPERATORS.contains(unaryExpr.getOperator()))
                .filter(unaryExpr -> !(unaryExpr.getExpression() instanceof ArrayAccessExpr))
                .map(ResolveUtils::resolveTarget).collect(Collectors.toList());
    }

    public static List<ResolvedValueDeclaration> getDeclaredVariables(List<Statement> currentStatements) {
        return getNodesOfType(currentStatements, VariableDeclarator.class).stream()
                .map(VariableDeclarator::resolve).collect(Collectors.toList());
    }

    public static Set<ResolvedValueDeclaration> getParameters(List<? extends Node> nodes) {
        return nodes.stream().flatMap(node -> node.findAll(NameExpr.class).stream())
                .map(ResolveUtils::resolveNameExpression)
                .flatMap(valueDeclaration -> valueDeclaration.map(Stream::of).orElse(Stream.empty()))
                .filter(AstUtils::isNotAField)
                .filter(declaration -> !ResolveUtils.isDeclaredIn(declaration, nodes))
                .collect(Collectors.toSet());
    }

    public static boolean isNotAField(ResolvedValueDeclaration declaration) {
        return !(declaration instanceof JavaParserFieldDeclaration
                || declaration instanceof JavaParserEnumConstantDeclaration);
    }

    public static Type getType(ResolvedValueDeclaration declaration) {
        Node wrappedNode = ResolveUtils.getWrappedNode(declaration);
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

    public static boolean isAssignedOnDeclaration(ResolvedValueDeclaration param) {
        Node wrappedNode = ResolveUtils.getWrappedNode(param);
        if (wrappedNode instanceof VariableDeclarator) {
            return isAssignedOnDeclaration((VariableDeclarator) wrappedNode);
        }
        if (wrappedNode instanceof VariableDeclarationExpr) {
            return ((VariableDeclarationExpr) wrappedNode).getVariables().stream()
                    .allMatch(AstUtils::isAssignedOnDeclaration);
        }
        return true;
    }

    private static boolean isAssignedOnDeclaration(VariableDeclarator variableDeclarator) {
        return variableDeclarator.getInitializer().isPresent();
    }

    public static EnumSet<Modifier> getModifiers(ResolvedValueDeclaration declaration) {
        Node wrappedNode = ResolveUtils.getWrappedNode(declaration);
        if (wrappedNode instanceof VariableDeclarationExpr) {
            return getModifiers((VariableDeclarationExpr) wrappedNode);
        }
        if (wrappedNode instanceof VariableDeclarator) {
            Optional<Node> maybeParent = wrappedNode.getParentNode();
            if (maybeParent.isPresent() && maybeParent.get() instanceof VariableDeclarationExpr) {
                return getModifiers((VariableDeclarationExpr) maybeParent.get());
            }
        }
        throw new IllegalArgumentException("Unsupported declaration: " + declaration);
    }

    private static EnumSet<Modifier> getModifiers(VariableDeclarationExpr declaration) {
        return EnumSet.copyOf(declaration.getModifiers());
    }

}
