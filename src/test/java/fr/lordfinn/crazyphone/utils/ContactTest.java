package fr.lordfinn.crazyphone.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContactTest {

    @Test
    void twoArgConstructor_leavesSkinAndUuidNull() {
        Contact contact = new Contact("111", "Alice");
        assertEquals("111", contact.getNumber());
        assertEquals("Alice", contact.getName());
        assertNull(contact.getUuid());
        assertNull(contact.getSkin());
    }

    @Test
    void fourArgConstructor_setsAllFields() {
        Contact contact = new Contact("111", "Alice", "skin-data", "uuid-1234");
        assertEquals("111", contact.getNumber());
        assertEquals("Alice", contact.getName());
        assertEquals("skin-data", contact.getSkin());
        assertEquals("uuid-1234", contact.getUuid());
    }

    @Test
    void settersUpdateFields() {
        Contact contact = new Contact("111", "Alice");
        contact.setUuid("uuid-5678");
        contact.setSkin("new-skin");
        contact.setPassword("secret");

        assertEquals("uuid-5678", contact.getUuid());
        assertEquals("new-skin", contact.getSkin());
        assertEquals("secret", contact.getPassword());
    }

    @Test
    void toString_includesNumberAndName() {
        Contact contact = new Contact("111", "Alice");
        String str = contact.toString();
        assertTrue(str.contains("111"));
        assertTrue(str.contains("Alice"));
    }
}
