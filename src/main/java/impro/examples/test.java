/*
This is a java scratchpad to test functions
 */

package impro.examples;

import java.util.Arrays;

public class test {
    public static double[] lpc(double[] r, double E, int p) {
        double[] a = new double[p+1];
        double[] new_a = new double[p+1];

        for (int i = 1; i <=p; i++) {
            //(1) a new set of reflexion coefficients k (i) are calculated
            double ki = 0;

            for (int j = 1; j <=(i - 1); j++)
                ki = ki + (a[j] * r[i - j -1]);
            ki = (r[i - 1] + ki) / E;


            //(2) the prediction energy is updated
            //Enew = (1 - ki ^ 2) * Eold
            E = (1 - (ki * ki)) * E;

            //(3) new filter coefficients are computed
            new_a[i] = -ki;

            for (int j = 1; j <=(i - 1); j++)
                new_a[j] = a[j] - ki * a[i - j];

            for (int j = 1; j <=i; j++)
                a[j] = new_a[j];
        }
        return Arrays.copyOfRange(a,1,a.length);
    }



    public static double[] meche(double[] r, double E, int p) {
        double[] a = new double[p];
        double[] new_a = new double[p];
        for (int i = 0; i <p; i++) {
            //(1) a new set of reflexion coefficients k (i) are calculated
            double ki = 0;

            for (int j = 0; j <=(i - 1); j++){
                ki = ki + (a[j] * r[i - j -1]);
                System.out.println("ki = "+ki+" a[j]="+a[j]+" r[i-j-1]="+r[i-j-1]);
            }

            System.out.println("ki after loop = "+ki);
            ki = (r[i] + ki) / E;
            System.out.println("ki ="+ki);

            //(2) the prediction energy is updated
            //Enew = (1 - ki ^ 2) * Eold
            E = (1 - (ki * ki)) * E;
            System.out.println("E="+E);
            //(3) new filter coefficients are computed
            new_a[i] = -ki;

            for (int j = 0; j <= (i - 1); j++)
                new_a[j] = a[j] - ki * a[i - j]; //?i-j-1?

            // from Lyubomira removed for ()... a[j] = new_a[j]
            //for (int j = 0; j <= i; j++)
            //    a[i] = new_a[i];
            System.out.println("new_a="+Arrays.toString(new_a));
            System.out.println("a="+Arrays.toString(a));
            System.out.println("new loop:\n\n");
        }

        return a;
    }

    public static void main(String[] args) {
        double E=0.3837519229795156;
        //double [] r = new double[] {0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,0.10,0.11,0.12,0.13,0.14,0.15,0.16,0.17,0.18,0.19,0.20};
        //int p=10;
        double [] r = new double[] {-0.1149128001536748,-0.02412794256593207,-0.1359029656195381,0.007147798102311573,
                0.08905884665776866,-0.01962041004528057,0.1106125757517161,-0.1262234218795259,0.02266651956524348,
                -0.06110003387865581,0.05732477294313056,0.009065980599399099,0.002709654449860168,0.04493963367663847,
                -0.0809965954999633,0.01857561346095194,-0.02315164230439053,0.06299451144267711,-0.01871421959710466,
                0.005953675036833393};
        int p=20;
        System.out.println(Arrays.toString(lpc(r, E, p)));

    }
}
