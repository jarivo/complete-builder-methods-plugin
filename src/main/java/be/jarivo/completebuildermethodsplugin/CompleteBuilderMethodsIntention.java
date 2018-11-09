package be.jarivo.completebuildermethodsplugin;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.PsiModifier.PUBLIC;

public class CompleteBuilderMethodsIntention extends PsiElementBaseIntentionAction implements IntentionAction {
    private static final String BUILDER_CLASS_SUFFIX = "BUILDER";
    private static final String INTENTION_MENU_LABEL = "Complete builder methods";

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
        Optional<PsiClass> builderClassOptional = getBuilderClass(element);
        final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        builderClassOptional.ifPresent(builderClass -> {
            List<PsiMethod> methods = getBuilderMethods(builderClass);
            methods.stream().filter(psiMethod -> psiMethod.hasModifierProperty(PUBLIC) && !psiMethod.isConstructor())
                    .forEach(psiMethod -> {
                        PsiElement prevSibling = element.getPrevSibling();
                        PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) psiElementFactory.createExpressionFromText(String.format("a.%s()", psiMethod.getName()), null);
                        Objects.requireNonNull(methodCallExpression.getMethodExpression().getQualifierExpression()).replace(prevSibling);
                        prevSibling.replace(methodCallExpression);
                    });
            PsiElement builderMethodCalls = element.getPrevSibling();
            if (semicolonNeeded(element)) {
                PsiStatement semicolon = psiElementFactory.createStatementFromText(";", null);
                builderMethodCalls.addAfter(semicolon, builderMethodCalls.getChildren()[builderMethodCalls.getChildren().length - 1]);
            }
            codeStyleManager.reformat(builderMethodCalls);
        });
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        return element.isWritable() && getBuilderClass(element).isPresent();
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return getText();
    }

    @NotNull
    @Override
    public String getText() {
        return INTENTION_MENU_LABEL;
    }

    private Optional<PsiClass> getBuilderClass(@NotNull PsiElement element) {
        Optional<PsiNewExpression> newExpression = findFirstNewExpression(element);
        if (newExpression.isPresent()) {
            final PsiNewExpression psiNewExpression = newExpression.get();
            PsiElement actualClass = getClass(psiNewExpression);
            if (actualClass instanceof PsiClass) {
                PsiClass candidate = (PsiClass) actualClass;
                String className = candidate.getQualifiedName();
                return className != null && className.toUpperCase().endsWith(BUILDER_CLASS_SUFFIX) ? Optional.of(candidate) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private Optional<PsiNewExpression> findFirstNewExpression(PsiElement element) {
        return Optional.ofNullable(PsiTreeUtil.findChildOfType(PsiTreeUtil.findFirstContext(element, true, Condition.TRUE), PsiNewExpression.class));
    }

    private PsiElement getClass(PsiNewExpression psiNewExpression) {
        PsiJavaCodeReferenceElement classReference = psiNewExpression.getClassReference();
        return classReference != null ? classReference.resolve() : null;
    }

    private List<PsiMethod> getBuilderMethods(PsiClass builderClass) {
        return Arrays.stream(builderClass.getMethods())
                .sorted(Comparator.comparing(PsiMethod::getReturnType, (type1, type2) -> Objects.equals(type1, type2) ? 0 : Objects.equals(builderClass, PsiTypesUtil.getPsiClass(type1)) ? -1 : 1))
                .collect(Collectors.toList());
    }

    private boolean semicolonNeeded(PsiElement element) {
        return !(element.getParent() instanceof PsiExpressionList) && !(element instanceof PsiJavaToken && Objects.equals(((PsiJavaToken) element).getTokenType(), JavaTokenType.SEMICOLON));
    }
}

