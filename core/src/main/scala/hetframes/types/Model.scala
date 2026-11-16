package hetframes.types

/**
 * Data models, as types (rules 10 to 14). None of them is parameterised by
 * a schema: the link between the two levels is carried by `Valid`. They
 * are never inhabited, and only appear as the first parameter of `Data`
 * and of `Valid`.
 */
sealed trait Model

/** Rules 11 and 13: atomic domains only. */
trait Relation extends Model

/** Rules 12 and 14: nested and multi-valued domains allowed. */
trait Document extends Model
