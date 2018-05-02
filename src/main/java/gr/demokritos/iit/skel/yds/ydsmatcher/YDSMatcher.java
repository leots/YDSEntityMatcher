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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author ggianna
 */
public class YDSMatcher {

    public static void main(String[] args) throws IOException {

        // Maximum list length parameter
        int iMaxListSize = Integer.MAX_VALUE;
        if (args.length > 0) {
            iMaxListSize = Integer.valueOf(args[0]);
            System.err.println("WARNING: Using only " + iMaxListSize + " records,"
                    + " due to command line limit argument...");
        }

        double dThreshold = 0.8;
        if (args.length > 1) {
            dThreshold = Double.valueOf(args[1]);
            System.err.println(
                    String.format("INFO: Set threshold to %6.4f.", dThreshold));
        }

        // Read entities
        EntityCSVReader ecrReader =
                new EntityCSVReader("./Data/YDS TED big sellers to match - companies to match.csv");
        ecrReader.setAttributeNamesInFirstRow(true);
        ecrReader.setAttributesToExclude(new int[]{0, 3, 4, 5}); // Ignore seller, contracts, amount, buyers
        List<EntityProfile> lpEntities = ecrReader.getEntityProfiles();
        lpEntities = lpEntities.subList(0, Math.min(iMaxListSize, lpEntities.size()));

        // Open file to write JSON result to
        BufferedWriter writer = new BufferedWriter(new FileWriter("out.json", false));

        // Create map that will contain company name -> line mappings (same as the exported JSON file)
        Map<String, List<Integer>> namesToLines = new HashMap<>();

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

        // Start JSON-like output
        System.out.println("[");
        writer.write("[");

        // Variables for adding comma between JSON array items
        boolean firstRow = true;
        int counter = 0;

        // Show clusters
        // For every cluster
        for (EquivalenceCluster ecCur : lClusters) {
            counter++;

            // If empty, warn and continue with next
            if (ecCur.getEntityIdsD1().isEmpty()) {
                // System.err.println("WARNING: Empty cluster. Ignoring...");
                continue;
            }

            // New cluster
            System.err.println("--- Cluster " + ecCur.toString() + " :");
            StringBuffer sbCluster = new StringBuffer();

            TIntList liFirst = ecCur.getEntityIdsD1();

            // Second list not applicable in "dirty list" scenario
            // Using only first list
            TIntIterator li1 = liFirst.iterator();
            EntityProfile eCur = null;

            // For each entity in cluster (only
            while (li1.hasNext()) {
                // get index
                int i1 = li1.next();

                // get entities
                EntityProfile ep1 = lpEntities.get(i1);

                // Output profiles
                System.err.println(String.format("Entity Line: %d --- Info: \n%s",
                        i1 + 1, entityProfileToString(ep1)));

                // Show line as 1-index based + 1 (for header line)
                sbCluster.append(i1 + 2).append(",");
                eCur = ep1; // Keep last info
            }

            // Start creating output JSON line, adding comma between json array items (not before first/after last)
            String outputLine = "";
            if (!firstRow && counter < lClusters.size() - 1) {
                outputLine += ",";
            }
            firstRow = false;

            // Create actual JSON array item data
            String sClusterIndices = sbCluster.toString().substring(0, sbCluster.toString().length() - 1);
            outputLine += "\n{" + entityProfileToString(eCur) + " \"lines\":[" + sClusterIndices + "]}";

            // Write the JSON array item to file
            System.out.println(outputLine);
            writer.write(outputLine);

            // Add to namesToLines map
            String companyName = getAttributeValue(eCur.getAttributes(), "company name");
            if (companyName == null) {
                // This shouldn't happen...
                continue;
            }

            // Get cluster indices
            List<Integer> clusterIndices = new ArrayList<>();
            for (String s : sClusterIndices.split(",")) {
                clusterIndices.add(Integer.parseInt(s));
            }

            // Add the indices to the map
            namesToLines.put(companyName, clusterIndices);
        }

        // End JSON-like output
        System.out.println("]");
        writer.write("\n]\n");

        writer.close();
    }

    public static String entityProfileToString(EntityProfile epToRender) {
        StringBuffer sb = new StringBuffer();

        for (Attribute aCur : attributeSetToSortableAttributeSet(epToRender.getAttributes())) {
            sb.append("\"").append(aCur.getName()).append("\":\"");
            sb.append(aCur.getValue()).append("\",\n");
        }

        return sb.toString();
    }

    protected static String getAttributeValue(Set<Attribute> attrs, String attrName) {
        String attrValue = null;
        // Find company name
        for (Attribute a : attrs) {
            if (a.getName().equals(attrName)) {
                attrValue = a.getValue();
            }
        }

        return attrValue;
    }

    protected static SortedSet<Attribute> attributeSetToSortableAttributeSet(Set<Attribute> toSort) {
        SortedSet<Attribute> sRes = new TreeSet<>();
        for (Attribute aCur : toSort)
            sRes.add(new SortableAttribute(aCur));

        return sRes;
    }

    protected static class SortableAttribute extends Attribute implements Comparable<Attribute> {

        public SortableAttribute(Attribute a) {
            super(a.getName(), a.getValue());
        }

        public int compareTo(Attribute t) {
            return getName().compareTo(t.getName());
        }

    }
}
