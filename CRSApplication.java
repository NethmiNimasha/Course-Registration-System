import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CRSApplication {
    public static void main(String[] args) {
        new CrsDemo().run();
    }
}

class CrsDemo {
    private final InMemoryCrsStore store = new InMemoryCrsStore();
    private final AuthService authService = new AuthService(store);
    private final CourseService courseService = new CourseService(store);
    private final StudentService studentService = new StudentService(store);
    private final EnrollmentService enrollmentService = new EnrollmentService(store);
    private final ReportingService reportingService = new ReportingService(store);

    void run() {
        seedData();
        UserAccount admin = authService.login("admin", "admin123");
        UserAccount faculty = authService.login("faculty1", "faculty123");
        UserAccount alice = authService.login("alice", "student123");

        System.out.println("Course Registration System Demo");
        System.out.println("--------------------------------");
        System.out.println("Logged in as: " + admin.displayName + " (" + admin.role + ")");

        courseService.createCourse(
                admin,
                new Course("CS103", "Data Structures", 3, "Computing", listOf("CS102"), 30)
        );
        courseService.updateCourse(
                faculty,
                "MATH101",
                "Calculus I",
                4,
                "Mathematics",
                listOf(),
                40
        );

        printSection("Course Catalog");
        courseService.listCourses().forEach(course -> System.out.println(course.describe()));

        printSection("Student Maintenance");
        studentService.updateStudent(
                admin,
                new Student("S1002", "Bob Perera", LocalDate.of(2004, 9, 17), "BSc Software Engineering", 2, "bob@example.com")
        );
        System.out.println("Updated student: " + studentService.findStudent("S1002").describe());

        printSection("Enrollment Flow");
        try {
            enrollmentService.registerCourse(alice, "S1001", "CS102", "2026-S1");
        } catch (CrsException exception) {
            System.out.println("Expected prereq failure: " + exception.getMessage());
        }

        enrollmentService.registerCourse(alice, "S1001", "CS101", "2026-S1");
        enrollmentService.recordGrade(faculty, "S1001", "CS101", "2026-S1", Grade.A);
        enrollmentService.registerCourse(alice, "S1001", "CS102", "2026-S1");
        enrollmentService.registerCourse(alice, "S1001", "MATH101", "2026-S1");

        printSection("Student Schedule");
        reportingService.studentSchedule("S1001", "2026-S1")
                .forEach(line -> System.out.println(line));

        printSection("Enrollment Report");
        reportingService.courseEnrollmentReport("2026-S1")
                .forEach(line -> System.out.println(line));

        printSection("Academic Progress");
        reportingService.academicProgressReport("S1001")
                .forEach(line -> System.out.println(line));

        printSection("Security Check");
        try {
            courseService.deleteCourse(alice, "CS103");
        } catch (CrsException exception) {
            System.out.println("RBAC blocked student action: " + exception.getMessage());
        }
    }

    private void seedData() {
        store.users.put("admin", new UserAccount("admin", PasswordHasher.hash("admin123"), Role.ADMIN, "System Admin", null));
        store.users.put("faculty1", new UserAccount("faculty1", PasswordHasher.hash("faculty123"), Role.FACULTY, "Dr. Silva", null));
        store.users.put("alice", new UserAccount("alice", PasswordHasher.hash("student123"), Role.STUDENT, "Alice Fernando", "S1001"));
        store.users.put("bob", new UserAccount("bob", PasswordHasher.hash("student123"), Role.STUDENT, "Bob Perera", "S1002"));

        store.students.put("S1001", new Student("S1001", "Alice Fernando", LocalDate.of(2004, 3, 21), "BSc Software Engineering", 2, "alice@example.com"));
        store.students.put("S1002", new Student("S1002", "Bob Perera", LocalDate.of(2004, 9, 17), "BSc Information Technology", 2, "bob@example.com"));
        store.courses.put("CS101", new Course("CS101", "Programming Fundamentals", 3, "Computing", listOf(), 2));
        store.courses.put("CS102", new Course("CS102", "Object-Oriented Programming", 3, "Computing", listOf("CS101"), 2));
        store.courses.put("MATH101", new Course("MATH101", "Calculus I", 4, "Mathematics", listOf(), 2));
    }

    private void printSection(String title) {
        System.out.println();
        System.out.println(title);
        System.out.println("=".repeat(title.length()));
    }

    private static List<String> listOf(String... values) {
        List<String> items = new ArrayList<>();
        Collections.addAll(items, values);
        return items;
    }
}

class AuthService {
    private final InMemoryCrsStore store;

    AuthService(InMemoryCrsStore store) {
        this.store = store;
    }

    UserAccount login(String username, String password) {
        UserAccount user = store.users.get(username);
        if (user == null) {
            throw new CrsException("Unknown username: " + username);
        }
        if (!user.passwordHash.equals(PasswordHasher.hash(password))) {
            throw new CrsException("Invalid credentials for user: " + username);
        }
        return user;
    }
}

class CourseService {
    private final InMemoryCrsStore store;

    CourseService(InMemoryCrsStore store) {
        this.store = store;
    }

    Course createCourse(UserAccount actor, Course course) {
        SecurityRules.requireAnyRole(actor, Role.ADMIN, Role.FACULTY);
        if (store.courses.containsKey(course.courseId)) {
            throw new CrsException("Course already exists: " + course.courseId);
        }
        store.courses.put(course.courseId, course);
        return course;
    }

    Course updateCourse(UserAccount actor, String courseId, String title, int creditHours, String department, List<String> prerequisites, int maxEnrollment) {
        SecurityRules.requireAnyRole(actor, Role.ADMIN, Role.FACULTY);
        Course course = getCourse(courseId);
        course.title = title;
        course.creditHours = creditHours;
        course.department = department;
        course.prerequisites = new ArrayList<>(prerequisites);
        course.maxEnrollment = maxEnrollment;
        return course;
    }

    void deleteCourse(UserAccount actor, String courseId) {
        SecurityRules.requireAnyRole(actor, Role.ADMIN, Role.FACULTY);
        if (store.courses.remove(courseId) == null) {
            throw new CrsException("Course not found: " + courseId);
        }
        store.enrollments.removeIf(enrollment -> enrollment.courseId.equals(courseId));
    }

    Course getCourse(String courseId) {
        Course course = store.courses.get(courseId);
        if (course == null) {
            throw new CrsException("Course not found: " + courseId);
        }
        return course;
    }

    List<Course> listCourses() {
        return new ArrayList<>(store.courses.values());
    }
}

class StudentService {
    private final InMemoryCrsStore store;

    StudentService(InMemoryCrsStore store) {
        this.store = store;
    }

    Student createStudent(UserAccount actor, Student student) {
        SecurityRules.requireAnyRole(actor, Role.ADMIN, Role.FACULTY);
        if (store.students.containsKey(student.studentId)) {
            throw new CrsException("Student already exists: " + student.studentId);
        }
        store.students.put(student.studentId, student);
        return student;
    }

    Student updateStudent(UserAccount actor, Student student) {
        SecurityRules.requireAnyRole(actor, Role.ADMIN, Role.FACULTY);
        if (!store.students.containsKey(student.studentId)) {
            throw new CrsException("Student not found: " + student.studentId);
        }
        store.students.put(student.studentId, student);
        return student;
    }

    void deleteStudent(UserAccount actor, String studentId) {
        SecurityRules.requireAnyRole(actor, Role.ADMIN);
        if (store.students.remove(studentId) == null) {
            throw new CrsException("Student not found: " + studentId);
        }
        store.enrollments.removeIf(enrollment -> enrollment.studentId.equals(studentId));
    }

    Student findStudent(String studentId) {
        Student student = store.students.get(studentId);
        if (student == null) {
            throw new CrsException("Student not found: " + studentId);
        }
        return student;
    }
}

class EnrollmentService {
    private final InMemoryCrsStore store;

    EnrollmentService(InMemoryCrsStore store) {
        this.store = store;
    }

    Enrollment registerCourse(UserAccount actor, String studentId, String courseId, String semester) {
        SecurityRules.requireSelfOrHigher(actor, studentId);
        ensureRegistrationOpen();

        Student student = getStudent(studentId);
        Course course = getCourse(courseId);

        if (findEnrollment(studentId, courseId, semester) != null) {
            throw new CrsException("Student is already enrolled in " + courseId + " for " + semester);
        }
        if (countActiveEnrollments(courseId, semester) >= course.maxEnrollment) {
            throw new CrsException("Course is full: " + courseId);
        }
        if (!student.completedCourses.containsAll(course.prerequisites)) {
            throw new CrsException("Prerequisites not satisfied for " + courseId + ": " + course.prerequisites);
        }

        Enrollment enrollment = new Enrollment(studentId, courseId, semester, EnrollmentStatus.ENROLLED, null, LocalDateTime.now());
        store.enrollments.add(enrollment);
        return enrollment;
    }

    void dropCourse(UserAccount actor, String studentId, String courseId, String semester) {
        SecurityRules.requireSelfOrHigher(actor, studentId);
        ensureRegistrationOpen();

        Enrollment enrollment = requireEnrollment(studentId, courseId, semester);
        if (enrollment.status != EnrollmentStatus.ENROLLED) {
            throw new CrsException("Only active enrollments can be dropped");
        }
        enrollment.status = EnrollmentStatus.DROPPED;
    }

    void recordGrade(UserAccount actor, String studentId, String courseId, String semester, Grade grade) {
        SecurityRules.requireAnyRole(actor, Role.ADMIN, Role.FACULTY);
        Enrollment enrollment = requireEnrollment(studentId, courseId, semester);
        enrollment.status = EnrollmentStatus.COMPLETED;
        enrollment.grade = grade;
        Student student = getStudent(studentId);
        student.completedCourses.add(courseId);
    }

    List<Enrollment> findEnrollments(String studentId) {
        List<Enrollment> results = new ArrayList<>();
        for (Enrollment enrollment : store.enrollments) {
            if (enrollment.studentId.equals(studentId)) {
                results.add(enrollment);
            }
        }
        return results;
    }

    List<Enrollment> findEnrollments(String studentId, String semester) {
        List<Enrollment> results = new ArrayList<>();
        for (Enrollment enrollment : store.enrollments) {
            if (enrollment.studentId.equals(studentId) && enrollment.semester.equals(semester)) {
                results.add(enrollment);
            }
        }
        return results;
    }

    int countActiveEnrollments(String courseId, String semester) {
        int count = 0;
        for (Enrollment enrollment : store.enrollments) {
            if (enrollment.courseId.equals(courseId)
                    && enrollment.semester.equals(semester)
                    && enrollment.status == EnrollmentStatus.ENROLLED) {
                count++;
            }
        }
        return count;
    }

    int remainingSeats(String courseId, String semester) {
        Course course = getCourse(courseId);
        return Math.max(0, course.maxEnrollment - countActiveEnrollments(courseId, semester));
    }

    private Enrollment findEnrollment(String studentId, String courseId, String semester) {
        for (Enrollment enrollment : store.enrollments) {
            if (enrollment.studentId.equals(studentId)
                    && enrollment.courseId.equals(courseId)
                    && enrollment.semester.equals(semester)
                    && enrollment.status != EnrollmentStatus.DROPPED) {
                return enrollment;
            }
        }
        return null;
    }

    private Enrollment requireEnrollment(String studentId, String courseId, String semester) {
        Enrollment enrollment = findEnrollment(studentId, courseId, semester);
        if (enrollment == null) {
            throw new CrsException("Enrollment not found for student " + studentId + " in course " + courseId + " for " + semester);
        }
        return enrollment;
    }

    private Course getCourse(String courseId) {
        Course course = store.courses.get(courseId);
        if (course == null) {
            throw new CrsException("Course not found: " + courseId);
        }
        return course;
    }

    private Student getStudent(String studentId) {
        Student student = store.students.get(studentId);
        if (student == null) {
            throw new CrsException("Student not found: " + studentId);
        }
        return student;
    }

    private void ensureRegistrationOpen() {
        if (!store.registrationOpen) {
            throw new CrsException("Registration period is closed");
        }
    }
}

class ReportingService {
    private final InMemoryCrsStore store;

    ReportingService(InMemoryCrsStore store) {
        this.store = store;
    }

    List<String> studentSchedule(String studentId, String semester) {
        Student student = requireStudent(studentId);
        List<String> lines = new ArrayList<>();
        lines.add("Student: " + student.studentId + " | " + student.name + " | Semester: " + semester);
        for (Enrollment enrollment : store.enrollments) {
            if (enrollment.studentId.equals(studentId) && enrollment.semester.equals(semester) && enrollment.status != EnrollmentStatus.DROPPED) {
                Course course = requireCourse(enrollment.courseId);
                lines.add(course.courseId + " - " + course.title + " | status=" + enrollment.status + formatGrade(enrollment.grade));
            }
        }
        if (lines.size() == 1) {
            lines.add("No enrollments found.");
        }
        return lines;
    }

    List<String> courseEnrollmentReport(String semester) {
        List<String> lines = new ArrayList<>();
        lines.add("Semester: " + semester);
        for (Course course : store.courses.values()) {
            int enrolled = 0;
            for (Enrollment enrollment : store.enrollments) {
                if (enrollment.courseId.equals(course.courseId) && enrollment.semester.equals(semester) && enrollment.status == EnrollmentStatus.ENROLLED) {
                    enrolled++;
                }
            }
            lines.add(course.courseId + " | enrolled=" + enrolled + " | vacancies=" + Math.max(0, course.maxEnrollment - enrolled));
        }
        return lines;
    }

    List<String> vacancyReport(String semester) {
        List<String> lines = new ArrayList<>();
        for (Course course : store.courses.values()) {
            int active = 0;
            for (Enrollment enrollment : store.enrollments) {
                if (enrollment.courseId.equals(course.courseId) && enrollment.semester.equals(semester) && enrollment.status == EnrollmentStatus.ENROLLED) {
                    active++;
                }
            }
            lines.add(course.courseId + " -> " + Math.max(0, course.maxEnrollment - active) + " seats open");
        }
        return lines;
    }

    List<String> academicProgressReport(String studentId) {
        Student student = requireStudent(studentId);
        List<String> lines = new ArrayList<>();
        lines.add("Student: " + student.studentId + " | " + student.name);
        lines.add("Program: " + student.program + " | Year: " + student.year);
        lines.add("Completed courses:");

        double qualityPoints = 0.0;
        int totalCredits = 0;
        for (Enrollment enrollment : store.enrollments) {
            if (enrollment.studentId.equals(studentId) && enrollment.status == EnrollmentStatus.COMPLETED && enrollment.grade != null) {
                Course course = requireCourse(enrollment.courseId);
                qualityPoints += enrollment.grade.points() * course.creditHours;
                totalCredits += course.creditHours;
                lines.add("- " + course.courseId + " | " + course.title + " | grade=" + enrollment.grade);
            }
        }
        lines.add("GPA: " + formatGpa(totalCredits == 0 ? 0.0 : qualityPoints / totalCredits));
        return lines;
    }

    private Student requireStudent(String studentId) {
        Student student = store.students.get(studentId);
        if (student == null) {
            throw new CrsException("Student not found: " + studentId);
        }
        return student;
    }

    private Course requireCourse(String courseId) {
        Course course = store.courses.get(courseId);
        if (course == null) {
            throw new CrsException("Course not found: " + courseId);
        }
        return course;
    }

    private String formatGrade(Grade grade) {
        return grade == null ? "" : " | grade=" + grade;
    }

    private String formatGpa(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}

class SecurityRules {
    static void requireAnyRole(UserAccount actor, Role... allowedRoles) {
        if (actor == null) {
            throw new CrsException("Authentication required");
        }
        for (Role role : allowedRoles) {
            if (actor.role == role) {
                return;
            }
        }
        throw new CrsException("Access denied for role " + actor.role + ". Allowed roles: " + EnumSet.of(allowedRoles[0], allowedRoles));
    }

    static void requireSelfOrHigher(UserAccount actor, String studentId) {
        if (actor == null) {
            throw new CrsException("Authentication required");
        }
        if (actor.role == Role.ADMIN || actor.role == Role.FACULTY) {
            return;
        }
        if (actor.role == Role.STUDENT && studentId.equals(actor.linkedStudentId)) {
            return;
        }
        throw new CrsException("Access denied for student action: " + actor.role);
    }
}

class InMemoryCrsStore {
    final Map<String, UserAccount> users = new LinkedHashMap<>();
    final Map<String, Student> students = new LinkedHashMap<>();
    final Map<String, Course> courses = new LinkedHashMap<>();
    final List<Enrollment> enrollments = new ArrayList<>();
    boolean registrationOpen = true;
    LocalDate registrationStart = LocalDate.now();
    LocalDate registrationEnd = LocalDate.now().plusDays(21);
}

class UserAccount {
    final String username;
    final String passwordHash;
    final Role role;
    final String displayName;
    final String linkedStudentId;

    UserAccount(String username, String passwordHash, Role role, String displayName, String linkedStudentId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.displayName = displayName;
        this.linkedStudentId = linkedStudentId;
    }
}

class Student {
    final String studentId;
    String name;
    LocalDate dateOfBirth;
    String program;
    int year;
    String contactInformation;
    final Set<String> completedCourses = new LinkedHashSet<>();

    Student(String studentId, String name, LocalDate dateOfBirth, String program, int year, String contactInformation) {
        this.studentId = studentId;
        this.name = name;
        this.dateOfBirth = dateOfBirth;
        this.program = program;
        this.year = year;
        this.contactInformation = contactInformation;
    }

    String describe() {
        return studentId + " | " + name + " | " + program + " | year=" + year + " | contact=" + contactInformation;
    }
}

class Course {
    final String courseId;
    String title;
    int creditHours;
    String department;
    List<String> prerequisites;
    int maxEnrollment;

    Course(String courseId, String title, int creditHours, String department, List<String> prerequisites, int maxEnrollment) {
        this.courseId = courseId;
        this.title = title;
        this.creditHours = creditHours;
        this.department = department;
        this.prerequisites = new ArrayList<>(prerequisites);
        this.maxEnrollment = maxEnrollment;
    }

    String describe() {
        String prereqText = prerequisites.isEmpty() ? "none" : String.join(", ", prerequisites);
        return courseId + " | " + title + " | credits=" + creditHours + " | dept=" + department + " | prereqs=" + prereqText + " | cap=" + maxEnrollment;
    }
}

class Enrollment {
    final String studentId;
    final String courseId;
    final String semester;
    EnrollmentStatus status;
    Grade grade;
    final LocalDateTime enrolledAt;

    Enrollment(String studentId, String courseId, String semester, EnrollmentStatus status, Grade grade, LocalDateTime enrolledAt) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.semester = semester;
        this.status = status;
        this.grade = grade;
        this.enrolledAt = enrolledAt;
    }
}

enum Role {
    STUDENT,
    FACULTY,
    ADMIN
}

enum EnrollmentStatus {
    ENROLLED,
    DROPPED,
    COMPLETED
}

enum Grade {
    A(4.0),
    B(3.0),
    C(2.0),
    D(1.0),
    F(0.0);

    private final double points;

    Grade(double points) {
        this.points = points;
    }

    double points() {
        return points;
    }
}

class PasswordHasher {
    static String hash(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

class CrsException extends RuntimeException {
    CrsException(String message) {
        super(message);
    }
}
