package com.fitness.app.directory;

import com.fitness.app.directory.model.DocumentType;
import com.fitness.app.directory.model.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long>
{
    /** The document is the natural key (uq_person_document): it decides create vs reuse. */
    Optional<Person> findByDocumentTypeAndDocumentNumber(DocumentType documentType, String documentNumber);
}
