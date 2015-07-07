/**
 * This file is licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015 Lukas Hennig
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.blablubbabc.glicko;

import java.util.List;

/**
 * Implements Glicko-2 rating.
 * Based on http://www.glicko.net/glicko/glicko2.pdf
 */
public class Glicko2 {

	/**
	 * The initial rating.
	 */
	public static final double DEFAULT_RATING = 1500;
	/**
	 * The initial rating deviation.
	 */
	public static final double DEFAULT_DEVIATION = 350;
	/**
	 * The initial rating volatility.
	 */
	public static final double DEFAULT_VOLATILITY = 0.06D;

	/**
	 * Constraints the change in volatility over time.
	 * 
	 * <p>
	 * Reasonable choices might be between 0.3 and 1.2, but it depends on the actual application.<br>
	 * Smaller values prevent the volatility measures from changing by large amounts, which in turn prevent enormous
	 * changes in ratings based on very improbable results. If extremely improbable collections of game outcomes are
	 * expected, then it should be set to a small value.
	 * </p>
	 */
	public static final double VOLATILITY_CONSTRAINT = 0.5D;

	private static final double GLICKO_SCALE = 173.7178D;
	private static final double CONVERGENCE_TOLERANCE = 0.000001D;

	public static class Rating {

		private double rating;
		private double deviation;
		private double volatility;

		private double ratingScaled;
		private double deviationScaled;

		/**
		 * Creates a new {@link Rating} with the default initial values.
		 */
		public Rating() {
			this.update(DEFAULT_RATING, DEFAULT_DEVIATION, DEFAULT_VOLATILITY);
		}

		/**
		 * Creates a new {@link Rating} using the given values.
		 * 
		 * @param rating
		 * @param deviation
		 * @param volatility
		 */
		public Rating(double rating, double deviation, double volatility) {
			// TODO validate values?
			this.update(rating, deviation, volatility);
		}

		public final void update(double rating, double deviation, double volatility) {
			this.rating = rating;
			this.deviation = deviation;
			this.volatility = volatility;

			this.ratingScaled = scaleRating(rating);
			this.deviationScaled = scaleDeviation(deviation);
		}

		public double getRating() {
			return rating;
		}

		public double getDeviation() {
			return deviation;
		}

		public double getVolatility() {
			return volatility;
		}
	}

	private static double scaleRating(double rating) {
		return (rating - DEFAULT_RATING) / GLICKO_SCALE;
	}

	private static double unscaleRating(double rating) {
		return (rating * GLICKO_SCALE) + DEFAULT_RATING;
	}

	private static double scaleDeviation(double deviation) {
		return deviation / GLICKO_SCALE;
	}

	private static double unscaleDeviation(double deviation) {
		return deviation * GLICKO_SCALE;
	}

	/**
	 * Updates the rating after a rating period.
	 * 
	 * <p>
	 * Works best when the number of games in a rating period is moderate to large, say an average of at least 10-15
	 * games per player in a rating period.
	 * </p>
	 * 
	 * @param rating
	 *            the current rating
	 * @param opponents
	 *            the ratings of the opponents in the individual matches
	 * @param scores
	 *            a win is 1.0, a draw is 0.5 a loss is 0.0
	 */
	public static void updateRating(Rating rating, List<Rating> opponents, List<Double> scores) {
		if (rating == null || opponents == null || opponents.isEmpty() || opponents.size() != scores.size()) {
			// invalid arguments
			return;
		}
		// also asserting: opponents doesn't contain null and scores are valid (0.0 - 1.0)

		// step 1: use defaults if no rating is given (happens during construction of Rating)
		// step 2: scale all ratings (happens during construction of Rating)

		// step 3: estimated variance of the team's/player's rating
		double v = 0.0D;
		// step 4: estimated improvement in rating comparing the current rating to the performance rating
		double delta = 0.0D;
		for (int i = 0; i < opponents.size(); i++) {
			Rating opponent = opponents.get(i);
			double score = scores.get(i).doubleValue();

			double g = g(opponent.deviationScaled);
			double E = E(rating, opponent);

			v += (g * g * E * (1.0D - E));
			delta += (g * (score - E));
		}
		v = 1.0D / v;

		// step 5.1
		double a = Math.log(rating.volatility * rating.volatility);
		// step 5.2
		double A = a;
		double delta2 = (delta * delta);
		double deviation2 = (rating.deviationScaled * rating.deviationScaled);
		double B;
		if (delta2 > (deviation2 + v)) {
			B = Math.log(delta2 - (deviation2 + v));
		} else {
			int k = 1;
			while (f(a - (k * VOLATILITY_CONSTRAINT), rating.deviationScaled, v, delta, a) < 0.0D) {
				k++;
			}
			B = a - (k * VOLATILITY_CONSTRAINT);
		}

		// step 5.3
		double fA = f(A, rating.deviationScaled, v, delta, a);
		double fB = f(B, rating.deviationScaled, v, delta, a);

		// step 5.4
		while (Math.abs(B - A) > CONVERGENCE_TOLERANCE) {
			double C = A + (A - B) * fA / (fB - fA);
			double fC = f(C, rating.deviationScaled, v, delta, a);
			if ((fC * fB) < 0.0D) {
				A = B;
				fA = fB;
			} else {
				fA = (fA / 2.0D);
			}
			B = C;
			fB = fC;
		}
		double newVolatility = Math.exp(A / 2.0D);

		// step 6
		double newDeviation = Math.sqrt(deviation2 + (newVolatility * newVolatility));

		// TODO:
		/* Note that if a player does not compete during the rating period, then only Step 6 applies.
		 * In this case, the player's rating and volatility parameters remain the same,
		 * but the RD increase according to the newDeviation from Step 6.
		 */

		// step 7
		newDeviation = 1.0D / Math.sqrt((1.0D / (newDeviation * newDeviation)) + (1.0D / v));
		double newRating = 0.0D;
		for (int i = 0; i < opponents.size(); i++) {
			Rating opponent = opponents.get(i);
			double score = scores.get(i).doubleValue();
			newRating += (g(opponent.deviationScaled) * (score - E(rating, opponent)));
		}
		newRating *= (newDeviation * newDeviation);
		newRating += rating.ratingScaled;

		// step 8: unscale results
		newDeviation = unscaleDeviation(newDeviation);
		newRating = unscaleRating(newRating);

		// update rating:
		rating.update(newRating, newDeviation, newVolatility);
	}

	private static double g(double deviation) {
		return 1.0D / (Math.sqrt(1.0D + 3.0D * (deviation * deviation) / (Math.PI * Math.PI)));
	}

	private static double E(Rating rating1, Rating rating2) {
		assert rating1 != null && rating2 != null;
		return 1.0D / (1.0D + Math.exp(-g(rating2.deviation) * (rating1.rating - rating2.rating)));
	}

	private static double f(double x, double deviation, double v, double delta, double a) {
		double eX = Math.pow(Math.E, x);
		double temp = ((deviation * deviation) + v + eX);
		return ((eX * ((delta * delta) - temp)) / (2.0D * temp * temp)) - ((x - a) / (VOLATILITY_CONSTRAINT * VOLATILITY_CONSTRAINT));
	}
}
