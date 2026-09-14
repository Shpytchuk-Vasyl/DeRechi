package org.shpytchuk.automaticsearch.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;
import org.shpytchuk.automaticsearch.language.SearchLanguage;

public class FullTextFunctionContributor implements FunctionContributor {

    private static final String PATTERN_TEMPLATE =
            "ts_rank(to_tsvector('%1$s', ?1), plainto_tsquery('%1$s', ?2))";

    @Override
    public void contributeFunctions(FunctionContributions functions) {
        var returnType = functions.getTypeConfiguration()
                .getBasicTypeRegistry()
                .resolve(StandardBasicTypes.DOUBLE);

        for (SearchLanguage language : SearchLanguage.values()) {
            functions.getFunctionRegistry().registerPattern(
                    language.rankFunction(),
                    PATTERN_TEMPLATE.formatted(language.regconfig()),
                    returnType);
        }
    }

    @Override
    public int ordinal() {
        return 1001;
    }
}
