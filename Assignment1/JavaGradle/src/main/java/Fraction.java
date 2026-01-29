import java.io.*;
/**
 * Purpose: demonstrate simple Java Fraction class with command line,
 * jdb debugging, and Ant build file.
 *
 * Ser321 Foundations of Distributed Applications
 * see http://pooh.poly.asu.edu/Ser321
 * @author Tim Lindquist Tim.Lindquist@asu.edu
 *         Software Engineering, CIDSE, IAFSE, ASU Poly
 * @version January 2020
 */
public class Fraction {

   private int numerator, denominator;

   public Fraction(){
      numerator = denominator = 0;
   }

   public void print() {
      System.out.print(numerator + "/" + denominator );
   }

   public void setNumerator (int n ){
      numerator = n;
   }

   public void setDenominator (int d) {
      denominator = d;
   }

   public int getDenominator() {
      return denominator;
   }

   public int getNumerator() {
      return numerator;
   }

   public static void main (String args[]) {
      try {
         Fraction frac = new Fraction();

         // Defaults (matches original example)
         int num = 1;
         int denom = 3;

         // Accept 1 or 2 args:
         // args[0] = numerator
         // args[1] = denominator
         if (args.length >= 1) {
            num = Integer.parseInt(args[0]);
         }
         if (args.length >= 2) {
            denom = Integer.parseInt(args[1]);
         }

         // Set values
         frac.setNumerator(num);
         frac.setDenominator(denom);

         // Print it
         System.out.print("The fraction is: ");
         frac.print();
         System.out.println("");

      } catch(Exception e) {
         e.printStackTrace();
      }
   }
}

