package com.stripe.brushfire

import com.twitter.algebird._

case class ChiSquaredEvaluator[V, L, W <% Double](implicit weightMonoid: Monoid[W])
    extends Evaluator[V, Map[L, W]] {
  def evaluate(split: Split[V, Map[L, W]]) = {
    val rows = split.predicates.map { _._2 }.filter { _.size > 0 }
    if (rows.size > 1) {
      val n = weightMonoid.sum(rows.flatMap { _.values })
      val rowTotals = rows.map { row => weightMonoid.sum(row.values) }.toList
      val columnKeys = rows.flatMap { _.keys }.toList
      val columnTotals = columnKeys.map { column => column -> weightMonoid.sum(rows.flatMap { _.get(column) }) }.toMap
      val testStatistic = (for (
        column <- columnKeys;
        (row, index) <- rows.zipWithIndex
      ) yield {
        val observed = row.getOrElse(column, weightMonoid.zero)
        val expected = (columnTotals(column) * rowTotals(index)) / n
        val delta = observed - expected
        ((delta * delta) / expected)
      }).sum
      (split, testStatistic)
    } else
      (EmptySplit[V, Map[L, W]], Double.NegativeInfinity)
  }
}

case class MinWeightEvaluator[V, L, W: Monoid](minWeight: W => Boolean, wrapped: Evaluator[V, Map[L, W]])
    extends Evaluator[V, Map[L, W]] {
  def evaluate(split: Split[V, Map[L, W]]) = {
    val (baseSplit, baseScore) = wrapped.evaluate(split)
    if (baseSplit.predicates.forall {
      case (pred, freq) =>
        val totalWeight = Monoid.sum(freq.values)
        minWeight(totalWeight)
    })
      (baseSplit, baseScore)
    else
      (EmptySplit[V, Map[L, W]], Double.NegativeInfinity)
  }
}

case class EmptySplit[V, P] extends Split[V, P] {
  val predicates = Nil
}

