/*
 * Copyright 2017 Databricks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.databricks.sparkdl.python

import scala.collection.mutable

import org.apache.spark.sql.{Column, SQLContext}
import org.apache.spark.sql.functions.udf
import org.apache.spark.ml.linalg.{DenseVector, Vector}
import org.apache.spark.sql.types.{ArrayType, DoubleType, FloatType}

/**
 * This file contains some interfaces with the JVM runtime: theses functions create UDFs and
 * transform UDFs using java code.
 */
// TODO: this pattern is repeated over and over again, it should be standard somewhere.
class PythonInterface {
private var _sqlCtx: SQLContext = null

  def sqlContext(ctx: SQLContext): this.type = {
    _sqlCtx = ctx
    this
  }

  /**
   * Takes a column, which may contain either arrays of floats or doubles, and returns the
   * content, cast as MLlib's vectors.
   */
  def listToVectorFunction(col: Column): Column = {
    Conversions.convertToVector(col)
  }
}

object Conversions {
  private def floatArrayToVector(x: Array[Float]): Vector = {
    new DenseVector(fromFloatArray(x))
  }

  // This code is intrinsically bad for performance: all the elements are not stored in a contiguous
  // array, but they are wrapped in java.lang.Float objects (subject to garbage collection, etc.)
  // TODO: find a way to directly an array of float from Spark, without going through a scala
  // sequence first.
  private def floatSeqToVector(x: Seq[Float]): Vector = x match {
    case wa: mutable.WrappedArray[Float] =>
      floatArrayToVector(wa.toArray) // This might look good, but boxing is still happening!!!
    case _ => throw new Exception(
      s"Expected a WrappedArray, got class of instance ${x.getClass}: $x")
  }

  private def doubleArrayToVector(x: Array[Double]): Vector = { new DenseVector(x) }

  private def fromFloatArray(x: Array[Float]): Array[Double] = {
    val res = Array.ofDim[Double](x.length)
    var idx = 0
    while (idx < res.length) {
      res(idx) = x(idx)
      idx += 1
    }
    res
  }

  def convertToVector(col: Column): Column = {
    col.expr.dataType match {
      case ArrayType(FloatType, false) =>
        val f = udf(floatSeqToVector _)
        f(col)
      case ArrayType(DoubleType, false) =>
        val f = udf(doubleArrayToVector _)
        f(col)
      case dt =>
        throw new Exception(s"convertToVector: cannot deal with type $dt")
    }
  }
}
