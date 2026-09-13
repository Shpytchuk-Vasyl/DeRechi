package org.shpytchuk.automaticsearch.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

public class FullTextFunctionContributor implements FunctionContributor {

    public static final String TS_RANK_SIMPLE = "ts_rank_simple";

    private static final String PATTERN =
            "ts_rank(to_tsvector('simple', ?1), plainto_tsquery('simple', ?2))";

    @Override
    public void contributeFunctions(FunctionContributions functions) {
        functions.getFunctionRegistry().registerPattern(
                TS_RANK_SIMPLE,
                PATTERN,
                functions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.DOUBLE));
    }

    @Override
    public int ordinal() {
        return 1000;
    }
}
