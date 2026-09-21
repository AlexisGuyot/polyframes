package polyframes.types

/**
 * Data models, as types.
 *
 * Encodes rules 10 to 14 of the article. `Model` is the common supertype;
 * `Relation` and `Document` are its two subtypes. Unlike the formalisation
 * of earlier work (Pridwen), none of them is parameterised by a schema: the link
 * between the model level and the schema level is carried by the `Valid` auxiliary type.
 *
 * These types are never inhabited by data. They appear only as the first
 * parameter of `Data` and of `Valid`.
 */
sealed trait Model

/** Rules 11 and 13. Schemas whose attributes all have atomic domains. */
trait Relation extends Model

/** Rules 12 and 14. Schemas allowing nesting and multi-valued domains. */
trait Document extends Model
