package com.aurea.longmethodrefactor;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
            List<Statement> nextStatements,
            List<Statement> children, int begin, int end, List<Integer> candidatePath) {
        //avoid moving entire method body to another method
        if (statement.getParentNode().isPresent() && statement.getParentNode().get() instanceof MethodDeclaration
                && begin == 0 && end == children.size() - 1) {
            return Optional.empty();
        }
        List<Statement> currentStatements = children.subList(begin, end + 1);
        if (AstUtils.containsReturnChildNode(currentStatements)) {
            return Optional.empty();
        }
        Optional<ReturnStmt> lastReturn = AstUtils.getLastReturnStatement(currentStatements);
        ResolvedValueDeclaration valueToAssign = null;
        if (!lastReturn.isPresent()) {
            List<ResolvedValueDeclaration> declarations = ListUtils.union(AstUtils.getAssignedVariables(currentStatements),
                    AstUtils.getDeclaredVariables(currentStatements));
            List<Statement> currentNext = AstUtils.getChildNextStatements(nextStatements, children, end);
            for (ResolvedValueDeclaration declaration : declarations) {
                if (AstUtils.isUsed(declaration, currentNext)) {
                    if (valueToAssign == null) {
                        valueToAssign = declaration;
                    } else if (!AstUtils.isSameValue(valueToAssign, declaration)) {
                        //too many values to return
                        return Optional.empty();
                    }
                }
            }
        }
        Set<ResolvedValueDeclaration> parameters = AstUtils.getParameters(currentStatements);
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

    List<RefactoringCandidate> refactorLongStatement(Statement statement,
            List<Statement> nextStatements, List<Integer> candidatePath) {
        List<Statement> children = AstUtils.getStatementChildren(statement);
        List<RefactoringCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            Statement child = children.get(i);
            List<Statement> childNextStatements = AstUtils.getChildNextStatements(nextStatements, children, i);
            List<Integer> newPath = new ArrayList<>(candidatePath);
            newPath.add(i);
            candidates.addAll(refactorLongStatement(child, childNextStatements, newPath));
        }
        for (int begin = 0; begin < children.size() - minStatements; begin++) {
            for (int end = begin + minStatements - 1; end <= children.size() - 1; end++) {
                Optional<RefactoringCandidate> candidate = getRefactoringCandidate(statement, nextStatements, children,
                        begin, end, candidatePath);
                candidate.ifPresent(candidates::add);
            }
        }
        return candidates;
    }
}
