/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.execution.datasources.hbase

import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql.execution.SparkSqlSerializer
import org.apache.spark.sql.types._
import org.apache.spark.unsafe.types.UTF8String

object Utils {

  /**
   * Parses the hbase field to it's corresponding
   * scala type which can then be put into a Spark GenericRow
   * which is then automatically converted by Spark.
   */
  def hbaseFieldToScalaType(
      f: Field,
      src: HBaseType,
      offset: Int,
      length: Int): Any = {
    if (f.sedes.isDefined) {
      // If we already have sedes defined , use it.
      f.sedes.get.deserialize(src, offset, length)
    } else if (f.exeSchema.isDefined) {
      // println("avro schema is defined to do deserialization")
      // If we have avro schema defined, use it to get record, and then covert them to catalyst data type
      val m = AvroSedes.deserialize(src, f.exeSchema.get)
      // println(m)
      val n = f.avroToCatalyst.map(_(m))
      n.get
    } else  {
      // Fall back to atomic type
      f.dt match {
        case BooleanType => toBoolean(src, offset)
        case ByteType => src(offset)
        case DoubleType => Bytes.toDouble(src, offset)
        case FloatType => Bytes.toFloat(src, offset)
        case IntegerType => Bytes.toInt(src, offset)
        case LongType => Bytes.toLong(src, offset)
        case ShortType => Bytes.toShort(src, offset)
        case StringType => toUTF8String(src, offset, length)
        case BinaryType =>
          val newArray = new Array[Byte](length)
          System.arraycopy(src, offset, newArray, 0, length)
          newArray
        case _ => SparkSqlSerializer.deserialize[Any](src) //TODO
      }
    }
  }

  // convert input to data type
  def toBytes(input: Any, field: Field): Array[Byte] = {
    if (field.sedes.isDefined) {
      field.sedes.get.serialize(input)
    } else if (field.schema.isDefined) {
      // Here we assume the top level type is structType
      val record = field.catalystToAvro(input)
      AvroSedes.serialize(record, field.schema.get)
    } else {
      input match {
        case data: Boolean => Bytes.toBytes(data)
        case data: Byte => Array(data)
        case data: Array[Byte] => data
        case data: Double => Bytes.toBytes(data)
        case data: Float => Bytes.toBytes(data)
        case data: Int => Bytes.toBytes(data)
        case data: Long => Bytes.toBytes(data)
        case data: Short => Bytes.toBytes(data)
        case data: UTF8String => data.getBytes
        case data: String => Bytes.toBytes(data)
          //Bytes.toBytes(input.asInstanceOf[String])//input.asInstanceOf[UTF8String].getBytes
        case _ => throw new Exception(s"unsupported data type ${field.dt}") //TODO
      }
    }
  }

  def toBoolean(input: HBaseType, offset: Int): Boolean = {
    input(offset) != 0
  }

  def toUTF8String(input: HBaseType, offset: Int, length: Int): UTF8String = {
    UTF8String.fromBytes(input.slice(offset, offset + length))
  }
}



