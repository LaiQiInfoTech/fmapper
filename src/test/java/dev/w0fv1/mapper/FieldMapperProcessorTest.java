package dev.w0fv1.mapper;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class FieldMapperProcessorTest {

    @Test
    void doesNotGenerateEntityFieldMapperSources() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.UserEntity",
                """
                        package dev.w0fv1.test;
                        import jakarta.persistence.Entity;

                        @Entity
                        public class UserEntity {
                            private Long id;
                            private String name;

                            public Long getId() { return id; }
                            public void setId(Long id) { this.id = id; }
                            public String getName() { return name; }
                            public void setName(String name) { this.name = name; }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withOptions("-Afmapper.inline=true")
                .withProcessors(new FieldMapperProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertTrue(compilation.generatedSourceFiles().isEmpty());
        assertTrue(compilation.generatedSourceFile("dev.w0fv1.test.UserEntityEntityFieldMapper").isEmpty());
    }

    @Test
    void injectsInlineFieldMapperForListField() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.TeamEntity",
                """
                        package dev.w0fv1.test;
                        import jakarta.persistence.Entity;
                        import java.util.List;

                        @Entity
                        public class TeamEntity {
                            private List<String> members;

                            public List<String> getMembers() { return members; }
                            public void setMembers(List<String> members) { this.members = members; }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withOptions("-Afmapper.inline=true")
                .withProcessors(new FieldMapperProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertTrue(compilation.generatedSourceFiles().isEmpty());
        assertTrue(compilation.generatedFiles().stream().anyMatch(f -> f.getName().endsWith("TeamEntity$FieldMapper.class")));
    }

    @Test
    void failsWhenSetterIsMissing() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.BrokenEntity",
                """
                        package dev.w0fv1.test;
                        import jakarta.persistence.Entity;

                        @Entity
                        public class BrokenEntity {
                            private String name;
                            public String getName() { return name; }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withOptions("-Afmapper.inline=true")
                .withProcessors(new FieldMapperProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("setName");
    }

    @Test
    void failsWhenGetterIsMissingForListField() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.BrokenListEntity",
                """
                        package dev.w0fv1.test;
                        import jakarta.persistence.Entity;
                        import java.util.List;

                        @Entity
                        public class BrokenListEntity {
                            private List<String> members;
                            public void setMembers(List<String> members) { this.members = members; }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withOptions("-Afmapper.inline=true")
                .withProcessors(new FieldMapperProcessor())
                .compile(source);

        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("getMembers");
    }

    @Test
    void doesNotGenerateMapperForClassWithoutEntityAnnotation() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.PlainPojo",
                """
                        package dev.w0fv1.test;
                        public class PlainPojo {
                            private String name;
                            public String getName() { return name; }
                            public void setName(String name) { this.name = name; }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withProcessors(new FieldMapperProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertTrue(compilation.generatedSourceFiles().isEmpty());
        assertTrue(compilation.generatedFiles().stream().noneMatch(f -> f.getName().endsWith("PlainPojo$FieldMapper.class")));
    }

    @Test
    void doesNotGenerateMapperForEntityWithoutFields() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.EmptyEntity",
                """
                        package dev.w0fv1.test;
                        import jakarta.persistence.Entity;

                        @Entity
                        public class EmptyEntity {
                            public String hello() { return "hi"; }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withProcessors(new FieldMapperProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertTrue(compilation.generatedSourceFiles().isEmpty());
        assertTrue(compilation.generatedFiles().stream().noneMatch(f -> f.getName().endsWith("EmptyEntity$FieldMapper.class")));
    }

    @Test
    void supportsRawListFieldGeneration() {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.RawListEntity",
                """
                        package dev.w0fv1.test;
                        import jakarta.persistence.Entity;
                        import java.util.List;

                        @Entity
                        public class RawListEntity {
                            @SuppressWarnings("rawtypes")
                            private List members;

                            @SuppressWarnings("rawtypes")
                            public List getMembers() { return members; }
                            @SuppressWarnings("rawtypes")
                            public void setMembers(List members) { this.members = members; }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withOptions("-Afmapper.inline=true")
                .withProcessors(new FieldMapperProcessor())
                .compile(source);

        assertThat(compilation).succeeded();
        assertTrue(compilation.generatedSourceFiles().isEmpty());
        assertTrue(compilation.generatedFiles().stream().anyMatch(f -> f.getName().endsWith("RawListEntity$FieldMapper.class")));
    }

    @Test
    void injectsInlineFieldMapperWhenEnabled() throws IOException {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.UserEntity",
                """
                        package dev.w0fv1.test;
                        import jakarta.persistence.Entity;

                        @Entity
                        public class UserEntity {
                            private Long id;
                            private String name;

                            public Long getId() { return id; }
                            public void setId(Long id) { this.id = id; }
                            public String getName() { return name; }
                            public void setName(String name) { this.name = name; }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withOptions("-Afmapper.inline=true")
                .withProcessors(new FieldMapperProcessor())
                .compile(source);

        assertThat(compilation).succeeded();

        JavaFileObject fieldMapperClass = compilation.generatedFiles().stream()
                .filter(f -> f.getName().endsWith("UserEntity$FieldMapper.class"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing injected class file: UserEntity$FieldMapper.class"));

        byte[] classBytes;
        try (InputStream in = fieldMapperClass.openInputStream()) {
            classBytes = in.readAllBytes();
        }

        AtomicBoolean hasSet = new AtomicBoolean(false);
        AtomicBoolean hasGet = new AtomicBoolean(false);
        AtomicBoolean hasSetId = new AtomicBoolean(false);
        AtomicBoolean hasGetId = new AtomicBoolean(false);
        AtomicBoolean setIsStatic = new AtomicBoolean(false);
        AtomicBoolean getIsStatic = new AtomicBoolean(false);
        AtomicBoolean setIdIsStatic = new AtomicBoolean(false);
        AtomicBoolean getIdIsStatic = new AtomicBoolean(false);

        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.equals("set")) {
                    hasSet.set(true);
                    setIsStatic.set((access & Opcodes.ACC_STATIC) != 0);
                }
                if (name.equals("get")) {
                    hasGet.set(true);
                    getIsStatic.set((access & Opcodes.ACC_STATIC) != 0);
                }
                if (name.equals("setId")) {
                    hasSetId.set(true);
                    setIdIsStatic.set((access & Opcodes.ACC_STATIC) != 0);
                }
                if (name.equals("getId")) {
                    hasGetId.set(true);
                    getIdIsStatic.set((access & Opcodes.ACC_STATIC) != 0);
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);

        assertTrue(hasSet.get());
        assertTrue(hasGet.get());
        assertTrue(hasSetId.get());
        assertTrue(hasGetId.get());
        assertTrue(setIsStatic.get());
        assertTrue(getIsStatic.get());
        assertTrue(setIdIsStatic.get());
        assertTrue(getIdIsStatic.get());
    }

    @Test
    void inlineFieldMapperIsUsableFromOtherCompilationUnit() {
        JavaFileObject entity = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.UserEntity",
                """
                        package dev.w0fv1.test;
                        import jakarta.persistence.Entity;

                        @Entity
                        public class UserEntity {
                            private Long id;
                            public Long getId() { return id; }
                            public void setId(Long id) { this.id = id; }
                        }
                        """
        );

        JavaFileObject caller = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.UserEntityCaller",
                """
                        package dev.w0fv1.test;
                        public class UserEntityCaller {
                            public static void run() {
                                UserEntity e = new UserEntity();
                                UserEntity.FieldMapper.setId(e, 1L);
                                Long id = UserEntity.FieldMapper.getId(e);
                            }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withOptions("-Afmapper.inline=true")
                .withProcessors(new FieldMapperProcessor())
                .compile(entity, caller);

        assertThat(compilation).succeeded();
    }

    @Test
    void inlineFieldMapperSupportsMultipleFieldTypesAndWorksAtRuntime() throws Exception {
        JavaFileObject entity = JavaFileObjects.forSourceString(
                "dev.w0fv1.test.ComplexEntity",
                """
                        package dev.w0fv1.test;

                        import jakarta.persistence.Entity;
                        import jakarta.persistence.Id;

                        import java.util.ArrayList;
                        import java.util.List;

                        @Entity
                        public class ComplexEntity {
                            @Id
                            private Long id;
                            private String name;
                            private int age;
                            private final List<String> members = new ArrayList<>();

                            public Long getId() { return id; }
                            public void setId(Long id) { this.id = id; }

                            public String getName() { return name; }
                            public void setName(String name) { this.name = name; }

                            public int getAge() { return age; }
                            public void setAge(int age) { this.age = age; }

                            public List<String> getMembers() { return members; }
                            public void setMembers(List<String> members) {
                                this.members.clear();
                                if (members != null) {
                                    this.members.addAll(members);
                                }
                            }
                        }
                        """
        );

        Compilation compilation = Compiler.javac()
                .withOptions("-Afmapper.inline=true")
                .withProcessors(new FieldMapperProcessor())
                .compile(entity);

        assertThat(compilation).succeeded();

        ClassLoader loader = newClassLoaderFromCompilation(compilation);
        Class<?> entityClass = loader.loadClass("dev.w0fv1.test.ComplexEntity");
        Object e = entityClass.getConstructor().newInstance();

        Class<?> fieldMapperClass = loader.loadClass("dev.w0fv1.test.ComplexEntity$FieldMapper");

        Method set = fieldMapperClass.getMethod("set", entityClass, String.class, Object.class);
        Method get = fieldMapperClass.getMethod("get", entityClass, String.class);
        assertTrue(Modifier.isStatic(set.getModifiers()));
        assertTrue(Modifier.isStatic(get.getModifiers()));

        set.invoke(null, e, "id", 1L);
        set.invoke(null, e, "name", "Alice");
        set.invoke(null, e, "age", 18);
        set.invoke(null, e, "members", new ArrayList<>(List.of("a", "b")));

        assertEquals(1L, get.invoke(null, e, "id"));
        assertEquals("Alice", get.invoke(null, e, "name"));
        assertEquals(18, get.invoke(null, e, "age"));
        assertEquals(List.of("a", "b"), get.invoke(null, e, "members"));

        Method setId = fieldMapperClass.getMethod("setId", entityClass, Long.class);
        Method getId = fieldMapperClass.getMethod("getId", entityClass);
        Method setName = fieldMapperClass.getMethod("setName", entityClass, String.class);
        Method getName = fieldMapperClass.getMethod("getName", entityClass);
        Method setAge = fieldMapperClass.getMethod("setAge", entityClass, int.class);
        Method getAge = fieldMapperClass.getMethod("getAge", entityClass);
        Method setMembers = fieldMapperClass.getMethod("setMembers", entityClass, List.class);
        Method getMembers = fieldMapperClass.getMethod("getMembers", entityClass);

        assertTrue(Modifier.isStatic(setId.getModifiers()));
        assertTrue(Modifier.isStatic(getId.getModifiers()));
        assertTrue(Modifier.isStatic(setName.getModifiers()));
        assertTrue(Modifier.isStatic(getName.getModifiers()));
        assertTrue(Modifier.isStatic(setAge.getModifiers()));
        assertTrue(Modifier.isStatic(getAge.getModifiers()));
        assertTrue(Modifier.isStatic(setMembers.getModifiers()));
        assertTrue(Modifier.isStatic(getMembers.getModifiers()));

        setId.invoke(null, e, 2L);
        assertEquals(2L, getId.invoke(null, e));

        setName.invoke(null, e, "Bob");
        assertEquals("Bob", getName.invoke(null, e));

        setAge.invoke(null, e, 21);
        assertEquals(21, getAge.invoke(null, e));

        setMembers.invoke(null, e, new ArrayList<>(List.of("x")));
        assertEquals(List.of("x"), getMembers.invoke(null, e));

        // Verify list "clear + addAll" behavior (no accumulation).
        setMembers.invoke(null, e, new ArrayList<>(List.of("y", "z")));
        assertEquals(List.of("y", "z"), getMembers.invoke(null, e));

        InvocationTargetException ex = assertThrows(
                InvocationTargetException.class,
                () -> get.invoke(null, e, "no_such_field")
        );
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
    }

    private static ClassLoader newClassLoaderFromCompilation(Compilation compilation) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();

        for (JavaFileObject f : compilation.generatedFiles()) {
            String binaryName = toBinaryNameOrNull(f);
            if (binaryName == null) {
                continue;
            }

            try (InputStream in = f.openInputStream()) {
                classes.put(binaryName, in.readAllBytes());
            }
        }

        return new ClassLoader(FieldMapperProcessorTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
    }

    private static String toBinaryNameOrNull(JavaFileObject f) {
        String name = null;
        try {
            name = f.toUri().getPath();
        } catch (RuntimeException ignored) {
            // Fallback to getName() below.
        }
        if (name == null || name.isEmpty()) {
            name = f.getName();
        }

        name = name.replace('\\', '/');

        int idx = name.indexOf("CLASS_OUTPUT/");
        if (idx >= 0) {
            name = name.substring(idx + "CLASS_OUTPUT/".length());
        }
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (!name.endsWith(".class")) {
            return null;
        }

        name = name.substring(0, name.length() - ".class".length());
        return name.replace('/', '.');
    }
}
