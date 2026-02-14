# fmapper sample

This sample demonstrates the inline injection API:

```java
MyEntity.FieldMapper.set(entity, "id", 1L);
Long id = (Long) MyEntity.FieldMapper.get(entity, "id");

MyEntity.FieldMapper.setId(entity, 1L);
Long id2 = MyEntity.FieldMapper.getId(entity);
```

Run:

```bash
.\gradlew.bat -p sample run
```

