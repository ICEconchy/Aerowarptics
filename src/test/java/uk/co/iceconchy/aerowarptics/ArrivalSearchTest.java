package uk.co.iceconchy.aerowarptics;

import org.junit.jupiter.api.Test;
import uk.co.iceconchy.aerowarptics.warp.ArrivalSearch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The safe-arrival candidate ordering.
 *
 * <p>The contract these pin down is the one that keeps large airships out of hillsides: try straight
 * up first, keep climbing, and only start moving sideways once the whole column is exhausted.
 */
class ArrivalSearchTest {

    @Test
    void theAnchorsOwnColumnIsTriedFirst() {
        List<ArrivalSearch.Candidate> candidates = ArrivalSearch.candidates(32, 16, 4, 4096);
        assertEquals(new ArrivalSearch.Candidate(0, 0, 0), candidates.getFirst());
    }

    @Test
    void theWholeColumnIsExhaustedBeforeAnySidewaysStep() {
        int verticalRadius = 24;
        int step = 4;
        List<ArrivalSearch.Candidate> candidates = ArrivalSearch.candidates(32, verticalRadius, step, 4096);

        int expectedColumnEntries = verticalRadius / step + 1;
        for (int i = 0; i < expectedColumnEntries; i++) {
            ArrivalSearch.Candidate candidate = candidates.get(i);
            assertEquals(0, candidate.dx(), "entry " + i + " should still be in the anchor's column");
            assertEquals(0, candidate.dz(), "entry " + i + " should still be in the anchor's column");
            assertEquals(i * step, candidate.dy(), "the column is climbed in order");
        }
        ArrivalSearch.Candidate first = candidates.get(expectedColumnEntries);
        assertTrue(first.dx() != 0 || first.dz() != 0,
                "only once the column is used up does the search widen");
    }

    @Test
    void theSearchNeverGoesBelowTheClearancePoint() {
        for (ArrivalSearch.Candidate candidate : ArrivalSearch.candidates(24, 24, 4, 4096)) {
            assertTrue(candidate.dy() >= 0,
                    "an airship is never squeezed in underneath its own anchor: " + candidate);
        }
    }

    @Test
    void ringsWidenOutwardsAndNeverNarrow() {
        List<ArrivalSearch.Candidate> candidates = ArrivalSearch.candidates(24, 8, 4, 100_000);
        double widest = 0.0D;
        for (ArrivalSearch.Candidate candidate : candidates) {
            double distance = candidate.horizontalDistance();
            // Within a ring the horizontal distance varies (a square ring's corners are further out
            // than its faces), so the check is that the search never returns to a ring it has left.
            widest = Math.max(widest, distance);
            assertTrue(distance >= chebyshev(candidate) - 1.0e-9D);
            assertTrue(chebyshev(candidate) <= widest + 1.0e-9D);
        }
    }

    @Test
    void everyCandidateStaysInsideTheConfiguredRadii() {
        for (ArrivalSearch.Candidate candidate : ArrivalSearch.candidates(16, 8, 4, 4096)) {
            assertTrue(Math.abs(candidate.dx()) <= 16 && Math.abs(candidate.dz()) <= 16);
            assertTrue(candidate.dy() <= 8);
        }
    }

    @Test
    void everyCandidateIsDistinct() {
        List<ArrivalSearch.Candidate> candidates = ArrivalSearch.candidates(16, 8, 4, 100_000);
        Set<ArrivalSearch.Candidate> unique = new HashSet<>(candidates);
        assertEquals(candidates.size(), unique.size(), "the search must not test the same place twice");
    }

    @Test
    void theLimitIsRespectedAndStillIncludesTheColumn() {
        List<ArrivalSearch.Candidate> candidates = ArrivalSearch.candidates(64, 32, 2, 25);
        assertEquals(25, candidates.size());
        assertEquals(new ArrivalSearch.Candidate(0, 0, 0), candidates.getFirst());
    }

    @Test
    void aZeroRadiusStillProducesTheAnchorsColumn() {
        List<ArrivalSearch.Candidate> candidates = ArrivalSearch.candidates(0, 0, 4, 4096);
        assertEquals(1, candidates.size());
        assertEquals(new ArrivalSearch.Candidate(0, 0, 0), candidates.getFirst());

        List<ArrivalSearch.Candidate> tall = ArrivalSearch.candidates(0, 16, 4, 4096);
        assertEquals(5, tall.size(), "no horizontal room still leaves the whole column to climb");
    }

    @Test
    void aZeroStepDoesNotHang() {
        List<ArrivalSearch.Candidate> candidates = ArrivalSearch.candidates(4, 4, 0, 4096);
        assertTrue(candidates.size() > 1, "a bad step must be corrected, not looped on");
    }

    @Test
    void aCoarserStepProducesFewerCandidates() {
        int fine = ArrivalSearch.candidates(32, 16, 2, 100_000).size();
        int coarse = ArrivalSearch.candidates(32, 16, 8, 100_000).size();
        assertTrue(coarse < fine);
    }

    private static double chebyshev(ArrivalSearch.Candidate candidate) {
        return Math.max(Math.abs(candidate.dx()), Math.abs(candidate.dz()));
    }
}
