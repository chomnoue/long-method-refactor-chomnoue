package com.aurea.longmethodrefactor;

import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
class ApplicableCandidate {

    private final MethodDeclaration remainingMethod;
    private final MethodDeclaration candidateMethod;
    private final float score;
    private boolean reducesLength;
}
