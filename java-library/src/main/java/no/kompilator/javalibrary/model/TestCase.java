package no.kompilator.javalibrary.model;

/**
 * Descriptor of a single test case within a suite.
 *
 * <p>Part of the shared model layer ({@code no.kompilator.javalibrary.model}) which
 * has no Spring dependency and can be used in any context — plain Java, Spring, or otherwise.
 *
 * <p>Returned as part of a {@link TestSuite} from
 * {@link no.kompilator.javalibrary.service.TestFrameworkService#getAllSuites()}.
 * Mutable fields with a no-arg constructor allow Jackson to deserialise this class
 * without extra configuration.
 */
public class TestCase {

    private String name;
    private String description;

    /** No-arg constructor required by Jackson for deserialisation. */
    public TestCase() {}

    /**
     * @param name        display name of the test case
     * @param description optional description of what the test verifies
     */
    public TestCase(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /** @return the display name of this test case */
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /** @return the optional description of this test case */
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
