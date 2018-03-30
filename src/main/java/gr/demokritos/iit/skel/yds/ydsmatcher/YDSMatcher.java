/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gr.demokritos.iit.skel.yds.ydsmatcher;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import org.scify.jedai.blockbuilding.IBlockBuilding;
import org.scify.jedai.blockbuilding.StandardBlocking;
import org.scify.jedai.blockprocessing.IBlockProcessing;
import org.scify.jedai.blockprocessing.blockcleaning.SizeBasedBlockPurging;
import org.scify.jedai.blockprocessing.comparisoncleaning.WeightedEdgePruning;
import org.scify.jedai.datamodel.*;
import org.scify.jedai.datareader.entityreader.EntityCSVReader;
import org.scify.jedai.entityclustering.IEntityClustering;
import org.scify.jedai.entityclustering.RicochetSRClustering;
import org.scify.jedai.entitymatching.ProfileMatcher;
import org.scify.jedai.utilities.enumerations.RepresentationModel;
import org.scify.jedai.utilities.enumerations.SimilarityMetric;

import java.util.List;

/**
 * @author ggianna
 */
public class YDSMatcher {

    public static void main(String[] args) {

        // Maximum list length parameter
        int iMaxListSize = Integer.MAX_VALUE;
        if (args.length > 0) {
            iMaxListSize = Integer.valueOf(args[0]);
            System.err.println("WARNING: Using only " + iMaxListSize + " records,"
                    + " due to command line limit argument...");
        }

        // Read entities
        EntityCSVReader ecrReader = new EntityCSVReader("./Data/YDS TED big sellers to match - companies to match.csv");
        ecrReader.setAttributeNamesInFirstRow(true);
        List<EntityProfile> lpEntities = ecrReader.getEntityProfiles().subList(0, iMaxListSize);

        // TODO: Cache results
        boolean bCacheOK = false;
        SimilarityPairs lspPairs = null;
        if (!bCacheOK) {
            // Create and process blocks
            IBlockBuilding block = new StandardBlocking();
            List<AbstractBlock> lbBlocks = block.getBlocks(lpEntities);

            IBlockProcessing bpProcessor = new SizeBasedBlockPurging();
            lbBlocks = bpProcessor.refineBlocks(lbBlocks);

            IBlockProcessing bpComparisonCleaning = new WeightedEdgePruning();
            lbBlocks = bpComparisonCleaning.refineBlocks(lbBlocks);

            // Measure similarities
            ProfileMatcher pm = new ProfileMatcher(RepresentationModel.CHARACTER_TRIGRAMS,
                    SimilarityMetric.COSINE_SIMILARITY);
            lspPairs = pm.executeComparisons(lbBlocks, lpEntities);
        }

        // Perform clustering
        IEntityClustering ie = new RicochetSRClustering();
        List<EquivalenceCluster> lClusters = ie.getDuplicates(lspPairs);

        // Show clusters
        // For every cluster
        for (EquivalenceCluster ecCur : lClusters) {
            System.out.println("--- Cluster " + ecCur.toString() + " :");
            TIntList liFirst = ecCur.getEntityIdsD1();

            // Second list not applicable in "dirty list" scenario
            // Using only first list
            TIntIterator li1 = liFirst.iterator();

            // For each entity in cluster (only 
            while (li1.hasNext()) {
                // get index
                int i1 = li1.next();

                // get entities
                EntityProfile ep1 = lpEntities.get(i1);

                // Output profiles
                System.out.println(entityProfileToString(ep1));
            }
        }

    }

    public static String entityProfileToString(EntityProfile epToRender) {
        StringBuffer sb = new StringBuffer();

        for (Attribute aCur : epToRender.getAttributes()) {
            sb.append("\t").append(aCur.getName()).append("=");
            sb.append(aCur.getValue()).append("\n");
        }

        return sb.toString();
    }
}
