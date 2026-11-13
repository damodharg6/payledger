package com.payledger.ledger;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link EntryDirection} to the {@code CHAR(1)} column {@code ledger_entries.direction}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Converter
public class EntryDirectionConverter implements AttributeConverter<EntryDirection, String> {

    @Override
    public String convertToDatabaseColumn(EntryDirection direction) {
        return direction == null ? null : direction.code();
    }

    @Override
    public EntryDirection convertToEntityAttribute(String code) {
        return code == null ? null : EntryDirection.fromCode(code);
    }
}
