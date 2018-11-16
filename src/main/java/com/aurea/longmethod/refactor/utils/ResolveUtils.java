package com.aurea.longmethod.refactor.utils;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.resolution.Resolvable;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserEnumConstantDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserParameterDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserVariableDeclaration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ResolveUtils {

    public static Node getWrappedNode(ResolvedValueDeclaration declaration) {
        if (declaration instanceof JavaParserVariableDeclaration) {
            return ((JavaParserVariableDeclaration) declaration).getWrappedNode();
        }
        if (declaration instanceof JavaParserParameterDeclaration) {
            return ((JavaParserParameterDeclaration) declaration).getWrappedNode();
        }
        if (declaration instanceof JavaParserSymbolDeclaration) {
            return ((JavaParserSymbolDeclaration) declaration).getWrappedNode();
        }
        if (declaration instanceof JavaParserFieldDeclaration) {
            return ((JavaParserFieldDeclaration) declaration).getWrappedNode();
        }
        if (declaration instanceof JavaParserEnumConstantDeclaration) {
            return ((JavaParserEnumConstantDeclaration) declaration).getWrappedNode();
        }
        throw new IllegalArgumentException("Unsupported type: " + declaration);
    }

    static Optional<ResolvedValueDeclaration> resolveNameExpression(NameExpr nameExpr) {
        try {
            return Optional.of(nameExpr.resolve());
        } catch (UnsolvedSymbolException ex) {
            return Optional.empty();
        }
    }

    static ResolvedValueDeclaration resolveTarget(AssignExpr assignExpr) {
        return resolveTargetExpression(assignExpr.getTarget());
    }

    static ResolvedValueDeclaration resolveTarget(UnaryExpr unaryExpr) {
        return resolveTargetExpression(unaryExpr.getExpression());
    }

    private static ResolvedValueDeclaration resolveTargetExpression(Expression target) {
        if (target instanceof Resolvable) {
            Object resolved = ((Resolvable) target).resolve();
            if (resolved instanceof ResolvedValueDeclaration) {
                return (ResolvedValueDeclaration) resolved;
            }
        }
        throw new IllegalArgumentException("Unsupported target: " + target);
    }

    public static boolean isDeclaredOutsideLoop(ResolvedValueDeclaration declaration, Statement statement) {
        Optional<Statement> loopBody = getLoopBody(statement);
        return loopBody.map(body -> !isDeclaredIn(declaration, Collections.singletonList(body))).orElse(false);
    }

    private static Optional<Statement> getLoopBody(Node node) {
        Statement body = null;
        if (node instanceof ForStmt) {
            body = ((ForStmt) node).getBody();
        } else if (node instanceof WhileStmt) {
            body = ((WhileStmt) node).getBody();
        } else if (node instanceof DoStmt) {
            body = ((DoStmt) node).getBody();
        }
        if (body != null) {
            return Optional.of(body);
        }
        return node.getParentNode().flatMap(ResolveUtils::getLoopBody);
    }

    public static boolean isDeclaredIn(ResolvedValueDeclaration declaration, List<? extends Node> nodes) {
        Node wrappedNode = getWrappedNode(declaration);
        return isDescendantOf(wrappedNode, nodes);
    }

    public static boolean isDescendantOf(Node descendant, List<? extends Node> nodes) {
        return nodes.stream().anyMatch(node -> isDescendantOf(descendant, node));
    }

    private static boolean isDescendantOf(Node descendant, Node node) {
        if (node.equals(descendant)) {
            return true;
        }
        return isDescendantOf(descendant, node.getChildNodes());
    }
}
