package no.testframework.javalibrary.api.model;

public class TestCase {
    private String name;
    private String description;

    public TestCase() {}

    public TestCase(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
