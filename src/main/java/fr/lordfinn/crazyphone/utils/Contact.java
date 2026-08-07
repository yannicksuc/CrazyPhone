package fr.lordfinn.crazyphone.utils;

public class Contact {
    String number;
    public String getNumber() {
        return number;
    }

    String name;
    public String getName() {
        return name;
    }

    String uuid;
    public String getUuid() {
        return uuid;
    }

    String skin;

    public String getSkin() {
        return skin;
    }

    public Contact(String number, String name) {
        this.number = number;
        this.name = name;
    }

    public Contact(String number, String name, String skin, String uuid) {
        this(number, name);
        this.skin = skin;
        this.uuid = uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setSkin(String skin) {
        this.skin = skin;
    }

    @Override
    public String toString() {
        return "Contact{" +
            "number='" + number + '\'' +
            ", name='" + name + '\'' +
            ", uuid='" + uuid + '\'' +
            ", skin='" + skin + '\'' +
            '}';
    }
}
