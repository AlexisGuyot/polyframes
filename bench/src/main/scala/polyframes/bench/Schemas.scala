package polyframes.bench

import shapeless.labelled.FieldType
import shapeless.{::, HNil}

/**
 * The schemas declared by the pipelines of section 5.
 *
 * Every attribute below was observed in the extracted sample, with the
 * domain SparkSQL infers for it when reading the JSONL. Nothing is guessed:
 * the survey that produced this list is `Inspect`, kept in the repository
 * for that reason.
 *
 * The sample has 2231 top-level fields, and its `nutriments` object alone
 * has 353. A pipeline declares a slice of that, which is what makes the
 * width axis of section 5 a realistic one rather than a synthetic ladder.
 */
object Schemas {

  // -------------------------------------------------------------------
  // The schema of the reference pipeline P1.
  //
  // Deliberately small, and deliberately of three shapes at once: atomic
  // attributes, a collection of atomic values, and a nested schema. The
  // last is what the augmentation of step 1 descends into, and what makes
  // the projection of step 5 a change of model rather than a filter.
  // -------------------------------------------------------------------

  type SNutriments =
    FieldType["energy-kcal_100g", Double] ::
    FieldType["fat_100g", Double] ::
    FieldType["carbohydrates_100g", Double] ::
    FieldType["proteins", Double] ::
    FieldType["salt", Double] :: HNil

  type SProduct =
    FieldType["code", String] ::
    FieldType["product_type", String] ::
    FieldType["nutriscore_grade", String] ::
    FieldType["pnns_groups_1", String] ::
    FieldType["completeness", Double] ::
    FieldType["countries_tags", List[String]] ::
    FieldType["nutriments", SNutriments] :: HNil

  // -------------------------------------------------------------------
  // The relational side of P2.
  //
  // A reference table on the Nutri-Score grades, kept in the repository
  // under reference/. It is ours, not an extract of the dump: the dump is
  // documents throughout, and P2 needs something relational to integrate
  // with. Six rows written by hand deceive nobody, where flattening the
  // published category taxonomy would have passed preparation work off as
  // data.
  //
  // Its attribute names share nothing with those of a product, which is
  // not a matter of taste: two schemas that disagree on a plain attribute
  // name cannot be merged, so the join would be rejected.
  // -------------------------------------------------------------------

  type SGrade =
    FieldType["grade_code", String] ::
    FieldType["grade_label", String] ::
    FieldType["grade_rank", Long] :: HNil

  // -------------------------------------------------------------------
  // The width axis of section 5.4.
  //
  // Each schema extends the previous one, so the axis widens a single
  // declaration rather than substituting unrelated ones. Domains are
  // interleaved so that no width is homogeneous in type, which would make
  // the proof trees unrepresentatively uniform.
  // -------------------------------------------------------------------

  type W5 =
    FieldType["_id", String] ::
    FieldType["complete", Long] ::
    FieldType["completeness", Double] ::
    FieldType["_keywords", List[String]] ::
    FieldType["additives_tags", List[String]] :: HNil

  type W10 =
    FieldType["allergens_from_user", String] ::
    FieldType["created_t", Long] ::
    FieldType["added_countries_tags", List[String]] ::
    FieldType["brands_tags", List[String]] ::
    FieldType["code", String] :: W5

  type W20 =
    FieldType["last_modified_t", Long] ::
    FieldType["allergens_tags", List[String]] ::
    FieldType["categories_tags", List[String]] ::
    FieldType["creator", String] ::
    FieldType["last_updated_t", Long] ::
    FieldType["categories_properties_tags", List[String]] ::
    FieldType["cities_tags", List[String]] ::
    FieldType["id", String] ::
    FieldType["nutrition_score_beverage", Long] ::
    FieldType["checkers_tags", List[String]] :: W10

  type W40 =
    FieldType["countries_hierarchy", List[String]] ::
    FieldType["interface_version_created", String] ::
    FieldType["popularity_key", Long] ::
    FieldType["codes_tags", List[String]] ::
    FieldType["ingredients_tags", List[String]] ::
    FieldType["lang", String] ::
    FieldType["rev", Long] ::
    FieldType["correctors_tags", List[String]] ::
    FieldType["labels_tags", List[String]] ::
    FieldType["lc", String] ::
    FieldType["countries_tags", List[String]] ::
    FieldType["manufacturing_places_tags", List[String]] ::
    FieldType["nova_group_debug", String] ::
    FieldType["data_quality_bugs_tags", List[String]] ::
    FieldType["minerals_tags", List[String]] ::
    FieldType["nutriscore_grade", String] ::
    FieldType["data_quality_errors_tags", List[String]] ::
    FieldType["origins_tags", List[String]] ::
    FieldType["nutriscore_version", String] ::
    FieldType["data_quality_info_tags", List[String]] :: W20

  type W80 =
    FieldType["packaging_tags", List[String]] ::
    FieldType["nutrition_grade_fr", String] ::
    FieldType["data_quality_tags", List[String]] ::
    FieldType["purchase_places_tags", List[String]] ::
    FieldType["nutrition_grades", String] ::
    FieldType["data_quality_warnings_tags", List[String]] ::
    FieldType["states_tags", List[String]] ::
    FieldType["nutrition_score_debug", String] ::
    FieldType["editors_tags", List[String]] ::
    FieldType["stores_tags", List[String]] ::
    FieldType["pnns_groups_1", String] ::
    FieldType["entry_dates_tags", List[String]] ::
    FieldType["traces_tags", List[String]] ::
    FieldType["pnns_groups_2", String] ::
    FieldType["food_groups_tags", List[String]] ::
    FieldType["vitamins_tags", List[String]] ::
    FieldType["product_type", String] ::
    FieldType["informers_tags", List[String]] ::
    FieldType["teams_tags", List[String]] ::
    FieldType["languages_hierarchy", List[String]] ::
    FieldType["popularity_tags", List[String]] ::
    FieldType["languages_tags", List[String]] ::
    FieldType["ecoscore_tags", List[String]] ::
    FieldType["last_edit_dates_tags", List[String]] ::
    FieldType["emb_codes_tags", List[String]] ::
    FieldType["main_countries_tags", List[String]] ::
    FieldType["misc_tags", List[String]] ::
    FieldType["nova_groups_tags", List[String]] ::
    FieldType["nutrient_levels_tags", List[String]] ::
    FieldType["nutriscore_2021_tags", List[String]] ::
    FieldType["nutriscore_2023_tags", List[String]] ::
    FieldType["nutriscore_tags", List[String]] ::
    FieldType["nutrition_grades_tags", List[String]] ::
    FieldType["packaging_materials_tags", List[String]] ::
    FieldType["packaging_recycling_tags", List[String]] ::
    FieldType["packaging_shapes_tags", List[String]] ::
    FieldType["photographers_tags", List[String]] ::
    FieldType["pnns_groups_1_tags", List[String]] ::
    FieldType["pnns_groups_2_tags", List[String]] ::
    FieldType["removed_countries_tags", List[String]] :: W40
}
