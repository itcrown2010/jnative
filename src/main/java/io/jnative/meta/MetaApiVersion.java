package io.jnative.meta;

/** Stable Compiler Extension API version for annotation processors (0.32).
 * @author 张晓达
 */
public final class MetaApiVersion {
    public static final int CURRENT = 1;
    public static final String NAME = "JNATIVE_PROCESSOR_API_VERSION";

    private MetaApiVersion() {
    }

    public static void checkCompatible(int minApi, int maxApi) {
        if (CURRENT < minApi || (maxApi > 0 && CURRENT > maxApi)) {
            throw new IncompatibleProcessorApiException(
                    "processor requires Compiler Meta API "
                            + minApi
                            + (maxApi > 0 ? ".." + maxApi : "+")
                            + "; current compiler provides API "
                            + CURRENT);
        }
    }
}
