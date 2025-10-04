package org.example;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SimpleHyperLogLog {
    private final int numBuckets; // number of buckets must be a power of 2
    private final int[] buckets; // array stores the rank (leading zeros + 1) for each bucket

    public SimpleHyperLogLog(int numBuckets) {
        this.numBuckets = numBuckets;
        this.buckets = new int[numBuckets];
    }

    private BigInteger hash(String value) {
        try {
            // we use MD5 because it produces well-distributed hash values across the
            // 128-bit space (any input -> one of 2^128 possible outputs) and the function
            // spreads its outputs over the entire space
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(value.getBytes());
            return new BigInteger(1, digest); // convert byte array to positive BigInteger
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    public void add(String value) {
        // generate a 128-bit hash value for the input value
        BigInteger hashValue = hash(value);

        // calculate the number of bits to use for the bucket index
        // log2(x) = ln(x) / ln(2) -> Math.log(x) == ln(x)
        int bucketBits = (int) (Math.log(numBuckets) / Math.log(2));

        // Use the rightmost bits for bucket index
        int bucket = hashValue.and(BigInteger.valueOf(numBuckets - 1)).intValue();

        // Use remaining bits for rank calculation
        BigInteger w = hashValue.shiftRight(bucketBits);

        // Count leading zeros + 1 for rank
        int rank = (w.equals(BigInteger.ZERO)) ? (128 - bucketBits + 1) : (128 - bucketBits - w.bitLength() + 1);

        buckets[bucket] = Math.max(buckets[bucket], rank);
    }

    public long count() {
        double sum = 0.0;
        int zeroRegisters = 0;

        for (int b : buckets) {
            sum += Math.pow(2.0, -b);
            if (b == 0) {
                zeroRegisters++;
            }
        }

        // Alpha is a bias correction constant that compensates for systematic errors in
        // the estimation formula
        double alpha;
        if (numBuckets >= 128) {
            alpha = 0.7213 / (1 + 1.079 / numBuckets);
        } else if (numBuckets >= 64) {
            alpha = 0.709;
        } else if (numBuckets >= 32) {
            alpha = 0.697;
        } else if (numBuckets >= 16) {
            alpha = 0.673;
        } else {
            alpha = 0.5;
        }

        double estimate = alpha * numBuckets * numBuckets / sum;

        // Small range correction for when many registers are zero
        if (estimate <= 2.5 * numBuckets && zeroRegisters > 0) {
            estimate = numBuckets * Math.log((double) numBuckets / zeroRegisters);
        }

        return Math.round(estimate);
    }
}
