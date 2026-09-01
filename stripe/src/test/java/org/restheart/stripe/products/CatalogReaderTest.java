package org.restheart.stripe.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bson.BsonDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Resolving a requested id to the document that describes what is being bought.
 *
 * <p>A variant is flattened onto its product rather than read field by field, so what these tests
 * pin down is the inheritance: a variant declares what differs and inherits the rest.
 */
class CatalogReaderTest {

    private static final BsonDocument TEE = BsonDocument.parse("""
            {
              "_id": "tee-classic",
              "type": "physical",
              "name": "Classic T-shirt",
              "description": "Heavyweight cotton",
              "unit_amount": 2500,
              "tax_code": "txcd_30011000",
              "variants": [
                { "id": "yellow-l", "unit_amount": 2900,
                  "metadata": { "colour": "yellow", "size": "L" } },
                { "id": "blue-m", "purchasable": false }
              ]
            }
            """);

    private static final BsonDocument MUG = BsonDocument.parse("""
            { "_id": "mug", "type": "physical", "name": "Enamel mug", "unit_amount": 1450 }
            """);

    @Test
    @DisplayName("a product without variants resolves to itself")
    void plainProduct() throws Exception {
        assertEquals(MUG, CatalogReader.resolve(MUG, "mug"));
    }

    @Test
    @DisplayName("a variant inherits everything it does not declare")
    void variantInherits() throws Exception {
        var resolved = CatalogReader.resolve(TEE, "tee-classic/yellow-l");

        // Declared by the variant.
        assertEquals(2900, resolved.getNumber("unit_amount").intValue());
        assertEquals("yellow", resolved.getDocument("metadata").getString("colour").getValue());

        // Inherited from the product — the reason a shop writes variants instead of whole products.
        assertEquals("Classic T-shirt", resolved.getString("name").getValue());
        assertEquals("Heavyweight cotton", resolved.getString("description").getValue());
        assertEquals("physical", resolved.getString("type").getValue());
        assertEquals("txcd_30011000", resolved.getString("tax_code").getValue());
    }

    @Test
    @DisplayName("the resolved id is the composite, so an order records what was bought")
    void idIsComposite() throws Exception {
        var resolved = CatalogReader.resolve(TEE, "tee-classic/yellow-l");
        assertEquals("tee-classic/yellow-l", resolved.getString("_id").getValue());
    }

    @Test
    @DisplayName("the variants array does not travel onto the line")
    void variantsAreStripped() throws Exception {
        // An order line carrying every sibling of what was bought would be a copy of the catalog
        // inside every order.
        assertFalse(CatalogReader.resolve(TEE, "tee-classic/yellow-l").containsKey("variants"));
    }

    @Test
    @DisplayName("a variant can override a boolean, not only a number")
    void variantOverridesPurchasable() throws Exception {
        // Flattening means every field overrides, which is the point: nothing has to be listed.
        var resolved = CatalogReader.resolve(TEE, "tee-classic/blue-m");
        assertFalse(resolved.getBoolean("purchasable").getValue());
        assertEquals(2500, resolved.getNumber("unit_amount").intValue());
    }

    @Test
    @DisplayName("a product with variants cannot be bought on its own")
    void productWithVariantsIsNotAnItem() {
        // Otherwise the buyer gets the family's base price for an unspecified colour and size.
        var e = assertThrows(CatalogReader.CatalogValidationException.class,
                () -> CatalogReader.resolve(TEE, "tee-classic"));
        assertTrue(e.getMessage().contains("tee-classic/<variant>"), e.getMessage());
    }

    @Test
    @DisplayName("an unknown variant names the product and the variant")
    void unknownVariant() {
        var e = assertThrows(CatalogReader.CatalogValidationException.class,
                () -> CatalogReader.resolve(TEE, "tee-classic/green-xxl"));
        assertTrue(e.getMessage().contains("green-xxl"), e.getMessage());
    }

    @Test
    @DisplayName("asking for a variant of a product that has none says so")
    void noVariantsAtAll() {
        var e = assertThrows(CatalogReader.CatalogValidationException.class,
                () -> CatalogReader.resolve(MUG, "mug/large"));
        assertTrue(e.getMessage().contains("no variants"), e.getMessage());
    }

    @Test
    @DisplayName("the document id is everything before the first slash")
    void documentId() {
        assertEquals("tee-classic", CatalogReader.documentIdOf("tee-classic/yellow-l"));
        assertEquals("mug", CatalogReader.documentIdOf("mug"));
        // A slash in the variant part belongs to the variant, not to a third level.
        assertEquals("tee", CatalogReader.documentIdOf("tee/a/b"));
    }
}
