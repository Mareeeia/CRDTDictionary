import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.anEmptyMap;

@ExtendWith(MockitoExtension.class)
public class LWWHashTableTest {

    @Mock
    private LWWDictionary<String, String> lwwDictionary;

    private static final LocalDateTime EXAMPLE_TIME = LocalDateTime.of(2007, 7, 7, 7, 7, 7);

    /**
     * Tests unique entries are added successfully.
     */
    @Test
    public void testAddUniqueEntries() {
        var lwwHashTable = makeEmptyLWWTable();
        lwwHashTable.add("first", "firstValue", EXAMPLE_TIME);
        lwwHashTable.add("second", "secondValue", EXAMPLE_TIME);

        assertThat(lwwHashTable.getElements(), aMapWithSize(2));
        assertThat(lwwHashTable.getElements(), hasEntry("first", "firstValue"));
        assertThat(lwwHashTable.getElements(), hasEntry("second", "secondValue"));
    }

    /**
     * Tests adding a key when it already exists can rewrite the key if
     * the timestamp is later.
     */
    @Test
    public void testAddOverExistingEntries() {
        var lwwHashTable = makeLWWTable();
        lwwHashTable.add("second", "secondValueUpdated", EXAMPLE_TIME.plusSeconds(5));
        lwwHashTable.add("second", "secondValueUpdatedAgain", EXAMPLE_TIME.plusSeconds(3));
        lwwHashTable.add("third", "thirdValue", EXAMPLE_TIME);

        assertThat(lwwHashTable.getElements(), aMapWithSize(3));
        assertThat(lwwHashTable.getElements(), hasEntry("first", "firstValue"));
        assertThat(lwwHashTable.getElements(), hasEntry("second", "secondValueUpdated"));
        assertThat(lwwHashTable.getElements(), hasEntry("third", "thirdValue"));
    }

    /**
     * Tests attempting to look up removed entries returns null.
     */
    @Test
    public void testLookupWithRemovedEntries() {
        var lwwHashTable = makeLWWTable();
        lwwHashTable.remove("second", EXAMPLE_TIME.plusSeconds(1));

        assertThat(lwwHashTable.getElements(), aMapWithSize(1));
        assertThat(lwwHashTable.lookup("first"), equalTo("firstValue"));
        assertThat(lwwHashTable.lookup("second"), equalTo(null));
    }

    /**
     * Tests updating values at a later time.
     */
    @Test
    public void testUpdateExistingValues() {
        var lwwHashTable = makeLWWTable();
        lwwHashTable.update("first", "firstValueUpdated", EXAMPLE_TIME.plusHours(1));
        lwwHashTable.update("second", "secondValueUpdated", EXAMPLE_TIME.plusHours(2));

        assertThat(lwwHashTable.getElements(), aMapWithSize(2));
        assertThat(lwwHashTable.getElements(), hasEntry("first", "firstValueUpdated"));
        assertThat(lwwHashTable.getElements(), hasEntry("second", "secondValueUpdated"));
    }

    /**
     * Tests attempting to update a nonexistent entry results in no changes to return values.
     */
    @Test
    public void testUpdateEmpty() {
        var lwwHashTable = makeEmptyLWWTable();
        lwwHashTable.update("first", "firstValueUpdated", EXAMPLE_TIME.plusHours(1));

        assertThat(lwwHashTable.getElements(), anEmptyMap());
    }

    /**
     * Tests attempting to update removed entries results in no changes to return values.
     */
    @Test
    public void testUpdateRemovedValues() {
        var lwwHashTable = makeLWWTable();
        lwwHashTable.remove("first", EXAMPLE_TIME.plusHours(1));
        lwwHashTable.remove("second", EXAMPLE_TIME.plusHours(1));
        lwwHashTable.update("second", "secondValue", EXAMPLE_TIME.plusHours(2));

        assertThat(lwwHashTable.getElements(), anEmptyMap());
        assertThat(lwwHashTable.getElements(), not(hasEntry("second", "secondValue")));
    }

    /**
     * Tests values can be removed if the removal timestamp is later than the existing entry timestamp.
     */
    @Test
    public void testRemoveExistingValues() {
        var lwwHashTable = makeLWWTable();
        lwwHashTable.add("first", "firstValue", EXAMPLE_TIME);
        lwwHashTable.add("second", "secondValue", EXAMPLE_TIME);
        lwwHashTable.add("third", "thirdValue", EXAMPLE_TIME);
        lwwHashTable.remove("second", EXAMPLE_TIME.plusMinutes(1));

        assertThat(lwwHashTable.getElements(), aMapWithSize(2));
        assertThat(lwwHashTable.getElements(), hasEntry("first", "firstValue"));
        assertThat(lwwHashTable.getElements(), hasEntry("third", "thirdValue"));
        assertThat(lwwHashTable.getElements(), not(hasEntry("second", "secondValue")));
    }

    /**
     * Tests attempting to remove a nonexistent entry results in no changes to return values.
     */
    @Test
    public void testRemoveFromEmptyTable() {
        var lwwHashTable = makeEmptyLWWTable();
        lwwHashTable.remove("second", EXAMPLE_TIME.plusMinutes(1));
        lwwHashTable.remove("second", EXAMPLE_TIME.plusMinutes(2));

        assertThat(lwwHashTable.getElements(), anEmptyMap());
        assertThat(lwwHashTable.getElements(), not(hasEntry("second", "secondValue")));
    }

    /**
     * Tests attempting to remove an entry before adding it does not prevent the entry from being added.
     */
    @Test
    public void testRemoveBeforeAdds() {
        var lwwHashTable = makeEmptyLWWTable();
        lwwHashTable.add("key", "value", EXAMPLE_TIME.plusMinutes(1));
        lwwHashTable.remove("key", EXAMPLE_TIME);

        assertThat(lwwHashTable.getElements(), aMapWithSize(1));
        assertThat(lwwHashTable.getElements(), hasEntry("key", "value"));
    }

    /**
     * Tests simultaneously adding and removing an entry is biased in favour of removal.
     */
    @Test
    public void testRemoveWithBias() {
        var lwwHashTable = makeEmptyLWWTable();
        lwwHashTable.add("key", "value", EXAMPLE_TIME);
        lwwHashTable.remove("key", EXAMPLE_TIME);

        assertThat(lwwHashTable.getElements(), anEmptyMap());
    }

    /**
     * Tests re-adding a removed element.
     */
    @Test
    public void testReAddAnElement() {
        var lwwHashTable = makeEmptyLWWTable();
        lwwHashTable.add("key", "value", EXAMPLE_TIME);
        lwwHashTable.remove("key", EXAMPLE_TIME);
        lwwHashTable.add("key", "re-added", EXAMPLE_TIME.plusMinutes(1));

        assertThat(lwwHashTable.getElements(), aMapWithSize(1));
        assertThat(lwwHashTable.getElements(), hasEntry("key", "re-added"));
    }

    /**
     * Tests simultaneously adding and updating an entry is biased in favour of update.
     */
    @Test
    public void testUpdateWithBias() {
        var lwwHashTable = makeEmptyLWWTable();
        lwwHashTable.add("key", "value", EXAMPLE_TIME);
        lwwHashTable.update("key", "updated", EXAMPLE_TIME);

        assertThat(lwwHashTable.getElements(), aMapWithSize(1));
        assertThat(lwwHashTable.getElements(), hasEntry("key", "updated"));
    }

    /**
     * Tests attempting to merge with a non-LWWHashTable object throws an exception.
     */
    @Test
    public void testWrongClassThrowsException() {
        try {
            var lwwHashTable = makeEmptyLWWTable();

            lwwHashTable.merge(this.lwwDictionary);
        } catch(RuntimeException e) {
            assertThat(e.getMessage(), equalTo("Can only apply merge to other LWWHashTable objects"));
        }
    }

    /**
     * Tests merging two LWWHashTables with no removals merges depending on timestamp.
     */
    @Test
    public void testMergeWithNoRemoves() {
        var firstTable = makeEmptyLWWTable();
        var secondTable = makeEmptyLWWTable();
        firstTable.add("first", "firstValue", EXAMPLE_TIME);
        firstTable.add("third", "nothing", EXAMPLE_TIME);
        secondTable.add("second", "secondValue", EXAMPLE_TIME);
        secondTable.add("third", "thirdValue", EXAMPLE_TIME.plusMinutes(1));

        firstTable.merge(secondTable);
        assertThat(firstTable.getElements(), aMapWithSize(3));
        assertThat(firstTable.getElements(), hasEntry("first", "firstValue"));
        assertThat(firstTable.getElements(), hasEntry("second", "secondValue"));
        assertThat(firstTable.getElements(), hasEntry("third", "thirdValue"));
    }

    /**
     * Tests merging two LWWHashTables merges depending on timestamp,
     * returning only the latest non-removed entries in the final table.
     */
    @Test
    public void testMergeWithRemoves() {
        var firstTable = makeEmptyLWWTable();
        var secondTable = makeEmptyLWWTable();
        firstTable.add("first", "firstValue", EXAMPLE_TIME);
        firstTable.add("third", "nothing", EXAMPLE_TIME);
        secondTable.add("second", "secondValue", EXAMPLE_TIME);
        secondTable.remove("third", EXAMPLE_TIME.plusMinutes(2));
        firstTable.remove("third", EXAMPLE_TIME.minusMinutes(1));

        firstTable.merge(secondTable);
        assertThat(firstTable.getElements(), aMapWithSize(2));
        assertThat(firstTable.getElements(), hasEntry("first", "firstValue"));
        assertThat(firstTable.getElements(), hasEntry("second", "secondValue"));
        assertThat(firstTable.getElements(), not(hasEntry("third", "thirdValue")));
    }

    private LWWHashTable<String, String> makeEmptyLWWTable() {
        return new LWWHashTable<>();
    }

    private LWWHashTable<String, String> makeLWWTable() {
        return new LWWHashTable<>(Map.of("first", "firstValue", "second", "secondValue"),
                EXAMPLE_TIME);
    }
}
