package com.aurea.longmethodrefactor;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntryStmt;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RefactoringCandidatesProvider {

    private final int minStatements;

    public RefactoringCandidatesProvider(@Value("${minStatements:3}") int minStatements) {
        this.minStatements = minStatements;
    }

    private static Optional<RefactoringCandidate> getRefactoringCandidate(Statement statement,
            List<Statement> nextStatements, List<Statement> children, int begin, int end, List<Integer> candidatePath) {
        if (isFullMethodBody(statement, children, begin, end)) { //avoid moving entire method body to another method
            return Optional.empty();
        }
        List<Statement> currentStatements = children.subList(begin, end + 1);
        if (isNotSupported(currentStatements)) {
            return Optional.empty();
        }
        Optional<ReturnStmt> lastReturn = AstUtils.getLastReturnStatement(currentStatements);
        ResolvedValueDeclaration valueToAssign = null;
        if (!lastReturn.isPresent()) {
            List<ResolvedValueDeclaration> declarations = ListUtils
                    .union(AstUtils.getAssignedVariables(currentStatements),
                            AstUtils.getDeclaredVariables(currentStatements))
                    .stream().filter(AstUtils::isNotAField).collect(Collectors.toList());
            List<Statement> currentNext = AstUtils.getChildNextStatements(nextStatements, children, end);
            for (ResolvedValueDeclaration declaration : declarations) {
                if (AstUtils.isUsed(declaration, currentNext)) {
                    if (valueToAssign == null) {
                        valueToAssign = declaration;
                        //TODO refactor this to avod deeply nested ifs
                    } else if (!AstUtils.isSameValue(valueToAssign, declaration)) { //NOPMD
                        return Optional.empty();//too many values to return
                    }
                }
            }
        }
        return buildRefactoringCandidate(begin, end, candidatePath, currentStatements, lastReturn.orElse(null),
                valueToAssign);
    }

    private static boolean isNotSupported(List<Statement> currentStatements) {
        if (AstUtils.containsReturnChildNode(currentStatements)) {
            return true;
        }
        if (currentStatements.stream().anyMatch(statement -> statement instanceof SwitchEntryStmt)) {
            return true;
        }
        if (breaksExternalLoop(currentStatements)) {
            return true;
        }
        if (continuesExternalLoop(currentStatements)) {
            return true;
        }

        return false;
    }

    private static boolean breaksExternalLoop(List<Statement> currentStatements) {
        List<BreakStmt> breakStmts = AstUtils.getNodesOfType(currentStatements, BreakStmt.class);
        return breakStmts.stream().anyMatch(breakStmt -> AstUtils.getBreakParent(breakStmt)
                .map(parent -> !AstUtils.isDescendantOf(parent, currentStatements)).orElse(true));
    }

    private static boolean continuesExternalLoop(List<Statement> currentStatements) {
        List<ContinueStmt> continueStmts = AstUtils.getNodesOfType(currentStatements, ContinueStmt.class);
        return continueStmts.stream().anyMatch(continueStmt -> AstUtils.getContinueParent(continueStmt)
                .map(parent -> !AstUtils.isDescendantOf(parent, currentStatements)).orElse(true));
    }

    private static boolean isFullMethodBody(Statement statement, List<Statement> children, int begin, int end) {
        return statement.getParentNode().isPresent() && statement.getParentNode().get() instanceof MethodDeclaration
                && begin == 0 && end == children.size() - 1;
    }

    private static Optional<RefactoringCandidate> buildRefactoringCandidate(int begin, int end,
            List<Integer> candidatePath, List<Statement> currentStatements, ReturnStmt lastReturn,
            ResolvedValueDeclaration valueToAssign) {
        Set<ResolvedValueDeclaration> parameters = AstUtils.getParameters(currentStatements);
        if (parameters.stream()
                .anyMatch(param -> !AstUtils.isAssignedOnDeclaration(param))) {
            return Optional.empty(); //value to assign might not have been initialized
        }
        RefactoringCandidate refactoringCandidate = RefactoringCandidate.builder()
                .firstStatement(begin)
                .lastStatement(end)
                .path(candidatePath)
                .returnStmt(lastReturn)
                .valueToAssign(valueToAssign)
                .parameters(parameters)
                .build();
        return Optional.of(refactoringCandidate);
    }

    List<RefactoringCandidate> refactorLongStatement(Statement statement,
            List<Statement> nextStatements, List<Integer> candidatePath) {
        if (statement.isTryStmt() && !statement.asTryStmt().getCatchClauses().isEmpty()) {
            return Collections.emptyList();
        }
        List<Statement> children = AstUtils.getStatementChildren(statement);
        List<RefactoringCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            Statement child = children.get(i);
            List<Statement> childNextStatements = AstUtils.getChildNextStatements(nextStatements, children, i);
            List<Integer> newPath = new ArrayList<>(candidatePath);
            newPath.add(i);
            candidates.addAll(refactorLongStatement(child, childNextStatements, newPath));
        }
        for (int begin = 0; begin <= children.size() - minStatements; begin++) {
            for (int end = begin + minStatements - 1; end <= children.size() - 1; end++) {
                Optional<RefactoringCandidate> candidate = getRefactoringCandidate(statement, nextStatements, children,
                        begin, end, candidatePath);
                candidate.ifPresent(candidates::add);
            }
        }
        return candidates;
    }
}
