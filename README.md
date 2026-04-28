# Course Registration System (CRS)

This workspace contains a runnable Java CRS prototype with:

- role-based access control for students, faculty, and admins
- course CRUD
- student CRUD
- prerequisite checking
- add/drop enrollment flow
- academic progress and enrollment reports
- a MySQL schema/dump file for the relational design

## Run

Compile and run from the workspace folder:

```bash
javac Example.java CRSApplication.java
java Example
```

`Example` is kept as the launch wrapper, so you can also run `java CRSApplication` after compilation.

## Notes

- The Java code uses an in-memory store to keep the demo self-contained and easy to run.
- The database file in `crs-mysql-dump.sql` shows the schema and sample seed data needed for a MySQL-backed version.
- The persistence and GUI layers can be swapped in later with JDBC/Hibernate and JavaFX or Swing without changing the core rules in the service layer.
