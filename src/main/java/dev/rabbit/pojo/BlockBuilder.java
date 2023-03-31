package dev.rabbit.pojo;

public enum BlockBuilder {
    FLASHBOTS("https://relay.flashbots.net/", true),
    BEAVER_BUILD("https://rpc.beaverbuild.org/", false),
    BUILDER_69("https://builder0x69.io/", false),
    RSYNC("https://rsync-builder.xyz/", false),
    BLOCKNATIVE("https://api.blocknative.com/v1/auction", true),
    ETH_BUILDER("https://eth-builder.com/", false),
    BUILD_AI("https://buildai.net/", false),
    PAYLOAD_DOT_DE("https://rpc.payload.de", true),
    LIGHT_SPEED_BUILDER("https://rpc.lightspeedbuilder.info/", false),
    EDEN_NETWORK("https://api.edennetwork.io/v1/bundle", true),
    MANIFOLD_FINANCE("https://api.securerpc.com/v1", false),
    N_FACTORIAL("https://rpc.nfactorial.xyz/", false);

    private final String url;
    private final boolean withSignatureHeader;

    BlockBuilder(String url, boolean withSignatureHeader) {
        this.url = url;
        this.withSignatureHeader = withSignatureHeader;
    }

    public String getUrl() {
        return url;
    }

    public boolean isWithSignatureHeader() {
        return withSignatureHeader;
    }
}
