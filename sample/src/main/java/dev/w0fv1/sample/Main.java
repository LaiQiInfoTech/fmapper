package dev.w0fv1.sample;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        MyEntity entity = new MyEntity();

        // Dynamic API (string field name).
        MyEntity.FieldMapper.set(entity, "id", 1L);
        MyEntity.FieldMapper.set(entity, "name", "Hello");
        MyEntity.FieldMapper.set(entity, "age", 18);
        MyEntity.FieldMapper.set(entity, "tags", List.of("a", "b"));

        Long id1 = (Long) MyEntity.FieldMapper.get(entity, "id");
        String name1 = (String) MyEntity.FieldMapper.get(entity, "name");
        int age1 = (Integer) MyEntity.FieldMapper.get(entity, "age");

        // Typed API (generated per field).
        MyEntity.FieldMapper.setId(entity, 2L);
        Long id2 = MyEntity.FieldMapper.getId(entity);

        MyEntity.FieldMapper.setTags(entity, List.of("x"));
        List<String> tags = MyEntity.FieldMapper.getTags(entity);

        System.out.println("dynamic id=" + id1 + ", name=" + name1 + ", age=" + age1);
        System.out.println("typed id=" + id2 + ", tags=" + tags);
    }
}

