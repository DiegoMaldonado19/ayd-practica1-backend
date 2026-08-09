package com.fitness.app.directory;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Identity data of anyone the gym holds a file about: members, employees and
 * walk-in guests. Owned by directory; iam only reads it through PersonService.
 *
 * No @Column anywhere: Boot's default naming strategy already maps
 * documentNumber to document_number.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Person
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long      personId;

    private String    documentType;
    private String    documentNumber;
    private String    firstName;
    private String    lastName;
    private String    gender;
    private LocalDate birthDate;
    private String    email;
    private String    phone;
    private String    address;
    private Instant   createdAt;
}
