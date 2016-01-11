package main.scala.iot

import kafka.serializer.DefaultDecoder
import org.apache.avro.generic.{GenericRecord}
import org.apache.spark.SparkConf
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka._


object DeviceIoTStreamApp {

  /**
    * Filter the DStream as determined by the predicate function
    * @param rec GenericRecord, the device message
    * @param temp filter by this temperature
    * @return boolean if the condition is satisfied
    */
    def filterByTemperature (rec: GenericRecord, temp : Int) : Boolean = {
        val tmp = rec.get("temp").asInstanceOf[Int]
        println("filterByTemperature: Processing record: " + rec.toString)
        println("Temperature = " + tmp)
        return (tmp >= temp)
    }

    def main(args: Array[String]) : Unit = {

        if (args.length < 2) {
            println("Need 2 arguments: <kafka-broker:port> <topic>")
            System.exit(1)
        }

        println("Spark Streaming..here.. I come!")

        val Array(brokers, topics) = args

        // Create context with 2 second batch interval
        val sparkConf = new SparkConf().setAppName("DeviceIoTStreamApp").set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        val ssc = new StreamingContext(sparkConf, Seconds(2))
        ssc.checkpoint("devices")

        var topicMap = Map[String, Int]()
        topicMap += (topics -> 1)

        var consumerConfig = Map[String, String]()
        consumerConfig += ("group.id" -> "group")
        consumerConfig += ("zookeeper.connect" -> "localhost:2181")
        consumerConfig += ("auto.offset.reset" -> "smallest")
        consumerConfig += ("metadata.broker.list" -> brokers)
        consumerConfig += ("schema.registry.url" -> "http://localhost:8081")


        val deviceMessages = KafkaUtils.createStream[Array[Byte], SchemaAndData, DefaultDecoder, AvroDecoder](ssc, consumerConfig, topicMap, StorageLevel.MEMORY_ONLY)

        // Use DStream.map() to serialize all GenericRecords and then use DStream.filter() to extract only records whose device temperature
        // is greater than or equal to 35, as evaluated in the filter predicate.
        val devicesRecords = deviceMessages.map(elem => {
            elem._2.deserialize().asInstanceOf[GenericRecord]
                    }).filter(e => filterByTemperature(e, 35))

        devicesRecords.print()

        // Start the computation
        ssc.start()
        ssc.awaitTermination()
    }
}