package com.aurea.longmethodrefactor;

import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
class RefactoringCandidate {

    private final int firstStatement;
    private final int lastStatement;
    private final List<Integer> path;
    private final Set<ResolvedValueDeclaration> parameters;
    private final ResolvedValueDeclaration valueToAssign;
    private final ReturnStmt returnStmt;
}
