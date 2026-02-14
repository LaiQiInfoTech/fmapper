package dev.w0fv1.mapper;

import com.google.auto.service.AutoService;
import jakarta.persistence.Entity;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("jakarta.persistence.Entity")
@SupportedOptions("fmapper.inline")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class FieldMapperProcessor extends AbstractProcessor {

    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;

    private boolean inlineEnabled;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        messager = env.getMessager();
        elementUtils = env.getElementUtils();
        typeUtils = env.getTypeUtils();
        inlineEnabled = Boolean.parseBoolean(env.getOptions().getOrDefault("fmapper.inline", "false"));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Entity.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Entity can only be applied to classes", element);
                continue;
            }

            TypeElement classElement = (TypeElement) element;

            List<VariableElement> fields = new ArrayList<>();
            for (Element enclosed : classElement.getEnclosedElements()) {
                if (enclosed.getKind() == ElementKind.FIELD) {
                    fields.add((VariableElement) enclosed);
                }
            }

            if (fields.isEmpty()) continue;

            if (inlineEnabled) {
                tryInjectFieldMapper(classElement, fields);
            }
        }
        return true;
    }

    private void tryInjectFieldMapper(TypeElement classElement, List<VariableElement> fields) {
        try {
            dev.w0fv1.mapper.javac.JavacFieldMapperInjector.inject(
                    processingEnv,
                    messager,
                    elementUtils,
                    typeUtils,
                    classElement,
                    fields
            );
        } catch (Throwable t) {
            messager.printMessage(
                    Diagnostic.Kind.WARNING,
                    "fmapper: inline injection failed for " + classElement.getSimpleName()
                            + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")",
                    classElement
            );
        }
    }

    // Code generation via JavaPoet has been removed; this processor is inline-only.
}
