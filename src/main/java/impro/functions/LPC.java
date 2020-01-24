package impro.functions;

import impro.data.KeyedDataPoint;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Time;
import java.util.Arrays;
import java.util.Iterator;

public class LPC implements WindowFunction<KeyedDataPoint<Double>, KeyedDataPoint<Double>, Tuple, TimeWindow> {
	protected static final double TWO_PI = 2 * Math.PI;

    @Override
    public void apply(Tuple arg0, TimeWindow window, Iterable<KeyedDataPoint<Double>> input, Collector<KeyedDataPoint<Double>> out) {
    	/*
    	We need a way to recreate the windows later. We will do this by assigning a window-specific
    	key to each element in the current window. window.getEnd() provides one such unique number.
    	How else can we do this?
    	 */
		this.window = window;
		this.out=out;
        String winKey = String.valueOf(window.getEnd());//input.iterator().next().getKey();
		double preEmphasisCoeff=0.97;
        //count window length
		int length = 0;
		Iterator countIter = input.iterator();
		for ( ; countIter.hasNext() ; ++length ) countIter.next();

        // get the sum of the elements in the window
        KeyedDataPoint<Double> newElem;
		Iterator inputIterator = input.iterator();

		//TODO maybe sort?
		//save window in array seq
		double[] seq = new double[length]; //sometimes windows are smaller than that but it's ok
		for (int index = 0; inputIterator.hasNext(); index++) {
			KeyedDataPoint<Double> in = (KeyedDataPoint<Double>) inputIterator.next();
			seq[index] = in.getValue();
		}

		//PREEMPHASIS AND HAMMING
        for (int index = 0;index<seq.length;index++) {
			seq[index] = seq[index]*preEmphasisCoeff;
			//apply hamming window
        	double factor = 0.54f - 0.46f * (float) Math.cos(TWO_PI * index / (windowSize - 1));
        	seq[index] = seq[index]*factor;
        }

        //DURBIN
		double[] a = new double[numCoeff];//a = lpc coefficients
		double[] new_a = new double [numCoeff]; //new arrays guaranteed filled with 0

		//System.out.println(Arrays.toString(seq));
		double[] r = autocorr(seq, numCoeff);
		System.out.println("r = "+Arrays.toString(r)+"\n\n\n");
		double E = r[0];
		r = Arrays.copyOfRange(r,1,r.length+1); //remove first element

		// CALCULATE a, we dont use new_a

		for (int i=0;i<numCoeff;i++){
			//(1) a new set of reflexion coefficients k(i) are calculated
			double ki = 0.0d;
			for (int j=0;j<i;j++){
				ki = ki + ( a[j] * r[i-j] );
			}
			System.out.println("("+r[i] + "-"+ki+")/"+E);
			ki = (double)((double)r[i] - (double)ki) / (double)E; //was r(i) + ki
			System.out.println("ki="+ki);
			//(2) the prediction energy is updated
			// Enew = (1-ki^2) * Eold
			E = ( 1.0d - (ki*ki) ) * E;
			//(3) new filter coefficients are computed
			a[i] = ki; //was minus

			for (int j=0;j<i;j++){
				a[j]= a[j]- ki * a[i-j];
			}
		}

		//CALCULATE G^2 = residual energy resulting from the coefficients...
		double G2 = r[0];
		for (int k=0; k<numCoeff;k++){
			G2 = a[k]*r[k] + G2;
		}

		//CALCULATE residual
		double[] residual = new double[seq.length]; // e = residual in Octave
		for(int n=0;n<seq.length;n++){
			residual[n] = 0;
			for (int k=0; k<numCoeff;k++){
				if ( (n-k) > 0 )
					residual[n] -= a[k]*seq[n-k]; //was +=
			}

			residual[n] = seq[n] + residual[n];
		}
		//System.out.println(Arrays.toString(seq));
		//System.out.println(a[0]);
		//System.out.println(Arrays.toString(a));
		//System.out.println(Arrays.toString(residual));
		//System.out.println(Arrays.toString(new double[]{G2}));


		//OUTPUT TO STREAM
		long timeStamp = input.iterator().next().getTimeStampMs();
		output("hamming", timeStamp, seq);
		output("a",timeStamp,a);
		output("residual",timeStamp,residual);
		output("G2",timeStamp,new double[]{G2}); //anonymous array


    }
	private TimeWindow window;
	private Collector<KeyedDataPoint<Double>> out;
    private int windowSize;
    private int numCoeff; //number of coefficients (p in Octave)
    public LPC(int windowSize, int numCoeff){
    	this.windowSize = windowSize;
		this.numCoeff = numCoeff;

	}
	private void output(String key,long timeStamp,double[] a){
		for (int i=0;i<a.length;i++){
			KeyedDataPoint<Double> newElem = new KeyedDataPoint<Double>(key,timeStamp, a[i]);
			out.collect(newElem);
		}
	}
	/**
	 * @param seq    x in Octave
	 * @param cutOff p in Octave
	 * @return
	 */
	public double[] autocorr(double[] seq, int cutOff) {

		double[] r = new double[numCoeff]; //new arrays guaranteed filled with 0
		for (int i = 0; i < cutOff; i++) {
			for (int j = 0; j < seq.length - i; j++)
				r[i] = (seq[j] * seq[j + i]) + r[i];
		}
		return r;
	}

}
