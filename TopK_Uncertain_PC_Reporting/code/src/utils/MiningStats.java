package utils;
public final class MiningStats {
    public long metadataJoins,metadataPairs,capChecks,capRejections,capRejectedPairs;
    public long tidPairMerges,tidRecordsMerged,probabilityMultiplications,branchPrunes,heapOffers;
    public long eligiblePairs, exactCandidateJoins;
    public int maxDepth;
    public String toJson() {
        return "{\"metadataJoins\":"+metadataJoins+",\"metadataPairs\":"+metadataPairs
            +",\"capChecks\":"+capChecks+",\"capRejections\":"+capRejections+",\"capRejectedPairs\":"+capRejectedPairs
            +",\"tidPairMerges\":"+tidPairMerges+",\"tidRecordsMerged\":"+tidRecordsMerged
            +",\"probabilityMultiplications\":"+probabilityMultiplications+",\"branchPrunes\":"+branchPrunes
            +",\"heapOffers\":"+heapOffers+",\"eligiblePairs\":"+eligiblePairs+",\"exactCandidateJoins\":"+exactCandidateJoins+",\"maxDepth\":"+maxDepth+"}";
    }
}
