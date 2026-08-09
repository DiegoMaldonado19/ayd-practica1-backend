package com.fitness.app.directory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 *
 * gender stays a String and documentType does not: the DDL closes document_type
 * with a CHECK and leaves gender deliberately open.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Person
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long         personId;

    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    private String       documentNumber;
    private String       firstName;
    private String       lastName;
    private String       gender;
    private LocalDate    birthDate;
    private String       email;
    private String       phone;
    private String       address;
    private Instant      createdAt;

    public String getFullName()
    {
        return firstName + " " + lastName;
    }
}
