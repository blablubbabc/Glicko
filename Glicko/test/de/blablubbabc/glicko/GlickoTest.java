package de.blablubbabc.glicko;

import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import de.blablubbabc.glicko.Glicko2.Rating;

public class GlickoTest {

	private static final double ERROR_EPSILON = 0.0001D;
	private static final double ERROR_EPSILON2 = 0.01D;
	private static final double ERROR_EPSILON3 = 0.001D;
	private static final double ERROR_EPSILON5 = 0.00001D;

	@Test
	public void example() {
		// example from http://www.glicko.net/glicko/glicko2.pdf
		// results might be slightly different due to rounding errors in the example
		// the error epsilons were picked to adjust for that
		Rating player = new Rating(1500, 200, 0.06D);
		Rating opponent1 = new Rating(1400, 30, 0.06D);
		Rating opponent2 = new Rating(1550, 100, 0.06D);
		Rating opponent3 = new Rating(1700, 300, 0.06D);

		// player
		Assert.assertEquals(0.0D, player.getScaledRating(), ERROR_EPSILON);
		Assert.assertEquals(1.1513D, player.getScaledDeviation(), ERROR_EPSILON);

		// opponent 1
		Assert.assertEquals(-0.5756D, opponent1.getScaledRating(), ERROR_EPSILON);
		Assert.assertEquals(0.1727D, opponent1.getScaledDeviation(), ERROR_EPSILON);
		Assert.assertEquals(0.9955D, Glicko2.g(opponent1.getScaledDeviation()), ERROR_EPSILON);
		Assert.assertEquals(0.639D, Glicko2.E(player, opponent1), ERROR_EPSILON3);

		// opponent 2
		Assert.assertEquals(0.2878D, opponent2.getScaledRating(), ERROR_EPSILON);
		Assert.assertEquals(0.5756D, opponent2.getScaledDeviation(), ERROR_EPSILON);
		Assert.assertEquals(0.9531D, Glicko2.g(opponent2.getScaledDeviation()), ERROR_EPSILON);
		Assert.assertEquals(0.432D, Glicko2.E(player, opponent2), ERROR_EPSILON3);

		// opponent 3
		Assert.assertEquals(1.1513D, opponent3.getScaledRating(), ERROR_EPSILON);
		Assert.assertEquals(1.7269D, opponent3.getScaledDeviation(), ERROR_EPSILON);
		Assert.assertEquals(0.7242D, Glicko2.g(opponent3.getScaledDeviation()), ERROR_EPSILON);
		Assert.assertEquals(0.303D, Glicko2.E(player, opponent3), ERROR_EPSILON3);

		List<Rating> opponents = Arrays.asList(opponent1, opponent2, opponent3);
		List<Double> scores = Arrays.asList(new Double(1.0), new Double(0.0), new Double(0.0));

		Glicko2.updateRating(player, opponents, scores);

		Assert.assertEquals(1464.06D, player.getRating(), ERROR_EPSILON2);
		Assert.assertEquals(151.52D, player.getDeviation(), ERROR_EPSILON2);
		Assert.assertEquals(0.05999D, player.getVolatility(), ERROR_EPSILON5);
	}
}
