package com.payledger.common;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Base class for entities whose id is generated in Java (a UUID) instead of by the database.
 * <p>
 * Spring Data normally decides "new or existing?" by checking whether the id is null. With a
 * pre-assigned UUID the id is never null, so {@code save()} would run a needless SELECT and a
 * merge. Implementing {@link Persistable} with a transient flag makes {@code save()} call
 * {@code persist()} directly for new objects.
 *
 * @param <ID> the id type
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@MappedSuperclass
public abstract class AssignedIdEntity<ID> implements Persistable<ID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
