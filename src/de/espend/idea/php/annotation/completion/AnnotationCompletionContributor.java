package de.espend.idea.php.annotation.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.documentation.phpdoc.lexer.PhpDocTokenTypes;
import com.jetbrains.php.lang.documentation.phpdoc.psi.PhpDocComment;
import com.jetbrains.php.lang.documentation.phpdoc.psi.tags.PhpDocTag;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import de.espend.idea.php.annotation.AnnotationPropertyParameter;
import de.espend.idea.php.annotation.PhpAnnotationExtension;
import de.espend.idea.php.annotation.Settings;
import de.espend.idea.php.annotation.completion.insert.AnnotationTagInsertHandler;
import de.espend.idea.php.annotation.completion.parameter.CompletionParameter;
import de.espend.idea.php.annotation.dict.AnnotationProperty;
import de.espend.idea.php.annotation.dict.AnnotationPropertyEnum;
import de.espend.idea.php.annotation.dict.AnnotationTarget;
import de.espend.idea.php.annotation.dict.PhpAnnotation;
import de.espend.idea.php.annotation.lookup.PhpAnnotationPropertyLookupElement;
import de.espend.idea.php.annotation.lookup.PhpClassAnnotationLookupElement;
import de.espend.idea.php.annotation.pattern.AnnotationPattern;
import de.espend.idea.php.annotation.util.AnnotationUtil;
import de.espend.idea.php.annotation.util.PhpElementsUtil;
import de.espend.idea.php.annotation.util.PluginUtil;
import de.espend.idea.php.annotation.util.WorkaroundUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class AnnotationCompletionContributor extends CompletionContributor {

    public AnnotationCompletionContributor() {
        extend(CompletionType.BASIC, AnnotationPattern.getDocBlockTag(), new PhpDocBlockTagAnnotations());
        extend(CompletionType.BASIC, AnnotationPattern.getDocAttribute(), new PhpDocAttributeList());
        extend(CompletionType.BASIC, AnnotationPattern.getTextIdentifier(), new PhpDocAttributeValue());
        extend(CompletionType.BASIC, AnnotationPattern.getDefaultPropertyValue(), new PhpDocDefaultValue());
    }

    private class PhpDocDefaultValue  extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {

            PsiElement psiElement = parameters.getOriginalPosition();
            if(psiElement == null | !PluginUtil.isEnabled(psiElement)) {
                return;
            }

            PhpDocTag phpDocTag = PsiTreeUtil.getParentOfType(parameters.getOriginalPosition(), PhpDocTag.class);
            PhpClass phpClass = AnnotationUtil.getAnnotationReference(phpDocTag);
            if(phpClass == null) {
                return;
            }

            AnnotationPropertyParameter annotationPropertyParameter = new AnnotationPropertyParameter(parameters.getOriginalPosition(), phpClass, AnnotationPropertyParameter.Type.DEFAULT);
            providerWalker(parameters, context, result, annotationPropertyParameter);

        }
    }

    private void providerWalker(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result, AnnotationPropertyParameter annotationPropertyParameter) {
        CompletionParameter completionParameter = new CompletionParameter(parameters, context, result);

        for(PhpAnnotationExtension phpAnnotationExtension : AnnotationUtil.getProvider()) {
            Collection<String> stringResults = phpAnnotationExtension.getPropertyValueCompletions(annotationPropertyParameter, completionParameter);
            if(stringResults != null) {
                for(String value: stringResults) {
                    result.addElement(LookupElementBuilder.create(value));
                }
            }
        }
    }

    private class PhpDocAttributeValue extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {

            PsiElement psiElement = parameters.getOriginalPosition();
            if(psiElement == null | !PluginUtil.isEnabled(psiElement)) {
                return;
            }

            // eap: provide psi element wrapped into phpDocString
            if(WorkaroundUtil.isClassFieldName("com.jetbrains.php.lang.documentation.phpdoc.parser.PhpDocElementTypes", "phpDocString")) {
                psiElement = parameters.getOriginalPosition().getContext();
            }

            if(psiElement == null) {
                return;
            }

            PsiElement propertyName = PhpElementsUtil.getPrevSiblingOfPatternMatch(psiElement, PlatformPatterns.psiElement(PhpDocTokenTypes.DOC_IDENTIFIER));
            if(propertyName == null) {
                return;
            }

            PhpDocTag phpDocTag = PsiTreeUtil.getParentOfType(parameters.getOriginalPosition(), PhpDocTag.class);
            PhpClass phpClass = AnnotationUtil.getAnnotationReference(phpDocTag);
            if(phpClass == null) {
                return;
            }

            AnnotationPropertyParameter annotationPropertyParameter = new AnnotationPropertyParameter(parameters.getOriginalPosition(), phpClass, propertyName.getText(), AnnotationPropertyParameter.Type.STRING);
            providerWalker(parameters, context, result, annotationPropertyParameter);

        }
    }

    private class PhpDocAttributeList  extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {
            PsiElement psiElement = completionParameters.getOriginalPosition();

            if(psiElement == null || !PluginUtil.isEnabled(psiElement)) {
                return;
            }

            PhpDocTag phpDocTag = PsiTreeUtil.getParentOfType(psiElement, PhpDocTag.class);
            if(phpDocTag == null) {
                return;
            }

            PhpClass phpClass = AnnotationUtil.getAnnotationReference(phpDocTag);
            if(phpClass == null) {
                return;
            }


            for(Field field: phpClass.getFields()) {
                if(!field.isConstant()) {
                    String propertyName = field.getName();

                    if(field.getDefaultValue() instanceof ArrayCreationExpression) {
                        completionResultSet.addElement(new PhpAnnotationPropertyLookupElement(new AnnotationProperty(propertyName, AnnotationPropertyEnum.ARRAY)));
                    } else {
                        completionResultSet.addElement(new PhpAnnotationPropertyLookupElement(new AnnotationProperty(propertyName, AnnotationPropertyEnum.STRING)));
                    }

                }

            }


        }
    }


    private class PhpDocBlockTagAnnotations  extends CompletionProvider<CompletionParameters> {

        @Override
        protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

            PsiElement psiElement = completionParameters.getOriginalPosition();

            if(psiElement == null || !PluginUtil.isEnabled(psiElement)) {
                return;
            }

            if(!AnnotationPattern.getPossibleDocTag().accepts(psiElement)) {
                return;
            }

            AnnotationTarget annotationTarget = PhpElementsUtil.findAnnotationTarget(PsiTreeUtil.getParentOfType(psiElement, PhpDocComment.class));
            if(annotationTarget == null) {
                return;
            }

            Project project = completionParameters.getPosition().getProject();
            attachLookupElements(project, annotationTarget, completionResultSet);

        }

        private void attachLookupElements(Project project, AnnotationTarget foundTarget, CompletionResultSet completionResultSet) {
            for(PhpAnnotation phpClass: getPhpAnnotationTargetClasses(project, foundTarget)) {
                completionResultSet.addElement(new PhpClassAnnotationLookupElement(phpClass.getPhpClass()).withInsertHandler(AnnotationTagInsertHandler.getInstance()));
            }
        }

        private List<PhpAnnotation> getPhpAnnotationTargetClasses(Project project, AnnotationTarget foundTarget) {
            // @TODO: how handle unknown types
            return AnnotationUtil.getAnnotationsOnTarget(project,
                foundTarget,
                AnnotationTarget.ALL,
                AnnotationTarget.UNKNOWN,
                AnnotationTarget.UNDEFINED
            );
        }

    }

}
