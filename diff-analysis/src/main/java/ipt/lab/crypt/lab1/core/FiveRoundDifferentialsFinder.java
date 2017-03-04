package ipt.lab.crypt.lab1.core;

import ipt.lab.crypt.lab1.Constants;
import ipt.lab.crypt.lab1.core.blockgeneration.BlocksDistributor;
import ipt.lab.crypt.lab1.core.branchbound.BranchAndBound;
import ipt.lab.crypt.lab1.datastructures.DiffPairProb;
import ipt.lab.crypt.lab1.datastructures.DiffProb;
import ipt.lab.crypt.lab1.probsource.DiffProbTableSource;
import ipt.lab.crypt.lab1.probsource.FileDiffPropTableSource;
import ipt.lab.crypt.lab1.utils.PrintUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.io.IOException;
import java.util.*;

import static ipt.lab.crypt.lab1.Constants.BLOCKS_NUMBER;

public class FiveRoundDifferentialsFinder {

    private static final Comparator<DiffProb> descByProbComparator = (lv, rv) -> Double.compare(rv.getProb(), lv.getProb());

    public static final Random rand = new Random();

    public static void main(String[] args) throws IOException {
        DiffProbTableSource source = new FileDiffPropTableSource();
        long[][] roundDiffProbs = source.getDiffProbTable(Constants.VARIANT);

        System.out.println("Deserialized");

        BranchAndBound bab = new BranchAndBound(roundDiffProbs/*, new ProbabilityThresholdStrategy(1.0 / (65535.0))*/);
        BlocksDistributor blocks = new BlocksDistributor(2);

        for (int i = 0; i < 1; i++) {
            new Thread(() -> {
                DiffPairProb threadMax = null;

                StopWatch sw = new StopWatch();

                int iteration = 0;
                while (true) {
                    iteration++;

                    Optional<Integer> diffHolder = blocks.getIfAvailable();

                    if (!diffHolder.isPresent()) {
                        break;
                    }

                    int diff = diffHolder.get();

                    sw.start();
                    double[] diffProbs = bab.differentialSearch(diff);
                    sw.stop();

                    DiffProb max = findMax(diffProbs);

                    if (threadMax == null || max.getProb() > threadMax.getProb()) {
                        threadMax = new DiffPairProb(diff, max.getDiff(), max.getProb());
                    }


                    String result = new Formatter(new StringBuilder(100))
                            .format(
                                    "[Thread = %s] i = %d, a = %s, b = %s, p = %.8f, threadMax = (%s), time = %d",
                                    Thread.currentThread().getName(),
                                    iteration,
                                    PrintUtils.toHexAsShort(diff),
                                    PrintUtils.toHexAsShort(max.getDiff()),
                                    max.getProb(),
                                    threadMax,
                                    sw.getTime()
                            ).toString();

                    System.out.println(result);

                    sw.reset();
                }
            }).start();
        }
    }

    private static DiffProb findMax(double[] diffProbs) {
        int maxDiff = 1;
        double maxProb = diffProbs[1];

        for (int diff = 2; diff < BLOCKS_NUMBER; diff++) {
            if (diffProbs[diff] > maxProb) {
                maxProb = diffProbs[diff];
                maxDiff = diff;
            }
        }

        return new DiffProb(maxDiff, maxProb);
    }

    private static List<DiffProb> toSortedList(double[] diffProbs) {
        List<DiffProb> probsList = new ArrayList<>(diffProbs.length);

        for (int diff = 0; diff < diffProbs.length; diff++) {
            if (diffProbs[diff] > 0) {
                probsList.add(new DiffProb(diff, diffProbs[diff]));
            }
        }

        probsList.sort(descByProbComparator);

        return probsList;
    }
}
