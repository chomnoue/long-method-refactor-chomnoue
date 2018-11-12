package com.aurea.longmethodrefactor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.ListUtils;

public class LongMethodRefactor {

    private static final int MIN_STATEMENTS = 3;
    private static final int maxLength = 10;

    public static void main(String[] args) throws FileNotFoundException {
        File javaFile = new File("/home/chomnoue/projects/bootcamp/long-method-refactor-reference/src/main/java/com"
                + "/aurea/longmethod/refactor/Test.java");
        CompilationUnit compilationUnit = JavaParser.parse(javaFile);
        compilationUnit.getTypes().stream().filter(type -> type instanceof ClassOrInterfaceDeclaration)
                .forEach(type -> refactorLongMethod(type.asClassOrInterfaceDeclaration()));
        System.out.println(compilationUnit);
    }

    private static boolean refactorLongMethod(ClassOrInterfaceDeclaration type) {
        return type.getMethods().stream().anyMatch(method -> refactorLongMethod(type, method));
    }

    private static boolean refactorLongMethod(ClassOrInterfaceDeclaration type, MethodDeclaration method) {
        int length = method.getEnd().get().line - method.getBegin().get().line + 1;
        if (length <= maxLength) {
            return false;
        }
        return method.getBody().map(body -> refactorLongStatement(type, body, Collections.emptyList())).orElse(false);
    }

    private static boolean refactorLongStatement(ClassOrInterfaceDeclaration type, Statement statement,
            List<Statement> nextStatements) {
        List<Statement> children = statement.getChildNodes().stream().filter(node -> node instanceof Statement)
                .map(Statement.class::cast).collect(Collectors.toList());
        for (int i = 0; i < children.size(); i++) {
            Statement child = children.get(i);
            List<Statement> childNextStatements = getChildNextStatements(nextStatements, children, i);
            if (refactorLongStatement(type, child, childNextStatements)) {
                return true;
            }
        }
        List<RefactoringCandidate> candidates = new ArrayList<>();
        for (int begin = 0; begin < children.size() - MIN_STATEMENTS; begin++) {
            for (int end = begin + MIN_STATEMENTS - 1; end <= children.size(); end++) {
                //avoid moving entire method body to another method
                if(statement.getParentNode().get() instanceof MethodDeclaration && begin ==0 && end ==children.size()){
                    continue;
                }
                List<Statement> currentStatements = children.subList(begin, end+1);
                if(containsReturnChildNode(currentStatements)){
                    continue;;
                }
                Optional<ReturnStmt> lastReturn = getLastReturnStatement(currentStatements);
                NameExpr nameExpr = null;
                if(!lastReturn.isPresent()){
                    List<AssignExpr> assignExprs = getAssignExpressions(currentStatements);
                    Set<ResolvedValueDeclaration> variables = assignExprs.stream()
                            .map(LongMethodRefactor::resolve).collect(Collectors.toSet());
                }

                List<Statement> currentNext = getChildNextStatements(nextStatements, children, end);

            }
        }
    }

    private static ResolvedValueDeclaration resolve(AssignExpr assignExpr) {
        return assignExpr.getTarget().asNameExpr().resolve();
    }

    private static Optional<ReturnStmt> getLastReturnStatement(List<Statement> currentStatements) {
        if(currentStatements.isEmpty() || ! (currentStatements.get(currentStatements.size()-1) instanceof ReturnStmt)){
            return Optional.empty();
        }
        return Optional.of(currentStatements.get(currentStatements.size()-1).asReturnStmt());
    }

    private static List<AssignExpr> getAssignExpressions(List<Statement> currentStatements) {
        return currentStatements.stream().flatMap(statement -> statement.findAll(AssignExpr.class).stream()).collect(
                Collectors.toList());
    }

    private static boolean containsReturnChildNode(List<Statement> statements) {
        return statements.stream().anyMatch(statement -> statement.findFirst(Statement.class).isPresent());
    }

    private static List<Statement> getChildNextStatements(List<Statement> nextStatements, List<Statement> children,
            int idx) {
        List<Statement> next = children.subList(Math.min(idx + 1, children.size() - 1), children.size() - 1);
        return ListUtils.union(next, nextStatements);
    }

    private class RefactoringCandidate {
        int beginIndex;
        int endIndex;
        List<Parameter> parameters;
        NameExpr nameExpr;
        ReturnStmt returnStmt;
    }
}
