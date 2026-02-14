package dev.w0fv1.mapper.javac;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;

public final class JavacFieldMapperInjector {

    private JavacFieldMapperInjector() {
    }

    public static void inject(
            ProcessingEnvironment processingEnv,
            Messager messager,
            Elements elementUtils,
            Types typeUtils,
            TypeElement classElement,
            List<VariableElement> fields
    ) {
        if (!(processingEnv instanceof JavacProcessingEnvironment javacEnv)) return;
        if (fields.isEmpty()) return;

        JavacTrees javacTrees = JavacTrees.instance(processingEnv);
        Context context = javacEnv.getContext();
        TreeMaker treeMaker = TreeMaker.instance(context);
        Names names = Names.instance(context);

        JCTree tree = javacTrees.getTree(classElement);
        if (!(tree instanceof JCTree.JCClassDecl outerClass)) return;
        if (hasInnerClassNamed(outerClass, "FieldMapper")) return;

        // Give injected nodes a reasonable source position to satisfy javac invariants.
        treeMaker.at(outerClass.pos);

        JCTree.JCClassDecl injected = makeFieldMapperClass(treeMaker, names, elementUtils, typeUtils, outerClass, fields);
        outerClass.defs = outerClass.defs.append(injected);
    }

    private static boolean hasInnerClassNamed(JCTree.JCClassDecl outerClass, String innerName) {
        for (JCTree def : outerClass.defs) {
            if (def instanceof JCTree.JCClassDecl inner && inner.name.toString().equals(innerName)) {
                return true;
            }
        }
        return false;
    }

    private static JCTree.JCClassDecl makeFieldMapperClass(
            TreeMaker treeMaker,
            Names names,
            Elements elementUtils,
            Types typeUtils,
            JCTree.JCClassDecl outerClass,
            List<VariableElement> fields
    ) {
        long mods = Flags.PUBLIC | Flags.STATIC;

        com.sun.tools.javac.util.List<JCTree> defs = com.sun.tools.javac.util.List.nil();
        defs = defs.append(makeFieldMapperSetMethod(treeMaker, names, elementUtils, typeUtils, outerClass, fields));
        defs = defs.append(makeFieldMapperGetMethod(treeMaker, names, outerClass, fields));
        for (VariableElement field : fields) {
            defs = defs.append(makeTypedFieldSetter(treeMaker, names, elementUtils, typeUtils, outerClass, field));
            defs = defs.append(makeTypedFieldGetter(treeMaker, names, outerClass, field));
        }

        return treeMaker.ClassDef(
                treeMaker.Modifiers(mods),
                names.fromString("FieldMapper"),
                com.sun.tools.javac.util.List.nil(),
                null,
                com.sun.tools.javac.util.List.nil(),
                defs
        );
    }

    private static JCTree.JCMethodDecl makeTypedFieldSetter(
            TreeMaker treeMaker,
            Names names,
            Elements elementUtils,
            Types typeUtils,
            JCTree.JCClassDecl outerClass,
            VariableElement field
    ) {
        long mods = Flags.PUBLIC | Flags.STATIC;

        String fieldName = field.getSimpleName().toString();
        String cap = capitalize(fieldName);
        String methodName = "set" + cap;

        JCTree.JCVariableDecl instanceParam = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("instance"),
                treeMaker.Ident(outerClass.name),
                null
        );

        JCTree.JCExpression valueType = treeMaker.Type((Type) field.asType());
        JCTree.JCVariableDecl valueParam = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString(fieldName),
                valueType,
                null
        );

        JCTree.JCExpression instanceIdent = treeMaker.Ident(names.fromString("instance"));
        JCTree.JCExpression valueIdent = treeMaker.Ident(names.fromString(fieldName));

        JCTree.JCStatement stmt;
        if (isList(elementUtils, typeUtils, field)) {
            stmt = makeTypedListSetStatement(treeMaker, names, instanceIdent, valueIdent, cap);
        } else {
            stmt = treeMaker.Exec(makeTypedSetterCall(treeMaker, names, instanceIdent, valueIdent, cap));
        }

        JCTree.JCBlock body = treeMaker.Block(0, com.sun.tools.javac.util.List.of(stmt));

        return treeMaker.MethodDef(
                treeMaker.Modifiers(mods),
                names.fromString(methodName),
                treeMaker.TypeIdent(TypeTag.VOID),
                com.sun.tools.javac.util.List.nil(),
                com.sun.tools.javac.util.List.of(instanceParam, valueParam),
                com.sun.tools.javac.util.List.nil(),
                body,
                null
        );
    }

    private static JCTree.JCMethodDecl makeTypedFieldGetter(
            TreeMaker treeMaker,
            Names names,
            JCTree.JCClassDecl outerClass,
            VariableElement field
    ) {
        long mods = Flags.PUBLIC | Flags.STATIC;

        String fieldName = field.getSimpleName().toString();
        String cap = capitalize(fieldName);
        String methodName = "get" + cap;

        JCTree.JCVariableDecl instanceParam = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("instance"),
                treeMaker.Ident(outerClass.name),
                null
        );

        JCTree.JCExpression instanceIdent = treeMaker.Ident(names.fromString("instance"));
        JCTree.JCExpression getterCall = makeGetterCall(treeMaker, names, instanceIdent, cap);

        JCTree.JCBlock body = treeMaker.Block(0, com.sun.tools.javac.util.List.of(treeMaker.Return(getterCall)));

        return treeMaker.MethodDef(
                treeMaker.Modifiers(mods),
                names.fromString(methodName),
                treeMaker.Type((Type) field.asType()),
                com.sun.tools.javac.util.List.nil(),
                com.sun.tools.javac.util.List.of(instanceParam),
                com.sun.tools.javac.util.List.nil(),
                body,
                null
        );
    }

    private static JCTree.JCStatement makeTypedListSetStatement(
            TreeMaker treeMaker,
            Names names,
            JCTree.JCExpression instanceIdent,
            JCTree.JCExpression valueIdent,
            String cap
    ) {
        JCTree.JCExpression getterCall1 = makeGetterCall(treeMaker, names, instanceIdent, cap);
        JCTree.JCExpression notNull = treeMaker.Binary(
                JCTree.Tag.NE,
                getterCall1,
                treeMaker.Literal(TypeTag.BOT, null)
        );

        JCTree.JCExpression getterCall2 = makeGetterCall(treeMaker, names, instanceIdent, cap);
        JCTree.JCStatement clearStmt = treeMaker.Exec(treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(getterCall2, names.fromString("clear")),
                com.sun.tools.javac.util.List.nil()
        ));

        JCTree.JCExpression getterCall3 = makeGetterCall(treeMaker, names, instanceIdent, cap);
        JCTree.JCStatement addAllStmt = treeMaker.Exec(treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(getterCall3, names.fromString("addAll")),
                com.sun.tools.javac.util.List.of(valueIdent)
        ));

        JCTree.JCStatement thenBlock = treeMaker.Block(0, com.sun.tools.javac.util.List.of(clearStmt, addAllStmt));
        JCTree.JCStatement elseBlock = treeMaker.Block(0, com.sun.tools.javac.util.List.of(
                treeMaker.Exec(makeTypedSetterCall(treeMaker, names, instanceIdent, valueIdent, cap))
        ));

        return treeMaker.If(notNull, thenBlock, elseBlock);
    }

    private static JCTree.JCExpression makeTypedSetterCall(
            TreeMaker treeMaker,
            Names names,
            JCTree.JCExpression instanceIdent,
            JCTree.JCExpression valueIdent,
            String cap
    ) {
        String setterName = "set" + cap;
        return treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(instanceIdent, names.fromString(setterName)),
                com.sun.tools.javac.util.List.of(valueIdent)
        );
    }

    private static JCTree.JCMethodDecl makeFieldMapperSetMethod(
            TreeMaker treeMaker,
            Names names,
            Elements elementUtils,
            Types typeUtils,
            JCTree.JCClassDecl outerClass,
            List<VariableElement> fields
    ) {
        long mods = Flags.PUBLIC | Flags.STATIC;

        JCTree.JCVariableDecl instanceParam = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("instance"),
                treeMaker.Ident(outerClass.name),
                null
        );
        JCTree.JCVariableDecl fieldParam = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("field"),
                treeMaker.Ident(names.fromString("String")),
                null
        );
        JCTree.JCVariableDecl valueParam = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("value"),
                treeMaker.Ident(names.fromString("Object")),
                null
        );

        JCTree.JCExpression instanceIdent = treeMaker.Ident(names.fromString("instance"));
        JCTree.JCExpression fieldIdent = treeMaker.Ident(names.fromString("field"));
        JCTree.JCExpression valueIdent = treeMaker.Ident(names.fromString("value"));

        JCTree.JCStatement elsePart = treeMaker.Block(0, com.sun.tools.javac.util.List.of(makeUnknownFieldThrow(treeMaker, names, fieldIdent)));

        for (int i = fields.size() - 1; i >= 0; i--) {
            VariableElement field = fields.get(i);
            String fieldName = field.getSimpleName().toString();
            String cap = capitalize(fieldName);

            JCTree.JCExpression condition = makeStringEquals(treeMaker, names, fieldName, fieldIdent);
            JCTree.JCStatement thenPart;

            if (isList(elementUtils, typeUtils, field)) {
                String castTypeName = typeUtils.erasure(field.asType()).toString();
                thenPart = makeListSetStatement(treeMaker, names, instanceIdent, valueIdent, cap, castTypeName);
            } else {
                String castTypeName = castTypeNameForSet(typeUtils, field);
                thenPart = treeMaker.Block(0, com.sun.tools.javac.util.List.of(
                        treeMaker.Exec(makeSetterCall(treeMaker, names, instanceIdent, valueIdent, cap, castTypeName))
                ));
            }

            elsePart = treeMaker.If(condition, thenPart, elsePart);
        }

        JCTree.JCBlock body = treeMaker.Block(0, com.sun.tools.javac.util.List.of(elsePart));

        return treeMaker.MethodDef(
                treeMaker.Modifiers(mods),
                names.fromString("set"),
                treeMaker.TypeIdent(TypeTag.VOID),
                com.sun.tools.javac.util.List.nil(),
                com.sun.tools.javac.util.List.of(instanceParam, fieldParam, valueParam),
                com.sun.tools.javac.util.List.nil(),
                body,
                null
        );
    }

    private static JCTree.JCMethodDecl makeFieldMapperGetMethod(
            TreeMaker treeMaker,
            Names names,
            JCTree.JCClassDecl outerClass,
            List<VariableElement> fields
    ) {
        long mods = Flags.PUBLIC | Flags.STATIC;

        JCTree.JCVariableDecl instanceParam = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("instance"),
                treeMaker.Ident(outerClass.name),
                null
        );
        JCTree.JCVariableDecl fieldParam = treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER),
                names.fromString("field"),
                treeMaker.Ident(names.fromString("String")),
                null
        );

        JCTree.JCExpression instanceIdent = treeMaker.Ident(names.fromString("instance"));
        JCTree.JCExpression fieldIdent = treeMaker.Ident(names.fromString("field"));

        JCTree.JCStatement elsePart = treeMaker.Block(0, com.sun.tools.javac.util.List.of(makeUnknownFieldThrow(treeMaker, names, fieldIdent)));

        for (int i = fields.size() - 1; i >= 0; i--) {
            VariableElement field = fields.get(i);
            String fieldName = field.getSimpleName().toString();
            String cap = capitalize(fieldName);

            JCTree.JCExpression condition = makeStringEquals(treeMaker, names, fieldName, fieldIdent);
            JCTree.JCExpression getterCall = makeGetterCall(treeMaker, names, instanceIdent, cap);
            JCTree.JCStatement thenPart = treeMaker.Block(0, com.sun.tools.javac.util.List.of(treeMaker.Return(getterCall)));

            elsePart = treeMaker.If(condition, thenPart, elsePart);
        }

        JCTree.JCBlock body = treeMaker.Block(0, com.sun.tools.javac.util.List.of(elsePart));

        return treeMaker.MethodDef(
                treeMaker.Modifiers(mods),
                names.fromString("get"),
                typeExprFrom(treeMaker, names, "java.lang.Object"),
                com.sun.tools.javac.util.List.nil(),
                com.sun.tools.javac.util.List.of(instanceParam, fieldParam),
                com.sun.tools.javac.util.List.nil(),
                body,
                null
        );
    }

    private static JCTree.JCStatement makeListSetStatement(
            TreeMaker treeMaker,
            Names names,
            JCTree.JCExpression instanceIdent,
            JCTree.JCExpression valueIdent,
            String cap,
            String castTypeName
    ) {
        JCTree.JCExpression getterCall1 = makeGetterCall(treeMaker, names, instanceIdent, cap);
        JCTree.JCExpression notNull = treeMaker.Binary(
                JCTree.Tag.NE,
                getterCall1,
                treeMaker.Literal(TypeTag.BOT, null)
        );

        JCTree.JCExpression getterCall2 = makeGetterCall(treeMaker, names, instanceIdent, cap);
        JCTree.JCStatement clearStmt = treeMaker.Exec(treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(getterCall2, names.fromString("clear")),
                com.sun.tools.javac.util.List.nil()
        ));

        JCTree.JCExpression getterCall3 = makeGetterCall(treeMaker, names, instanceIdent, cap);
        JCTree.JCExpression castValue = treeMaker.TypeCast(typeExprFrom(treeMaker, names, castTypeName), valueIdent);
        JCTree.JCStatement addAllStmt = treeMaker.Exec(treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(getterCall3, names.fromString("addAll")),
                com.sun.tools.javac.util.List.of(castValue)
        ));

        JCTree.JCStatement thenBlock = treeMaker.Block(0, com.sun.tools.javac.util.List.of(clearStmt, addAllStmt));
        JCTree.JCStatement elseBlock = treeMaker.Block(0, com.sun.tools.javac.util.List.of(
                treeMaker.Exec(makeSetterCall(treeMaker, names, instanceIdent, valueIdent, cap, castTypeName))
        ));

        return treeMaker.If(notNull, thenBlock, elseBlock);
    }

    private static JCTree.JCExpression makeStringEquals(TreeMaker treeMaker, Names names, String fieldName, JCTree.JCExpression fieldIdent) {
        JCTree.JCExpression lit = treeMaker.Literal(fieldName);
        return treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(lit, names.fromString("equals")),
                com.sun.tools.javac.util.List.of(fieldIdent)
        );
    }

    private static JCTree.JCStatement makeUnknownFieldThrow(TreeMaker treeMaker, Names names, JCTree.JCExpression fieldIdent) {
        JCTree.JCExpression message = treeMaker.Binary(
                JCTree.Tag.PLUS,
                treeMaker.Literal("Unknown field: "),
                fieldIdent
        );
        JCTree.JCExpression exType = typeExprFrom(treeMaker, names, "java.lang.IllegalArgumentException");
        JCTree.JCExpression newEx = treeMaker.NewClass(
                null,
                com.sun.tools.javac.util.List.nil(),
                exType,
                com.sun.tools.javac.util.List.of(message),
                null
        );
        return treeMaker.Throw(newEx);
    }

    private static String castTypeNameForSet(Types typeUtils, VariableElement field) {
        return switch (field.asType().getKind()) {
            case BOOLEAN -> "java.lang.Boolean";
            case BYTE -> "java.lang.Byte";
            case SHORT -> "java.lang.Short";
            case INT -> "java.lang.Integer";
            case LONG -> "java.lang.Long";
            case CHAR -> "java.lang.Character";
            case FLOAT -> "java.lang.Float";
            case DOUBLE -> "java.lang.Double";
            default -> typeUtils.erasure(field.asType()).toString();
        };
    }

    private static JCTree.JCExpression makeSetterCall(
            TreeMaker treeMaker,
            Names names,
            JCTree.JCExpression instanceIdent,
            JCTree.JCExpression valueIdent,
            String cap,
            String castTypeName
    ) {
        String setterName = "set" + cap;
        JCTree.JCExpression castValue = treeMaker.TypeCast(typeExprFrom(treeMaker, names, castTypeName), valueIdent);
        return treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(instanceIdent, names.fromString(setterName)),
                com.sun.tools.javac.util.List.of(castValue)
        );
    }

    private static JCTree.JCExpression makeGetterCall(TreeMaker treeMaker, Names names, JCTree.JCExpression instanceIdent, String cap) {
        String getterName = "get" + cap;
        return treeMaker.Apply(
                com.sun.tools.javac.util.List.nil(),
                treeMaker.Select(instanceIdent, names.fromString(getterName)),
                com.sun.tools.javac.util.List.nil()
        );
    }

    private static JCTree.JCExpression typeExprFrom(TreeMaker treeMaker, Names names, String typeName) {
        String[] parts = typeName.split("\\.");
        JCTree.JCExpression expr = treeMaker.Ident(names.fromString(parts[0]));
        for (int i = 1; i < parts.length; i++) {
            expr = treeMaker.Select(expr, names.fromString(parts[i]));
        }
        return expr;
    }

    private static boolean isList(Elements elementUtils, Types typeUtils, VariableElement field) {
        TypeMirror listType = elementUtils.getTypeElement("java.util.List").asType();
        return typeUtils.isAssignable(typeUtils.erasure(field.asType()), typeUtils.erasure(listType));
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
