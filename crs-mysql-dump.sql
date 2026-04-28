CREATE DATABASE IF NOT EXISTS crs;
USE crs;

DROP TABLE IF EXISTS enrollments;
DROP TABLE IF EXISTS course_prerequisites;
DROP TABLE IF EXISTS courses;
DROP TABLE IF EXISTS students;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    username VARCHAR(50) PRIMARY KEY,
    password_hash VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    linked_student_id VARCHAR(20) NULL
);

CREATE TABLE students (
    student_id VARCHAR(20) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    program VARCHAR(100) NOT NULL,
    year INT NOT NULL,
    contact_information VARCHAR(150) NOT NULL
);

CREATE TABLE courses (
    course_id VARCHAR(20) PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    credit_hours INT NOT NULL,
    department VARCHAR(100) NOT NULL,
    max_enrollment INT NOT NULL
);

CREATE TABLE course_prerequisites (
    course_id VARCHAR(20) NOT NULL,
    prerequisite_course_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (course_id, prerequisite_course_id),
    CONSTRAINT fk_cp_course FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_prereq FOREIGN KEY (prerequisite_course_id) REFERENCES courses(course_id) ON DELETE RESTRICT
);

CREATE TABLE enrollments (
    enrollment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    student_id VARCHAR(20) NOT NULL,
    course_id VARCHAR(20) NOT NULL,
    semester VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    grade VARCHAR(2) NULL,
    enrolled_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_enrollment_student FOREIGN KEY (student_id) REFERENCES students(student_id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollment_course FOREIGN KEY (course_id) REFERENCES courses(course_id) ON DELETE CASCADE
);

INSERT INTO users (username, password_hash, role, display_name, linked_student_id) VALUES
('admin', '240be518fabd5d51...', 'ADMIN', 'System Admin', NULL),
('faculty1', '26a9f8f7d53d2d61...', 'FACULTY', 'Dr. Silva', NULL),
('alice', 'ef92b778bafe771e...', 'STUDENT', 'Alice Fernando', 'S1001');

INSERT INTO students (student_id, name, date_of_birth, program, year, contact_information) VALUES
('S1001', 'Alice Fernando', '2004-03-21', 'BSc Software Engineering', 2, 'alice@example.com'),
('S1002', 'Bob Perera', '2004-09-17', 'BSc Information Technology', 2, 'bob@example.com');

INSERT INTO courses (course_id, title, credit_hours, department, max_enrollment) VALUES
('CS101', 'Programming Fundamentals', 3, 'Computing', 30),
('CS102', 'Object-Oriented Programming', 3, 'Computing', 30),
('MATH101', 'Calculus I', 4, 'Mathematics', 40),
('CS103', 'Data Structures', 3, 'Computing', 30);

INSERT INTO course_prerequisites (course_id, prerequisite_course_id) VALUES
('CS102', 'CS101'),
('CS103', 'CS102');

INSERT INTO enrollments (student_id, course_id, semester, status, grade) VALUES
('S1001', 'CS101', '2025-S2', 'COMPLETED', 'A');
