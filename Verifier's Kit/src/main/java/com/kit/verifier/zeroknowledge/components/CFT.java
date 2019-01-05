package com.kit.verifier.zeroknowledge.components;

import com.kit.verifier.zeroknowledge.TTPGenerator;
import com.kit.verifier.zeroknowledge.dto.CFTProof;
import com.kit.verifier.zeroknowledge.exception.ZeroKnowledgeException;
import com.kit.verifier.zeroknowledge.util.BigIntUtil;
import com.kit.verifier.zeroknowledge.util.DigestUtil;
import org.bouncycastle.util.BigIntegers;

import java.math.BigInteger;
import java.security.SecureRandom;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

/**
 * Proof that a commitment contains an integer in [−2^(t+l)*b, 2^(t+l)*b]
 *
 * This protocol is described in section 1.2.3 in the following paper:
 * Fabrice Boudot, Efficient Proofs that a Committed Number Lies in an Interval
 */
public class CFT {

    // Security parameters
    public static final int t = 128;  // Parameter for soundness (bitlength of hash divided by 2)
    public static final int l = 40;   // Parameter for zero-knowledge property
    public static final int s = TTPGenerator.s; // Parameter for size of r in commitment
    public static final BigInteger TWO = BigInteger.valueOf(2);

    public static CFTProof calculateProof(
            BigInteger b,   // Maximum number to hide
            BigInteger N,   // large composite number whose factorization is unknown by Alice and Bob
            BigInteger g,   // element of large order in Zn*
            BigInteger h,   // elements of the group generated by g1 such that discrete logarithms are unknown
            BigInteger x,   // the secretly committed number
            BigInteger r,   // random number used in commitment (secret)
            SecureRandom random) {

        if (x.compareTo(b) > 0) {
            throw new IllegalArgumentException("Committed number is larger than maximum");
        }

        BigInteger C, c, D1, D2;
        do {
            // Step 1
            BigInteger w = BigIntegers.createRandomInRange(ZERO, TWO.pow(t + l).multiply(b), random);
            BigInteger n = BigIntUtil.randomSignedInt(TWO.pow(t + l + s).multiply(N).subtract(ONE), random);

            BigInteger W = g.modPow(w, N).multiply(h.modPow(n, N)).mod(N); // g^w h^n

            // Step 2
            C = DigestUtil.calculateHash(W);
            c = C.mod(TWO.pow(t));

            // Step 3
            D1 = w.add(c.multiply(x)); // w + cx
            D2 = n.add(c.multiply(r)); // n + cr
        } while (!isValidD1(D1, c, b));

        return new CFTProof(C, D1, D2);
    }

    // Check that D1 is in range [cb, 2^(t+l) b]
    // w + cx < cb would reveal x is smaller than the maximum hidden value
    // w + cx > 2^(t+l) b would allow x larger than 2^(t+l)*b
    private static boolean isValidD1(BigInteger D1, BigInteger c, BigInteger b) {
        return D1.compareTo(c.multiply(b)) >= 0 && D1.compareTo(TWO.pow(l + t).multiply(b)) <= 0;
    }

    public static void validateZeroKnowledgeProof(BigInteger b, BigInteger N, BigInteger g, BigInteger h, BigInteger E, CFTProof cftProof) {

        if (E.equals(BigInteger.ZERO)) {
            // To prevent failure at 0 ^ -c
            throw new ZeroKnowledgeException("Zero-knowledge proof validation failed");
        }

        BigInteger C = cftProof.getC();
        BigInteger c = C.mod(TWO.pow(t));
        BigInteger D1 = cftProof.getD1();
        BigInteger D2 = cftProof.getD2();

        BigInteger W = g.modPow(D1, N).multiply(h.modPow(D2, N)).multiply(E.modPow(c.negate(), N)).mod(N); // g1^D h1^D1 E^-c

        if (!isValidD1(D1, c, b) || !C.equals(DigestUtil.calculateHash(W))) {
            throw new ZeroKnowledgeException("Zero-knowledge proof validation failed");
        }
    }
}
