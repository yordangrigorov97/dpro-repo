package impro.examples;

import impro.connectors.sinks.InfluxDBSink;
import impro.connectors.sources.AudioDataSourceFunction;
import impro.data.KeyedDataPoint;
import impro.functions.Durbin_old;
import impro.functions.LPC;
import impro.util.AssignKeyFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.CountTrigger;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.util.concurrent.TimeUnit;

/**
 * This example reads the audio data from a wave file and apply some processing to the samples of data
 * when reading the audio data, the sampling frequency is obtained from the .wav file
 * the timestamps are assigned according to the period=1/sampling_frequency
 * period = (1.0/samplingRate) * 10e6;  // period in miliseconds
 * so every point of the data has a timestamp every period miliseconds
 * since we need a date to create the ts, it is selected to start: 04.11.2019 <-- adjust if necessary
 * <p>
 * Run with:
 * --input ./src/java/resources/LPCin/short_curious.wav
 * the ouput is saved in the same directory in two .csv files:
 * tmp_wav.csv
 * tmp_energy.csv
 * these two files can be plotted with the R script:
 */
public class StreamingLPCJob_old {
    public static void main(String[] args) throws Exception {
        //Parameters
        String wavFile = "./src/main/resources/LPCin/curious.wav";
        int p = 20; // number of lpc coefficients
        long w_frame = 250000;  // 0.025 seconds length of window frame
        //=> 400 samples per window at 16000 sample rate
        long w_period = 50000;  // 0.005 seconds window period
        //=> 80 samples per window at 16000 sample rate

        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        // Read the audio data from a wave file and assign fictional time
        DataStream<KeyedDataPoint<Double>> audioDataStream = env.addSource(new AudioDataSourceFunction(wavFile))
                .map(new AssignKeyFunction("pressure")).setParallelism(1);

        // write wav file in CSV format
        audioDataStream.writeAsText("./src/main/resources/LPCin/curious.csv", FileSystem.WriteMode.OVERWRITE).setParallelism(1);

        // Apply the Energy function per window
        DataStream<KeyedDataPoint<Double>> audioDataStreamEnergy = audioDataStream
                // the timestamps are from the data
                .assignTimestampsAndWatermarks(new ExtractTimestamp())
                .keyBy("key")
                // slide a window
                //.window of((25000, milliseconds), (10000, milliseconds)
                //.window(SlidingEventTimeWindows.of(Time.seconds(2), Time.seconds(1)))
                .window(SlidingEventTimeWindows.of(Time.of(w_frame, TimeUnit.MILLISECONDS), //size
                        Time.of(w_period, TimeUnit.MILLISECONDS))) //sliding
                //or do it with countwindow
                // calculate energy per window
                .apply(new LPC(400)); //apply destroys windows
        audioDataStreamEnergy
                .addSink(new InfluxDBSink<>("sineWave", "sensors"))
                .name("sensors-sink");

        DataStream<KeyedDataPoint<Double>> durbinStream = audioDataStreamEnergy
                .keyBy("key")
                .window(GlobalWindows.create()).trigger(CountTrigger.of(400)) //sliding
                //or do it with countwindow
                // calculate energy per window
                .apply(new Durbin_old(400, 20)); //apply destroys windows*/

        durbinStream.filter(new FilterByKey("a"))
                .rebalance()
                .writeAsText("./src/main/resources/LPCout/a.csv", FileSystem.WriteMode.OVERWRITE)
                .setParallelism(1);
        durbinStream.filter(new FilterByKey("residual"))
                .rebalance()
                .writeAsText("./src/main/resources/LPCout/residual.csv", FileSystem.WriteMode.OVERWRITE)
                .setParallelism(1);
        durbinStream.filter(new FilterByKey("G2"))
                .rebalance()
                .writeAsText("./src/main/resources/LPCout/G2.csv", FileSystem.WriteMode.OVERWRITE)
                .setParallelism(1);


        //audioDataStreamEnergy.print();
        // print and write in a csv file the input data, just for vizualisation
        audioDataStreamEnergy.writeAsText("./src/main/resources/LPCout/hamming.csv", FileSystem.WriteMode.OVERWRITE).setParallelism(1);

        env.execute("StreamingAudioProcesingJob");
    }

    private static class FilterByKey implements FilterFunction<KeyedDataPoint<Double>> {
        String key;

        FilterByKey(String key) {
            this.key = key;
        }

        @Override
        public boolean filter(KeyedDataPoint<Double> doubleKeyedDataPoint) throws Exception {
            return doubleKeyedDataPoint.getKey().equals(key);
        }
    }


    /**
     * The energy of a window can be calculated as:
     * window_energy = sum(x[i]^2)
     */
    private static class EnergyCalculationFunction implements WindowFunction<KeyedDataPoint<Double>, KeyedDataPoint<Double>, Tuple, TimeWindow> {

        @Override
        public void apply(Tuple arg0, TimeWindow window, Iterable<KeyedDataPoint<Double>> input, Collector<KeyedDataPoint<Double>> out) {
            int count = 0;
            double winEnergy = 0;
            String winKey = input.iterator().next().getKey(); // get the key of this window

            // get the sum^2 of the elements in the window
            for (KeyedDataPoint<Double> in : input) {
                winEnergy = winEnergy + (in.getValue() * in.getValue());
                count++;
            }

            System.out.println("EnergyCalculationFunction: win energy=" + winEnergy + "  count=" + count + "  time=" + window.getStart());

            KeyedDataPoint<Double> windowEnergy = new KeyedDataPoint<Double>(winKey, window.getEnd(), winEnergy);

            out.collect(windowEnergy);

        }
    }


    private static class ExtractTimestamp extends AscendingTimestampExtractor<KeyedDataPoint<Double>> {
        private static final long serialVersionUID = 1L;

        @Override
        public long extractAscendingTimestamp(KeyedDataPoint<Double> element) {
            //return (long)(element.getTimeStampMs() * 0.001);  //???
            return element.getTimeStampMs();
        }
    }


}